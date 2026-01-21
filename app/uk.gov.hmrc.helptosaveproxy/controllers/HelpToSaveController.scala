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
import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosaveproxy.auth.Auth
import uk.gov.hmrc.helptosaveproxy.config.AppConfig
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.controllers.HelpToSaveController.Error
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.util.JsErrorOps.*
import uk.gov.hmrc.helptosaveproxy.util.Toggles.FEATURE
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging, NINO}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveController @Inject()(
  nsiConnector: NSIConnector,
  jsonSchemaValidationService: JSONSchemaValidationService,
  override val authConnector: AuthConnector,
  cc: ControllerComponents)(implicit transformer: LogMessageTransformer, appConfig: AppConfig, ec: ExecutionContext)
    extends BackendController(cc) with Auth with Logging {

  import HelpToSaveController.Error.*

  lazy val correlationIdHeaderName: String = appConfig.getString("microservice.correlationIdHeaderName")

  logger.info(s"Logger level: ${appConfig.logLevel}")

  def jsonSchemaValidationToggle(nino: NINO): FEATURE =
    FEATURE("create-account-json-validation", appConfig.runModeConfiguration, logger, Some(nino))

  def createAccount(): Action[AnyContent] = authorised { implicit request =>
    processRequest[SubmissionSuccess] {
      nsiConnector.createAccount(_).leftMap[Error](NSIError.apply)
    } { (submissionSuccess, _) =>
      submissionSuccess.accountNumber match {
        case None => Conflict
        case Some(account) => Created(Json.toJson(account))
      }
    }
  }

  def updateEmail(): Action[AnyContent] = authorised { implicit request =>
    processRequest[Unit](
      nsiConnector.updateEmail(_).leftMap[Error](e => NSIError(SubmissionFailure(e, "Could not update email")))) {
      (_, _) =>
        Ok
    }
  }

  def queryAccount(resource: String): Action[AnyContent] = authorised { implicit request =>
    nsiConnector
      .queryAccount(resource, request.queryString)
      .fold(
        { e =>
          val message = s"Could not retrieve $resource due to : $e"
          logger.warn(message)
          InternalServerError(message)
        }, { response =>
          Option(response.body).fold[Result](Status(response.status))(body => Status(response.status)(body))
        }
      )
  }

  private def processRequest[T](
    doRequest: NSIPayload => EitherT[Future, Error, T])(handleResult: (T, NSIPayload) => Result)(
    implicit request: Request[AnyContent]): Future[Result] = { // scalastyle:ignore
    val result: EitherT[Future, Error, (T, NSIPayload)] = for {
      nsiPayload <- EitherT.fromEither[Future](extractNSIPayload(request))
      _ <- validateAgainstJSONSchema(nsiPayload)
      response <- doRequest(nsiPayload)
    } yield (response, nsiPayload)

    result.fold[Result](
      {
        case InvalidRequest(m, d) =>
          logger.warn(s"Invalid request: $m, $d")
          BadRequest(SubmissionFailure(m, d).toJson)
        case NSIError(f) =>
          InternalServerError(f.toJson)
      }, {
        case (subResult, nsiPayload) => handleResult(subResult, nsiPayload)
      }
    )
  }

  private def validateAgainstJSONSchema(payload: NSIPayload)(
    implicit ec: ExecutionContext): EitherT[Future, Error, JsValue] = {
    val json = Json.toJson(payload)
    jsonSchemaValidationToggle(payload.nino).thenOrElse(
      EitherT
        .fromEither[Future](jsonSchemaValidationService.validate(json))
        .leftMap[Error](e => InvalidRequest("Invalid data found in request", e)),
      EitherT.pure(json)
    )
  }

  private def extractNSIPayload(request: Request[AnyContent]): Either[InvalidRequest, NSIPayload] =
    request.body.asJson.map(_.validate[NSIPayload]) match {
      case Some(JsSuccess(payload, _)) => Right(payload)
      case Some(e: JsError) => Left(InvalidRequest("Could not parse JSON in request", e.prettyPrint()))
      case None => Left(InvalidRequest("No JSON found in request", "JSON body required"))

    }

}

object HelpToSaveController {

  private sealed trait Error

  private object Error {

    case class InvalidRequest(message: String, details: String) extends Error

    case class NSIError(error: SubmissionFailure) extends Error

  }

}
