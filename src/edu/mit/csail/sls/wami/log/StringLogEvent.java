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
package edu.mit.csail.sls.wami.log;

import org.w3c.dom.Document;

import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * An easy way to log strings
 * 
 */
public class StringLogEvent implements ILoggable {
	private String event;
	private String eventType;

	public StringLogEvent(String event, String eventType) {
		fromLogEvent(event, eventType);
	}
	
	/**
	 * Empty constructor for log playback
	 */
	public StringLogEvent() {	
	}
	
	public void fromLogEvent(String event, String eventType) {
		this.event = event;
		this.eventType = eventType;
	}
		
	public String getEventType() {
		return eventType;
	}

	public String toLogEvent() {
		return event;
	}
	
	public String getEvent() {
		return toLogEvent();
	}
	
	/**
	 * Parse the event string as xml and return the
	 * document
	 */
	public Document getEventAsXml() {
		return XmlUtils.toXMLDocument(event);
	}
	
	@Override
	public String toString() {
		return toLogEvent();
	}
}
