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

import java.util.Base64

import cats.instances.future._
import com.google.inject.Inject
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosaveproxy.connectors.DWPConnector
import uk.gov.hmrc.helptosaveproxy.util.TryOps._
import uk.gov.hmrc.helptosaveproxy.util.{Logging, NINO, NINOLogMessageTransformer}
import uk.gov.hmrc.play.microservice.controller.BaseController
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.util.Try

class UCClaimantCheckController @Inject() (dwpConnector: DWPConnector)(implicit transformer: NINOLogMessageTransformer) extends BaseController with Logging {

  val base64Decoder: Base64.Decoder = Base64.getDecoder()

  def ucClaimantCheck(encodedNino: String): Action[AnyContent] = Action.async { implicit request ⇒
    withBase64DecodedNINO(encodedNino) {
      decodedNino ⇒
        dwpConnector.ucClaimantCheck(decodedNino).fold(
          {
            e ⇒
              logger.warn(s"Could not perform UC Claimant check: $e")
              InternalServerError
          }, {
            r ⇒
              Ok(r.json)
          }
        )
    }
  }

  private def withBase64DecodedNINO(ninoParam: String)(f: NINO ⇒ Future[Result])(implicit request: Request[AnyContent]): Future[Result] =
    Try(new String(base64Decoder.decode(ninoParam))).fold(
      { error ⇒
        logger.warn(s"Could not decode nino from encrypted param: $error")
        InternalServerError
      }, f
    )

}
