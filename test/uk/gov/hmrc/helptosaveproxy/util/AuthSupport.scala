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

package uk.gov.hmrc.helptosaveproxy.util

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.helptosaveproxy.TestSupport

import scala.concurrent.Future

trait AuthSupport extends TestSupport {

  val authProviders: AuthProviders = mock[AuthProviders]

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthResultWithFail()(ex: Throwable): Unit =
    when(mockAuthConnector
      .authorise(any(), any())(using any(), any()))
      .thenReturn(Future.failed(ex))

  def mockAuthResultWithSuccess(): Unit =
    when(mockAuthConnector
      .authorise(any(), any())(using any(), any()))
      .thenReturn(Future.successful(()))

}
