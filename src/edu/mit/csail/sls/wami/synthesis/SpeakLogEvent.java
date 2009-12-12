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
package edu.mit.csail.sls.wami.synthesis;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.ILoggable;
import edu.mit.csail.sls.wami.util.XmlUtils;

public class SpeakLogEvent implements ILoggable {
	private String ttsStr;

	public SpeakLogEvent(String ttsStr) {
		this.ttsStr = ttsStr;
	}

	public SpeakLogEvent() {

	}

	public void fromLogEvent(String logStr, String eventType) {
		Document doc = XmlUtils.toXMLDocument(logStr);
		ttsStr = ((Element) doc.getFirstChild()).getAttribute("text");
	}

	public String toLogEvent() {
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("speak");
		root.setAttribute("text", ttsStr);
		doc.appendChild(root);
		return XmlUtils.toXMLString(doc);
	}

	public String getEventType() {
		return IEventLogger.Speak;
	}
	
	public String getText() {
		return ttsStr;
	}
}
