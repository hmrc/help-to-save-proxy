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

import configs.syntax._
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import com.google.inject.Inject
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.helptosaveproxy.connectors.DWPConnector
import uk.gov.hmrc.helptosaveproxy.health.DWPConnectionHealthCheck.DWPConnectionHealthCheckRunner
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.util.lock.Lock
import uk.gov.hmrc.helptosaveproxy.util.{Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{MongoLockRepository}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class DWPConnectionHealthCheck @Inject()(
  system: ActorSystem,
  configuration: Configuration,
  metrics: Metrics,
  dWPConnector: DWPConnector,
  mongo: MongoComponent,
  lifecycle: ApplicationLifecycle,
  pagerDutyAlerting: PagerDutyAlerting,
    mongoLockRepository: MongoLockRepository)
    extends Logging {

  private val name: String = "dwp-connection"

  private val enabled: Boolean = configuration.underlying.get[Boolean](s"health.$name.enabled").value

  private val lockDuration: FiniteDuration = configuration.underlying.get[FiniteDuration](s"health.$name.lock-duration").value

  private def newHealthCheck(): ActorRef = system.actorOf(
    HealthCheck.props(
      name,
      configuration.underlying,
      system.scheduler,
      metrics.metrics,
      () => pagerDutyAlerting.alert("DWP health check has failed"),
      DWPConnectionHealthCheckRunner.props(dWPConnector, metrics)
    )
  )

  // make sure we only have one instance of the health check running across
  // multiple instances of the application in the same environment
  private lazy val lockedHealthCheck: ActorRef =
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

object DWPConnectionHealthCheck {

  class DWPConnectionHealthCheckRunner(dwpConnector: DWPConnector, metrics: Metrics)
      extends Actor with HealthCheckRunner with Logging {

    import context.dispatcher

    implicit val hc: HeaderCarrier = HeaderCarrier()

    override def performTest(): Future[HealthCheckResult] = {
      val timer = metrics.dwpHealthCheckTimer.time()

      dwpConnector
        .healthCheck()
        .value
        .map { result =>
          val time = timer.stop()
          result.fold[HealthCheckResult](
            e => HealthCheckResult.Failure(e, time),
            _ => HealthCheckResult.Success("DWP health check returned 200 OK", time))
        }
        .recover {
          case NonFatal(e) =>
            val time = timer.stop()
            HealthCheckResult.Failure(e.getMessage, time)
        }
    }

  }

  object DWPConnectionHealthCheckRunner {
    def props(dwpConnector: DWPConnector, metrics: Metrics): Props =
      Props(new DWPConnectionHealthCheckRunner(dwpConnector, metrics))
  }

}
