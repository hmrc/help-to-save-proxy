/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, NINO}

object TestLogMessageTransformer {

  implicit val transformer: LogMessageTransformer = new LogMessageTransformer {
    override def transform(message: String, nino: NINO, correlationId: Option[String]): String =
      s"$nino ${correlationId.getOrElse("")} - $message"
  }

}
