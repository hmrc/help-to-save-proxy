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

package uk.gov.hmrc.helptosaveproxy.http

import com.google.inject.ImplementedBy
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import uk.gov.hmrc.helptosaveproxy.config.DwpWsClient
import uk.gov.hmrc.http.client.{HttpClientV2, HttpClientV2Impl}
import uk.gov.hmrc.http.hooks.HttpHook

import javax.inject.{Inject, Singleton}

@ImplementedBy(classOf[DwpHttpClientV2Impl])
trait DwpHttpClientV2 extends HttpClientV2

@Singleton
class DwpHttpClientV2Impl @Inject()(
  dwpWsClient: DwpWsClient,
  actorSystem: ActorSystem,
  configuration: Configuration,
  httpHooks: Seq[HttpHook])
    extends HttpClientV2Impl(dwpWsClient, actorSystem, configuration, httpHooks) with DwpHttpClientV2
