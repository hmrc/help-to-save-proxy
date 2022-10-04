/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID
import cats.instances.int._
import cats.syntax.eq._
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.Result
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.http.HttpProxyClient
import uk.gov.hmrc.helptosaveproxy.models.AccountNumber
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.testutil.MockPagerDuty
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosaveproxy.util.Result
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.HttpAuditing

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class NSIConnectorSpec
    extends TestSupport with HttpSupport with MockFactory with ScalaCheckDrivenPropertyChecks with MockPagerDuty {

  override lazy val additionalConfig: Configuration = Configuration(
    "feature-toggles.log-account-creation-json.enabled" -> Random.nextBoolean())

  private val mockAuditor = mock[HttpAuditing]
  private val mockWsClient = mock[WSClient]

  class MockedHttpProxyClient
      extends HttpProxyClient(
        mockAuditor,
        configuration,
        mockWsClient,
        "microservice.services.nsi.proxy",
        fakeApplication.actorSystem)

  override val mockProxyClient : MockedHttpProxyClient = mock[MockedHttpProxyClient]

  lazy val nsiConnector: NSIConnectorImpl =
    new NSIConnectorImpl(mockAuditor, mockMetrics, mockPagerDuty, mockWsClient, fakeApplication.actorSystem) {
      override val proxyClient : MockedHttpProxyClient = mockProxyClient
    }

  val nsiCreateAccountUrl : String = appConfig.nsiCreateAccountUrl
  val nsiAuthHeaderKey : String = appConfig.nsiAuthHeaderKey
  val nsiBasicAuth : String = appConfig.nsiBasicAuth
  val authHeaders : Map[String, String]= Map(nsiAuthHeaderKey -> nsiBasicAuth)
  val noHeaders: Map[String, Seq[String]] = Map[String, Seq[String]]()

  "the updateEmail method" must {

    def doRequest = nsiConnector.updateEmail(validNSIPayload)

    "return a Right when the status is OK" in {

      mockPut(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(Status.OK, "")))
      Await.result(doRequest.value, 3.seconds) shouldBe Right(())
    }

    "return a Left " when {
      "the status is not OK" in {
        forAll { status: Int =>
          whenever(status =!= Status.OK && status > 0) {
            inSequence {
              mockPut(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(status, "")))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Received unexpected http status in response to update email")
            }

            Await.result(doRequest.value, 3.seconds).isLeft shouldBe true
          }
        }
      }

      "the PUT to NS&I fails" in {
        inSequence {
          mockPut(nsiCreateAccountUrl, validNSIPayload, authHeaders)(None)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to update email")
        }

        Await.result(doRequest.value, 3.seconds).isLeft shouldBe true
      }
    }

  }

  "the createAccount Method" must {

    def doRequest = nsiConnector.createAccount(validNSIPayload)

    "Return a SubmissionSuccess when the status is Created" in {
      mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
        Some(HttpResponse(Status.CREATED, Json.parse("""{"accountNumber" : "1234567890"}"""), noHeaders)))
      Await.result(doRequest.value, 3.seconds) shouldBe Right(SubmissionSuccess(Some(AccountNumber("1234567890"))))
    }

    "Return a SubmissionSuccess when the status is CONFLICT" in {
      mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(Status.CONFLICT, "")))
      Await.result(doRequest.value, 3.seconds) shouldBe Right(SubmissionSuccess(None))
    }

    "return a SubmissionFailure" when {
      "the status is BAD_REQUEST" in {
        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
            Some(HttpResponse(Status.BAD_REQUEST, JsObject(Seq("error" -> submissionFailure.toJson)), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        Await.result(doRequest.value, 3.seconds) shouldBe Left(submissionFailure)
      }

      "the status is BAD_REQUEST but fails to parse json in the response body" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
            Some(HttpResponse(Status.BAD_REQUEST, JsString("no json in the response"), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        Await.result(doRequest.value, 3.seconds) shouldBe Left(SubmissionFailure("Bad request", ""))
      }

      "the status is INTERNAL_SERVER_ERROR" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
            Some(HttpResponse(Status.INTERNAL_SERVER_ERROR, JsString("500 Internal Server Error"), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        Await.result(doRequest.value, 3.seconds) shouldBe Left(SubmissionFailure("Server error", ""))
      }

      "the status is SERVICE_UNAVAILABLE" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(
            Some(HttpResponse(Status.SERVICE_UNAVAILABLE, JsString("502 Bad Gateway"), noHeaders)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        Await.result(doRequest.value, 3.seconds) shouldBe Left(SubmissionFailure("Server error", ""))
      }

      "the status is anything else" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(Status.BAD_GATEWAY, "")))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")

        }
        Await.result(doRequest.value, 3.seconds) match {
          case Right(_) => fail()
          case Left(_) => ()
        }
      }

      "the call to createAccount fails" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIPayload, authHeaders)(None)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to create account")
        }
        Await.result(doRequest.value, 3.seconds).isLeft shouldBe true
      }
    }

    "the health-check Method" must {
      def doRequest = nsiConnector.healthCheck(validNSIPayload)

      "Return a Right when the status is OK" in {
        mockPut(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(Status.OK, "")))
        Await.result(doRequest.value, 3.seconds) shouldBe Right(())
      }

      "Return a Left when the status is not OK" in {
        forAll { status: Int =>
          whenever(status > 0 && status =!= Status.OK) {
            mockPut(nsiCreateAccountUrl, validNSIPayload, authHeaders)(Some(HttpResponse(status, "")))
            Await.result(doRequest.value, 3.seconds).isLeft shouldBe true
          }
        }
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

    val queryParamsSeq =
      Seq("nino" -> nino, "version" -> version, "systemId" -> systemId, "correlationId" -> correlationId.toString)

    def doRequest = nsiConnector.queryAccount(resource, queryParameters)

    def url = s"${appConfig.nsiQueryAccountUrl}/$resource"

    "handle the successful response and return it" in {
      val responseBody = s"""{"version":$version,"correlationId":"$correlationId"}"""
      val httpResponse = HttpResponse(Status.OK, responseBody)

      mockGet(url, queryParamsSeq, authHeaders)(Some(httpResponse))

      Await.result(doRequest.value, 3.seconds) shouldBe Right(httpResponse)
    }

    "handle unexpected errors" in {
      forAll { status: Int =>
        whenever(status > 0 && status =!= Status.OK && status =!= Status.BAD_REQUEST) {
          mockGet(url, queryParamsSeq, authHeaders)(Some(HttpResponse(status, "")))
          Await.result(doRequest.value, 3.seconds).isLeft shouldBe true
        }
      }
    }
  }
}
