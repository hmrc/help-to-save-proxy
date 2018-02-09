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

import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.EitherValues
import play.api.libs.json.Json
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.config.AppConfig.dwpUrl
import uk.gov.hmrc.helptosaveproxy.models.UCDetails
import uk.gov.hmrc.helptosaveproxy.testutil.{MockPagerDuty, UCClaimantTestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DWPConnectorSpec extends TestSupport with MockFactory with EitherValues with UCClaimantTestSupport with MockPagerDuty {

  def testDWPConnectorImpl = new DWPConnectorImpl(configuration, mockMetrics, mockPagerDuty) {
    override val httpProxy = mockHTTPProxy
  }

  val transactionId = UUID.randomUUID()

  def mockGet(url: String)(result: Either[String, HttpResponse]): Unit =
    (mockHTTPProxy.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(
        result.fold(
          e ⇒ Future.failed(new Exception(e)),
          r ⇒ Future.successful(r)
        ))

  def build200Response(uCDetails: UCDetails): HttpResponse = {
    HttpResponse(200, Some(Json.toJson(uCDetails))) // scalastyle:ignore magic.number
  }

  def resultValue(result: EitherT[Future, String, HttpResponse]): Either[String, HttpResponse] = Await.result(result.value, 3.seconds)

  "the ucClaimantCheck call" must {
    "return a Right with HttpResponse(200, Some(Y, Y)) when given an eligible NINO of a UC Claimant within the threshold" in {
      val ucDetails = build200Response(eUCDetails)
      mockGet(dwpUrl("WP010123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP010123A", transactionId)
      resultValue(result).right.value.body shouldBe ucDetails.body
    }

    "return a Right with HttpResponse(200, Some(Y, N)) when given a NINO of a UC Claimant that is not within the threshold" in {
      val ucDetails = build200Response(nonEUCDetails)
      mockGet(dwpUrl("WP020123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP020123A", transactionId)
      resultValue(result).right.value.body shouldBe ucDetails.body
    }

    "return a Right with HttpResponse(200, Some(N)) when given a NINO of a non UC Claimant" in {
      val ucDetails = build200Response(nonUCClaimantDetails)
      mockGet(dwpUrl("WP030123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP030123A", transactionId)
      resultValue(result).right.value.body shouldBe ucDetails.body
    }

    "return a Left when the ucClaimant call comes back with a status other than 200" in {
      val ucDetails = HttpResponse(500, Some(Json.toJson(nonUCClaimantDetails))) // scalastyle:ignore magic.number
      inSequence {
        mockGet(dwpUrl("WP030123A", transactionId))(Right(ucDetails))
        mockPagerDutyAlert("Received unexpected http status in response to uc claimant check")
      }
      val result = testDWPConnectorImpl.ucClaimantCheck("WP030123A", transactionId)
      resultValue(result).isLeft shouldBe true
    }

    "return a Left when the ucClaimant call fails" in {
      inSequence {
        mockGet(dwpUrl("WP030123A", transactionId))(Left("the call failed"))
        mockPagerDutyAlert("Failed to make call to uc claimant check")
      }
      val result = testDWPConnectorImpl.ucClaimantCheck("WP030123A", transactionId)
      resultValue(result).isLeft shouldBe true
    }

  }

}
