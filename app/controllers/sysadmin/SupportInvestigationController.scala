package controllers.sysadmin

import java.io.ByteArrayOutputStream
import java.util.UUID

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.elasticsearch.action.search.{SearchRequest, SearchScrollRequest}
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.{Scroll, SearchHit}
import services.elasticsearch.ESClientConfig
import services.{AssessmentService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.SetHasAsScala

@Singleton
class SupportInvestigationController @Inject()(
  client: ESClientConfig,
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  assessmentService: AssessmentService
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  private val oneMinute = TimeValue.timeValueMinutes(1L)

  def test() = RequireSysadmin.async { implicit req =>
    val assessmentId = UUID.fromString(req.getQueryString("assessment").get)
    ServiceResults.zip(studentAssessmentService.get(UniversityID(req.getQueryString("uniId").get), assessmentId), assessmentService.get(assessmentId)).successFlatMap { case (sa, assessment) =>
      val saId = sa.id.toString
      val searchRequest = new SearchRequest("web-development-audit")
      val searchSourceBuilder = new SearchSourceBuilder
      val scroll = new Scroll(oneMinute)
      val start = assessment.startTime.get
      val end = assessment.lastAllowedStartTime.get
      val query = QueryBuilders.boolQuery
        .must(QueryBuilders.termQuery("node_deployment", "prod"))
        .must(QueryBuilders.termQuery("node_app", "onlineexams"))
        .must(QueryBuilders.boolQuery().minimumShouldMatch(1)
          .should(QueryBuilders.matchQuery("onlineexams.StudentAssessment.keyword", saId))
          .should(QueryBuilders.matchQuery("onlineexams.StudentAssessment.keyword", Some(saId).toString))
          .should(QueryBuilders.matchQuery("onlineexams.studentAssessmentId.keyword", saId))
          .should(QueryBuilders.matchQuery("onlineexams.Declarations.keyword", saId))
        ).must(QueryBuilders.rangeQuery("@timestamp").from(start).to(end))
      searchSourceBuilder.query(query)
      searchSourceBuilder.size(5000)
      searchSourceBuilder.timeout(oneMinute)
      searchSourceBuilder.fetchSource(Array[String]("username", "user-agent-detail.device", "user-agent-detail.os", "request_headers.user-agent", "event_type", "source_ip", "onlineexams.*"), null)
      searchRequest.source(searchSourceBuilder)
      searchRequest.scroll(scroll)
      val response = client.clogsClient.search(searchRequest, RequestOptions.DEFAULT)

      var searchHits = Option(response.getHits.getHits).getOrElse(Array.empty)
      var scrollId = Option(response.getScrollId)
      var fieldNamesSet = scala.collection.mutable.Set[String]()
      var hits = scala.collection.mutable.ListBuffer[SearchHit]()

      while (searchHits.nonEmpty) {
        searchHits.map(sh => {
          flatten(sh.getSourceAsMap).keySet()
        }).foreach(s => fieldNamesSet ++= s.asScala)
        hits.addAll(searchHits)
        val scrollRequest = new SearchScrollRequest(scrollId.getOrElse(""))
        scrollRequest.scroll(scroll)
        val searchResponse = client.clogsClient.scroll(scrollRequest, RequestOptions.DEFAULT)
        scrollId = Option(searchResponse.getScrollId)
        searchHits = Option(searchResponse.getHits.getHits).getOrElse(Array.empty)
      }

      val wb = new SXSSFWorkbook
      val sheet = wb.createSheet("Audit")
      val row = sheet.createRow(0)
      fieldNamesSet.zipWithIndex.foreach {
        case (name, i) =>
          row.createCell(i).setCellValue(name)
      }

      hits.zipWithIndex.foreach {
        case (hit, i) =>
          val currentRow = sheet.createRow(i+1)
          fieldNamesSet.zipWithIndex.foreach {
            case (name, i) =>
              if (hit.getSourceAsMap.containsKey(name)) {
                currentRow.createCell(i).setCellValue(hit.getSourceAsMap.get(name).toString)
              } else {
                currentRow.createCell(i).setCellValue("-")
              }
          }
      }

      val responseStream: java.io.ByteArrayOutputStream = new ByteArrayOutputStream()
      wb.write(responseStream)

      Future(Ok(
        responseStream.toByteArray
      ).as("application/vnd.ms-excel").withHeaders("Content-Disposition" -> s"attachment; filename=filename.xls"))
    }

  }

  private def flatten(map: java.util.Map[String, Object], output: java.util.Map[String, Object], key: String): Unit = {
    var prefix = ""
    if (key != null) prefix = key + "."
    for (entry <- map.entrySet.asScala) {
      val currentKey = prefix + entry.getKey
      if (entry.getValue.isInstanceOf[java.util.Map]) flatten(entry.getValue.asInstanceOf[Nothing], output, prefix + entry.getKey)
      else if (entry.getValue.isInstanceOf[java.util.List]) output.put(currentKey, entry.getValue)
      else output.put(currentKey, entry.getValue)
    }
  }
}
