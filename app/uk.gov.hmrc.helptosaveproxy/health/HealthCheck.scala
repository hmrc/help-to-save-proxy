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

import cats.data.{NonEmptyList, OptionT}
import cats.instances.future._
import cats.instances.int._
import cats.syntax.eq._
import com.codahale.metrics.{Histogram, MetricRegistry}
import com.typesafe.config.Config
import configs.syntax._
import org.apache.pekko.actor.{Actor, ActorLogging, Cancellable, PoisonPill, Props, Scheduler}
import org.apache.pekko.pattern.{after, ask, pipe}
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics.nanosToPrettyString

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * An actor which handles the results of a generic health check. The checks that will be run are
  * defined by `runnerProps`. The [[HealthCheck]] actor will repeatedly create a child using these props
  * at a configured time interval. These children will have to respond to a
  * [[uk.gov.hmrc.helptosaveproxy.health.HealthCheck.PerformHealthCheck]] message and respond with a [[HealthCheckResult]].
  * The appropriate interface of the children is handily provided by the [[HealthCheckRunner]] trait. After a
  * configured number of successful tests the [[HealthCheck]] actor will switch tests by using the next [[Props]] in
  * `runnerProps`. When the last [[Props]] in `runnerProps` is used, the next switch will result in the first
  * [[Props]] being used again and the [[Props]] are iterated through again in a cyclical fashion.
  *
  * The [[HealthCheck]] actor will handle [[HealthCheckResult]]s by keeping a record of the number of failures using
  * the given [[MetricRegistry]] and triggering pager duty alerts when a configured number of consecutive failures
  * has occurred.
  *
  * @param name The name of the test
  * @param config The config variables will be looked for under the namespace `health-check.{name}` where `name` is
  *               the name of the test
  * @param scheduler         The scheduler used to schedule the health tests
  * @param metrics           The metrics to be updated
  * @param pagerDutyAlert    Triggers pager duty alerts
  * @param runnerProps       The props of the tests to be run
  */
class HealthCheck(
  name: String,
  config: Config,
  scheduler: Scheduler,
  metrics: MetricRegistry,
  pagerDutyAlert: () => Unit,
  runnerProps: NonEmptyList[Props])
    extends Actor with ActorLogging {

  import context.dispatcher
  import uk.gov.hmrc.helptosaveproxy.health.HealthCheck._

  val minimumTimeBetweenChecks: FiniteDuration =
    config.get[FiniteDuration](s"health.$name.minimum-poll-period").value

  val timeBetweenChecks: FiniteDuration =
    config.get[FiniteDuration](s"health.$name.poll-period").value.max(minimumTimeBetweenChecks)

  val healthCheckTimeout: FiniteDuration =
    config.get[FiniteDuration](s"health.$name.poll-timeout").value

  val numberOfChecksBetweenUpdates: Int =
    config.get[Int](s"health.$name.poll-count-between-updates").value

  val maximumConsecutiveFailures: Int =
    config.get[Int](s"health.$name.poll-count-failures-to-alert").value

  val numberOfChecksBetweenAlerts: Int =
    config.get[Int](s"health.$name.poll-count-between-pager-duty-alerts").value

  val zone: ZoneId = ZoneId.of("Europe/London")

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

  val failureHistogram: Histogram = metrics.histogram(s"health.$name.number-of-failures")

  val propsQueue: NonEmptyCyclicalQueue[Props] = NonEmptyCyclicalQueue(runnerProps)

  var currentRunnerProps: Props = propsQueue.next()

  var performTestTask: Option[Cancellable] = None

  def prettyString(d: ZonedDateTime): String = d.format(dateTimeFormatter)

  override def receive: Receive = ok(0)

  /**
    * In this state the previous check was a success.
    *
    * @param successCount This count determines when the props used to perform
    *                     the next health check switches
    */
  def ok(successCount: Int): Receive = performTest orElse {
    case HealthCheckResult.Success(message, nanos) =>
      log.info(s"$loggingPrefix - OK ${timeString(nanos)}. Missed-beat counter is 0. $message")
      val newCount = successCount + 1

      if (newCount === numberOfChecksBetweenUpdates - 1) {
        currentRunnerProps = propsQueue.next()
      }

      becomeOK(newCount % numberOfChecksBetweenUpdates)

    case HealthCheckResult.Failure(message, nanos) =>
      log.warning(s"$loggingPrefix - just started to fail ${timeString(nanos)}. Missed-beat counter is 1. $message")

      val now = ZonedDateTime.now(zone)

      if (maximumConsecutiveFailures > 1) {
        becomeFailing(now)
      } else {
        pagerDutyAlert()
        becomeFailed(now)
      }

  }

  /**
    * In this state the previous check was a failure and the number of failures had
    * not reached the maximum allowed yet.
    */
  def failing(downSince: ZonedDateTime, fails: Int): Receive = performTest orElse {
    case HealthCheckResult.Success(message, nanos) =>
      log.info(
        s"$loggingPrefix - was failing since ${prettyString(downSince)} but now OK ${timeString(nanos)}. Missed-beat counter is 0. $message")
      becomeOK()

    case HealthCheckResult.Failure(message, nanos) =>
      val newFails = fails + 1
      log.warning(
        s"$loggingPrefix - still failing since ${prettyString(downSince)} ${timeString(nanos)}. Missed-beat counter is $newFails. $message")

      if (newFails < maximumConsecutiveFailures) {
        becomeFailing(downSince, newFails)
      } else {
        pagerDutyAlert()
        becomeFailed(downSince)
      }

  }

  /**
    * In this state the previous check was a failure and the maximum allowed failures had
    * been reached
    */
  def failed(downSince: ZonedDateTime, fails: Int): Receive = performTest orElse {
    case HealthCheckResult.Success(message, nanos) =>
      log.warning(
        s"$loggingPrefix - had failed since ${prettyString(downSince)} but now OK ${timeString(nanos)}. Missed-beat counter is 0. $message")
      becomeOK()

    case HealthCheckResult.Failure(message, nanos) =>
      val newFails = fails + 1
      log.warning(
        s"$loggingPrefix - still failing since ${prettyString(downSince)} ${timeString(nanos)}. Missed-beat counter is $newFails. $message")
      becomeFailed(downSince, newFails)

      if (newFails % numberOfChecksBetweenAlerts === 0) {
        pagerDutyAlert()
      }
  }

  def performTest: Receive = {
    case PerformHealthCheck =>
      val runner = context.actorOf(currentRunnerProps)
      val result: Future[HealthCheckResult] =
        withTimeout(
          runner.ask(PerformHealthCheck)(healthCheckTimeout).mapTo[HealthCheckResult],
          healthCheckTimeout
        ).getOrElse(HealthCheckResult.Failure("Health check timed out", healthCheckTimeout.toNanos))
          .recover { case NonFatal(e) => HealthCheckResult.Failure(e.getMessage, 0L) }

      result.onComplete { _ =>
        runner ! PoisonPill
      }
      result pipeTo self
  }

  def becomeOK(count: Int = 1): Unit = {
    failureHistogram.update(0)
    context become ok(count)
  }

  def becomeFailing(downSince: ZonedDateTime, fails: Int = 1): Unit = {
    failureHistogram.update(fails)
    context become failing(downSince, fails)
  }

  def becomeFailed(downSince: ZonedDateTime, fails: Int = maximumConsecutiveFailures): Unit = {
    failureHistogram.update(fails)
    context become failed(downSince, fails)
  }

  def withTimeout[A](f: Future[A], timeout: FiniteDuration): OptionT[Future, A] =
    OptionT(
      Future.firstCompletedOf(
        Seq(
          f.map(Some(_)),
          after(timeout, scheduler)(Future.successful(None))
        )))

  val loggingPrefix: String = s"[HealthCheck: $name]"

  def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"$loggingPrefix Starting scheduler to poll every $timeBetweenChecks")
    performTestTask = Some(
      scheduler.scheduleAtFixedRate(timeBetweenChecks, timeBetweenChecks, self, PerformHealthCheck))
  }

  override def postStop(): Unit = {
    super.postStop()
    performTestTask.foreach(_.cancel())
  }

}

object HealthCheck {

  def props(
    name: String,
    config: Config,
    scheduler: Scheduler,
    metricRegistry: MetricRegistry,
    pagerDutyAlert: () => Unit,
    runnerProps: Props,
    otherRunnerProps: Props*): Props =
    Props(
      new HealthCheck(
        name,
        config,
        scheduler,
        metricRegistry,
        pagerDutyAlert,
        NonEmptyList(runnerProps, otherRunnerProps.toList))
    )

  case object PerformHealthCheck

  /**
    * Uses the `NonEmptyVector` to produce a queue which when dequeueing an element will
    * put the dequeued element back on the end of the queue. In this way, this queue is
    * never-ending because once the elements in the `NonEmptyVector` have been exhausted
    * the queue will dequeue the same elements again. For example:
    * {{{
    *   val queue = NonEmptyCyclicalQueue(NonEmptyVector("a", Vector("b", "c"))
    *   queue.next()  // = "a"
    *   queue.next()  // = "b"
    *   queue.next()  // = "c"
    *   queue.next()  // = "a"
    *   queue.next()  // = "b"
    *   queue.next()  // = "c"
    *   queue.next()  // = "a"
    *   ...
    * }}}
    */
  private[health] case class NonEmptyCyclicalQueue[A](xs: NonEmptyList[A]) {

    private var queue: List[A] = xs.toList

    val next: () => A = xs.tail match {
      case Nil =>
        () =>
          xs.head

      case _ =>
        () =>
          queue.headOption.fold {
            // we've exhausted the list - start from the beginning
            queue = xs.tail
            xs.head
          } { next =>
            queue = queue.tail
            next
          }
    }
  }

}
