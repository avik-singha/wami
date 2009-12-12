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

import java.io.InputStream;

import org.w3c.dom.Document;

import edu.mit.csail.sls.wami.WamiConfig;
import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.IEventPlayer;
import edu.mit.csail.sls.wami.log.ILoggable;
import edu.mit.csail.sls.wami.recognition.lm.LanguageModel;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelCompilationException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelServerException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.UnsupportedLanguageModelException;

/**
 * An instance of this interface will be passed to implementors of
 * {@link IApplication}
 * 
 * @author alexgru
 * 
 */
public interface IApplicationController {

	/**
	 * Set the language model in the current session
	 * 
	 * @param lm
	 * @throws LanguageModelCompilationException
	 *             If there was a problem with the language model which meant it
	 *             couldn't be compiled or loaded
	 * @throws LanguageModelServerException
	 *             When an unexpected error occurred with the language
	 *             model/recognition server
	 * @throws UnsupportedLanguageModelException
	 *             When an unsupported language model is provided
	 */
	public void setLanguageModel(LanguageModel lm)
			throws LanguageModelCompilationException,
			UnsupportedLanguageModelException, LanguageModelServerException;

	/**
	 * Use TTS to speak a particular string (requires that you have specified a
	 * synthesizer in the config file).
	 * 
	 * @param ttsString
	 *            The string to say
	 */
	public void speak(String ttsString);

	/**
	 * Send a message to the handler in the javascript client running in the
	 * browser
	 * 
	 * @param message
	 *            The message to send
	 */
	public void sendMessage(Document xmlMessage);

	/**
	 * Play an arbitrary audio stream through to the client. Audio header
	 * information should appear at the beginning of the stream
	 * 
	 * @param audioIn
	 *            A stream of audio to play
	 */
	public void play(InputStream audioIn);

	/**
	 * Returns the audio most recently recorded from the client. Returns null if
	 * no sound has been recorded yet. Appropriate header information will be
	 * available in the stream.
	 * 
	 * @return
	 */
	public InputStream getLastRecordedAudio();

	/**
	 * Log an application-specific event. The event will only be logged if an
	 * event logger has been specified in the configuration file. The event's
	 * meta info will be filled in with the name of the class of the
	 * {@link ILoggable} item
	 * 
	 * @param loggable
	 *            The event to be logged
	 * @param timestampMillis
	 *            The timestamp to associate with the event; typically
	 *            System.currentTimeMillis()
	 */
	public void logEvent(ILoggable loggable, long timestampMillis);

	/**
	 * Set the event logger for this application.
	 * 
	 * @param logger
	 */
	public void setEventLogger(IEventLogger logger);

	/**
	 * Returns the log player associated with the application.
	 * 
	 * @return null if no log player is specified.
	 */
	public IEventPlayer getLogPlayer();

	/**
	 * Returns the WAMI Configuration associated with the application.
	 */
	public WamiConfig getWamiConfig();

	/**
	 * Returns an audio input stream found given a "fileName". Really, it's just
	 * a string representation of the WAV's location, which could be a database,
	 * a file system, a URL, depending on how the app controller decides to
	 * implement IAudioRetriever.
	 * 
	 * @param wsessionid
	 * @param utt_id
	 * @return
	 */
	public InputStream getRecording(String fileName);

}
