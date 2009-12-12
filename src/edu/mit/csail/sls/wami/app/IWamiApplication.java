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
package edu.mit.csail.sls.wami.app;

import java.util.Map;

import javax.servlet.http.HttpSession;

import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.recognition.IRecognitionListener;

/**
 * Application developers should create an instance of this class specific to
 * their application. The name of that class should be set in the config.xml
 * file, as in e.g.
 * 
 * <pre>
 * &lt;portal appClass=&quot;com.mycompany.MyWamiApplication&quot; .../&gt;
 * </pre>
 * 
 * @author alexgru
 * 
 */
public interface IWamiApplication extends IRecognitionListener {
	/**
	 * A new instance is created for each user interacting with the system. If a
	 * user presses 'reload', then a new instance of the application may be
	 * called with the same session
	 * 
	 * @param appController
	 *            The application "controller" which may be used to send
	 *            messages to the client, and speak TTS
	 * @param session
	 *            The servlet session which will be associated with this
	 * @param paramMap
	 *            The parameters provided in the application tag of the
	 *            config.xml
	 */
	public void initialize(IApplicationController appController,
			HttpSession session, Map<String, String> paramMap);

	/**
	 * Called when the client (browser) has sent a message to the server
	 * 
	 * @param xmlRoot
	 *            the root of the xml message received from the client The
	 *            message received from the client
	 */
	public void onClientMessage(Element xmlRoot);

	/**
	 * Called when this application should "close", either due to a timeout or
	 * the user navigating away from the page, or hitting reload to start a new
	 * instance. It is not guaranteed that this method will always be called,
	 * for instance if the servlet engine is shutdown before a session times out
	 */
	public void onClosed();

	/**
	 * This is called every time the java applet has finished playing a piece of
	 * audio.
	 */
	public abstract void onFinishedPlayingAudio();
}
