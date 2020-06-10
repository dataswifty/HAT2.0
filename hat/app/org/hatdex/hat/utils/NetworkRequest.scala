package org.hatdex.hat.utils

//import sttp.client._
//import sttp.client.akkahttp._
import org.hatdex.hat.resourceManagement.models.HatSignup
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.Future

object NetworkRequest {

  def getPublicKey(keyId: String, ws: WSClient)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val url = s"http://localhost:8080/v1/tokens/${keyId}"

    val request: WSRequest = ws.url(url) //.withHttpHeaders("Accept" -> "application/json") // , "X-Auth-Token" -> hatSharedSecret)

    request.get()
  }
}