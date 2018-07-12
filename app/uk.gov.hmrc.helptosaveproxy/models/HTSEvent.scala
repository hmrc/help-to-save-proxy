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

package uk.gov.hmrc.helptosaveproxy.models

import uk.gov.hmrc.helptosaveproxy.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.DataEvent

trait HTSEvent {
  val value: DataEvent
}

object HTSEvent {
  def apply(appName:   String,
            auditType: String,
            detail:    Map[String, String])(implicit hc: HeaderCarrier): DataEvent =
    DataEvent(appName, auditType = auditType, detail = detail, tags = hc.toAuditTags("", "N/A"))

}

case class AccountCreated(userInfo: NSIUserInfo, source: Option[String])(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {

  val value: DataEvent = HTSEvent(
    appConfig.appName,
    "AccountCreated",
    Map[String, String](
      "forename" → userInfo.forename,
      "surname" → userInfo.surname,
      "dateOfBirth" → userInfo.dateOfBirth.toString,
      "nino" → userInfo.nino,
      "address1" → userInfo.contactDetails.address1,
      "address2" → userInfo.contactDetails.address2,
      "address3" → userInfo.contactDetails.address3.fold("")(identity),
      "address4" → userInfo.contactDetails.address4.fold("")(identity),
      "address5" → userInfo.contactDetails.address5.fold("")(identity),
      "postcode" → userInfo.contactDetails.postcode,
      "countryCode" → userInfo.contactDetails.countryCode.fold("")(identity),
      "email" → userInfo.contactDetails.email.fold("")(identity),
      "phoneNumber" → userInfo.contactDetails.phoneNumber.fold("")(identity),
      "communicationPreference" → userInfo.contactDetails.communicationPreference,
      "registrationChannel" → userInfo.registrationChannel,
      "source" → source.getOrElse("")
    )
  )
}
