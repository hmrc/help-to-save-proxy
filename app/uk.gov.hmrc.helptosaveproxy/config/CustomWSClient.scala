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

package uk.gov.hmrc.helptosaveproxy.config

import akka.stream.Materializer
import com.google.inject.{ImplementedBy, Inject}
import com.typesafe.sslconfig.ssl.SSLConfigParser
import com.typesafe.sslconfig.util.EnrichedConfig
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSConfigParser}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfigFactory, StandaloneAhcWSClient}

import javax.inject.Singleton
import scala.util.chaining.scalaUtilChainingOps

object CustomWSClient {
  def standaloneAhcWsClient(wsConfigParser: WSConfigParser, configuration: Configuration, serviceName: String)(implicit materializer: Materializer): StandaloneAhcWSClient = {
    val wsConfig = wsConfigParser.parse()

    // Path "play.ws.ssl" copied from play
    val defaultSSLConfigPath = "play.ws.ssl"
    val sslConfigParser = new SSLConfigParser(EnrichedConfig(configuration.underlying.getConfig(defaultSSLConfigPath)), getClass.getClassLoader)
    val keyManagerConfig = sslConfigParser.parseKeyManager(EnrichedConfig(configuration.underlying.getConfig(s"microservice.services.$serviceName.keyManager")))

    AhcWSClientConfigFactory.forClientConfig(wsConfig.copy(ssl = wsConfig.ssl
      .withKeyManagerConfig(keyManagerConfig))).pipe(StandaloneAhcWSClient(_))
  }
}

@ImplementedBy(classOf[DwpWsClientImpl])
trait DwpWsClient extends WSClient {}

@Singleton
class DwpWsClientImpl @Inject()(parser: WSConfigParser, config: Configuration)(implicit materializer: Materializer)
  extends AhcWSClient(CustomWSClient.standaloneAhcWsClient(parser, config, "dwp")) with DwpWsClient {}

@ImplementedBy(classOf[NsiWsClientImpl])
trait NsiWsClient extends WSClient {}

@Singleton
class NsiWsClientImpl @Inject()(parser: WSConfigParser, config: Configuration)(implicit materializer: Materializer)
  extends AhcWSClient(CustomWSClient.standaloneAhcWsClient(parser, config, "nsi")) with NsiWsClient {}

