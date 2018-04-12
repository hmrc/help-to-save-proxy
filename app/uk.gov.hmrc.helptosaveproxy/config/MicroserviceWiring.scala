/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosaveproxy.config

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSProxyServer
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.ws._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[WSHttpExtension])
trait WSHttp extends HttpPost with WSPost {

  def post[A](url:     String,
              body:    A,
              headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class WSHttpExtension @Inject() (override val auditConnector: AuditConnector, config: AppConfig) extends WSHttp with HttpAuditing {

  val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  override val hooks: Seq[HttpHook] = NoneRequired

  override def appName: String = config.getString("appName")

  override def mapErrors(httpMethod: String, url: String, f: Future[HttpResponse])(implicit ec: ExecutionContext): Future[HttpResponse] = f

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status is not `2xx`. Needed
   * to replace [POST] mhod provided by the hmrc library which will throw exceptions in such cases.
   */
  def post[A](url:     String,
              body:    A,
              headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    super.POST(url, body)(w, httpReads, hc.withExtraHeaders(headers.toSeq: _*), ec)

}

class WSHttpProxy(override val auditConnector: AuditConnector, config: Configuration, proxyConfigPath: String)
  extends HttpPost with WSPost
  with HttpPut with WSPut
  with HttpGet with WSGet
  with WSProxy
  with HttpAuditing
  with HttpVerbs {

  val httpReads: HttpReads[HttpResponse] = new RawHttpReads

  override lazy val appName: String = config.underlying.getString("appName")
  override lazy val wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration(proxyConfigPath)
  override val hooks: Seq[HttpHook] = Seq(AuditingHook)

  override def mapErrors(httpMethod: String, url: String, f: Future[HttpResponse])(implicit ec: ExecutionContext): Future[HttpResponse] = f

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def post[A](url:     String,
              body:    A,
              headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    super.POST(url, body)(w, httpReads, hc.withExtraHeaders(headers.toSeq: _*), ec)

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[PUT]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def put[A](url:           String,
             body:          A,
             needsAuditing: Boolean             = true,
             headers:       Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    withTracing(PUT, url) {
      val httpResponse = doPut(url, body)(w, hc.withExtraHeaders(headers.toSeq: _*))
      if (needsAuditing) {
        executeHooks(url, PUT, Option(Json.stringify(w.writes(body))), httpResponse)
      }
      mapErrors(PUT, url, httpResponse).map(response ⇒ httpReads.read(PUT, url, response))
    }
  }

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[GET]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def get[A](url:     String,
             headers: Map[String, String] = Map.empty[String, String]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    super.GET(url)(httpReads, hc.withExtraHeaders(headers.toSeq: _*), ec)
}

class RawHttpReads extends HttpReads[HttpResponse] {
  override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
}

