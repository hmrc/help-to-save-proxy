/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.Base64

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, sc: ServicesConfig) {

  def base64Encode(input: String): Array[Byte] = Base64.getEncoder.encode(input.getBytes)

  def base64Decode(input: String): Array[Byte] = Base64.getDecoder.decode(input)

  def getString(key: String): String = sc.getString(key)

  def logLevel: String = sc.getString("logger.help-to-save-proxy")

  val appName: String = sc.getString("appName")

  val nsiAuthHeaderKey: String = sc.getString("microservice.services.nsi.client.httpheader.header-key")

  val nsiBasicAuth: String = {
    val user = new String(
      base64Decode(sc.getString("microservice.services.nsi.client.httpheader.basicauth.Base64User")))
    val password = new String(
      base64Decode(sc.getString("microservice.services.nsi.client.httpheader.basicauth.Base64Password")))
    val encoding = sc.getString("microservice.services.nsi.client.httpheader.encoding")

    s"Basic ${new String(base64Encode(s"$user:$password"), encoding)}"
  }

  val nsiCreateAccountUrl: String = s"${sc.baseUrl("nsi")}/nsi-services/account"

  val nsiQueryAccountUrl: String = s"${sc.baseUrl("nsi")}/nsi-services"

  val systemId: String = sc.getString("microservice.services.dwp.system-id")

  val dwpBaseUrl: String = sc.baseUrl("dwp")

  val dwpHealthCheckURL: String = s"$dwpBaseUrl/hmrc-healthcheck"

}
