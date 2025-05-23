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

package uk.gov.hmrc.helptosaveproxy

import com.codahale.metrics.*
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.ControllerComponents
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.helptosaveproxy.config.AppConfig
import uk.gov.hmrc.helptosaveproxy.metrics.Metrics
import uk.gov.hmrc.helptosaveproxy.testutil.TestLogMessageTransformer
import uk.gov.hmrc.helptosaveproxy.util.{LogMessageTransformer, UnitSpec}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionId}

import java.util
import java.util.UUID
import scala.concurrent.ExecutionContext

trait TestSupport extends UnitSpec  with BeforeAndAfterAll with ScalaFutures { this: Suite =>

  lazy val additionalConfig = Configuration()

  lazy implicit val configuration: Configuration = fakeApplication.injector.instanceOf[Configuration]

  def buildFakeApplication(additionalConfig: Configuration): Application =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString("""
                                      | metrics.enabled       = true
                                      | play.modules.disabled = [ "uk.gov.hmrc.helptosaveproxy.config.HealthCheckModule",
                                      | "play.api.libs.ws.ahc.AhcWSModule",
                                      | "play.api.mvc.LegacyCookiesModule" ]
                                      | microservice.services.dwp.keyManager.stores = []
                                      | microservice.services.nsi.keyManager.stores = []
                                      |     """.stripMargin)
        ) withFallback (additionalConfig))
      .build()

  lazy val fakeApplication: Application = buildFakeApplication(additionalConfig)

  lazy val mockCc: ControllerComponents = fakeApplication.injector.instanceOf[ControllerComponents]

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]
  
  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)), authorization = Some(Authorization("auth")))
  
  override def beforeAll(): Unit = {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    Play.stop(fakeApplication)
    super.afterAll()
  }

  class StubMetricRegistry extends MetricRegistry {
    override def getGauges(filter: MetricFilter): util.SortedMap[String, Gauge[_]] =
      new util.TreeMap[String, Gauge[_]]()
  }

  val mockMetrics: Metrics = new Metrics(new StubMetricRegistry()) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()

    override def histogram(name: String): Histogram = new Histogram(new UniformReservoir())
  }

  implicit lazy val transformer: LogMessageTransformer = TestLogMessageTransformer.transformer

  implicit lazy val appConfig: AppConfig = fakeApplication.injector.instanceOf[AppConfig]
}
