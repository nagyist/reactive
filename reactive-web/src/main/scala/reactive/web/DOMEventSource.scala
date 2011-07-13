package reactive
package web

import net.liftweb.http.{ S, SHtml }
import net.liftweb.util.Helpers.urlDecode
import javascript._
import JsTypes._

import scala.xml.{ Elem, NodeSeq }

/**
 * Represents a DOM event type and related JsEventStreams.
 * Generates the javascript necessary to fire JsEventStreams
 * in response to events.
 */
//TODO better name--it is not an EventSource; only wraps a JsEventStream

class DOMEventSource[T <: DOMEvent:Manifest:EventEncoder]
extends (NodeSeq => NodeSeq) with Forwardable[T] with Logger with JsForwardable[JsObj] {
  /**
   * The JsEventStream that fires the primary event data
   */
  val jsEventStream = new JsEventStream[JsObj]
  /**
   * An EventStream that fires events of type T on the server.
   * Calls toServer on jsEventStream.
   */
  lazy val eventStream: EventStream[T] =
    jsEventStream.toServer

  /**
   * The name of the event
   */
  def eventName = scalaClassName(manifest[T].erasure).toLowerCase
  
  /**
   * The name of the attribute to add the handler to
   */
  def attributeName = "on"+eventName

  /**
   * Pairs a javascript expression to fire when this event occurs, with
   * a javascript event stream to fire it from.
   */
  case class EventData[T<:JsAny](encode: $[T], es: JsEventStream[T])
  private var eventData: List[EventData[_]] = List(
    EventData(implicitly[EventEncoder[T]].encodeExp, jsEventStream)
  )
  
  /**
   * Register data to be fired whenever this event occurs
   * @param jsExp the javascript to be evaluated when it occurs
   * @param es the JsEventStream that the value will be fired from
   */
  def addEventData[T<:JsAny](jsExp: $[T], es: JsEventStream[T]) = synchronized {
    eventData ::= EventData(jsExp, es)
  }
  

  /**
   * The javascript to run whenever the browser fires the event.
   * Whether this will result in an ajax call to the server
   * depends on the JsEventStreams registered with this DOMEventSource
   * (for instance, whether toServer has been called on them,
   * such as by calling DOMEventSource#eventStream),
   * propagate the event to the server
   */
  def propagateJS: String = {
    eventData.map{ case EventData(enc, es) =>
      es.fireExp(enc).render
    }.mkString(";") + ";reactive.doAjax()"
  }

  /**
   * Returns an attribute that will register a handler with the event.
   * Combines attributeName and propagateJS in a scala.xml.MetaData.
   */
  def asAttribute: xml.MetaData = new xml.UnprefixedAttribute(
    attributeName,
    propagateJS,
    xml.Null
  )

  /**
   * Adds asAttribute to an Elem.
   * If an attribute exists with the same name, combine the two values,
   * separated by a semicolon.
   */
  def apply(elem: Elem): Elem = {
    val a = asAttribute
    elem.attribute(a.key) match {
      case None => elem % asAttribute
      case Some(ns) => elem % new xml.UnprefixedAttribute(a.key, ns.text+";"+a.value.text, xml.Null)
    }
  }
  /**
   * Like apply(Elem). Needed to extend NodeSeq=>NodeSeq, for use with binding/css selectors.
   * Forces `in` to an Elem by calling nodeSeqToElem (in the package object)
   */
  def apply(in: NodeSeq): NodeSeq = apply(nodeSeqToElem(in))

  /**
   * Calls eventStream.foreach
   */
  def foreach(f: T => Unit)(implicit o: Observing) = eventStream.foreach(f)(o)
  /**
   * Calls jsEventStream.foreach
   */
  def foreach[E[J <: JsAny] <: $[J], F: ToJs.To[JsObj=|>JsVoid, E]#From](f: F) = jsEventStream.foreach(f)
  /**
   * Calls jsEventStream.foreach
   */
  def foreach(f: $[JsObj =|> JsVoid]) = jsEventStream.foreach(f)

  override def toString = "DOMEventSource["+manifest[T]+"]"
}

object DOMEventSource {
  /**
   * Creates a new Click DOMEventSource
   */
  def click = new DOMEventSource[Click]
  /**
   * Creates a new DblClick DOMEventSource
   */
  def dblClick = new DOMEventSource[DblClick]
  /**
   * Creates a new KeyUp DOMEventSource
   */
  def keyUp = new DOMEventSource[KeyUp]
  /**
   * Creates a new Change DOMEventSource
   */
  def change = new DOMEventSource[Change.type]

}
