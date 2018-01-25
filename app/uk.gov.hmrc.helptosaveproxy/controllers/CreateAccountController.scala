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

import CreateAccountController.CreateAccountError
import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveproxy.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosaveproxy.util.JsErrorOps._
import uk.gov.hmrc.http.logging.LoggingDetails
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class CreateAccountController @Inject() (nsiConnector:                NSIConnector,
                                         jsonSchemaValidationService: JSONSchemaValidationService) extends BaseController {

  import CreateAccountController.CreateAccountError._

  implicit def mdcExecutionContext(implicit loggingDetails: LoggingDetails): ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails

  def createAccount(): Action[AnyContent] = Action.async { implicit request ⇒
    val result: EitherT[Future, CreateAccountError, SubmissionSuccess] = for {
      userInfo ← EitherT.fromEither[Future](extractNSIUSerInfo(request))
      _ ← EitherT.fromEither[Future](jsonSchemaValidationService.validate(Json.toJson(userInfo)))
        .leftMap(e ⇒ InvalidRequest("Invalid data found in request", e))
      response ← nsiConnector.createAccount(userInfo).leftMap[CreateAccountError](NSIError)
    } yield response

    result.fold[Result]({
      case InvalidRequest(m, d) ⇒
        BadRequest(SubmissionFailure(m, d).toJson)
      case NSIError(f) ⇒
        InternalServerError(f.toJson)
    }, s ⇒
      if (s.accountAlreadyCreated) {
        Conflict
      } else {
        Created
      }
    )
  }

  private def extractNSIUSerInfo(request: Request[AnyContent]): Either[InvalidRequest, NSIUserInfo] = {
    request.body.asJson.map(_.validate[NSIUserInfo]) match {
      case Some(JsSuccess(userInfo, _)) ⇒ Right(userInfo)
      case Some(e: JsError)             ⇒ Left(InvalidRequest("Could not parse JSON in request", e.prettyPrint))
      case None                         ⇒ Left(InvalidRequest("No JSON found in request", "JSON body required"))

    }
  }

}

object CreateAccountController {

  private sealed trait CreateAccountError

  private object CreateAccountError {

    case class InvalidRequest(message: String, details: String) extends CreateAccountError

    case class NSIError(error: SubmissionFailure) extends CreateAccountError

  }

}
