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
package edu.mit.csail.sls.wami.recognition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * A recognition result contains a list of results. In the future, more
 * attributes may be added here (e.g. confidence scores)
 * 
 * @author alexgru
 * 
 */
public class RecognitionResult implements IRecognitionResult {
	private List<String> hyps;
	private boolean isIncremental;
	private String errorMessage;

	/**
	 * Empty constructor which performs no initialization. Typically used for
	 * log playback
	 */
	public RecognitionResult() {
	}

	public RecognitionResult(String errorMessage) {
		this.hyps = new ArrayList<String>();
		this.errorMessage = errorMessage;
	}

	public RecognitionResult(boolean isIncremental, String... hyps) {
		this.hyps = Arrays.asList(hyps);
		this.isIncremental = isIncremental;
	}

	public RecognitionResult(boolean isIncremental, List<String> hyps) {
		this.hyps = new ArrayList<String>(hyps);
		this.isIncremental = isIncremental;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.mit.csail.sls.wami.recognition.IRecognitionResult#getHyps()
	 */
	public List<String> getHyps() {
		return new ArrayList<String>(hyps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.mit.csail.sls.wami.recognition.IRecognitionResult#isIncremental()
	 */
	public boolean isIncremental() {
		return isIncremental;
	}

	public String toLogEvent() {
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("recognition_result");
		doc.appendChild(root);
		root.setAttribute("is_incremental", new Boolean(isIncremental)
				.toString());

		if (errorMessage != null) {
			root.setAttribute("error_message", errorMessage);
		}

		for (String hyp : hyps) {
			Element hypE = doc.createElement("hyp");
			hypE.setAttribute("text", hyp);
			root.appendChild(hypE);
		}

		return XmlUtils.toXMLString(doc);
	}

	public void fromLogEvent(String logStr, String eventType) {
		Document doc = XmlUtils.toXMLDocument(logStr);
		Element root = (Element) doc.getElementsByTagName("recognition_result")
				.item(0);
		isIncremental = Boolean.parseBoolean(root
				.getAttribute("is_incremental"));
		hyps = new ArrayList<String>();

		NodeList hypNodes = root.getElementsByTagName("hyp");
		for (int i = 0; i < hypNodes.getLength(); i++) {
			Element hypE = (Element) hypNodes.item(i);
			hyps.add(hypE.getAttribute("text"));
		}

		errorMessage = root.getAttribute("error_message");
		if ("".equals(errorMessage)) {
			errorMessage = null;
		}
	}

	public String getEventType() {
		return isIncremental ? IEventLogger.IncrementalRecResult
				: IEventLogger.FinalRecResult;
	}

	public String getError() {
		return errorMessage;
	}
}
