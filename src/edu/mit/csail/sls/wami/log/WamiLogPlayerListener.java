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

import java.io.InputStream;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.mit.csail.sls.wami.app.IApplicationController;
import edu.mit.csail.sls.wami.app.IWamiApplication;
import edu.mit.csail.sls.wami.recognition.IRecognitionResult;
import edu.mit.csail.sls.wami.recognition.RecognitionResult;
import edu.mit.csail.sls.wami.recognition.RecognitionStartedLogEvent;
import edu.mit.csail.sls.wami.relay.ClientMessageLogEvent;
import edu.mit.csail.sls.wami.relay.SentMessageLogEvent;
import edu.mit.csail.sls.wami.synthesis.SpeakLogEvent;
import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * This is an example implementation of the ILogPlayerListener class. The
 * purpose of this class is to convert logged events back into messages that the
 * client can understand and replay. For example to the browser during the
 * original session should also be resent.
 * 
 * There are two ways that one might wish to re-send the XML messages to the
 * client. The first is to re-send them directly, ignoring everything else (like
 * the recognition results, and onClientMessage). The second version of playback
 * is one that uses the IWamiApplication, and recomputes onRecognitionResult and
 * onClientMessage. In this case we can IGNORE the logged "sent messages",
 * because we expect our IWamiApplication to reproduce them.
 * 
 * In either case, it might be useful to have a way of automatically sending
 * back the XML originally received in onClientMessage. Thus, if a client
 * message is of the form &gt;update send_back_during_replay="true"&lt ...>, the
 * update will get sent directly to the client.
 * 
 * You can use this class by defining an instance in your WamiApplication. Then
 * you can add it as a listener to the log player, which is made available on
 * the controller. You can then start the player from your application.
 * 
 * @author imcgraw
 */
public class WamiLogPlayerListener implements IPlaybackListener {

	private final IApplicationController controller;
	private final IWamiApplication application;

	/**
	 * Creates a log listener that only passes along events sent to the client.
	 * 
	 * @param controller
	 */
	public WamiLogPlayerListener(IApplicationController controller) {
		this.controller = controller;
		this.application = null;
	}

	/**
	 * Creates a log listener that sends events to the application. The only
	 * events that get sent to the controller will be those SentMessage events
	 * with the attribute send_back="true"; All SentMessage events will be give
	 * to onClientMessage in the application.
	 * 
	 * @param application
	 * @param controller
	 */
	public WamiLogPlayerListener(IWamiApplication application,
			IApplicationController controller) {
		this.application = application;
		this.controller = controller;
	}

	public void onNextEvent(ILoggable event, long timestamp) {
		if (event instanceof ClientMessageLogEvent) {
			relayEvent((ClientMessageLogEvent) event);
		} else if (event instanceof SentMessageLogEvent) {
			relayEvent((SentMessageLogEvent) event);
		} else if (event instanceof RecognitionResult) {
			relayEvent((IRecognitionResult) event);
		} else if (event instanceof SpeakLogEvent) {
			relayEvent((SpeakLogEvent) event);
		} else if (event instanceof RecognitionStartedLogEvent) {
			relayEvent((RecognitionStartedLogEvent) event);
		}
	}

	public void onNextEvent(InputStream audioIn, long timestamp) {
		System.out.println("Attempting to play audio!");
		controller.play(audioIn);
	}

	private void relayEvent(IRecognitionResult result) {
		if (application != null) {
			application.onRecognitionResult(result);
		}
	}

	private void relayEvent(SpeakLogEvent event) {
		if (application == null) {
			controller.speak(event.getText());
		}
	}

	private void relayEvent(SentMessageLogEvent event) {
		if (application == null) {
			// We're not using a wami application, so just resend directly
			controller.sendMessage(event.getEventAsXml());
		}
	}

	private boolean hasBooleanAttribute(Element e, String name) {
		return e.getAttribute(name) != null
				&& Boolean.parseBoolean(e.getAttribute(name));
	}

	private Document replaceRootTagName(Element root, String newName) {
		Document doc = XmlUtils.newXMLDocument();

		// Convert this to a "reply" as if it had been a "sent message"

		Element newroot = doc.createElement(newName);
		doc.appendChild(newroot);

		// Copy the attributes to the new element
		NamedNodeMap attrs = root.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			Attr attr2 = (Attr) doc.importNode(attrs.item(i), true);
			newroot.getAttributes().setNamedItem(attr2);
		}

		// Move all the children
		NodeList list = root.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			newroot.appendChild(doc.importNode(node, true));
		}

		return doc;
	}

	private void relayEvent(ClientMessageLogEvent event) {
		Element root = (Element) event.getEventAsXml().getFirstChild();

		if (hasBooleanAttribute(root, "send_back_during_replay")) {
			Document doc = replaceRootTagName(root, "reply");

			controller.sendMessage(doc);
		}

		if (application != null) {
			application.onClientMessage(root);
		}
	}

	private void relayEvent(RecognitionStartedLogEvent event) {
		if (application != null) {
			application.onRecognitionStarted();
		}
	}

	public void onLogFinished() {
		// TODO Auto-generated method stub

	}

	public void onStartLog(String sessionId) {
		// TODO Auto-generated method stub

	}

	public void onEventPlayerException(EventPlayerException e) {
		// TODO Auto-generated method stub

	}

}
