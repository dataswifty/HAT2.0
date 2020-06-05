package org.hatdex.hat.utils

import sttp.client._
import sttp.client.akkahttp._

class NetworkRequest {
  implicit val backend = AkkaHttpBackend()



  def run() = {
    val keyId = "http language:scala"
    val request = basicRequest.get(
      uri"https://api.github.com/search/repositories?q=$query")

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