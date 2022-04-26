/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.ActorSystem
import cats.data.EitherT
import cats.instances.string._
import cats.syntax.eq._
import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.helptosaveproxy.config.AppConfig
import uk.gov.hmrc.helptosaveproxy.http.HttpProxyClient
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosaveproxy.models.{AccountNumber, NSIPayload}
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload.nsiPayloadFormat
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult._
import uk.gov.hmrc.helptosaveproxy.util.HttpResponseOps._
import uk.gov.hmrc.helptosaveproxy.util.Logging._
import uk.gov.hmrc.helptosaveproxy.util.Toggles._
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging, NINO, PagerDutyAlerting, Result, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.HttpAuditing

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(payload: NSIPayload)(
    implicit hc: HeaderCarrier,
    ex: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess]

  def updateEmail(payload: NSIPayload)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

  def healthCheck(payload: NSIPayload)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

  def queryAccount(resource: String, queryString: Map[String, Seq[String]])(
    implicit hc: HeaderCarrier,
    ex: ExecutionContext): Result[HttpResponse]

}

@Singleton
class NSIConnectorImpl @Inject()(
  httpAuditing: HttpAuditing,
  metrics: Metrics,
  pagerDutyAlerting: PagerDutyAlerting,
  wsClient: WSClient,
  system: ActorSystem)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
    extends NSIConnector with Logging {

  val proxyClient: HttpProxyClient = new HttpProxyClient(
    httpAuditing,
    appConfig.runModeConfiguration,
    wsClient,
    "microservice.services.nsi.proxy",
    system)

  private val nsiCreateAccountUrl = appConfig.nsiCreateAccountUrl
  private val nsiAuthHeaderKey = appConfig.nsiAuthHeaderKey
  private val nsiBasicAuth = appConfig.nsiBasicAuth
  private val correlationIdHeaderName: String = appConfig.getString("microservice.correlationIdHeaderName")

  private def getCorrelationId(implicit hc: HeaderCarrier) =
    hc.headers(Seq(correlationIdHeaderName)).find(p ⇒ p._1 === correlationIdHeaderName).map(_._2)

  override def createAccount(payload: NSIPayload)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess] = {

    logCreateAccount(payload)
    val correlationId = getCorrelationId
    val timeContext = metrics.nsiAccountCreationTimer.time()
    EitherT(proxyClient
      .post(nsiCreateAccountUrl, payload, Map(nsiAuthHeaderKey → nsiBasicAuth))(
        nsiPayloadFormat,
        hc.copy(authorization = None),
        ec)
      .map[Either[SubmissionFailure, SubmissionSuccess]] { response ⇒
        val time = stopTime(timeContext)
        response.status match {
          case Status.CREATED ⇒
            logger.info(s"createAccount/insert returned 201 (Created) $time", payload.nino, correlationId)
            response.parseJSON[AccountNumber]() match {

              case Right(AccountNumber(number)) ⇒ Right(SubmissionSuccess(Some(AccountNumber(number))))
              case _ ⇒ Left(SubmissionFailure(None, "account created but no account number was returned", ""))
            }

          case Status.CONFLICT ⇒
            logger.info(
              s"createAccount/insert returned 409 (Conflict). Account had already been created - proceeding as normal $time",
              payload.nino,
              correlationId)
            Right(SubmissionSuccess(None))

          case other ⇒
            pagerDutyAlerting.alert("Received unexpected http status in response to create account")
            Left(handleErrorStatus(other, response, payload.nino, time, correlationId))
        }
      }
      .recover {
        case e ⇒
          val time = stopTime(timeContext)
          pagerDutyAlerting.alert("Failed to make call to create account")
          metrics.nsiAccountCreationErrorCounter.inc()

          logger.warn(s"Encountered error while trying to create account $time", e, payload.nino, correlationId)
          Left(SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage))
      })
  }

  private def logCreateAccount(payload: NSIPayload)(implicit hc: HeaderCarrier): Unit = {
    val nino = payload.nino
    val correlationId = getCorrelationId

    logger.info(s"Trying to create an account using NS&I endpoint $nsiCreateAccountUrl", nino, correlationId)

    FEATURE("log-account-creation-json", appConfig.runModeConfiguration, logger).thenOrElse(
      logger.info(s"CreateAccount JSON is ${Json.toJson(payload)}", nino, correlationId),
      ()
    )
  }

  override def updateEmail(payload: NSIPayload)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = {
    val nino = payload.nino
    val timeContext = metrics.nsiUpdateEmailTimer.time()
    val correlationId = getCorrelationId

    EitherT(
      proxyClient
        .put(nsiCreateAccountUrl, payload, Map(nsiAuthHeaderKey → nsiBasicAuth))(
          nsiPayloadFormat,
          hc.copy(authorization = None),
          ec)
        .map[Either[String, Unit]] { response ⇒
          val time = stopTime(timeContext)

          response.status match {
            case Status.OK ⇒
              logger.info(s"Update email returned 200 OK from NS&I $time", nino, correlationId)
              Right(())
            case other =>
              logger.warn(s"Update email returned status: $other and response: ${maskNino(response.body)} from NS&I.")
              metrics.nsiUpdateEmailErrorCounter.inc()
              pagerDutyAlerting.alert("Received unexpected http status in response to update email")
              Left(s"Received unexpected status $other from NS&I while trying to update email $time. Body was ${maskNino(response.body)}")
          }
        }
        .recover {
          case e ⇒
            val time = timeContext.stop()
            pagerDutyAlerting.alert("Failed to make call to update email")
            metrics.nsiUpdateEmailErrorCounter.inc()
            Left(s"Encountered error while trying to update email: ${e.getMessage} $time")
        })
  }

  private def stopTime(timeContext: Timer.Context) = {
    val nanos = timeContext.stop()
    s"(round-trip time: ${nanosToPrettyString(nanos)})"
  }

  override def healthCheck(payload: NSIPayload)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] =
    EitherT(
      proxyClient
        .put(nsiCreateAccountUrl, payload, Map(nsiAuthHeaderKey → nsiBasicAuth))(
          nsiPayloadFormat,
          hc.copy(authorization = None),
          ex)
        .map[Either[String, Unit]] { response ⇒
          response.status match {
            case Status.OK ⇒ Right(())
            case other ⇒
              logger.warn(s"Health check returned status: $other and response: ${maskNino(response.body)} from NS&I.")
              Left(s"Received unexpected status $other from NS&I while trying to do health-check. Body was ${maskNino(
                response.body)}")
          }
        }
        .recover {
          case e ⇒ Left(s"Encountered error while trying to do health-check: ${e.getMessage}")
        })

  override def queryAccount(resource: String, queryParameters: Map[String, Seq[String]])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Result[HttpResponse] = {
    val url = s"${appConfig.nsiQueryAccountUrl}/$resource"
    logger.info(s"Trying to query account: $url")
    val timeContext: Timer.Context = metrics.nsiAccountQueryTimer.time()

    val queryParams = queryParameters.toSeq.flatMap { case (name, values) ⇒ values.map(value ⇒ (name, value)) }

    EitherT(
      proxyClient
        .get(url, queryParams, Map(nsiAuthHeaderKey → nsiBasicAuth))(hc.copy(authorization = None), ec)
        .map[Either[String, HttpResponse]] { response ⇒
          val time = timeContext.stop()
          response.status match {
            case Status.OK ⇒
              logger.info(s"queryAccount resource: $resource, response: ${response.body}")
              Right(response)
            case other ⇒
              logger.warn(s"Query account returned status: ${Status.BAD_REQUEST} and response: ${maskNino(response.body)} from NS&I.")
              Left(s"Received unexpected status $other from NS&I while trying to query account. Body was ${maskNino(response.body)} $time")
          }
        }
        .recover {
          case e ⇒
            val time = timeContext.stop()
            pagerDutyAlerting.alert("Failed to make call to query account")
            metrics.nsiAccountQueryErrorCounter.inc()
            Left(s"Encountered error while trying to query account: ${e.getMessage} $time")
        })
  }

  private def handleErrorStatus(
    status: Int,
    response: HttpResponse,
    nino: NINO,
    time: String,
    correlationId: Option[String]) = {
    metrics.nsiAccountCreationErrorCounter.inc()

    status match {
      case Status.BAD_REQUEST ⇒
        logger
          .warn(s"Failed to create account, received status 400 (Bad Request) from NS&I $time", nino, correlationId)
        handleBadRequest(response)

      case Status.INTERNAL_SERVER_ERROR ⇒
        logger.warn(
          s"Failed to create account, received status 500 (Internal Server Error) from NS&I $time",
          nino,
          correlationId)
        handleError(response)

      case Status.SERVICE_UNAVAILABLE ⇒
        logger.warn(
          s"Failed to create account, received status 503 (Service Unavailable) from NS&I $time",
          nino,
          correlationId)
        handleError(response)

      case other ⇒
        logger.warn(s"Unexpected error when creating account, received status $other $time", nino, correlationId)
        handleError(response)
    }
  }

  private def handleBadRequest(response: HttpResponse): SubmissionFailure =
    response.parseJSON[SubmissionFailure](Some("error")) match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error) ⇒
        logger.warn(
          s"error parsing bad request response from NS&I, error = $error, response body is = ${maskNino(response.body)}")
        SubmissionFailure("Bad request", "")
    }

  private def handleError(response: HttpResponse): SubmissionFailure = {
    logger.warn(s"response body from NS&I=${maskNino(response.body)}")
    SubmissionFailure("Server error", "")
  }

}
