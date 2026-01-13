/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveproxy.connectors

import cats.implicits.catsSyntaxEq
import com.codahale.metrics.*
import com.typesafe.config.ConfigFactory
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.prop.TableDrivenPropertyChecks.whenever
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.{Application, Configuration}
import uk.gov.hmrc.helptosaveproxy.config.AppConfig
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.models.AccountNumber
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.testutil.MockPagerDuty
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosaveproxy.testutil.TestLogMessageTransformer.transformer
import uk.gov.hmrc.helptosaveproxy.util.WireMockMethods
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse, SessionId}

import java.util
import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

class NSIConnectorSpec
    extends AnyWordSpec with WireMockMethods with WireMockSupport  with MockPagerDuty
    with GuiceOneAppPerSuite with EitherValues with Matchers with ScalaCheckDrivenPropertyChecks{

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services{
         |    nsi {
         |      protocol = http
         |      host = $wireMockHost
         |      port = $wireMockPort
         |    }
         |  }
         |}
         |
         |""".stripMargin
    )
  )

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString(
            """
              | metrics.enabled       = true
              | metrics.jvm = false
              | play.modules.disabled = [ "uk.gov.hmrc.helptosaveproxy.config.HealthCheckModule",
              | "play.api.libs.ws.ahc.AhcWSModule",
              | "play.api.mvc.LegacyCookiesModule" ]
              | microservice.services.dwp.keyManager.stores = []
              | microservice.services.nsi.keyManager.stores = []
              |     """.stripMargin)
        ) withFallback config)
      .disable[uk.gov.hmrc.play.bootstrap.BuiltinModule]
      .build()

  lazy implicit val configuration: Configuration = app.injector.instanceOf[Configuration]

  val mockMetrics: Metrics = new Metrics(new StubMetricRegistry()) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()

    override def histogram(name: String): Histogram = new Histogram(new UniformReservoir())
  }

  val httpClient: HttpClientV2 = fakeApplication().injector.instanceOf[HttpClientV2]

  implicit lazy val appConfig: AppConfig = fakeApplication().injector.instanceOf[AppConfig]

  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)), authorization = Some(Authorization("auth")))

  lazy val nsiConnector: NSIConnectorImpl =
    new NSIConnectorImpl(mockMetrics, mockPagerDuty, httpClient)

  class StubMetricRegistry extends MetricRegistry {
    override def getGauges(filter: MetricFilter): util.SortedMap[String, Gauge[_]] =
      new util.TreeMap[String, Gauge[_]]()
  }

  val nsiCreateAccountUrl: String = appConfig.nsiCreateAccountUrl
  val nsiAuthHeaderKey: String = appConfig.nsiAuthHeaderKey
  val nsiBasicAuth: String = appConfig.nsiBasicAuth
  val authHeaders: Map[String, String] = Map(nsiAuthHeaderKey -> nsiBasicAuth)
  val noHeaders: Map[String, Seq[String]] = Map[String, Seq[String]]()
  val emptyJsonBody = "{}"
  val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  "the updateEmail method" must {

    def doRequest() = nsiConnector.updateEmail(validNSIPayload)

    "return a Right when the status is OK" in {
      when(
        method = PUT,
        uri = "/nsi-services/account",
        headers = authHeaders,
        body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(Status.OK, "")
      Await.result(doRequest().value, 3.seconds) shouldBe Right(())
    }

    "return a Left " when {
      "the status is not OK" in {
        List(
          HttpResponse(400, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(403, emptyJsonBody),
          HttpResponse(500, emptyJsonBody),
          HttpResponse(502, emptyJsonBody),
          HttpResponse(503, emptyJsonBody)
        ).foreach { httpResponse =>
          when(
            method = PUT,
            uri = "/nsi-services/account",
            headers = authHeaders,
            body = Some(Json.toJson(validNSIPayload).toString()))
            .thenReturn(httpResponse.status, httpResponse.body)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to update email")
          Await.result(doRequest().value, 3.seconds).isLeft shouldBe true
        }
      }

      "the PUT to NS&I fails" in {
        when(method = PUT,
          uri = "/nsi-services/account",
          headers = authHeaders,
          body = Some(Json.toJson(validNSIPayload).toString()))
        //          mockPut(nsiCreateAccountUrl, validNSIPayload, authHeaders)(None)
        // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
        mockPagerDutyAlert("Failed to make call to update email")
        Await.result(doRequest().value, 3.seconds).isLeft shouldBe true
      }
    }

    "the createAccount Method" must {

      def doRequest() = nsiConnector.createAccount(validNSIPayload)

      "Return a SubmissionSuccess when the status is Created" in {
        val httpResponse = HttpResponse(Status.CREATED, Json.parse("""{"accountNumber" : "1234567890"}"""), noHeaders)

        when(method = POST,
          headers = authHeaders,
          uri = "/nsi-services/account",
          body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
        //      mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
        //        Some(HttpResponse(Status.CREATED, Json.parse("""{"accountNumber" : "1234567890"}"""), noHeaders)))
        Await.result(doRequest().value, 3.seconds) shouldBe Right(SubmissionSuccess(Some(AccountNumber("1234567890"))))
      }

      "Return a SubmissionSuccess when the status is CONFLICT" in {
        val httpResponse = HttpResponse(Status.CONFLICT, "")
        when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
        //      mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(Status.CONFLICT, "")))
        Await.result(doRequest().value, 3.seconds) shouldBe Right(SubmissionSuccess(None))
      }

      "return a SubmissionFailure" when {
        "the status is BAD_REQUEST" in {
          val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
          val httpResponse = HttpResponse(Status.BAD_REQUEST, JsObject(Seq("error" -> submissionFailure.toJson)), noHeaders)
          when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
          //          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
          //            Some(HttpResponse(Status.BAD_REQUEST, JsObject(Seq("error" -> submissionFailure.toJson)), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
          Await.result(doRequest().value, 3.seconds) shouldBe Left(submissionFailure)
        }

        "the status is BAD_REQUEST but fails to parse json in the response body" in {
          val httpResponse = HttpResponse(Status.BAD_REQUEST, JsString("no json in the response"), noHeaders)
          when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
          //          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
          //            Some(HttpResponse(Status.BAD_REQUEST, JsString("no json in the response"), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
          Await.result(doRequest().value, 3.seconds) shouldBe Left(SubmissionFailure("Bad request", ""))
        }

        "the status is INTERNAL_SERVER_ERROR" in {
          val httpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR, JsString("500 Internal Server Error"), noHeaders)
          when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
          //          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
          //            Some(HttpResponse(Status.INTERNAL_SERVER_ERROR, JsString("500 Internal Server Error"), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
          Await.result(doRequest().value, 3.seconds) shouldBe Left(SubmissionFailure("Server error", ""))
        }

        "the status is SERVICE_UNAVAILABLE" in {
          val httpResponse = HttpResponse(Status.SERVICE_UNAVAILABLE, JsString("502 Bad Gateway"), noHeaders)
          when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
          //        mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
          //            Some(HttpResponse(Status.SERVICE_UNAVAILABLE, JsString("502 Bad Gateway"), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
          Await.result(doRequest().value, 3.seconds) shouldBe Left(SubmissionFailure("Server error", ""))
        }

        "the status is anything else" in {
          val httpResponse = HttpResponse(Status.BAD_GATEWAY, "")
          when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString())).thenReturn(httpResponse.status, httpResponse.body)
          //        mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(Status.BAD_GATEWAY, "")))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
          Await.result(doRequest().value, 3.seconds) match {
            case Right(_) => fail()
            case Left(_) => ()
          }
        }

        "the call to createAccount fails" in {
          when(method = POST, uri = "/nsi-services/account", headers = authHeaders, body = Some(Json.toJson(validNSIPayload).toString()))
          //              mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(None)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to create account")
          Await.result(doRequest().value, 3.seconds).isLeft shouldBe true
        }
      }
    }

    "queryAccount" must {

      val nino = "AE123456C"
      val version = "1.0"
      val systemId = "mobile-help-to-save"
      val correlationId = UUID.randomUUID()

      val resource = "account"

      val queryParameters = Map(
        "nino" -> Seq(nino),
        "version" -> Seq(version),
        "systemId" -> Seq(systemId),
        "correlationId" -> Seq(correlationId.toString)
      )
      val queryParams = queryParameters.flatMap { case (name, values) => values.map(value => (name, value)) }

      def doRequest() = nsiConnector.queryAccount(resource, queryParameters)

      def url = s"${appConfig.nsiQueryAccountUrl}/$resource"

      "handle the successful response and return it" in {
        val responseBody = s"""{"version":$version,"correlationId":"$correlationId"}"""
        val httpResponse = HttpResponse(Status.OK, responseBody)
        when(method = GET, uri = "/nsi-services/account", headers = authHeaders, queryParams = queryParams).thenReturn(httpResponse.status, httpResponse.body)
//              mockGet(url, queryParamsSeq, authHeaders)(Some(httpResponse))
        val result = Await.result(doRequest().value, 3.seconds).value
        result.status shouldBe httpResponse.status
        result.body shouldBe httpResponse.body
      }

          "handle unexpected errors" in {
            forAll { (status: Int) =>
              whenever (status > 0 && status =!= Status.OK && status =!= Status.BAD_REQUEST) {
                when(method = GET, uri = url, headers = authHeaders, queryParams = queryParams)
//                mockGet(url, queryParamsSeq, authHeaders)(Some(HttpResponse(status, "")))
                Await.result(doRequest().value, 3.seconds).isLeft shouldBe true
              }
            }
          }
    }
  }
}
