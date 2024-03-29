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

package uk.gov.hmrc.helptosaveproxy.health

import java.time.LocalDate
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import com.google.inject.{Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.helptosaveproxy.connectors.NSIConnector
import uk.gov.hmrc.helptosaveproxy.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner
import uk.gov.hmrc.helptosaveproxy.health.NSIConnectionHealthCheck.NSIConnectionHealthCheckRunner.Payload
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload
import uk.gov.hmrc.helptosaveproxy.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosaveproxy.util.lock.Lock
import uk.gov.hmrc.helptosaveproxy.util.{Email, Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

// $COVERAGE-OFF$
@Singleton
class NSIConnectionHealthCheck @Inject()(
  system: ActorSystem,
  configuration: Configuration,
  metrics: Metrics,
  nSIConnector: NSIConnector,
  lifecycle: ApplicationLifecycle,
  mongoLockRepository: MongoLockRepository,
  pagerDutyAlerting: PagerDutyAlerting)
    extends Logging {

  val name: String = "nsi-connection"

  val enabled: Boolean = configuration.underlying.get[Boolean](s"health.$name.enabled").value

  val lockDuration: FiniteDuration = configuration.underlying.get[FiniteDuration](s"health.$name.lock-duration").value

  val ninoLoggingEnabled: Boolean = configuration.underlying.getBoolean("nino-logging.enabled")

  def newHealthCheck(): ActorRef = system.actorOf(
    HealthCheck.props(
      name,
      configuration.underlying,
      system.scheduler,
      metrics.metrics,
      () => pagerDutyAlerting.alert("NSI health check has failed"),
      NSIConnectionHealthCheckRunner.props(nSIConnector, metrics, Payload.Payload1, ninoLoggingEnabled),
      NSIConnectionHealthCheckRunner.props(nSIConnector, metrics, Payload.Payload2, ninoLoggingEnabled)
    )
  )

  // make sure we only have one instance of the health check running across
  // multiple instances of the application in the same environment
  lazy val lockedHealthCheck: ActorRef =
    system.actorOf(
      Lock.props[Option[ActorRef]](
        s"health-check-$name",
        lockDuration,
        system.scheduler,
        None,
        _.fold(Some(newHealthCheck()))(Some(_)),
        _.flatMap { ref =>
          ref ! PoisonPill
          None
        },
        mongoLockRepository,
        lifecycle
      ),
      s"health-check-$name-lock"
    )

  // start the health check only if it is enabled
  if (enabled) {
    logger.info(s"HealthCheck $name enabled")
    val _ = lockedHealthCheck
  } else {
    logger.info(s"HealthCheck $name not enabled")
  }

}
// $COVERAGE-ON$

object NSIConnectionHealthCheck {

  class NSIConnectionHealthCheckRunner(
    nsiConnector: NSIConnector,
    metrics: Metrics,
    payload: Payload,
    ninoLoggingEnabled: Boolean)
      extends Actor with HealthCheckRunner with Logging {

    import context.dispatcher

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val successMessage: String = {
      val message = "NS&I createAccount/health check returned 200 OK"
      if (ninoLoggingEnabled) { s"For NINO [${payload.value.nino}]: $message" } else { message }
    }

    override def performTest(): Future[HealthCheckResult] = {
      val timer = metrics.nsiHealthCheckTimer.time()

      nsiConnector
        .healthCheck(payload.value)
        .value
        .map { result =>
          val time = timer.stop()
          result.fold[HealthCheckResult](
            e => HealthCheckResult.Failure(e, time),
            _ => HealthCheckResult.Success(successMessage, time))
        }
        .recover {
          case NonFatal(e) =>
            val time = timer.stop()
            HealthCheckResult.Failure(e.getMessage, time)
        }
    }

  }

  object NSIConnectionHealthCheckRunner {

    def props(nsiConnector: NSIConnector, metrics: Metrics, payload: Payload, ninoLoggingEnabled: Boolean): Props =
      Props(new NSIConnectionHealthCheckRunner(nsiConnector, metrics, payload, ninoLoggingEnabled))

    sealed trait Payload {
      val value: NSIPayload
    }

    object Payload {

      private def payload(email: Email): NSIPayload =
        NSIPayload(
          "Service",
          "Account",
          LocalDate.ofEpochDay(0L),
          "XX999999X",
          ContactDetails("Health", "Check", None, None, None, "AB12CD", None, Some(email), None, "02"),
          "online",
          None,
          None,
          None
        )

      private[health] case object Payload1 extends Payload {
        override val value: NSIPayload = payload("healthcheck_ping@noreply.com")
      }

      private[health] case object Payload2 extends Payload {
        override val value: NSIPayload = payload("healthcheck_pong@noreply.com")
      }

    }
  }

}
