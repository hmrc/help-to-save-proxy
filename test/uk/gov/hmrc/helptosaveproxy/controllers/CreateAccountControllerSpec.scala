package uk.gov.hmrc.helptosaveproxy.controllers

import cats.data.EitherT
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.mvc.Http.Status._
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.health.HealthTestSpec.ProxyActor.Created
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.util.toFuture
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}

import scala.concurrent.{ExecutionContext, Future}

class CreateAccountControllerSpec extends TestSupport {

  class Test {
    val mockNSIConnector = mock[NSIConnector]
    val mockJsonSchema = mock[JSONSchemaValidationService]

    val controller = new CreateAccountController(mockNSIConnector, mockJsonSchema)

    def mockNSICreateAccount(result: Either[SubmissionFailure, SubmissionSuccess]): Unit =
      (mockNSIConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(EitherT.fromEither[Future](result))
  }

  "The CreateAccountController" must {

    "return a Created status when valid json is given for an eligible new user" in new Test{
      //create a request with valid json inside
      mockNSICreateAccount(Right(SubmissionSuccess(false)))

      val requestBody = Json.parse(jsonString("20200101"))
      val result = controller.createAccount()(FakeRequest().withJsonBody(requestBody))

      status(result) shouldBe CREATED
    }

    "return a Conflict status when valid json is given for an existing user" in {
      //create a request with the right data

      val result = doRequest()
      result shouldBe Conflict
    }

    "return an InternalServerError status when "
  }
}