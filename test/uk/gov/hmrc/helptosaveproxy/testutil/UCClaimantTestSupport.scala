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

package uk.gov.hmrc.helptosaveproxy.testutil

import uk.gov.hmrc.helptosaveproxy.models.UCDetails

trait UCClaimantTestSupport {

  val eUCDetails = UCDetails("Y", Some("Y"))
  val nonEUCDetails = UCDetails("Y", Some("N"))
  val nonUCClaimantDetails = UCDetails("N", None)

  /**
   * NINO starting with WP01 is UC claimant and within threshold (Y, Y), returning HTTP status 200
   * NINO starting with WP02 is UC claimant but outside threshold (Y, N), returning HTTP status 200
   * NINO starting with WP03 is NOT UC claimant (N), returning HTTP status 200
   * NINO starting with WSXXX returns HTTP status XXX (currently with no JSON payload), e.g. NINO WS400111D returns HTTP status 400
   */
  val encodedNinoForWP01 = "V1AwMTAxMjNB"
  val encodedNinoForWP02 = "V1AwMjAxMjNB"
  val encodedNinoForWP03 = "V1AwMzAxMjNB"

}
