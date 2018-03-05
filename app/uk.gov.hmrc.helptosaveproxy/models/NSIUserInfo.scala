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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo.ContactDetails

import scala.util.{Failure, Success, Try}

case class NSIUserInfo(forename:            String,
                       surname:             String,
                       dateOfBirth:         LocalDate,
                       nino:                String,
                       contactDetails:      ContactDetails,
                       registrationChannel: String)

object NSIUserInfo {

  case class ContactDetails(address1:                String,
                            address2:                String,
                            address3:                Option[String],
                            address4:                Option[String],
                            address5:                Option[String],
                            postcode:                String,
                            countryCode:             Option[String],
                            email:                   Option[String],
                            phoneNumber:             Option[String],
                            communicationPreference: String)

  private implicit class StringOps(val s: String) {
    def removeAllSpaces: String = s.replaceAll(" ", "")

    def cleanupSpecialCharacters: String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  }

  implicit val dateFormat: Format[LocalDate] = new Format[LocalDate] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    override def writes(o: LocalDate): JsValue = JsString(o.format(formatter))

    override def reads(json: JsValue): JsResult[LocalDate] = json match {
      case JsString(s) ⇒ Try(LocalDate.parse(s, formatter)) match {
        case Success(date)  ⇒ JsSuccess(date)
        case Failure(error) ⇒ JsError(s"Could not parse date as yyyyMMdd: ${error.getMessage}")
      }

      case other ⇒ JsError(s"Expected string but got $other")
    }
  }

  implicit val contactDetailsFormat: Format[ContactDetails] = Json.format[ContactDetails]

  implicit val nsiUserInfoFormat: Format[NSIUserInfo] = new Format[NSIUserInfo] {

    val writes: Writes[NSIUserInfo] = Json.writes[NSIUserInfo]
    val reads: Reads[NSIUserInfo] = Json.reads[NSIUserInfo]

    override def writes(o: NSIUserInfo): JsValue = writes.writes(o)

    override def reads(json: JsValue): JsResult[NSIUserInfo] = reads.reads(json).map{ u ⇒
      val c = u.contactDetails
      NSIUserInfo(
        u.forename.cleanupSpecialCharacters,
        u.surname.cleanupSpecialCharacters,
        u.dateOfBirth,
        u.nino.cleanupSpecialCharacters.removeAllSpaces,
        ContactDetails(
          c.address1.cleanupSpecialCharacters,
          c.address2.cleanupSpecialCharacters,
          c.address3.map(_.cleanupSpecialCharacters),
          c.address4.map(_.cleanupSpecialCharacters),
          c.address5.map(_.cleanupSpecialCharacters),
          c.postcode.cleanupSpecialCharacters.removeAllSpaces,
          c.countryCode,
          c.email,
          c.phoneNumber,
          c.communicationPreference.cleanupSpecialCharacters.removeAllSpaces
        ),
        u.registrationChannel.cleanupSpecialCharacters.removeAllSpaces
      )

    }
  }

}
