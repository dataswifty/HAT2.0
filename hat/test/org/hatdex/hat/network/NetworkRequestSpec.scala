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

import org.hatdex.hat.api.HATTestContext
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.{ BeforeAll, BeforeEach }
import play.api.Logger
import play.api.libs.json.Json
import play.api.test.{ FakeRequest, Helpers, PlaySpecification }

import scala.concurrent.Await
import scala.concurrent.duration._
import org.hatdex.hat.utils._
import org.specs2.Specification

class NetworkRequestSpec extends Specification {
  val logger = Logger(this.getClass)

  def is = s2"""
  This is a specification for the 'Hello world' string

  The 'Hello world' string should
    contain 11 characters $e4
  """

  def e4 = {
    val nr = new NetworkRequest()
    val fut = nr.run()
    val response = Await.ready(fut, 10.seconds)
    println(response)
    "Hello world" must endWith("world")
  }
}

//  extends Specification {
//
//  val logger = Logger(this.getClass)
//
//  "The `profile` method" should {
//    "Return bundle data with profile information" in {
//      val nr = new NetworkRequest()
//
//      nr.run()
//
//      (5) must be equalTo 5
//    }
//  }
//}

