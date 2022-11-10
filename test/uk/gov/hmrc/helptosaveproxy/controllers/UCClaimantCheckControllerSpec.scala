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

package uk.gov.hmrc.helptosaveproxy.controllers

import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveproxy.util.AuthSupport
import uk.gov.hmrc.helptosaveproxy.connectors.DWPConnector
import uk.gov.hmrc.helptosaveproxy.testutil.UCClaimantTestSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class UCClaimantCheckControllerSpec extends AuthSupport with UCClaimantTestSupport {

  val mockDWPConnector = mock[DWPConnector]

  val controller = new UCClaimantCheckController(mockDWPConnector, mockAuthConnector, mockCc)

  val nino = "WP010123A"
  val transactionId = UUID.randomUUID()
  val threshold = 650.0
  val noHeaders = Map[String, Seq[String]]()

  def doUCClaimantCheck(controller: UCClaimantCheckController, encodedNino: String): Future[PlayResult] =
    controller.ucClaimantCheck(encodedNino, transactionId, threshold)(FakeRequest())

  def mockUCClaimantCheck(encodedNino: String, transactionId: UUID, threshold: Double)(
    result: Either[String, HttpResponse]): Unit =
    (mockDWPConnector
      .ucClaimantCheck(_: String, _: UUID, _: Double)(_: HeaderCarrier, _: ExecutionContext))
      .expects(encodedNino, transactionId, threshold, *, *)
      .returning(EitherT.fromEither[Future](result))

  "ucClaimantCheck" must {

    "return a 200 status with the expected json when given a NINO starting with WP01" in {
      val ucDetails = HttpResponse(200, Json.toJson(eUCDetails), noHeaders)

      inSequence {
        mockAuthResultWithSuccess()
        mockUCClaimantCheck(nino, transactionId, threshold)(Right(ucDetails))
      }

      val result = doUCClaimantCheck(controller, nino)
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(eUCDetails)

    }

    "return a 500 status with no payload when the ucClaimantCheck call fails" in {
      inSequence {
        mockAuthResultWithSuccess()
        mockUCClaimantCheck(nino, transactionId, threshold)(Left("uc claimant check failed"))
      }

      val result = doUCClaimantCheck(controller, nino)
      status(result) shouldBe 500
      contentAsString(result) shouldBe empty
    }
  }
}
