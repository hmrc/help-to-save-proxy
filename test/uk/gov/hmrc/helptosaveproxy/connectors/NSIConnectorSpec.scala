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

package uk.gov.hmrc.helptosaveproxy.connectors

import java.util.UUID

import cats.instances.int._
import cats.syntax.eq._
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.testutil.MockPagerDuty
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class NSIConnectorSpec extends TestSupport with HttpSupport with MockFactory with GeneratorDrivenPropertyChecks with MockPagerDuty {

  override lazy val additionalConfig = Configuration("feature-toggles.log-account-creation-json.enabled" → Random.nextBoolean())

  private val mockAuditor = mock[AuditConnector]
  private val mockWsClient = mock[WSClient]

  lazy val nsiConnector = new NSIConnectorImpl(mockAuditor, mockMetrics, mockPagerDuty, mockWsClient) {
    override val proxyClient = mockProxyClient
  }

  val nsiCreateAccountUrl = appConfig.nsiCreateAccountUrl
  val nsiAuthHeaderKey = appConfig.nsiAuthHeaderKey
  val nsiBasicAuth = appConfig.nsiBasicAuth

  val authHeaders = Map(nsiAuthHeaderKey → nsiBasicAuth)

  private val headerCarrierWithoutAuthorization = argThat[HeaderCarrier](_.authorization.isEmpty)

  "the updateEmail method" must {
    "return a Right when the status is OK" in {

      mockPut(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(Status.OK)))
      val result = nsiConnector.updateEmail(validNSIUserInfo)
      Await.result(result.value, 3.seconds) shouldBe Right(())
    }

    "return a Left " when {
      "the status is not OK" in {
        forAll { status: Int ⇒
          whenever(status =!= Status.OK && status > 0) {
            inSequence {
              mockPut(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(status)))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Received unexpected http status in response to update email")
            }

            val result = nsiConnector.updateEmail(validNSIUserInfo)
            Await.result(result.value, 3.seconds).isLeft shouldBe true
          }
        }
      }

      "the POST to NS&I fails" in {
        inSequence {
          mockPut(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(None)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to update email")
        }

        val result = nsiConnector.updateEmail(validNSIUserInfo)
        Await.result(result.value, 3.seconds).isLeft shouldBe true
      }
    }

  }

  "the createAccount Method" must {

    "Return a SubmissionSuccess when the status is Created" in {
      mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(Status.CREATED, Some(Json.parse("""{"accountNumber" : "1234567890"}""")))))
      val result = nsiConnector.createAccount(validNSIUserInfo)
      Await.result(result.value, 3.seconds) shouldBe Right(SubmissionSuccess(false))
    }

    "Return a SubmissionSuccess when the status is CONFLICT" in {
      mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(Status.CONFLICT)))
      val result = nsiConnector.createAccount(validNSIUserInfo)
      Await.result(result.value, 3.seconds) shouldBe Right(SubmissionSuccess(true))
    }

    "return a SubmissionFailure" when {
      "the status is BAD_REQUEST" in {
        val submissionFailure = SubmissionFailure(Some("id"), "message", "detail")
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(
            HttpResponse(Status.BAD_REQUEST, Some(JsObject(Seq("error" → submissionFailure.toJson))))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = nsiConnector.createAccount(validNSIUserInfo)
        Await.result(result.value, 3.seconds) shouldBe Left(submissionFailure)
      }

      "the status is BAD_REQUEST but fails to parse json in the response body" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(
            HttpResponse(Status.BAD_REQUEST, Some(JsString("no json in the response")))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = nsiConnector.createAccount(validNSIUserInfo)
        Await.result(result.value, 3.seconds) shouldBe Left(SubmissionFailure("Bad request", ""))
      }

      "the status is INTERNAL_SERVER_ERROR" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(
            HttpResponse(Status.INTERNAL_SERVER_ERROR, Some(JsString("500 Internal Server Error")))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = nsiConnector.createAccount(validNSIUserInfo)
        Await.result(result.value, 3.seconds) shouldBe Left(SubmissionFailure("Server error", ""))
      }

      "the status is SERVICE_UNAVAILABLE" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(
            HttpResponse(Status.SERVICE_UNAVAILABLE, Some(JsString("502 Bad Gateway")))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")
        }
        val result = nsiConnector.createAccount(validNSIUserInfo)
        Await.result(result.value, 3.seconds) shouldBe Left(SubmissionFailure("Server error", ""))
      }

      "the status is anything else" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(Status.BAD_GATEWAY)))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Received unexpected http status in response to create account")

        }
        val result = nsiConnector.createAccount(validNSIUserInfo).value
        Await.result(result, 3.seconds) match {
          case Right(_) ⇒ fail()
          case Left(_)  ⇒ ()
        }
      }

      "the call to createAccount fails" in {
        inSequence {
          mockPost(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(None)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to create account")
        }
        val result = nsiConnector.createAccount(validNSIUserInfo)
        Await.result(result.value, 3.seconds).isLeft shouldBe true
      }
    }

    "the health-check Method" must {
      "Return a Right when the status is OK" in {
        mockPut(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(Status.OK)))
        val result = nsiConnector.healthCheck(validNSIUserInfo)
        Await.result(result.value, 3.seconds) shouldBe Right(())
      }

      "Return a Left when the status is OK" in {
        forAll { status: Int ⇒
          whenever(status > 0 && status =!= Status.OK) {
            mockPut(nsiCreateAccountUrl, validNSIUserInfo, authHeaders)(Some(HttpResponse(status)))
            val result = nsiConnector.healthCheck(validNSIUserInfo)
            Await.result(result.value, 3.seconds).isLeft shouldBe true
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

    val queryString = s"nino=$nino&version=$version&systemId=$systemId&correlationId=$correlationId"

    val queryParamsMap = Map("nino" -> nino, "version" -> version, "systemId" -> systemId, "correlationId" -> correlationId.toString)

      def doRequest = nsiConnector.queryAccount(resource, queryString)

      def url = s"${appConfig.nsiQueryAccountUrl}/$resource"

    "handle the successful response and return it" in {
      val responseBody = s"""{"version":$version,"correlationId":"$correlationId"}"""
      val httpResponse = HttpResponse(Status.OK, responseString = Some(responseBody))

      mockGet(url, queryParamsMap, authHeaders)(Some(httpResponse))

      Await.result(doRequest.value, 3.seconds) shouldBe Right(httpResponse)
    }

    "handle unexpected errors" in {

      mockGet(url, queryParamsMap, authHeaders)(None)

      Await.result(doRequest.value, 3.seconds).isLeft shouldBe true
    }
  }
}
