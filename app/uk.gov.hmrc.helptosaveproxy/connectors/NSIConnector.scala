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

import cats.data.EitherT
import cats.instances.string._
import cats.syntax.eq._
import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.helptosaveproxy.config.{AppConfig, WSHttpProxy}
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo
import uk.gov.hmrc.helptosaveproxy.models.NSIUserInfo.nsiUserInfoFormat
import uk.gov.hmrc.helptosaveproxy.models.SubmissionResult._
import uk.gov.hmrc.helptosaveproxy.util.HttpResponseOps._
import uk.gov.hmrc.helptosaveproxy.util.Logging._
import uk.gov.hmrc.helptosaveproxy.util.Toggles._
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, Logging, NINO, PagerDutyAlerting, Result, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.helptosaveproxy.models.AccountNumber

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess]

  def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

  def healthCheck(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

  def queryAccount(resource: String, queryString: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HttpResponse]

}

@Singleton
class NSIConnectorImpl @Inject() (auditConnector:    AuditConnector,
                                  metrics:           Metrics,
                                  pagerDutyAlerting: PagerDutyAlerting)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig) extends NSIConnector with Logging {

  val httpProxy: WSHttpProxy = new WSHttpProxy(auditConnector, appConfig.runModeConfiguration, "microservice.services.nsi.proxy")

  private val nsiCreateAccountUrl = appConfig.nsiCreateAccountUrl
  private val nsiAuthHeaderKey = appConfig.nsiAuthHeaderKey
  private val nsiBasicAuth = appConfig.nsiBasicAuth
  private val correlationIdHeaderName: String = appConfig.getString("microservice.correlationIdHeaderName")

  private def getCorrelationId(implicit hc: HeaderCarrier) = hc.headers.find(p ⇒ p._1 === correlationIdHeaderName).map(_._2)

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess] = {

    val nino = userInfo.nino
    val correlationId = getCorrelationId

    logger.info(s"Trying to create an account using NSI endpoint ${appConfig.nsiCreateAccountUrl}", nino, correlationId)

    FEATURE("log-account-creation-json", appConfig.runModeConfiguration, logger).thenOrElse(
      logger.info(s"CreateAccount JSON is ${Json.toJson(userInfo)}", nino, correlationId),
      ()
    )

    val timeContext: Timer.Context = metrics.nsiAccountCreationTimer.time()

    EitherT(httpProxy.post(nsiCreateAccountUrl, userInfo, Map(nsiAuthHeaderKey → nsiBasicAuth))(nsiUserInfoFormat, hc.copy(authorization = None), ec)
      .map[Either[SubmissionFailure, SubmissionSuccess]] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.CREATED ⇒
            logger.info(s"createAccount/insert returned 201 (Created) ${timeString(time)}", nino, correlationId)
            response.parseJSON[AccountNumber]() match {

              case Right(AccountNumber(number)) ⇒ Right(SubmissionSuccess(Some(AccountNumber(number))))
              case _                            ⇒ Left(SubmissionFailure(None, "account created but no account number was returned", ""))
            }

          case Status.CONFLICT ⇒
            logger.info(s"createAccount/insert returned 409 (Conflict). Account had already been created - " +
              s"proceeding as normal ${timeString(time)}", nino, correlationId)
            Right(SubmissionSuccess(None))

          case other ⇒
            pagerDutyAlerting.alert("Received unexpected http status in response to create account")
            Left(handleErrorStatus(other, response, userInfo.nino, time, correlationId))
        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          pagerDutyAlerting.alert("Failed to make call to create account")
          metrics.nsiAccountCreationErrorCounter.inc()

          logger.warn(s"Encountered error while trying to create account ${timeString(time)}", e, nino, correlationId)
          Left(SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage))
      })
  }

  override def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = EitherT[Future, String, Unit] {
    val nino = userInfo.nino

    val timeContext: Timer.Context = metrics.nsiUpdateEmailTimer.time()
    val correlationId = getCorrelationId

    httpProxy.put(nsiCreateAccountUrl, userInfo, true, Map(nsiAuthHeaderKey → nsiBasicAuth))(nsiUserInfoFormat, hc.copy(authorization = None), ec)
      .map[Either[String, Unit]] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.OK ⇒
            logger.info(s"createAccount/update returned 200 OK from NSI ${timeString(time)}", nino, correlationId)
            Right(())

          case other ⇒
            metrics.nsiUpdateEmailErrorCounter.inc()
            pagerDutyAlerting.alert("Received unexpected http status in response to update email")
            Left(s"Received unexpected status $other from NS&I while trying to update email ${timeString(time)}. " +
              s"Body was ${maskNino(response.body)}")

        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          pagerDutyAlerting.alert("Failed to make call to update email")
          metrics.nsiUpdateEmailErrorCounter.inc()

          Left(s"Encountered error while trying to create account: ${e.getMessage} ${timeString(time)}")
      }
  }

  override def healthCheck(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] = EitherT[Future, String, Unit] {
    httpProxy.put(nsiCreateAccountUrl, userInfo, false, Map(nsiAuthHeaderKey → nsiBasicAuth))(nsiUserInfoFormat, hc.copy(authorization = None), ex)
      .map[Either[String, Unit]] { response ⇒
        response.status match {
          case Status.OK ⇒ Right(())
          case other     ⇒ Left(s"Received unexpected status $other from NS&I while trying to do health-check. Body was ${maskNino(response.body)}")
        }
      }.recover {
        case e ⇒ Left(s"Encountered error while trying to create account: ${e.getMessage}")
      }
  }

  override def queryAccount(resource: String, queryString: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[HttpResponse] = {
    val url = s"${appConfig.nsiQueryAccountUrl}/$resource?$queryString"
    logger.info(s"Trying to query account: $url")

    EitherT(httpProxy.get(url, Map(nsiAuthHeaderKey → nsiBasicAuth))(hc.copy(authorization = None), implicitly[ExecutionContext])
      .map[Either[String, HttpResponse]](Right(_))
      .recover {
        case e ⇒ Left(e.getMessage)
      })
  }

  private def handleErrorStatus(status: Int, response: HttpResponse, nino: NINO, time: Long, correlationId: Option[String])(implicit hc: HeaderCarrier) = {
    metrics.nsiAccountCreationErrorCounter.inc()

    status match {
      case Status.BAD_REQUEST ⇒
        logger.warn(s"Failed to create account as NSI, received status 400 (Bad Request) from NSI ${timeString(time)}", nino, correlationId)
        handleBadRequest(response)

      case Status.INTERNAL_SERVER_ERROR ⇒
        logger.warn(s"Failed to create account as NSI, received status 500 (Internal Server Error) from NSI ${timeString(time)}", nino, correlationId)
        handleError(response)

      case Status.SERVICE_UNAVAILABLE ⇒
        logger.warn(s"Failed to create account as NSI, received status 503 (Service Unavailable) from NSI ${timeString(time)}", nino, correlationId)
        handleError(response)

      case other ⇒
        logger.warn(s"Unexpected error during creating account, received status $other ${timeString(time)}", nino, correlationId)
        handleError(response)
    }
  }

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  private def handleBadRequest(response: HttpResponse): SubmissionFailure = {
    response.parseJSON[SubmissionFailure](Some("error")) match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error) ⇒
        logger.warn(s"error parsing bad request response from NSI, error = $error, response body is = ${maskNino(response.body)}")
        SubmissionFailure("Bad request", "")
    }
  }

  private def handleError(response: HttpResponse): SubmissionFailure = {
    logger.warn(s"response body from NSI=${maskNino(response.body)}")
    SubmissionFailure("Server error", "")
  }
}
