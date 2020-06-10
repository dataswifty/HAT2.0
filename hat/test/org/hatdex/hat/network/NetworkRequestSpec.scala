/*
 * Copyright (C) 2017 HAT Data Exchange Ltd
 * SPDX-License-Identifier: AGPL-3.0
 *
 * This file is part of the Hub of All Things project (HAT).
 *
 * HAT is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of
 * the License.
 *
 * HAT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General
 * Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>
 * 2 / 2017
 */

package org.hatdex.hat.network.controllers

import io.dataswift.adjudicator.Types.{ Contract, ContractId }
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.Logger
import org.hatdex.hat.utils._
import play.api.test.{ PlaySpecification, WsTestClient }

import scala.concurrent.duration._

class NetworkRequestSpec(implicit ee: ExecutionEnv) extends PlaySpecification with Mockito {

  val logger = Logger(this.getClass)

  sequential

  val hatName = "tylertesthat"
  val contractId = ContractId(java.util.UUID.fromString("6ed280aa-f2d7-4eb7-80f9-921578acb303"))
  val contract = Contract(contractId)

  "The network" should {
        "make a request, telling adjudicator-service, to create a contract" in {
          WsTestClient.withClient { client =>
            val fut = NetworkRequest.createContract(contract, client)
            fut.map { result =>
              println(result)
              true must be equalTo (true)
            }.await(1, 10.seconds)
          }
        }

    "make a request, telling adjudicator-service, that the HAT is joining a contract" in {
      WsTestClient.withClient { client =>
        val fut = NetworkRequest.joinContract(hatName, contractId, client)
        fut.map { result =>
          println(result)
          true must be equalTo (true)
        }.await(1, 10.seconds)
      }
    }

        "make a request, telling adjudicator-service, that the HAT is joining a contract" in {
          WsTestClient.withClient { client =>
            val fut = NetworkRequest.leaveContract(hatName, contractId, client)
            fut.map { result =>
              println(result)
              true must be equalTo (true)
            }.await(1, 10.seconds)
          }
        }

        "make a request to get the public key" in {
          WsTestClient.withClient { client =>
            val fut = NetworkRequest.getPublicKey("keyId", client)
            fut.map { result =>
              println(result)
              true must be equalTo (true)
            }.await(1, 10.seconds)
          }
        }

  }
}

