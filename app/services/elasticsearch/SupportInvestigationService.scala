package services.elasticsearch

import java.util
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.{Assessment, StudentAssessment}
import javax.inject.Inject
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.streaming.{SXSSFSheet, SXSSFWorkbook}
import org.elasticsearch.action.search.{SearchRequest, SearchScrollRequest}
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilder, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.{Scroll, SearchHit}
import services.{AssessmentService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, User, UserLookupService}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.SetHasAsScala

@ImplementedBy(classOf[SupportInvestigationServiceImpl])
trait SupportInvestigationService {
  def produceWorkbook(assessmentIdText: String, universityId: String, currentUser: User)(implicit t: TimingContext): Future[ServiceResult[SXSSFWorkbook]]
}

class SupportInvestigationServiceImpl @Inject()(
  client: ESClientConfig,
  studentAssessmentService: StudentAssessmentService,
  assessmentService: AssessmentService,
  userLookup: UserLookupService
)(implicit ec: ExecutionContext) extends SupportInvestigationService {

  private val oneMinute = TimeValue.timeValueMinutes(1L)

  import warwick.core.helpers.ServiceResults.Implicits._

  val maxSize = 5000

  var fetchFieldsForAuditIndex: Array[String] = Array[String]("@timestamp", "username", "user-agent-detail.*", "request_headers.user-agent", "event_type", "source_ip", "onlineexams.*")
  var fetchFieldsForAccessIndex: Array[String] = Array[String]("@timestamp", "username", "user-agent-detail.*", "request_headers.*", "status_code", "response_headers.*", "geoip.*", "source_ip", "requested_uri", "method", "elapsed_time", "log_source_hostname")

  private def addAccessSheet(wb: SXSSFWorkbook, auditResult: AuditSheetResult, assessment: Assessment): Unit = {
    val start = assessment.startTime.get.minusHours(1)
    val end = assessment.lastAllowedStartTime.get.plusHours(1)
    val disjunctiveBuilder = QueryBuilders.boolQuery().minimumShouldMatch(1)

    if (auditResult.username.isDefined) {
      disjunctiveBuilder.should(QueryBuilders.matchQuery("username", auditResult.username.get))
    }

    auditResult.possibleIps.map(ip => QueryBuilders.matchQuery("source_ip", ip)).foreach(mqb => {
      disjunctiveBuilder.should(mqb)
    })

    val query = QueryBuilders.boolQuery
      .must(QueryBuilders.termQuery("node_deployment", "prod"))
      .must(QueryBuilders.termQuery("node_app", "onlineexams"))
      .must(disjunctiveBuilder).must(QueryBuilders.rangeQuery("@timestamp").from(start).to(end))

    addQueryToSpreadsheet(
      wb.createSheet("Access"),
      query,
      fetchFieldsForAccessIndex,
      new SearchRequest("web-development-access")
    )
   }

  override def produceWorkbook(assessmentIdText: String, uniIdStr: String, currentUser: User)(implicit t: TimingContext): Future[ServiceResult[SXSSFWorkbook]] = {
    val assessmentId = UUID.fromString(assessmentIdText)
    val uniId = UniversityID(uniIdStr)
    ServiceResults.zip(studentAssessmentService.get(uniId, assessmentId), assessmentService.get(assessmentId)).successFlatMapTo { case (sa, assessment) =>
      val wb = new SXSSFWorkbook
      val coreProp = wb.getXSSFWorkbook.getProperties.getCoreProperties
      coreProp.setCreator("AEP, exported by " + currentUser.usercode)
      coreProp.setTitle(assessmentId.toString + "_" + uniIdStr)
      val auditResult = supplyUsernameIfUnavailable(addAuditSheet(wb, assessment, sa), uniId)

      if (auditResult.possibleIps.nonEmpty || auditResult.username.nonEmpty) {
        addAccessSheet(wb, auditResult, assessment)
      }

      Future(ServiceResults.success(wb))
    }
  }

  private def supplyUsernameIfUnavailable(auditResult: AuditSheetResult, uniId: UniversityID): AuditSheetResult = {
    if (auditResult.username.isEmpty) {
      val userLookupResponse = userLookup.getUsers(Seq(uniId), includeDisabled = true)
      if (userLookupResponse.isSuccess && userLookupResponse.get.contains(uniId)) {
        AuditSheetResult(auditResult.sheet, auditResult.possibleIps, Some(userLookupResponse.get(uniId).usercode.toString))
      }
    }
    auditResult
  }

  private def addAuditSheet(wb: SXSSFWorkbook, assessment: Assessment, sa: StudentAssessment): AuditSheetResult = {
    val saId = sa.id.toString
    val start = assessment.startTime.get
    val end = assessment.lastAllowedStartTime.get
    val query: BoolQueryBuilder = QueryBuilders.boolQuery
      .must(QueryBuilders.termQuery("node_deployment", "prod"))
      .must(QueryBuilders.termQuery("node_app", "onlineexams"))
      .must(QueryBuilders.boolQuery().minimumShouldMatch(1)
        .should(QueryBuilders.matchQuery("onlineexams.StudentAssessment.keyword", saId))
        .should(QueryBuilders.matchQuery("onlineexams.StudentAssessment.keyword", Some(saId).toString))
        .should(QueryBuilders.matchQuery("onlineexams.studentAssessmentId.keyword", saId))
        .should(QueryBuilders.matchQuery("onlineexams.Declarations.keyword", saId))
      ).must(QueryBuilders.rangeQuery("@timestamp").from(start).to(end))
    val sheetName = "Audit"
    val fetchFields = fetchFieldsForAuditIndex

    val queryToSpreadsheetResult: TransformSearchToSpreadsheet = addQueryToSpreadsheet(
      wb.createSheet(sheetName),
      query,
      fetchFields,
      new SearchRequest("web-development-audit")
    )
    val hits: ListBuffer[SearchHit] = queryToSpreadsheetResult.hits
    val sheet: SXSSFSheet = queryToSpreadsheetResult.sheet
    val ips = hits.map(h => h.getSourceAsMap.getOrDefault("source_ip", null).toString).filterNot(v => v == null).distinct.toList
    val username = hits.filter(h => h.getSourceAsMap != null && h.getSourceAsMap.containsKey("username")).collectFirst(h => h.getSourceAsMap.get("username").toString)
    AuditSheetResult(sheet, ips, username)
  }

  private def prioritiseSomeFields(s: String): Int = s match {
    case "@timestamp" => 0
    case "username" => 1
    case "source_ip" => 2
    case "event_type" => 3
    case "method" => 3
    case "requested_uri" => 4
    case "status_code" => 5
    case "user-agent-detail.os" => 6
    case "user-agent-detail.name" => 7
    case "user-agent-detail.major" => 8
    case _ => 9
  }

  private def addQueryToSpreadsheet(sheet: SXSSFSheet, query: QueryBuilder, fetchFields: Array[String], searchRequest: SearchRequest) = {
    val searchSourceBuilder = new SearchSourceBuilder
    val scroll = new Scroll(oneMinute)
    searchSourceBuilder.query(query)
    searchSourceBuilder.size(maxSize)
    searchSourceBuilder.timeout(oneMinute)
    searchSourceBuilder.fetchSource(fetchFields, null)
    searchRequest.source(searchSourceBuilder)
    searchRequest.scroll(scroll)
    val response = client.clogsClient.search(searchRequest, RequestOptions.DEFAULT)

    var searchHits = Option(response.getHits.getHits).getOrElse(Array.empty)
    var scrollId = Option(response.getScrollId)
    var fieldNamesSet = scala.collection.mutable.Set[String]()
    val hits = scala.collection.mutable.ListBuffer[SearchHit]()

    while (searchHits.nonEmpty) {
      searchHits.map(sh => {
        flatten(sh.getSourceAsMap, new util.HashMap[String, Object](), null).keySet()
      }).foreach(s => fieldNamesSet ++= s.asScala)
      hits.addAll(searchHits)
      val scrollRequest = new SearchScrollRequest(scrollId.getOrElse(""))
      scrollRequest.scroll(scroll)
      val searchResponse = client.clogsClient.scroll(scrollRequest, RequestOptions.DEFAULT)
      scrollId = Option(searchResponse.getScrollId)
      searchHits = Option(searchResponse.getHits.getHits).getOrElse(Array.empty)
    }

    val row = sheet.createRow(0)
    sheet.trackAllColumnsForAutoSizing()
    val sortedFieldNames = fieldNamesSet.toList.sortBy(prioritiseSomeFields)
    sortedFieldNames.zipWithIndex.foreach {
      case (name, i) =>
        row.createCell(i).setCellValue(name)
    }
    var lastRowNum = 0
    var lastCellIndex = 0
    hits.zipWithIndex.foreach {
      case (hit, i) =>
        val currentRow = sheet.createRow(i + 1)
        lastRowNum = i + 1
        sortedFieldNames.zipWithIndex.foreach {
          case (name, i) =>
            val flattened = flatten(hit.getSourceAsMap, new util.HashMap[String, Object](), null)
            if (flattened.containsKey(name)) {
              currentRow.createCell(i).setCellValue(flattened.get(name).toString)
            } else {
              currentRow.createCell(i).setCellValue("-")
            }
            lastCellIndex = math.max(i, lastCellIndex)
        }
    }

    for (i <- 0 until lastCellIndex) {
      sheet.autoSizeColumn(i)
    }

    sheet.setAutoFilter(new CellRangeAddress(0, lastRowNum, 0, lastCellIndex))
    new TransformSearchToSpreadsheet(hits, sheet)
  }

  private def flatten(map: java.util.Map[String, Object], output: java.util.Map[String, Object], key: String): java.util.Map[String, Object] = {
    var prefix = ""
    if (key != null) prefix = key + "."
    for (entry <- map.entrySet.asScala) {
      val currentKey = prefix + entry.getKey
      entry.getValue match {
        case _: util.Map[_, _] => flatten(entry.getValue.asInstanceOf[java.util.Map[String, Object]], output, prefix + entry.getKey)
        case _: util.List[_] => output.put(currentKey, entry.getValue)
        case _ => output.put(currentKey, entry.getValue)
      }
    }
    output
  }

  case class AuditSheetResult(sheet: SXSSFSheet, possibleIps: List[String], username: Option[String])
  case class TransformSearchToSpreadsheet(hits: collection.mutable.ListBuffer[SearchHit], sheet: SXSSFSheet)
}

