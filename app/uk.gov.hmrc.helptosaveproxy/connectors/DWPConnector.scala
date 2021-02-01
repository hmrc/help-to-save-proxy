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

package uk.gov.hmrc.helptosaveproxy.connectors

import java.util.UUID

import akka.actor.ActorSystem
import cats.data.EitherT
import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.libs.ws.WSClient
import uk.gov.hmrc.helptosaveproxy.config.AppConfig
import uk.gov.hmrc.helptosaveproxy.http.HttpProxyClient
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics._
import uk.gov.hmrc.helptosaveproxy.util.Logging._
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.HttpAuditing

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DWPConnectorImpl])
trait DWPConnector {

  def ucClaimantCheck(nino: String, transactionId: UUID, threshold: Double)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, String, HttpResponse]

  def healthCheck()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit]
}

@Singleton
class DWPConnectorImpl @Inject()(
  httpAuditing: HttpAuditing,
  metrics: Metrics,
  pagerDutyAlerting: PagerDutyAlerting,
  wsClient: WSClient,
  system: ActorSystem)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
    extends DWPConnector with Logging {

  val proxyClient: HttpProxyClient = new HttpProxyClient(
    httpAuditing,
    appConfig.runModeConfiguration,
    wsClient,
    "microservice.services.dwp.proxy",
    system)

  def ucClaimantCheck(nino: String, transactionId: UUID, threshold: Double)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, String, HttpResponse] = {

    val timeContext: Timer.Context = metrics.dwpClaimantCheckTimer.time()

    val queryParams = Seq(
      "systemId"        -> appConfig.systemId,
      "thresholdAmount" -> threshold.toString,
      "transactionId"   -> transactionId.toString)

    EitherT(
      proxyClient
        .get(s"${appConfig.dwpBaseUrl}/hmrc/$nino", queryParams)(hc.copy(authorization = None), ec)
        .map[Either[String, HttpResponse]] { response ⇒
          val time = timeContext.stop()
          response.status match {
            case Status.OK ⇒
              logger.info(
                s"ucClaimantCheck returned 200 (OK) with UCDetails: ${response.json}, transactionId: " +
                  s"$transactionId, thresholdAmount: $threshold, ${timeString(time)}",
                nino,
                None
              )
              Right(HttpResponse(200, response.json, Map[String, Seq[String]]())) // scalastyle:ignore magic.number
            case other ⇒
              pagerDutyAlerting.alert("Received unexpected http status in response to uc claimant check")
              metrics.dwpClaimantErrorCounter.inc()
              Left(
                s"ucClaimantCheck returned a status other than 200, with response body: ${response.body}, " +
                  s"status: $other, transactionId: $transactionId, thresholdAmount: $threshold, ${timeString(time)}")
          }
        }
        .recover {
          case e ⇒
            val time = timeContext.stop()
            pagerDutyAlerting.alert("Failed to make call to uc claimant check")
            metrics.dwpClaimantErrorCounter.inc()
            Left(
              s"Encountered error while trying to make ucClaimantCheck call, with message: ${e.getMessage}, " +
                s"transactionId: $transactionId, thresholdAmount: $threshold, ${timeString(time)}")
        })
  }

  def healthCheck()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT(proxyClient.get(appConfig.dwpHealthCheckURL).map[Either[String, Unit]] { response ⇒
      response.status match {
        case Status.OK ⇒ Right(())
        case other ⇒ Left(s"Received status $other from DWP health check. Response body was '${response.body}'")
      }
    })

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

}
