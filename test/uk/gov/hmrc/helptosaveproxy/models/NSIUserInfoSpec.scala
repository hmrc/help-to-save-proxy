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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._

import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIUserInfo

class NSIUserInfoSpec extends WordSpec with Matchers { // scalastyle:off magic.number

  val email = validNSIUserInfo.contactDetails.email

  "The NSIUSerInfo" must {

    "have JSON format" which {

      "reads and writes NSIUserInfo" in {
        Json.fromJson[NSIUserInfo](Json.toJson(validNSIUserInfo)) shouldBe JsSuccess(validNSIUserInfo)
      }

      "reads and writes dates in the format 'yyyyMMdd'" in {
        val date = LocalDate.of(1234, 5, 6)

        // check the happy path
        val json1 = Json.toJson(validNSIUserInfo.copy(dateOfBirth = date))
        (json1 \ "dateOfBirth").get shouldBe JsString("12340506")

        // check the read will fail if the date is in the wrong format
        val json2 = json1.as[JsObject] ++ Json.obj("dateOfBirth" → JsString("not a date"))
        Json.fromJson[NSIUserInfo](json2).isError shouldBe true

        // check that read will fail if the date is not a string
        val json3 = json1.as[JsObject] ++ Json.obj("dateOfBirth" → JsNumber(0))
        Json.fromJson[NSIUserInfo](json3).isError shouldBe true
      }
    }

    "have an reads instance" which {

        def performReads(userInfo: NSIUserInfo): NSIUserInfo = Json.toJson(userInfo).validate[NSIUserInfo].get

      "removes new line, tab and carriage return in forename" in {
        val modifiedForename = "\n\t\rname\t"
        performReads(validNSIUserInfo.copy(forename = modifiedForename)).forename shouldBe "name"
      }

      "removes white spaces in forename" in {
        val forenameWithSpaces = " " + "forename" + " "
        performReads(validNSIUserInfo.copy(forename = forenameWithSpaces)).forename shouldBe "forename"
      }

      "removes spaces, tabs, new lines and carriage returns from a double barrel forename" in {
        val forenameDoubleBarrel = "   John\t\n\r   Paul\t\n\r   "
        performReads(validNSIUserInfo.copy(forename = forenameDoubleBarrel)).forename shouldBe "John Paul"
      }

      "removes spaces, tabs, new lines and carriage returns from a double barrel forename with a hyphen" in {
        val forenameDoubleBarrel = "   John\t\n\r-Paul\t\n\r   "
        performReads(validNSIUserInfo.copy(forename = forenameDoubleBarrel)).forename shouldBe "John -Paul"
      }

      "removes whitespace from surname" in {
        performReads(validNSIUserInfo.copy(surname = " surname")).surname shouldBe "surname"
      }

      "removes leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname" in {
        val modifiedSurname = "   Luis\t\n\r   Guerra\t\n\r   "
        performReads(validNSIUserInfo.copy(surname = modifiedSurname)).surname shouldBe "Luis Guerra"
      }

      "removes leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname with a hyphen" in {
        val modifiedSurname = "   Luis\t\n\r-Guerra\t\n\r   "
        performReads(validNSIUserInfo.copy(surname = " " + modifiedSurname)).surname shouldBe "Luis -Guerra"
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
        val ui: NSIUserInfo = validNSIUserInfo.copy(contactDetails = specialAddress)
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

        val json: JsValue = Json.toJson(validNSIUserInfo.copy(contactDetails = specialAddress))
        val result = json.validate[NSIUserInfo].get

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

        val json: JsValue = Json.toJson(validNSIUserInfo.copy(forename       = longName, surname = longSurname, contactDetails = specialAddress))
        val result = json.validate[NSIUserInfo].get

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
    }
  }

}
