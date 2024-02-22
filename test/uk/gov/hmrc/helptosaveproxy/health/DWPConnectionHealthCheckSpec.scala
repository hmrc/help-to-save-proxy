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

import org.apache.pekko.actor.{ActorRef, Props}
import cats.data.EitherT
import uk.gov.hmrc.helptosaveproxy.connectors.DWPConnector
import uk.gov.hmrc.helptosaveproxy.health.DWPConnectionHealthCheck.DWPConnectionHealthCheckRunner
import uk.gov.hmrc.helptosaveproxy.health.HealthCheck.PerformHealthCheck
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class DWPConnectionHealthCheckSpec extends ActorTestSupport("DWPConnectionHealthCheckRunnerSpec") {

  val dwpConnector: DWPConnector = mock[DWPConnector]

  def newRunner(): ActorRef = system.actorOf(Props(new DWPConnectionHealthCheckRunner(dwpConnector, mockMetrics)))

  def mockDwpConnectorTest(result: Option[Either[String, Unit]]): Unit =
    (dwpConnector
      .healthCheck()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(
        EitherT(result.fold[Future[Either[String, Unit]]](Future.failed(new Exception("")))(Future.successful))
      )

  "The DWPConnectionHealthCheckRunner" when {

    "sent a PerformTest message" must {

      "call the DWPConnector to do the test and reply back with a successful result " +
        "if the DWPConnector returns a success" in {
        mockDwpConnectorTest(Some(Right(())))

        val runner = newRunner()
        runner ! PerformHealthCheck
        expectMsgType[HealthCheckResult.Success]
      }

      "call the DWPConnector to do the test and reply back with a negative result " +
        "if the DWPConnector returns a failure" in {
        def test(mockActions: => Unit): Unit = {
          mockActions

          val runner = newRunner()
          runner ! PerformHealthCheck
          expectMsgType[HealthCheckResult.Failure]
        }

        test(mockDwpConnectorTest(Some(Left(""))))
        test(mockDwpConnectorTest(None))
      }
    }

  }

}
