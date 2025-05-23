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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.http.HttpProxyClient
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

trait HttpSupport extends TestSupport {
  
  val mockProxyClient: HttpProxyClient
    def mockGet(
    url: String,
    queryParams: Seq[(String, String)] = Seq.empty[(String, String)],
    headers: Map[String, String] = Map.empty)(response: Option[HttpResponse]): Unit =
    when(mockProxyClient
      .get(url, queryParams, headers)(any(),any()))
      .thenReturn(response.fold(Future.failed[HttpResponse](new Exception("Test exception message")))(Future.successful))

  def mockPut[A](url: String, body: A, headers: Map[String, String] = Map.empty, needsAuditing: Boolean = true)(
    result: Option[HttpResponse]): Unit =
    when(mockProxyClient
      .put(url, body, headers, needsAuditing)(any(),any(), any()))
      .thenReturn(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful))

  def mockPost[A](url: String, body: A, headers: Map[String, String] = Map.empty[String, String])(
    result: Option[HttpResponse]): Unit =
    when(mockProxyClient
      .post(url, body, headers)(any(),any(), any()))
      .thenReturn(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful))
}
