/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveproxy.services

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.models.BankDetails
import uk.gov.hmrc.helptosaveproxy.testutil.TestData.UserData.validNSIPayload

class JSONSchemaValidationServiceImplSpec extends TestSupport {

  val fakeConfiguration = fakeApplication.configuration
  val service = new JSONSchemaValidationServiceImpl(fakeConfiguration)

  val validNSIPayloadJSON: JsValue = Json.toJson(validNSIPayload)

  implicit class JsValueOps(val jsValue: JsValue) {

    def replace[A <: JsValue](field: String, newValue: A): JsValue =
      jsValue.as[JsObject] ++ Json.obj(field → newValue)

    def replaceInner[A <: JsValue](field: String, innerField: String, newValue: A): JsValue = {
      val inner = (jsValue \ field).getOrElse(sys.error(s"Could not find field $field"))
      val innerReplaced = inner.replace(innerField, newValue)
      jsValue.replace(field, innerReplaced)
    }

    def remove(field: String): JsValue =
      jsValue.as[JsObject] - field

    def removeInner(field: String, innerField: String): JsValue = {
      val inner = (jsValue \ field).getOrElse(sys.error(s"Could not find field $field"))
      val innerReplaced = inner.remove(innerField)
      jsValue.replace(field, innerReplaced)
    }

  }

  object Fields {
    val forename = "forename"
    val surname = "surname"
    val dob = "dateOfBirth"
    val contactDetails = "contactDetails"
    val registrationChannel = "registrationChannel"
    val countryCode = "countryCode"
    val address1 = "address1"
    val address2 = "address2"
    val address3 = "address3"
    val address4 = "address4"
    val address5 = "address5"
    val postcode = "postcode"
    val communicationPreference = "communicationPreference"
    val phoneNumber = "phoneNumber"
    val email = "email"
    val nino = "nino"
    val version = "version"
    val systemId = "systemId"
    val nbaDetails = "nbaDetails"
  }

  "The JSONSchemaValidationServiceImpl" must {

    def testError(userInfo: JsValue): Unit =
      service.validate(userInfo).isLeft shouldBe true

    val dateTimeFormmater = DateTimeFormatter.BASIC_ISO_DATE

    "If the outgoing-json validation feature detects no errors return a right" in {
      service.validate(validNSIPayloadJSON) shouldBe Right(validNSIPayloadJSON)
    }

    "If the outgoing-json validation feature detects a birth date prior to 1800 it returns a left" in {
      testError(
        validNSIPayloadJSON.replace(
          Fields.dob,
          JsString(LocalDate.of(1799, 5, 5).format(dateTimeFormmater))
        ))
    }

    "If the outgoing-json validation feature detects a birth date just after to 1800 it returns a right" in {
      service
        .validate(
          validNSIPayloadJSON.replace(
            Fields.dob,
            JsString(LocalDate.of(1800, 1, 1).format(dateTimeFormmater))
          ))
        .isRight shouldBe true
    }

    "If the outgoing-json futureDate function detects a birth date in the future it returns a left " in {
      testError(
        validNSIPayloadJSON.replace(
          Fields.dob,
          JsString(LocalDate.now().plusDays(1).format(dateTimeFormmater))
        ))
    }

    "when given a NSIPayload that the json validation schema reports that the forename is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.forename, JsNumber(0)))
    }

    "when given a NSIPayload that the json validation schema reports that the forename is too short, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.forename, JsString("")))
    }

    "when given a NSIPayload that the json validation schema reports that the forename is too long, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.forename, JsString("A" * 27)))
    }

    "when given a NSIPayload that the json validation schema reports that the forename is missing" in {
      testError(validNSIPayloadJSON.remove(Fields.forename))
    }

    "when given a NSIPayload that the json validation schema reports that the surname is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.surname, JsNumber(0)))
    }

    "when given a NSIPayload that the json validation schema reports that the surname is too short, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.surname, JsString("")))

    }

    "when given a NSIPayload that the json validation schema reports that the surname is too long, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.forename, JsString("A" * 301)))
    }

    "when given a NSIPayload that the json validation schema reports that the surname is missing" in {
      testError(validNSIPayloadJSON.remove(Fields.surname))
    }

    "when given a NSIPayload that the json validation schema reports that the date of birth is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.dob, JsBoolean(false)))
    }

    "when given a NSIPayload that the json validation schema reports that the dateOfBirth is too short, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.dob, JsString("1800505")))
    }

    "when given a NSIPayload that the json validation schema reports that the dateOfBirth is too long, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.dob, JsString("180000525")))
    }

    "when given a NSIPayload that the json validation schema reports that the dateOfBirth does not meet the regex, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.dob, JsString("18oo0525")))
    }

    "when given a NSIPayload that the json validation schema reports that the dateOfBirth is missing, return a message" in {
      testError(validNSIPayloadJSON.remove(Fields.dob))
    }

    "when given a NSIPayload that the json validation schema reports that the country code is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsNumber(1)))
    }

    "when given a NSIPayload that the json validation schema reports that the country code is too short, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsString("G")))
    }

    "when given a NSIPayload that the json validation schema reports that the country code is too long, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsString("GRG")))
    }

    "when given a NSIPayload that the json validation schema reports that the country code does not meet the regex, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsString("--")))
    }

    def testAddressLines(number: Int, field: String): Unit = {
      s"when given a NSIPayload that the json validation schema reports that the address$number field is the wrong type, return a message" in {
        testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, field, JsNumber(1)))
      }

      s"when given a NSIPayload that the json validation schema reports that the address$number field is too long" in {
        testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, field, JsString("A" * 36)))
      }

      if (number == 1 || number == 2) {
        s"when given a NSIPayload that the json validation schema reports that the address$number field is missing, return a message" in {
          testError(validNSIPayloadJSON.removeInner(Fields.contactDetails, field))
        }
      }
    }

    testAddressLines(1, Fields.address1)
    testAddressLines(2, Fields.address2)
    testAddressLines(3, Fields.address3)
    testAddressLines(4, Fields.address4)
    testAddressLines(5, Fields.address5)

    "when given a NSIPayload that the json validation schema reports that the postcode field is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.postcode, JsNumber(1)))
    }

    "when given a NSIPayload that the json validation schema reports that the postcode field is too long" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.postcode, JsString("P" * 11)))
    }

    "when given a NSIPayload that the json validation schema reports that the postcode is missing, return a message" in {
      testError(validNSIPayloadJSON.removeInner(Fields.contactDetails, Fields.postcode))
    }

    "when given a NSIPayload that the json validation schema reports that the communicationPreference field is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsNumber(1)))
    }

    "when given a NSIPayload that the json validation schema reports that the communicationPreference field is too short" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsString("")))
    }

    "when given a NSIPayload that the json validation schema reports that the communicationPreference field is too long" in {
      testError(
        validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsString("AAA")))
    }

    "when given a NSIPayload that the json validation schema reports that the communicationPreference field does not meet regex" in {
      testError(
        validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsString("01")))
    }

    "when given a NSIPayload that the json validation schema reports that the communicationPreference field is missing, return a message" in {
      testError(validNSIPayloadJSON.removeInner(Fields.contactDetails, Fields.communicationPreference))
    }

    "when given a NSIPayload that the json validation schema reports that the phone number field is the wrong type, return a message" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.phoneNumber, JsNumber(0)))
    }

    "when given a NSIPayload that the json validation schema reports that the phone number is too long" in {
      testError(validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.phoneNumber, JsString("A" * 16)))
    }

    "when given a NSIPayload that the json validation schema reports that the email address is too long" in {
      testError(
        validNSIPayloadJSON.replaceInner(Fields.contactDetails, Fields.email, JsString("A" * 63 + "@" + "A" * 251)))
    }

    "when given a NSIPayload that the json validation schema reports that the registration channel is too long" in {
      testError(validNSIPayloadJSON.replace(Fields.registrationChannel, JsString("A" * 11)))
    }

    "when given a NSIPayload that the json validation schema reports that the registration channel does not meet regex, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.registrationChannel, JsString("offline")))
    }

    "when given a NSIPayload that the json validation schema reports that the registration channel is missing, return a message" in {
      testError(validNSIPayloadJSON.remove(Fields.registrationChannel))
    }

    "when given a NSIPayload that the json validation schema reports that the nino is too short" in {
      testError(validNSIPayloadJSON.replace(Fields.nino, JsString("WM23456C")))
    }

    "when given a NSIPayload that the json validation schema reports that the nino is too long" in {
      testError(validNSIPayloadJSON.replace(Fields.nino, JsString("WM1234567C")))
    }

    "when given a NSIPayload that the json validation schema reports that the nino does not meet the validation regex" in {
      testError(validNSIPayloadJSON.replace(Fields.nino, JsString("WMAA3456C")))
    }

    "when given a NSIPayload that the json validation schema reports that the nino is missing, return a message" in {
      testError(validNSIPayloadJSON.remove(Fields.nino))
    }

    "when given a NSIPayload that the json validation schema reports that the version is invalid, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.version, JsString("BAD_VERSION")))
    }

    "when given a NSIPayload that the json validation schema reports that the systemId is invalid, return a message" in {
      testError(validNSIPayloadJSON.replace(Fields.systemId, JsString("BAD_SYSTEM_ID_BAD_SYSTEM_ID_")))
    }

    "when given a NSIPayload that the json validation schema reports that the rollNumber is invalid, return a message" in {
      testError(
        validNSIPayloadJSON.replace(
          Fields.nbaDetails,
          Json.toJson(BankDetails("12-34-56", "12345678", Some("a b & c !@_"), "account name"))))
    }

    "when given a NSIPayload that the json validation schema reports that the accountNumber is invalid - too short, return a message" in {
      testError(
        validNSIPayloadJSON
          .replace(Fields.nbaDetails, Json.toJson(BankDetails("12-34-56", "", Some("897/98X"), "account name"))))
    }

    "when given a NSIPayload that the json validation schema reports that the accountNumber is invalid - too long, return a message" in {
      testError(
        validNSIPayloadJSON.replace(
          Fields.nbaDetails,
          Json.toJson(BankDetails("12-34-56", "123456789", Some("897/98X"), "account name"))))
    }

    "when given a NSIPayload that the json validation schema reports that the accountNumber is invalid - it contains a non digit, return a message" in {
      testError(validNSIPayloadJSON
        .replace(Fields.nbaDetails, Json.toJson(BankDetails("12-34-56", "1234567w", Some("897/98X"), "account name"))))
    }
  }

}
