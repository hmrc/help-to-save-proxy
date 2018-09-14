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

import cats.instances.string._
import cats.syntax.eq._
import play.api.libs.json._
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload.ContactDetails

import scala.util.{Failure, Success, Try}

case class NSIPayload(forename:            String,
                      surname:             String,
                      dateOfBirth:         LocalDate,
                      nino:                String,
                      contactDetails:      ContactDetails,
                      registrationChannel: String,
                      nbaDetails:          Option[BankDetails] = None,
                      version:             Option[String],
                      systemId:            Option[String])

object NSIPayload {

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

  implicit val nsiPayloadFormat: Format[NSIPayload] = new Format[NSIPayload] {

    val writes: Writes[NSIPayload] = Json.writes[NSIPayload]
    val reads: Reads[NSIPayload] = Json.reads[NSIPayload]

    override def writes(o: NSIPayload): JsValue = writes.writes(o)

    override def reads(json: JsValue): JsResult[NSIPayload] = reads.reads(json).map { u ⇒
      val c = u.contactDetails
      NSIPayload(
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
          c.countryCode.map(_.cleanupSpecialCharacters.removeAllSpaces).filter(_.toLowerCase =!= "other"),
          c.email,
          c.phoneNumber,
          c.communicationPreference.cleanupSpecialCharacters.removeAllSpaces
        ),
        u.registrationChannel.cleanupSpecialCharacters.removeAllSpaces,
        u.nbaDetails.map(formatBankDetails),
        u.version,
        u.systemId
      )

    }

    private val allowedSortCodeSeparators = Set(' ', '-', '_')

    private def formatBankDetails(bankDetails: BankDetails): BankDetails =
      BankDetails(
        bankDetails.sortCode.cleanupSpecialCharacters.removeAllSpaces.filterNot(allowedSortCodeSeparators.contains).grouped(2).mkString("-"),
        bankDetails.accountNumber.cleanupSpecialCharacters.removeAllSpaces,
        bankDetails.rollNumber.map(_.cleanupSpecialCharacters.removeAllSpaces),
        bankDetails.accountName.trim.cleanupSpecialCharacters
      )

  }

}
