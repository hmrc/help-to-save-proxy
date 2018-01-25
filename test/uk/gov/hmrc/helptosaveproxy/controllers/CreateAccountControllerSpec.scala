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

package uk.gov.hmrc.helptosaveproxy.controllers

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}

import scala.concurrent.{ExecutionContext, Future}

class CreateAccountControllerSpec extends TestSupport {

  val mockNSIConnector = mock[NSIConnector]
  val mockJsonSchema = mock[JSONSchemaValidationService]

  val controller = new CreateAccountController(mockNSIConnector, mockJsonSchema)

  def mockNSICreateAccount(result: Either[SubmissionFailure, SubmissionSuccess]): Unit =
    (mockNSIConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockJSONSchemaValidationService(expectedInfo: NSIUserInfo)(result: Either[String,Unit]) =
    (mockJsonSchema.validate(_: JsValue))
    .expects(Json.toJson(expectedInfo))
    .returning(result.map(_ ⇒ Json.toJson(expectedInfo)))


  "The CreateAccountController" must {

      def doCreateAccountRequest(userInfo: NSIUserInfo) =
        controller.createAccount()(FakeRequest().withJsonBody(Json.toJson(userInfo)))

    "return a Created status when valid json is given for an eligible new user" in {
      inSequence {
        mockNSICreateAccount(Right(SubmissionSuccess(false)))
        // TODO: mock out JSON schema validation here
      }

      val result = doCreateAccountRequest(validNSIUserInfo)
      status(result) shouldBe CREATED
    }

    "return a Conflict status when valid json is given for an existing user" in {
      mockNSICreateAccount(Right(SubmissionSuccess(true)))

      val result = doCreateAccountRequest(validNSIUserInfo)
      status(result) shouldBe CONFLICT
    }

    "return an InternalServerError status when the call to NSI returns an error" in {
      mockNSICreateAccount(Left(SubmissionFailure("", "")))

      val result = doCreateAccountRequest(validNSIUserInfo)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
    }

    "return a BadRequest" when {

      "" in {

      }

    }

  }
}
