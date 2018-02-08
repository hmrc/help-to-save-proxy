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

import org.scalamock.scalatest.MockFactory
import org.scalatest.EitherValues
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.config.WSHttpProxy
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.helptosaveproxy.config.AppConfig.dwpUrl
import uk.gov.hmrc.helptosaveproxy.testutil.UCClaimantTestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DWPConnectorSpec extends TestSupport with MockFactory with EitherValues with UCClaimantTestSupport {

  lazy val mockHTTPProxy = mock[WSHttpProxy]

  def testDWPConnectorImpl = new DWPConnectorImpl(
    fakeApplication.configuration ++ Configuration()) {
    override val httpProxy = mockHTTPProxy
  }

  val transactionId = UUID.randomUUID()

  // put in fake authorization details - these should be removed by the call to create an account
  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("auth")))

  def mockGet(url: String)(result: Either[String, HttpResponse]): Unit =
    (mockHTTPProxy.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(
        result.fold(
          e ⇒ Future.failed(new Exception(e)),
          r ⇒ Future.successful(r)
        ))

  "the ucClaimantCheck call" must {
    "return a Right with HttpResponse(200, Some(Y, Y)) when given an eligible NINO of a UC Claimant within the threshold" in {
      val ucDetails = HttpResponse(200, Some(Json.toJson(eUCDetails))) // scalastyle:ignore magic.number
      mockGet(dwpUrl("WP010123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP010123A", transactionId)
      val resultValue = Await.result(result.value, 3.seconds)
      resultValue.right.value.body shouldBe ucDetails.body
    }

    "return a Right with HttpResponse(200, Some(Y, N)) when given a NINO of a UC Claimant that is not within the threshold" in {
      val ucDetails = HttpResponse(200, Some(Json.toJson(nonEUCDetails))) // scalastyle:ignore magic.number
      mockGet(dwpUrl("WP020123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP020123A", transactionId)
      val resultValue = Await.result(result.value, 3.seconds)
      resultValue.right.value.body shouldBe ucDetails.body
    }

    "return a Right with HttpResponse(200, Some(N)) when given a NINO of a non UC Claimant" in {
      val ucDetails = HttpResponse(200, Some(Json.toJson(nonUCClaimantDetails))) // scalastyle:ignore magic.number
      mockGet(dwpUrl("WP030123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP030123A", transactionId)
      val resultValue = Await.result(result.value, 3.seconds)
      resultValue.right.value.body shouldBe ucDetails.body
    }

    "return a Left when the ucClaimant call comes back with a status other than 200" in {
      val ucDetails = HttpResponse(500, Some(Json.toJson(nonUCClaimantDetails))) // scalastyle:ignore magic.number
      mockGet(dwpUrl("WP030123A", transactionId))(Right(ucDetails))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP030123A", transactionId)
      val resultValue = Await.result(result.value, 3.seconds)
      resultValue.isLeft shouldBe true
    }

    "return a Left when the ucClaimant call fails" in {
      mockGet(dwpUrl("WP030123A", transactionId))(Left("the call failed"))

      val result = testDWPConnectorImpl.ucClaimantCheck("WP030123A", transactionId)
      val resultValue = Await.result(result.value, 3.seconds)
      resultValue.isLeft shouldBe true
    }

  }

}
