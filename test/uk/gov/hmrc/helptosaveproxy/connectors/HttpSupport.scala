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

package uk.gov.hmrc.helptosaveproxy.connectors

import org.scalamock.scalatest.MockFactory
import play.api.libs.json.Writes
import uk.gov.hmrc.helptosaveproxy.http.HttpProxyClient
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.matchers.should.Matchers

trait HttpSupport { this: MockFactory with Matchers =>

  val mockProxyClient: HttpProxyClient

  def mockGet(
    url: String,
    queryParams: Seq[(String, String)] = Seq.empty[(String, String)],
    headers: Map[String, String] = Map.empty)(response: Option[HttpResponse]): Unit =
    (mockProxyClient
      .get(_: String, _: Seq[(String, String)], _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(where {
        (u: String, q: Seq[(String, String)], hdrs: Map[String, String], h: HeaderCarrier, _: ExecutionContext) =>
          // use matchers here to get useful error messages when the following predicates
          // are not satisfied - otherwise it is difficult to tell in the logs what went wrong
          u shouldBe url
          q shouldBe queryParams
          hdrs shouldBe headers
          true
      })
      .returning(response.fold(Future.failed[HttpResponse](new Exception("Test exception message")))(Future.successful))

  def mockPut[A](url: String, body: A, headers: Map[String, String] = Map.empty, needsAuditing: Boolean = true)(
    result: Option[HttpResponse]): Unit =
    (mockProxyClient
      .put(_: String, _: A, _: Map[String, String], _: Boolean)(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(where {
        (u: String, a: A, hdrs: Map[String, String], n: Boolean, _: Writes[A], h: HeaderCarrier, _: ExecutionContext) =>
          u shouldBe url
          a shouldBe body
          hdrs shouldBe headers
          n shouldBe needsAuditing
          true
      })
      .returning(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful))

  def mockPost[A](url: String, body: A, headers: Map[String, String] = Map.empty[String, String])(
    result: Option[HttpResponse]): Unit =
    (mockProxyClient
      .post(_: String, _: A, _: Map[String, String])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, headers, *, *, *)
      .returning(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful))

}
