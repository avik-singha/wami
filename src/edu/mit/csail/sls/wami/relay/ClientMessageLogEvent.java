/* -*- Java -*-
 *
 * Copyright (c) 2009
 * Spoken Language Systems Group
 * MIT Computer Science and Artificial Intelligence Laboratory
 * Massachusetts Institute of Technology
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package edu.mit.csail.sls.wami.relay;

import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.StringLogEvent;
import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * Represents a message sent from client to server
 * 
 * @author alexgru
 * 
 */
public class ClientMessageLogEvent extends StringLogEvent {
	public ClientMessageLogEvent(Element event) {
		super(XmlUtils.toXMLString(event), IEventLogger.ClientLog);
	}

	public ClientMessageLogEvent(String event) {
		super(event, IEventLogger.ClientMessage);
	}

	public ClientMessageLogEvent() {
		super();
	}

	// A client explicitly log events and set an event type with:
	// <update type="logevents"> <event type="myType" /> </update>
	private static String extractType(Element event) {
		if (!"event".equals(event.getTagName())) {
			return IEventLogger.ClientMessage;
		}

		String type = event.getAttribute("type");
		if ("".equals(type) || type == null) {
			return IEventLogger.ClientMessage;
		} else {
			return IEventLogger.ClientMessage + "(" + type + ")";
		}
	}
}
