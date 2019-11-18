/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.mvc.ControllerComponents
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport extends TestSupport {

  val authProviders: AuthProviders = AuthProviders(GovernmentGateway, PrivilegedApplication)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthResultWithFail()(ex: Throwable): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(authProviders, EmptyRetrieval, *, *)
      .returning(Future.failed(ex))

  def mockAuthResultWithSuccess(): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(authProviders, EmptyRetrieval, *, *)
      .returning(Future.successful(()))

}
