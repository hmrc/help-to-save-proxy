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

package uk.gov.hmrc.helptosaveproxy.controllers

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.models.{AccountNumber, NSIPayload}
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosaveproxy.util.AuthSupport
import uk.gov.hmrc.http.HttpResponse

import java.util.UUID
import scala.concurrent.Future

class HelpToSaveControllerSpec extends AuthSupport {

  val mockNSIConnector = mock[NSIConnector]
  val mockJsonSchema = mock[JSONSchemaValidationService]

  val controller = new HelpToSaveController(mockNSIConnector, mockJsonSchema, mockAuthConnector, mockCc)

  val ggCreds = Credentials("id", "GovernmentGateway")
  val ggRetrievals: Option[Credentials] = Some(ggCreds)
  val noHeaders = Map[String, Seq[String]]()

  def mockJSONSchemaValidationService(expectedInfo: NSIPayload)(result: Either[String, Unit]) =
    when(mockJsonSchema
      .validate(Json.toJson(expectedInfo)))
      .thenReturn(result.map(_ => Json.toJson(expectedInfo)))

  def mockCreateAccount(expectedInfo: NSIPayload)(result: Either[SubmissionFailure, SubmissionSuccess]): Unit =
    when(mockNSIConnector
      .createAccount(eqTo(expectedInfo))(using any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockUpdateEmail(expectedInfo: NSIPayload)(result: Either[String, Unit]) =
    when(mockNSIConnector
      .updateEmail(eqTo(expectedInfo))(using any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockGetAccountByNino(resource: String, queryString: Map[String, Seq[String]])(
    result: Either[String, HttpResponse]): Unit =
    when(mockNSIConnector
      .queryAccount(eqTo(resource), eqTo(queryString))(using any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  "The createAccount method" must {

    val correlationId = "correlationId"

    def doCreateAccountRequest(userInfo: NSIPayload) =
      controller.createAccount()(

        FakeRequest().withJsonBody(Json.toJson(userInfo)).withHeaders("X-Correlation-Id" -> correlationId))

    behave like commonBehaviour(
      (() => controller.createAccount()),
      () => mockCreateAccount(validNSIPayload)(Left(SubmissionFailure("", ""))),
      validNSIPayload)

    "return a Created status when valid json is given for an eligible new user" in {
      mockAuthResultWithSuccess()
      mockJSONSchemaValidationService(validNSIPayload)(Right(()))
      mockCreateAccount(validNSIPayload)(Right(SubmissionSuccess(Some(AccountNumber("1234567890")))))

      val result = doCreateAccountRequest(validNSIPayload)
      status(result) shouldBe CREATED
    }

    "return a Conflict status when valid json is given for an existing user" in {
      mockAuthResultWithSuccess()
      mockJSONSchemaValidationService(validNSIPayload)(Right(()))
      mockCreateAccount(validNSIPayload)(Right(SubmissionSuccess(None)))

      val result = doCreateAccountRequest(validNSIPayload)
      status(result) shouldBe CONFLICT
    }

  }

  "The updateEmail method" must {

    def doUpdateEmailRequest(userInfo: NSIPayload) =
      controller.updateEmail()(FakeRequest().withJsonBody(Json.toJson(userInfo)))

    val updatePayload = validNSIPayload.copy(version = None, systemId = None)

    behave like commonBehaviour((() => controller.updateEmail()), () => mockUpdateEmail(updatePayload)(Left("")), updatePayload)

    "return an OK status when a user successfully updates their email address" in {
      mockAuthResultWithSuccess()
      mockJSONSchemaValidationService(updatePayload)(Right(()))
      mockUpdateEmail(updatePayload)(Right(()))

      val result = doUpdateEmailRequest(updatePayload)
      status(result) shouldBe OK
    }

  }

  "the queryAccount endpoint" must {

    val nino = "AE123456C"
    val version = "1.0"
    val systemId = "mobile-help-to-save"
    val correlationId = UUID.randomUUID()

    val resource = "account"

    val queryParameters = Map(
      "nino"          -> Seq(nino),
      "version"       -> Seq(version),
      "systemId"      -> Seq(systemId),
      "correlationId" -> Seq(correlationId.toString)
    )

    val queryString = s"nino=$nino&version=$version&systemId=$systemId&correlationId=$correlationId"

    def doRequest() = controller.queryAccount(resource)(FakeRequest("GET", s"/nsi-services/account?$queryString"))

    "handle successful response" in {

      val responseBody = s"""{"version":$version,"correlationId":"$correlationId"}"""
      val httpResponse = HttpResponse(Status.OK, responseBody, noHeaders)

      mockAuthResultWithSuccess()
      mockGetAccountByNino(resource, queryParameters)(Right(httpResponse))

      val result = doRequest()

      status(result) shouldBe OK
      contentAsString(result) shouldBe responseBody
    }

    "handle unexpected errors from NS&I" in {
      mockAuthResultWithSuccess()
      mockGetAccountByNino(resource, queryParameters)(Left("boom"))
      status(doRequest()) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  def commonBehaviour(doCall: () => Action[AnyContent], mockNSIFailure: () => Unit, nsiPayload: NSIPayload): Unit = {

    "return an InternalServerError status when the call to NSI returns an error" in {
      mockAuthResultWithSuccess()

      mockJSONSchemaValidationService(nsiPayload)(Right(()))
      mockNSIFailure()

      val result = doCall()(FakeRequest().withJsonBody(Json.toJson(nsiPayload)))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
    }

    "return a BadRequest" when {

      "the given user info doesn't pass the json schema validation" in {
        mockAuthResultWithSuccess()
        mockJSONSchemaValidationService(nsiPayload)(Left(""))

        val result = doCall()(FakeRequest().withJsonBody(Json.toJson(nsiPayload)))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
      }

      "there was no json in the request" in {
        mockAuthResultWithSuccess()

        val result = doCall()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
      }

      "the given json is invalid" in {
        mockAuthResultWithSuccess()

        val result = doCall()(FakeRequest().withJsonBody(Json.toJson("json")))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).validate[SubmissionFailure].isSuccess shouldBe true
      }

    }
  }

}
