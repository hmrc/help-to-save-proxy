/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.{validBankDetails, validNSIPayload}

class NSIPayloadSpec extends WordSpec with Matchers { // scalastyle:off magic.number

  val email = validNSIPayload.contactDetails.email

  "The NSIUSerInfo" must {

    "have JSON format" which {

      "reads and writes NSIUserInfo" in {
        Json.fromJson[NSIPayload](Json.toJson(validNSIPayload)) shouldBe JsSuccess(validNSIPayload)
      }

      "reads and writes dates in the format 'yyyyMMdd'" in {
        val date = LocalDate.of(1234, 5, 6)

        // check the happy path
        val json1 = Json.toJson(validNSIPayload.copy(dateOfBirth = date))
        (json1 \ "dateOfBirth").get shouldBe JsString("12340506")

        // check the read will fail if the date is in the wrong format
        val json2 = json1.as[JsObject] ++ Json.obj("dateOfBirth" → JsString("not a date"))
        Json.fromJson[NSIPayload](json2).isError shouldBe true

        // check that read will fail if the date is not a string
        val json3 = json1.as[JsObject] ++ Json.obj("dateOfBirth" → JsNumber(0))
        Json.fromJson[NSIPayload](json3).isError shouldBe true
      }
    }

    "have an reads instance" which {

      def performReads(userInfo: NSIPayload): NSIPayload = Json.toJson(userInfo).validate[NSIPayload].get

      "removes new line, tab and carriage return in forename" in {
        val modifiedForename = "\n\t\rname\t"
        performReads(validNSIPayload.copy(forename = modifiedForename)).forename shouldBe "name"
      }

      "removes white spaces in forename" in {
        val forenameWithSpaces = " " + "forename" + " "
        performReads(validNSIPayload.copy(forename = forenameWithSpaces)).forename shouldBe "forename"
      }

      "removes spaces, tabs, new lines and carriage returns from a double barrel forename" in {
        val forenameDoubleBarrel = "   John\t\n\r   Paul\t\n\r   "
        performReads(validNSIPayload.copy(forename = forenameDoubleBarrel)).forename shouldBe "John Paul"
      }

      "removes spaces, tabs, new lines and carriage returns from a double barrel forename with a hyphen" in {
        val forenameDoubleBarrel = "   John\t\n\r-Paul\t\n\r   "
        performReads(validNSIPayload.copy(forename = forenameDoubleBarrel)).forename shouldBe "John -Paul"
      }

      "removes whitespace from surname" in {
        performReads(validNSIPayload.copy(surname = " surname")).surname shouldBe "surname"
      }

      "removes leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname" in {
        val modifiedSurname = "   Luis\t\n\r   Guerra\t\n\r   "
        performReads(validNSIPayload.copy(surname = modifiedSurname)).surname shouldBe "Luis Guerra"
      }

      "removes leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname with a hyphen" in {
        val modifiedSurname = "   Luis\t\n\r-Guerra\t\n\r   "
        performReads(validNSIPayload.copy(surname = " " + modifiedSurname)).surname shouldBe "Luis -Guerra"
      }

      "removes new lines, tabs, carriage returns and trailing whitespaces from all address lines" in {
        val specialAddress =
          ContactDetails(
            "\naddress \tline1\r  ",
            " line2",
            Some("line3\t  "),
            Some("line4"),
            Some("line5\t\n  "),
            "BN43 XXX",
            None,
            None,
            None,
            "comms"
          )
        val ui: NSIPayload = validNSIPayload.copy(contactDetails = specialAddress)
        val result = performReads(ui)

        result.contactDetails.address1 shouldBe "address line1"
        result.contactDetails.address2 shouldBe "line2"
        result.contactDetails.address3 shouldBe Some("line3")
        result.contactDetails.address4 shouldBe Some("line4")
        result.contactDetails.address5 shouldBe Some("line5")
        result.contactDetails.postcode shouldBe "BN43XXX"
      }

      "removes leading and trailing whitespaces, new lines, tabs, carriage returns from all address lines" in {
        val specialAddress = ContactDetails(
          "   Address\t\n\r   line1\t\n\r   ",
          "   Address\t\n\r   line2\t\n\r   ",
          Some("   Address\t\n\r   line3\t\n\r   "),
          Some("   Address\t\n\r   line4\t\n\r   "),
          Some("   Address\t\n\r   line5\t\n\r   "),
          "BN43 XXX",
          None,
          None,
          None,
          "comms"
        )

        val json: JsValue = Json.toJson(validNSIPayload.copy(contactDetails = specialAddress))
        val result = json.validate[NSIPayload].get

        result.contactDetails.address1 shouldBe "Address line1"
        result.contactDetails.address2 shouldBe "Address line2"
        result.contactDetails.address3 shouldBe Some("Address line3")
        result.contactDetails.address4 shouldBe Some("Address line4")
        result.contactDetails.address5 shouldBe Some("Address line5")
        result.contactDetails.postcode shouldBe "BN43XXX"
      }

      "removes any spaces bigger than 1 character" in {
        val longName = "John Paul      \n\t\r   Harry"
        val longSurname = "  Smith    Brown  \n\r  "
        val specialAddress = ContactDetails(
          "   Address\t\n\r     line1\t\n\r   ",
          "   Address\t\n\r     line2\t\n\r   ",
          Some("   Address\t\n\rline3\t\n\r   "),
          Some("   Address\t\n\r   line4\t\n\r   "),
          Some("   Address\t\n\r             line5\t\n\r   "),
          "BN43XXX  \t\r\n",
          Some("GB    \n\r\t"),
          None,
          None,
          "comms"
        )

        val json: JsValue =
          Json.toJson(validNSIPayload.copy(forename = longName, surname = longSurname, contactDetails = specialAddress))
        val result = json.validate[NSIPayload].get

        result.forename shouldBe "John Paul Harry"
        result.surname shouldBe "Smith Brown"
        result.contactDetails.address1 shouldBe "Address line1"
        result.contactDetails.address2 shouldBe "Address line2"
        result.contactDetails.address3 shouldBe Some("Address line3")
        result.contactDetails.address4 shouldBe Some("Address line4")
        result.contactDetails.address5 shouldBe Some("Address line5")
        result.contactDetails.postcode shouldBe "BN43XXX"
        result.contactDetails.countryCode shouldBe Some("GB")
      }

      "filters out country codes equal to the string 'other'" in {
        Set("other", "OTHER", "Other").foreach { other ⇒
          val result = performReads(
            validNSIPayload.copy(contactDetails = validNSIPayload.contactDetails.copy(countryCode = Some(other))))
          result.contactDetails.countryCode shouldBe None
        }
      }

      "remove hyphened suffix from country codes" in {
        Set("GB-ENG", "GB-NIR", "GB-SCT", "GB-WLS").foreach { gb ⇒
          val result = performReads(
            validNSIPayload.copy(contactDetails = validNSIPayload.contactDetails.copy(countryCode = Some(gb))))
          result.contactDetails.countryCode shouldBe Some("GB")
        }
      }

      "formats sort codes correctly" in {
        List(
          "1234-56",
          "123456",
          "12 34 56",
          "12_34_56"
        ).foreach { sortCode ⇒
          withClue(s"For sort code $sortCode: ") {
            val json: JsValue =
              Json.toJson(validNSIPayload.copy(nbaDetails = Some(validBankDetails.copy(sortCode = sortCode))))
            val result = json.validate[NSIPayload].get
            result.nbaDetails.map(_.sortCode) shouldBe Some("12-34-56")
          }
        }
      }

      "removes leading and trailing whitespaces, new lines, tabs, carriage returns from sort codes" in {
        val sortCode = "  12\t34\r5\n6  "

        val json: JsValue =
          Json.toJson(validNSIPayload.copy(nbaDetails = Some(validBankDetails.copy(sortCode = sortCode))))
        val result = json.validate[NSIPayload].get
        result.nbaDetails.map(_.sortCode) shouldBe Some("12-34-56")
      }

      "removes leading and trailing whitespaces, new lines, tabs, carriage returns from account numbers" in {
        val accountNumber = "  12\t34\r5\n678  "

        val json: JsValue =
          Json.toJson(validNSIPayload.copy(nbaDetails = Some(validBankDetails.copy(accountNumber = accountNumber))))
        val result = json.validate[NSIPayload].get
        result.nbaDetails.map(_.accountNumber) shouldBe Some("12345678")
      }

      "removes leading and trailing whitespaces, new lines, tabs, carriage returns from roll numbers" in {
        val rollNumber = "  ab\tcd\re\n1  "
        val json: JsValue =
          Json.toJson(validNSIPayload.copy(nbaDetails = Some(validBankDetails.copy(rollNumber = Some(rollNumber)))))
        val result = json.validate[NSIPayload].get
        result.nbaDetails.flatMap(_.rollNumber) shouldBe Some("abcde1")
      }

      "removes leading and trailing whitespaces, new lines, tabs, carriage returns from account names" in {
        val accountName = "  ab\tcd\re f\ng  "

        val json: JsValue =
          Json.toJson(validNSIPayload.copy(nbaDetails = Some(validBankDetails.copy(accountName = accountName))))
        val result = json.validate[NSIPayload].get
        result.nbaDetails.map(_.accountName) shouldBe Some("ab cd e f g")
      }

    }
  }

}
