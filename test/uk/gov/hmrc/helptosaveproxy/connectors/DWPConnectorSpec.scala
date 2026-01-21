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

import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, inject}
import uk.gov.hmrc.helptosaveproxy.http.DwpHttpClientV2
import uk.gov.hmrc.helptosaveproxy.models.UCDetails
import uk.gov.hmrc.helptosaveproxy.testutil.UCClaimantTestSupport
import uk.gov.hmrc.helptosaveproxy.util.WireMockMethods
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class DWPConnectorSpec
    extends AnyWordSpec with WireMockMethods with WireMockSupport with GuiceOneAppPerSuite with UCClaimantTestSupport
    with EitherValues {
  val transactionId: UUID = UUID.randomUUID()
  val threshold = 650.0
  val noHeaders: Map[String, Seq[String]] = Map[String, Seq[String]]()

  val configString =  s"""
                         |microservice {
                         |  services{
                         |    dwp {
                         |      protocol = http
                         |      host = $wireMockHost
                         |      port = $wireMockPort
                         |    }
                         |  }
                         |}
                         |
                         |"""

  private val config = Configuration(
    ConfigFactory.parseString(
     configString.stripMargin
    )
  )

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(config)
      .build()

  val connector: DWPConnector = app.injector.instanceOf[DWPConnectorImpl]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  def build200Response(uCDetails: UCDetails): HttpResponse =
    HttpResponse(200, Json.toJson(uCDetails), noHeaders) // scalastyle:ignore magic.number

  def resultValue(result: EitherT[Future, String, HttpResponse]): Either[String, HttpResponse] =
    Await.result(result.value, 3.seconds)

  val queryParams: Map[String, String] =
    Map("systemId" -> "607", "thresholdAmount" -> threshold.toString, "transactionId" -> transactionId.toString)

  "the ucClaimantCheck call" must {
    "return a Right with HttpResponse(200, Some(Y, Y)) when given an eligible NINO of a UC Claimant within the threshold" in {
      val ucDetails = build200Response(eUCDetails)
      val dwpUrl = "/hmrc/WP010123A"
      when(
        GET,
        dwpUrl,
        queryParams
      ) `thenReturn` (ucDetails.status, ucDetails.body)
      val result = connector.ucClaimantCheck("WP010123A", transactionId, threshold)
      val httpResp = resultValue(result)
      httpResp.value.status shouldBe 200
    }
    "return a Right with HttpResponse(200, Some(Y, N)) when given a NINO of a UC Claimant that is not within the threshold" in {
      val ucDetails = build200Response(nonEUCDetails)
      val dwpUrl = "/hmrc/WP020123A"
      when(
        GET,
        dwpUrl,
        queryParams
      ) `thenReturn` (ucDetails.status, ucDetails.body)
      val result = connector.ucClaimantCheck("WP020123A", transactionId, threshold)
      val httpResp = resultValue(result)
      httpResp.value.status shouldBe 200
    }
    "return a Right with HttpResponse(200, Some(N)) when given a NINO of a non UC Claimant" in {
      val ucDetails = build200Response(nonUCClaimantDetails)
      val dwpUrl = "/hmrc/WP030123A"
      when(
        GET,
        dwpUrl,
        queryParams
      ) `thenReturn` (ucDetails.status, ucDetails.body)
      val result = connector.ucClaimantCheck("WP030123A", transactionId, threshold)
      val httpResp = resultValue(result)
      httpResp.value.status shouldBe 200
    }

    "return a Left when the ucClaimant call comes back with a status other than 200" in {
      val ucDetails = HttpResponse(500, Json.toJson(nonUCClaimantDetails), noHeaders) // scalastyle:ignore magic.number
      val dwpUrl = "/hmrc/WP030123A"
      when(
        GET,
        dwpUrl,
        queryParams
      ) `thenReturn` (ucDetails.status, ucDetails.body)
      val result = connector.ucClaimantCheck("WP030123A", transactionId, threshold)
      val httpResp = resultValue(result)
      httpResp.isLeft shouldBe true
    }

    "return a Left when the ucClaimant call fails" in {
      wireMockServer.stop()
      val result = connector.ucClaimantCheck("WP030123A", transactionId, threshold)
      val httpResp = resultValue(result)
      wireMockServer.start()
      httpResp.isLeft shouldBe true
    }

    "Verify when stub is enabled" in {

      val ucDetails = build200Response(nonUCClaimantDetails)
      val requestBuilder : RequestBuilder = mock[RequestBuilder]
      val http = mock[DwpHttpClientV2]

      val testApp = new GuiceApplicationBuilder().configure(config)
        .overrides(inject.bind[DwpHttpClientV2].toInstance(http))
        .build()

      Mockito.when(http.get(any())(using any())).thenReturn(requestBuilder)
      Mockito.when(requestBuilder.transform(any())).thenReturn(requestBuilder)
      Mockito.when(requestBuilder.execute(using any(),any())).thenReturn(Future.successful(ucDetails))
      val testConnector = testApp.injector.instanceOf[DWPConnectorImpl]

      val result = testConnector.ucClaimantCheck("WP030123A", transactionId, threshold)

      val httpResp = resultValue(result)

      verify(requestBuilder, never()).withProxy

      httpResp.value.status shouldBe 200
    }
    "Verify when stub is disabled" in {

      val configStringDisabled = configString.concat("dwp.stubs.enabled = false")

      val ucDetails = build200Response(nonUCClaimantDetails)
      val requestBuilder: RequestBuilder = mock[RequestBuilder]
      val http = mock[DwpHttpClientV2]

      val testApp = new GuiceApplicationBuilder().configure(Configuration(
          ConfigFactory.parseString(
            configStringDisabled.stripMargin
          )
        ))
        .overrides(inject.bind[DwpHttpClientV2].toInstance(http))
        .build()

      Mockito.when(http.get(any())(using any())).thenReturn(requestBuilder)
      Mockito.when(requestBuilder.withProxy).thenReturn(requestBuilder)
      Mockito.when(requestBuilder.transform(any())).thenReturn(requestBuilder)
      Mockito.when(requestBuilder.execute(using any(), any())).thenReturn(Future.successful(ucDetails))
      val testConnector = testApp.injector.instanceOf[DWPConnectorImpl]

      val result = testConnector.ucClaimantCheck("WP030123A", transactionId, threshold)

      val httpResp = resultValue(result)

      verify(requestBuilder,times(1)).withProxy

      httpResp.value.status shouldBe 200
    }
  }
}
