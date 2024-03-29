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

package uk.gov.hmrc.helptosaveproxy.util

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.{Configuration, Logger}

trait Logging {

  val logger: Logger = Logger(this.getClass)

}

object Logging {

  implicit class LoggerOps(val logger: Logger) {

    def debug(message: String, nino: NINO, correlationId: Option[String])(
      implicit transformer: LogMessageTransformer): Unit =
      logger.debug(transformer.transform(message, nino, correlationId))

    def info(message: String, nino: NINO, correlationId: Option[String])(
      implicit transformer: LogMessageTransformer): Unit =
      logger.info(transformer.transform(message, nino, correlationId))

    def warn(message: String, nino: NINO, correlationId: Option[String])(
      implicit transformer: LogMessageTransformer): Unit =
      logger.warn(transformer.transform(message, nino, correlationId))

    def warn(message: String, e: => Throwable, nino: NINO, correlationId: Option[String])(
      implicit transformer: LogMessageTransformer): Unit =
      logger.warn(transformer.transform(message, nino, correlationId), e)

    def error(message: String, nino: NINO, correlationId: Option[String])(
      implicit transformer: LogMessageTransformer): Unit =
      logger.error(transformer.transform(message, nino, correlationId))

    def error(message: String, e: => Throwable, nino: NINO, correlationId: Option[String])(
      implicit transformer: LogMessageTransformer): Unit =
      logger.error(transformer.transform(message, nino, correlationId), e)

  }

}

@ImplementedBy(classOf[LogMessageTransformerImpl])
trait LogMessageTransformer {
  def transform(message: String, nino: NINO, correlationId: Option[String] = None): String
}

@Singleton
class LogMessageTransformerImpl @Inject()(configuration: Configuration) extends LogMessageTransformer {

  private val ninoPrefix: NINO => String =
    if (configuration.underlying.getBoolean("nino-logging.enabled")) { nino =>
      s"For NINO [$nino], "
    } else { _ =>
      ""
    }

  private val correlationIdPrefix: Option[String] => String = {
    case Some(id) => s"for CorrelationId $id, "
    case None => ""
  }

  def transform(message: String, nino: NINO, correlationId: Option[String]): String =
    ninoPrefix(nino) + correlationIdPrefix(correlationId) + message

}
