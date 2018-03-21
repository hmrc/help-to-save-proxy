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

import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.audit.HTSAuditor
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIUserInfo
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveControllerSpec extends TestSupport {

  val mockNSIConnector = mock[NSIConnector]
  val mockJsonSchema = mock[JSONSchemaValidationService]
  val mockAuditor = mock[AuditConnector]

  val htsAuditor = new HTSAuditor {
    override val auditConnector: AuditConnector = mockAuditor
  }

  val controller = new HelpToSaveController(mockNSIConnector, mockJsonSchema, htsAuditor)

  def mockJSONSchemaValidationService(expectedInfo: NSIUserInfo)(result: Either[String, Unit]) =
    (mockJsonSchema.validate(_: JsValue))
      .expects(Json.toJson(expectedInfo))
      .returning(result.map(_ ⇒ Json.toJson(expectedInfo)))

  def mockCreateAccount(expectedInfo: NSIUserInfo)(result: Either[SubmissionFailure, SubmissionSuccess]): Unit =
    (mockNSIConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedInfo, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockUpdateEmail(expectedInfo: NSIUserInfo)(result: Either[String, Unit]) =
    (mockNSIConnector.updateEmail(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedInfo, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAuditAccountCreated() =
    (mockAuditor.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where { (dataEvent, _, _) ⇒ dataEvent.auditType === "AccountCreated" })
      .returning(Future.successful(AuditResult.Success))

  def mockGetAccountByNino(resource: String, queryString: String)(result: Either[String, HttpResponse]): Unit =
    (mockNSIConnector.queryAccount(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(resource, queryString, *, *)
      .returning(EitherT.fromEither[Future](result))

  "The createAccount method" must {

      def doCreateAccountRequest(userInfo: NSIUserInfo) =
        controller.createAccount()(FakeRequest().withJsonBody(Json.toJson(userInfo)))

    behave like commonBehaviour(
      controller.createAccount,
      () ⇒ mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure("", ""))))

    "return a Created status when valid json is given for an eligible new user" in {
      inSequence {
        mockJSONSchemaValidationService(validNSIUserInfo)(Right(()))
        mockCreateAccount(validNSIUserInfo)(Right(SubmissionSuccess(false)))
        mockAuditAccountCreated()
      }

      val result = doCreateAccountRequest(validNSIUserInfo)
      status(result) shouldBe CREATED
    }

    "return a Conflict status when valid json is given for an existing user" in {
      inSequence {
        mockJSONSchemaValidationService(validNSIUserInfo)(Right(()))
        mockCreateAccount(validNSIUserInfo)(Right(SubmissionSuccess(true)))
      }

      val result = doCreateAccountRequest(validNSIUserInfo)
      status(result) shouldBe CONFLICT
    }

  }

  "The updateEmail method" must {

      def doUpdateEmailRequest(userInfo: NSIUserInfo) =
        controller.updateEmail()(FakeRequest().withJsonBody(Json.toJson(userInfo)))

    behave like commonBehaviour(
      controller.updateEmail,
      () ⇒ mockUpdateEmail(validNSIUserInfo)(Left("")))

    "return an OK status when a user successfully updates their email address" in {
      inSequence {
        mockJSONSchemaValidationService(validNSIUserInfo)(Right(()))
        mockUpdateEmail(validNSIUserInfo)(Right(()))
      }

      val result = doUpdateEmailRequest(validNSIUserInfo)
      status(result) shouldBe OK
    }

  }

  "the queryAccount endpoint" must {

    val nino = "AE123456C"
    val version = "1.0"
    val systemId = "mobile-help-to-save"
    val correlationId = UUID.randomUUID()

    val resource = "account"

    val queryString = s"nino=$nino&version=$version&systemId=$systemId&correlationId=$correlationId"
      def doRequest() = controller.queryAccount(resource)(FakeRequest("GET", s"/nsi-services/account?$queryString"))

    "handle successful response" in {

      val responseBody = s"""{"version":$version,"correlationId":"$correlationId"}"""
      val httpResponse = HttpResponse(Status.OK, responseString = Some(responseBody))

      mockGetAccountByNino(resource, queryString)(Right(httpResponse))

      val result = doRequest()

      status(result) shouldBe OK
      contentAsString(result) shouldBe responseBody
    }

    "handle unexpected errors from NS&I" in {
      mockGetAccountByNino(resource, queryString)(Left("boom"))
      status(doRequest()) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  def commonBehaviour(doCall:         () ⇒ Action[AnyContent],
                      mockNSIFailure: () ⇒ Unit): Unit = {

    "return an InternalServerError status when the call to NSI returns an error" in {
      inSequence {
        mockJSONSchemaValidationService(validNSIUserInfo)(Right(()))
        mockNSIFailure()
      }

      val result = doCall()(FakeRequest().withJsonBody(Json.toJson(validNSIUserInfo)))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
    }

    "return a BadRequest" when {

      "the given user info doesn't pass the json schema validation" in {
        mockJSONSchemaValidationService(validNSIUserInfo)(Left(""))

        val result = doCall()(FakeRequest().withJsonBody(Json.toJson(validNSIUserInfo)))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
      }

      "there was no json in the request" in {
        val result = doCall()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
      }

      "the given json is invalid" in {
        val result = doCall()(FakeRequest().withJsonBody(Json.toJson("json")))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
      }

    }
  }

}