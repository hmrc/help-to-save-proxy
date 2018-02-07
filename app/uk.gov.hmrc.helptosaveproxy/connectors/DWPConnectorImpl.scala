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
class DWPConnectorImpl @Inject()(conf: Configuration) extends DWPConnector with Logging {

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