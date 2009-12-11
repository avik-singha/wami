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

/**
 * This listener is used by {@link IEventPlayer} which plays back events from a
 * log in the order they were recorded. Each time a listener method is called,
 * the player will block until it has finished
 * 
 * @author alexgru
 */
public interface IPlaybackListener {

	/**
	 * Called to simulate the next event recorded in the log
	 */
	public void onNextEvent(ILoggable event, long timestamp);

	/**
	 * Immediately after RecognitionStarted event has occurred, this method will
	 * be called with the audio stream which was recorded as part of that event
	 * 
	 * @param audioIn
	 *            An input stream, containing audio with header information
	 * @param timestamp
	 *            The timestamp associated with this audio stream. In this case,
	 *            the timestamp will actually be "in the future" - it will
	 *            correspond to the FinalRecResult event which will be received
	 *            eventually via
	 *            {@link #nextEvent(String, String, String, long)
	 */
	public void onNextEvent(InputStream audioOut, long timestamp);

	/**
	 * This method is called before any of the events of a log file have started
	 * firing.
	 * @param sessionid 
	 */
	public void onStartLog(String sessionid);

	/**
	 * This is called once all the events of a log file have finished firing.
	 */
	public void onLogFinished();

	/**
	 * Allows error handling in the playback listener.
	 * @param e
	 */
	public void onEventPlayerException(EventPlayerException e);

}
