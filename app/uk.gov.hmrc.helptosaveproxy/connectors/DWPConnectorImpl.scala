/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.Configuration
import play.api.http.Status
import uk.gov.hmrc.helptosaveproxy.config.AppConfig.dwpUCClaimantCheckUrl
import uk.gov.hmrc.helptosaveproxy.config.WSHttpProxy
import uk.gov.hmrc.helptosaveproxy.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DWPConnectorImpl])
trait DWPConnector {

  def ucClaimantCheck(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, HttpResponse]

}

@Singleton
class DWPConnectorImpl @Inject() (conf: Configuration) extends DWPConnector with Logging {

  val httpProxy: WSHttpProxy = new WSHttpProxy(conf)

  def ucClaimantCheck(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, HttpResponse] = {

    EitherT(httpProxy.get(dwpUCClaimantCheckUrl(nino))(hc, ec)
      .map[Either[String, HttpResponse]]{ response ⇒
        response.status match {
          case Status.OK ⇒
            logger.info(s"ucClaimantCheck returned 200 (OK) with UCDetails: ${response.json}")
            Right(HttpResponse(200, Some(response.json))) // scalastyle:ignore magic.number
          case _ ⇒
            Left(s"ucClaimantCheck returned a status other than 200, with response body: ${response.body}")
        }
      }.recover {
        case e ⇒
          Left(s"Encountered error while trying to make ucClaimantCheck call, with message: ${e.getMessage}")
      })
  }

}
