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

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.sound.sampled.AudioInputStream;


/**
 * Interface which allows for WAMI logging on a particular session
 * 
 * @author alexgru
 */
public interface IEventLogger {
	// these are type core event types used internally by the wami system
	static final String RecognitionStarted = "RecognitionStarted";
	static final String IncrementalRecResult = "IncrementalRecResult";
	static final String FinalRecResult = "FinalRecResult";
	static final String ClientMessage = "ClientMessage";
	static final String Speak = "Speak";
	static final String SentMessage  = "SentMessage";
	static final String Instantiation = "Instantiation";
	static final String ClientLog = "ClientLog"; 
	static final String RequestHeaders = "RequestHeaders";

	/**
	 * Set any initialization parameters from the xml
	 */
	void setParameters(ServletContext sc, Map<String, String> map)
			throws EventLoggerException;

	/**
	 * This will be called before any events are logged, it sets a unique
	 * session id for this logger. It will be called exactly once.
	 * 
	 * @param serverAddress
	 *            The address of the server
	 * @param clientAddress
	 *            The address of the client
	 * @param wsessionid 
	 * @throws EventLoggerException
	 *             If a logging exception occurs
	 */
	void createSession(String serverAddress, String clientAddress,
			String wsessionid, long timestampMillis, String recDomain) throws EventLoggerException;

	/**
	 * Log a single event, with the given timestamp
	 * 
	 * @param logEvent
	 *            The event to log
	 * @param timestampMillis
	 *            The timestamp to associate with the event * @throws
	 *            EventLoggerException If a logging exception occurs
	 */
	void logEvent(ILoggable logEvent, long timestampMillis)
			throws EventLoggerException;

	/**
	 * Log a recorded utterance
	 * 
	 * @param audioIn
	 *            An input stream with the utterance to log
	 * @param timestampMillis
	 *            The timestamp for the utterance
	 * @throws EventLoggerException
	 *             If a logging exception occurs
	 * @throws IOException
	 *             If there is an error reading the audioIn stream
	 */
	void logUtterance(AudioInputStream audioIn, long timestampMillis)
			throws EventLoggerException, IOException;

	/**
	 * Close the logger. The logger should finish logging any events sent to it,
	 * and then close down.
	 */
	void close() throws EventLoggerException;
	
	public void addSessionCreatedListener(IEventLoggerListener l);
		
}
