/*
 * Copyright (C) 2009-2010 Boris Okunskiy (http://incarnate.ru)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package ru.circumflex.core

import Circumflex._

case class RouteMatchedException(val response: Option[HttpResponse]) extends Exception

class RequestRouter {

  implicit def textToResponse(text: String): HttpResponse = TextResponse(text)
  implicit def requestRouterToResponse(router: RequestRouter): HttpResponse = error(404)

  def ctx = Circumflex.ctx

  val get = new Route("get")
  val getOrPost = new Route("get", "post")
  val getOrHead = new Route("get", "head")
  val post = new Route("post")
  val put = new Route("put")
  val delete = new Route("delete")
  val head = new Route("head")
  val options = new Route("options")
  val any = new Route("get", "post", "put", "delete", "head", "options")

  val header = new HeadersHelper

  def isXhr = header("X-Requested-With") match {
    case Some("XMLHttpRequest") => true
    case _ => false
  }

  def rewrite(target: String): Nothing = {
    ctx.request.getRequestDispatcher(target).forward(ctx.request, ctx.response)
    throw RouteMatchedException(None)
  }

  def headers(crit: (String, String)*) = new HeadersRegexMatcher(crit : _*)

  def param(key: String): Option[String] = ctx.stringParam(key)

  def error(errorCode: Int, message: String) = ErrorResponse(errorCode, message)
  def error(errorCode: Int) = ErrorResponse(errorCode, "")

  def redirect(location: String) = RedirectResponse(location)

  def done: HttpResponse = done(200)

  def done(statusCode: Int): HttpResponse = {
    ctx.statusCode = statusCode
    EmptyResponse()
  }

  def requireParams(names: String*) = names.toList.foreach(name => {
    if (param(name) == None)
      throw new RouteMatchedException(Some(error(400, "Missing " + name + " parameter.")))
  })

}

class Route(val matchingMethods: String*) {

  protected def dispatch(response: =>HttpResponse, matchers: RequestMatcher*): Unit =
    matchingMethods.find(ctx.method.equalsIgnoreCase(_)) match {
      case Some(_) => {
        var params = Map[String, String]()
        matchers.toList.foreach(rm => rm(ctx.request) match {
          case None => return
          case Some(p) => params ++= p
        })
        // All matchers succeeded
        ctx ++= params
        throw RouteMatchedException(Some(response))
      } case _ =>
    }

  def update(uriRegex: String, response: =>HttpResponse): Unit =
    dispatch(response, new UriRegexMatcher(uriRegex))

  def update(uriRegex: String, matcher1: RequestMatcher, response: =>HttpResponse): Unit =
    dispatch(response, new UriRegexMatcher(uriRegex), matcher1)
}

class HeadersHelper {

  def apply(name: String): Option[String] = {
    val value = ctx.request.getHeader(name)
    if (value == null) None
    else Some(value)
  }

  def update(name: String, value: String) = ctx.stringHeaders += name -> value

  def update(name: String, value: Long) = ctx.dateHeaders += name -> value

  def update(name: String, value: java.util.Date) = ctx.dateHeaders += name -> value.getTime

}
