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

package uk.gov.hmrc.helptosaveproxy.connectors

import java.util.UUID
import javax.inject.{Inject, Singleton}

import cats.data.EitherT
import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import play.api.http.Status
import uk.gov.hmrc.helptosaveproxy.config.{AppConfig, WSHttpProxy}
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics._
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DWPConnectorImpl])
trait DWPConnector {

  def ucClaimantCheck(nino: String, transactionId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, HttpResponse]

  def healthCheck()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit]
}

@Singleton
class DWPConnectorImpl @Inject() (httpProxy:         WSHttpProxy,
                                  metrics:           Metrics,
                                  pagerDutyAlerting: PagerDutyAlerting)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
  extends DWPConnector with Logging {

  def ucClaimantCheck(nino: String, transactionId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, HttpResponse] = {

    val timeContext: Timer.Context = metrics.dwpClaimantCheckTimer.time()

    EitherT(httpProxy.get(appConfig.dwpUrl(nino, transactionId))(hc.copy(authorization = None, token = None), ec)
      .map[Either[String, HttpResponse]] { response ⇒
        val time = timeContext.stop()
        response.status match {
          case Status.OK ⇒
            logger.info(s"ucClaimantCheck returned 200 (OK) with UCDetails: ${response.json}, transactionId: " +
              s"$transactionId, ${timeString(time)}, $nino")
            Right(HttpResponse(200, Some(response.json))) // scalastyle:ignore magic.number
          case other ⇒
            pagerDutyAlerting.alert("Received unexpected http status in response to uc claimant check")
            metrics.dwpClaimantErrorCounter.inc()
            Left(s"ucClaimantCheck returned a status other than 200, with response body: ${response.body}, " +
              s"status: $other, transactionId: $transactionId, ${timeString(time)}, $nino")
        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          pagerDutyAlerting.alert("Failed to make call to uc claimant check")
          metrics.dwpClaimantErrorCounter.inc()
          Left(s"Encountered error while trying to make ucClaimantCheck call, with message: ${e.getMessage}, " +
            s"transactionId: $transactionId, ${timeString(time)}, $nino")
      })
  }

  def healthCheck()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT(httpProxy.get(appConfig.dwpHealthCheckURL).map[Either[String, Unit]] { response ⇒
      response.status match {
        case Status.OK ⇒ Right(())
        case other     ⇒ Left(s"Received status $other from DWP health check. Response body was '${response.body}'")
      }
    })

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

}
