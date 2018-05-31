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

import cats.instances.future._
import com.google.inject.Inject
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosaveproxy.connectors.DWPConnector
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging, WithMdcExecutionContext}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

class UCClaimantCheckController @Inject() (dwpConnector: DWPConnector)(implicit transformer: LogMessageTransformer)
  extends BaseController with Logging with WithMdcExecutionContext {

  def ucClaimantCheck(nino: String, transactionId: UUID, threshold: Double): Action[AnyContent] = Action.async { implicit request ⇒
    dwpConnector.ucClaimantCheck(nino, transactionId, threshold).fold(
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
