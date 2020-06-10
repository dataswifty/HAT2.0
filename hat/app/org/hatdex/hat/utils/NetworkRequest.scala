package org.hatdex.hat.utils

import io.dataswift.adjudicator.Types.ContractId
import play.api.http.HttpVerbs
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.Future

// TODO: rename to adjudicator
// TODO: details from config
object NetworkRequest {

  //get the URL from config
  def getPublicKey(keyId: String, ws: WSClient)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val url = s"http://localhost:8080/v1/tokens/${keyId}"
    println(url)
    val req = makeRequest(url, ws)
    req.get()

  }

  def joinContract(hatName: String, contractId: ContractId, ws: WSClient)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val url = s"http://localhost:8080/v1/contracts/${contractId}/hat/${hatName}"
    println(url)
    val req = makeRequest(url, ws)
    req.post("d")
  }

  def leaveContract(hatName: String, contractId: ContractId, ws: WSClient)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val url = s"http://localhost:8080/v1/contracts/${contractId}/hat/${hatName}"
    println(url)
    val req = makeRequest(url, ws)
    req.delete()
  }

  // TODO: pass in the verb
  private def makeRequest(url: String, ws: WSClient)(implicit ec: ExecutionContext): WSRequest = {
    ws.url(url)
    //.withHttpHeaders("Accept" -> "application/json"
    //, "X-Auth-Token" -> hatSharedSecret)
    // )
    //request.get()
  }
}