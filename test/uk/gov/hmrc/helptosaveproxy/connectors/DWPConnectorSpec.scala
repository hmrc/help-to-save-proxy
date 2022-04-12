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

import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.EitherValues
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.http.HttpProxyClient
import uk.gov.hmrc.helptosaveproxy.models.UCDetails
import uk.gov.hmrc.helptosaveproxy.testutil.{MockPagerDuty, UCClaimantTestSupport}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.HttpAuditing

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DWPConnectorSpec
    extends TestSupport with HttpSupport with MockFactory with EitherValues with UCClaimantTestSupport
    with MockPagerDuty {

  private val mockAuditor = mock[HttpAuditing]
  private val mockWsClient = mock[WSClient]

  class MockedHttpProxyClient
      extends HttpProxyClient(
        mockAuditor,
        configuration,
        mockWsClient,
        "microservice.services.dwp.proxy",
        fakeApplication.actorSystem)

  override val mockProxyClient = mock[MockedHttpProxyClient]

  lazy val connector =
    new DWPConnectorImpl(mockAuditor, mockMetrics, mockPagerDuty, mockWsClient, fakeApplication.actorSystem) {
      override val proxyClient = mockProxyClient
    }

  val transactionId = UUID.randomUUID()
  val threshold = 650.0
  val noHeaders = Map[String, Seq[String]]()

  def build200Response(uCDetails: UCDetails): HttpResponse =
    HttpResponse(200, Json.toJson(uCDetails), noHeaders) // scalastyle:ignore magic.number

  def resultValue(result: EitherT[Future, String, HttpResponse]): Either[String, HttpResponse] =
    Await.result(result.value, 3.seconds)

  val queryParams = Seq(
    "systemId"        -> appConfig.systemId,
    "thresholdAmount" -> threshold.toString,
    "transactionId"   -> transactionId.toString)

  "the ucClaimantCheck call" must {
    "return a Right with HttpResponse(200, Some(Y, Y)) when given an eligible NINO of a UC Claimant within the threshold" in {
      val ucDetails = build200Response(eUCDetails)
      val dwpUrl = "http://localhost:7002/hmrc/WP010123A"
      mockGet(dwpUrl, queryParams)(Some(ucDetails))

      val result = connector.ucClaimantCheck("WP010123A", transactionId, threshold)
      resultValue(result).value.body shouldBe ucDetails.body
    }

    "return a Right with HttpResponse(200, Some(Y, N)) when given a NINO of a UC Claimant that is not within the threshold" in {
      val ucDetails = build200Response(nonEUCDetails)
      val dwpUrl = "http://localhost:7002/hmrc/WP020123A"
      mockGet(dwpUrl, queryParams)(Some(ucDetails))

      val result = connector.ucClaimantCheck("WP020123A", transactionId, threshold)
      resultValue(result).value.body shouldBe ucDetails.body
    }

    "return a Right with HttpResponse(200, Some(N)) when given a NINO of a non UC Claimant" in {
      val ucDetails = build200Response(nonUCClaimantDetails)
      val dwpUrl = "http://localhost:7002/hmrc/WP030123A"
      mockGet(dwpUrl, queryParams)(Some(ucDetails))

      val result = connector.ucClaimantCheck("WP030123A", transactionId, threshold)
      resultValue(result).value.body shouldBe ucDetails.body
    }

    "return a Left when the ucClaimant call comes back with a status other than 200" in {
      val ucDetails = HttpResponse(500, Json.toJson(nonUCClaimantDetails), noHeaders) // scalastyle:ignore magic.number
      val dwpUrl = "http://localhost:7002/hmrc/WP030123A"
      inSequence {
        mockGet(dwpUrl, queryParams)(Some(ucDetails))
        mockPagerDutyAlert("Received unexpected http status in response to uc claimant check")
      }
      val result = connector.ucClaimantCheck("WP030123A", transactionId, threshold)
      resultValue(result).isLeft shouldBe true
    }

    "return a Left when the ucClaimant call fails" in {
      val dwpUrl = "http://localhost:7002/hmrc/WP030123A"
      inSequence {
        mockGet(dwpUrl, queryParams)(None)
        mockPagerDutyAlert("Failed to make call to uc claimant check")
      }
      val result = connector.ucClaimantCheck("WP030123A", transactionId, threshold)
      resultValue(result).isLeft shouldBe true
    }

  }

}
