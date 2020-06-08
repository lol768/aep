package services.elasticsearch

import akka.http.scaladsl.model.Uri
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import play.api.Configuration

/**
  * Cribbed from My Warwick
  */
@ImplementedBy(classOf[ESClientConfigImpl])
trait ESClientConfig {
  def clogsClient: RestHighLevelClient
}

@Singleton
class ESClientConfigImpl @Inject()(
  config: Configuration
) extends ESClientConfig {


  override def clogsClient: RestHighLevelClient = {
    val clogsHttpHosts = ESNode
      .fromConfigStrings(config.get[Seq[String]]("clogs.nodes"))
      .map(_.httpHost)

    val credentialsProvider = new BasicCredentialsProvider
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.get[String]("clogs.user"), config.get[String]("clogs.password")))

    val clogsLowLevelBuilder = RestClient
      .builder(clogsHttpHosts.toArray: _*)
      .setMaxRetryTimeoutMillis(60000)
      .setHttpClientConfigCallback((configBuilder: HttpAsyncClientBuilder) => {
        configBuilder
          .setDefaultRequestConfig(
            RequestConfig.custom()
              .setSocketTimeout(60000)
              .setConnectTimeout(60000)
              .build()
          )
          .setMaxConnPerRoute(50)
          .setMaxConnTotal(200)
          .setDefaultCredentialsProvider(credentialsProvider)
      })

    new RestHighLevelClient(clogsLowLevelBuilder)
  }
}

case class ESNode(httpHost: HttpHost)

object ESNode {
  def fromConfigString(confString: String): ESNode = {
    val uri = confString match {
      case s if s.isEmpty => throw new IllegalArgumentException("Missing configuration parameter for ESNode")
      case s if s.startsWith("//") => Uri(s"http:$s")
      case s if s.contains("//") => Uri(s)
      case s => Uri(s"http://$s")
    }

    ESNode(new HttpHost(uri.authority.host.toString, uri.authority.port, uri.scheme))
  }

  def fromConfigStrings(configs: Seq[String]): Seq[ESNode] = {
    configs.map(fromConfigString)
  }
}