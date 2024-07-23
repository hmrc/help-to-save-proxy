/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosaveproxy.testutil

import org.scalacheck.Gen
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosaveproxy.models.{BankDetails, NSIPayload}
import uk.gov.hmrc.smartstub.AutoGen.{GenProvider, instance}
import uk.gov.hmrc.smartstub._

import java.time.LocalDate
import scala.language.implicitConversions

object TestData {

  object UserData {

    implicit def providerLocalDate(s: String): GenProvider[LocalDate] =
      instance({
        s.toLowerCase match {
          case "dateofbirth" | "dob" | "birthdate" => Gen.date(LocalDate.of(1900, 1, 1), LocalDate.now())
          case _                                   => Gen.date
        }
      })

    val contactDetailsGen: Gen[ContactDetails] = for {
      address1                <- Gen.alphaNumStr
      address2                <- Gen.alphaNumStr
      address3                <- Gen.option(Gen.alphaNumStr)
      address4                <- Gen.option(Gen.alphaNumStr)
      address5                <- Gen.option(Gen.alphaNumStr)
      postcode                <- Gen.postcode
      countryCode             <- Gen.option(Gen.alphaStr)
      email                   <- Gen.option(Gen.alphaNumStr)
      phoneNumber             <- Gen.option(Gen.ukPhoneNumber)
      communicationPreference <- Gen.alphaStr
    } yield
      ContactDetails(
        address1,
        address2,
        address3,
        address4,
        address5,
        postcode,
        countryCode,
        email,
        phoneNumber,
        communicationPreference)

    val bankDetailsGen: Gen[BankDetails] = for {
      sortCode      <- Gen.numStr
      accountNumber <- Gen.numStr
      rollNumber    <- Gen.option(Gen.numStr)
      accountName   <- Gen.alphaStr
    } yield BankDetails(sortCode, accountNumber, rollNumber, accountName)

    val userInfoGen: Gen[NSIPayload] =
      for {
        forename            <- Gen.alphaStr
        surname             <- Gen.alphaStr
        dateOfBirth         <- Gen.date
        nino                <- Gen.alphaNumStr
        contactDetails      <- contactDetailsGen
        registrationChannel <- Gen.alphaNumStr
        nbaDetails          <- Gen.option(bankDetailsGen)
        version             <- Gen.option(Gen.numStr)
        systemId            <- Gen.option(Gen.numStr)
      } yield
        NSIPayload(
          forename,
          surname,
          dateOfBirth,
          nino,
          contactDetails,
          registrationChannel,
          nbaDetails,
          version,
          systemId)

    def randomUserInfo(): NSIPayload = sample(userInfoGen)

    /**
      * Valid user details which will pass NSI validation checks
      */
    val (nsiValidContactDetails, validNSIPayload, validBankDetails) = {
      val (forename, surname) = "Tyrion" -> "Lannister"
      val dateOfBirth = LocalDate.ofEpochDay(0L)
      val addressLine1 = "Casterly Rock"
      val addressLine2 = "The Westerlands"
      val addressLine3 = "Westeros"
      val postcode = "BA148FY"
      val country = "GB"
      val nino = "WM123456C"
      val email = "tyrion_lannister@gmail.com"
      val phone = "07890000000"
      val comms = "00"
      val regChannel = "callCentre"

      val bankDetails = BankDetails("12-34-56", "12345678", Some("rollnumber"), "account name")

      val nsiValidContactDetails =
        ContactDetails(
          addressLine1,
          addressLine2,
          Some(addressLine3),
          None,
          None,
          postcode,
          Some(country),
          Some(email),
          Some(phone),
          comms)
      val nsiPayload =
        NSIPayload(
          forename,
          surname,
          dateOfBirth,
          nino,
          nsiValidContactDetails,
          regChannel,
          Some(bankDetails),
          Some("V2.0"),
          Some("systemId"))

      (nsiValidContactDetails, nsiPayload, bankDetails)
    }
  }

}
