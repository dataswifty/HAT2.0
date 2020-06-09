package org.hatdex.hat.utils

import sttp.client._
import sttp.client.akkahttp._

import scala.concurrent.Future

object NetworkRequest {
  implicit val backend = AkkaHttpBackend()

  def getPublicKey(keyId: String): Future[Response[Either[String, String]]] = {
    val url = s"http://localhost:8080/${keyId}"
    val request = basicRequest.get(uri"${url}")

    request.send()
  }

  def run() = {

    val request = basicRequest.get(uri"http://localhost:8080/")

    request.send()

    // response.header(...): Option[String]
    //    println(response.header("Content-Length"))
    //
    //    // response.body: by default read into an Either[String, String]
    //    // to indicate failure or success
    //    println(response.body)
    //
    //    // alternatively, if you prefer to pass the backend explicitly, instead
    //    // of using implicits, you can also call:
    //    val sameResponse = backend.send(request)
  }
}