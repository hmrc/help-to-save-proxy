package uk.gov.hmrc.helptosaveproxy.connectors

import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.helptosaveproxy.TestSupport
import uk.gov.hmrc.helptosaveproxy.config.WSHttpProxy
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.helptosaveproxy.config.AppConfig.{dwpUCClaimantCheckUrl, nsiAuthHeaderKey, nsiBasicAuth}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DWPConnectorSpec extends TestSupport with MockFactory {

  lazy val mockHTTPProxy = mock[WSHttpProxy]

  def testDWPConnectorImpl = new DWPConnectorImpl(
    fakeApplication.configuration ++ Configuration()) {
    override val httpProxy = mockHTTPProxy
  }

  // put in fake authorization details - these should be removed by the call to create an account
  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("auth")))

  def mockGet[I](url: String)(result: Either[String, HttpResponse]): Unit =
    (mockHTTPProxy.get(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *)
      .returning(
        result.fold(
          e ⇒ Future.failed(new Exception(e)),
          r ⇒ Future.successful(r)
        ))

  val eligibleUCDetails = Json.toJson("""{"ucClaimant":"Y", "withinThreshold":"Y"}""")
  val nonEligibleUCDetails = Json.toJson("""{"ucClaimant":"Y", "withinThreshold":"N"}""")
  val nonUCClaimantDetails = Json.toJson("""{"ucClaimant":"N"}""")

  "the ucClaimantCheck call" must {
    "return a Right with HttpResponse(200, Some(Y, Y)) when given an eligible NINO" in {
      mockGet(dwpUCClaimantCheckUrl("WP010123A"))(Right(HttpResponse(200, Some(eligibleUCDetails)))) // scalastyle:ignore magic.number

      val result = testDWPConnectorImpl.ucClaimantCheck("WP010123A")
      Await.result(result.value, 3.seconds) shouldBe Right(Some(eligibleUCDetails))
    }

    "return a Right with HttpResponse(200, Some(N)) when given an iligible NINO" in {

    }

  }

}