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
import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosaveproxy.audit.HTSAuditor
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.controllers.HelpToSaveController.Error
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.models.{AccountCreated, NSIUserInfo}
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.util.JsErrorOps._
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging}
import uk.gov.hmrc.http.logging.LoggingDetails
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveController @Inject() (nsiConnector:                NSIConnector,
                                      jsonSchemaValidationService: JSONSchemaValidationService,
                                      auditor:                     HTSAuditor)(implicit transformer: LogMessageTransformer) extends BaseController with ServicesConfig with Logging {

  import HelpToSaveController.Error._

  implicit def mdcExecutionContext(implicit loggingDetails: LoggingDetails): ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails

  lazy val correlationIdHeaderName: String = getString("microservice.correlationIdHeaderName")

  def createAccount(): Action[AnyContent] = Action.async { implicit request ⇒

    val correlationId = request.headers.get(correlationIdHeaderName)

    processRequest[SubmissionSuccess] {
      nsiConnector.createAccount(_).leftMap[Error](NSIError)
    } {
      (submissionSuccess, nSIUserInfo) ⇒
        if (submissionSuccess.accountAlreadyCreated) {
          Conflict
        } else {
          auditor.sendEvent(AccountCreated(nSIUserInfo), nSIUserInfo.nino, correlationId)
          Created
        }
    }
  }

  def updateEmail(): Action[AnyContent] = Action.async { implicit request ⇒
    processRequest[Unit](
      nsiConnector.updateEmail(_).leftMap[Error](e ⇒ NSIError(SubmissionFailure(e, "Could not update email")))) {
        (_, _) ⇒ Ok
      }
  }

  def getAccountByNino: Action[AnyContent] = Action.async { implicit request ⇒
    nsiConnector.getAccountByNino(request.rawQueryString)
      .fold({
        e ⇒
          val message = s"Could not get account details due to : $e"
          logger.warn(message)
          Status(500)(message)
      }, {
        response ⇒ Option(response.body).fold[Result](Status(response.status))(body ⇒ Status(response.status)(body))
      })
  }

  private def processRequest[T](doRequest: NSIUserInfo ⇒ EitherT[Future, Error, T])(handleResult: (T, NSIUserInfo) ⇒ Result)(implicit request: Request[AnyContent]): Future[Result] = {
    val result: EitherT[Future, Error, (T, NSIUserInfo)] = for {
      userInfo ← EitherT.fromEither[Future](extractNSIUSerInfo(request))
      _ ← EitherT.fromEither[Future](jsonSchemaValidationService.validate(Json.toJson(userInfo)))
        .leftMap(e ⇒ InvalidRequest("Invalid data found in request", e))
      response ← doRequest(userInfo)
    } yield (response, userInfo)

    result.fold[Result]({
      case InvalidRequest(m, d) ⇒
        BadRequest(SubmissionFailure(m, d).toJson)
      case NSIError(f) ⇒
        InternalServerError(f.toJson)
    }, {
      case (subResult, nsiUserInfo) ⇒ handleResult(subResult, nsiUserInfo)
    }
    )
  }

  private def extractNSIUSerInfo(request: Request[AnyContent]): Either[InvalidRequest, NSIUserInfo] = {
    request.body.asJson.map(_.validate[NSIUserInfo]) match {
      case Some(JsSuccess(userInfo, _)) ⇒ Right(userInfo)
      case Some(e: JsError)             ⇒ Left(InvalidRequest("Could not parse JSON in request", e.prettyPrint()))
      case None                         ⇒ Left(InvalidRequest("No JSON found in request", "JSON body required"))

    }
  }

}

object HelpToSaveController {

  private sealed trait Error

  private object Error {

    case class InvalidRequest(message: String, details: String) extends Error

    case class NSIError(error: SubmissionFailure) extends Error

  }

}
