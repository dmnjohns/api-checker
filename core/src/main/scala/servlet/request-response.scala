/***
 *   Copyright 2014 Rackspace US, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.rackspace.com.papi.components.checker.servlet

import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URI
import java.io.BufferedReader
import java.io.InputStreamReader

import java.net.URISyntaxException

import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponseWrapper

import javax.xml.transform.Transformer
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.w3c.dom.Document

import java.util.Enumeration

import com.netaporter.uri.encoding.PercentEncoder

import com.typesafe.scalalogging.slf4j.LazyLogging

import com.rackspace.com.papi.components.checker.util.IdentityTransformPool._
import com.rackspace.com.papi.components.checker.util.ObjectMapperPool

import scala.collection.JavaConversions._

//
//  Request Keys
//
object RequestAttributes {
  val PARSED_XML    = "com.rackspace.com.papi.components.checker.servlet.ParsedXML"
  val PARSED_JSON   = "com.rackspace.com.papi.components.checker.servlet.ParsedJSONTokens"
  val CONTENT_ERROR = "com.rackspace.com.papi.components.checker.servlet.ContentError"
  val CONTENT_ERROR_CODE = "com.rackspace.com.papi.components.checker.servlet.ContentErrorCode"
  val CONTENT_ERROR_PRIORITY = "com.rackspace.com.papi.components.checker.servlet.ContentErrorPriority"
}

import RequestAttributes._

object CherkerServletRequest {
  val DEFAULT_CONTENT_ERROR_CODE : Integer = 400
  val DEFAULT_URI_CHARSET : String = "ASCII"

  val uriEncoder = new PercentEncoder()
}

import CherkerServletRequest._

//
//  An HTTP Request with some additional helper functions
//
class CheckerServletRequest(val request : HttpServletRequest) extends HttpServletRequestWrapper(request) with LazyLogging {

  //
  //  We maintain our own version of the request URI. If a request URI
  //  is submitted by the client without being properly encoded then
  //  this can cause greif when trying to parse it.  We attempt to
  //  parse the request URI if we recieve a syntax error, then we
  //  encode the URI and try again. If that fails again, well then it
  //  fails, we throw a syntax exception which should be handled by
  //  the validator.
  //
  //  Note that we don't always encode the URI because we don't want
  //  to risk double encoding it.
  //
  private val requestURI = {
    val uri = request.getRequestURI()
    try {
      new URI(uri)
    } catch {
      case u : URISyntaxException =>  logger.warn(s"Syntax error while attempting to parse the URI {$uri} will try encoding it.")
                                      new URI(uriEncoder.encode(uri, DEFAULT_URI_CHARSET))
    }
  }

  val URISegment : Array[String] = requestURI.getPath.split("/").filterNot(e => e == "")

  def pathToSegment(uriLevel : Int) : String = {
    "/" + URISegment.slice(0, uriLevel).reduceLeft( _ + "/" +_ )
  }

  def parsedXML : Document = request.getAttribute(PARSED_XML).asInstanceOf[Document]
  def parsedXML_= (doc : Document):Unit = request.setAttribute (PARSED_XML, doc)

  def parsedJSON : JsonNode = request.getAttribute(PARSED_JSON).asInstanceOf[JsonNode]
  def parsedJSON_= (tb : JsonNode):Unit = request.setAttribute (PARSED_JSON, tb)

  def contentError : Exception = request.getAttribute(CONTENT_ERROR).asInstanceOf[Exception]
  def contentError_= (e : Exception):Unit = {
    request.setAttribute(CONTENT_ERROR, e)
    request.setAttribute(CONTENT_ERROR_CODE, DEFAULT_CONTENT_ERROR_CODE)
  }
  def contentError(e : Exception, c : Int, p : Long = -1) : Unit = {
    request.setAttribute(CONTENT_ERROR, e)
    request.setAttribute(CONTENT_ERROR_CODE, c)
    request.setAttribute(CONTENT_ERROR_PRIORITY, p)
  }

  def contentErrorCode : Int = request.getAttribute(CONTENT_ERROR_CODE).asInstanceOf[Int]

  def contentErrorPriority : Long = request.getAttribute(CONTENT_ERROR_PRIORITY) match {
    case l : Object => l.asInstanceOf[Long]
    case null => -1
  }
  def contentErrorPriority_= (p : Long) : Unit = request.setAttribute (CONTENT_ERROR_PRIORITY, p)

  override def getRequestURI : String = requestURI.getRawPath

  override def getInputStream : ServletInputStream = {
    if (parsedXML != null) {
      var transformer : Transformer = null
      val bout = new ByteArrayOutputStream()
      try {
        parsedXML.normalizeDocument
        transformer = borrowTransformer
        transformer.transform (new DOMSource(parsedXML), new StreamResult(bout))
        new ByteArrayServletInputStream(bout.toByteArray())
      } catch {
        case e : Exception => throw new IOException("Error while serializing!", e)
      } finally {
        returnTransformer(transformer)
      }
    } else if (parsedJSON != null) {
      var om : ObjectMapper = null
      try {
        om = ObjectMapperPool.borrowParser
        new ByteArrayServletInputStream(om.writeValueAsBytes(parsedJSON))
      } finally {
        if (om != null) {
          ObjectMapperPool.returnParser(om)
        }
      }
    } else {
      super.getInputStream()
    }
  }

  override def getReader : BufferedReader = {
    if (parsedXML != null) {
      new BufferedReader(new InputStreamReader (getInputStream(), parsedXML.getInputEncoding()))
    } else if (parsedJSON != null) {
      new BufferedReader(new InputStreamReader (getInputStream(), "UTF-8"))
    }else {
      super.getReader
    }
  }
}

//
//  An HTTP Response with some additional helper functions
//
class CheckerServletResponse(val request : HttpServletResponse) extends HttpServletResponseWrapper(request) {}
