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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.io.input.TeeInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.mit.csail.sls.wami.WamiConfig;
import edu.mit.csail.sls.wami.app.IApplicationController;
import edu.mit.csail.sls.wami.app.IWamiApplication;
import edu.mit.csail.sls.wami.audio.IAudioRetriever;
import edu.mit.csail.sls.wami.log.EventLoggerException;
import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.IEventPlayer;
import edu.mit.csail.sls.wami.log.ILoggable;
import edu.mit.csail.sls.wami.recognition.IRecognitionListener;
import edu.mit.csail.sls.wami.recognition.IRecognitionResult;
import edu.mit.csail.sls.wami.recognition.IRecognizer;
import edu.mit.csail.sls.wami.recognition.RecognitionStartedLogEvent;
import edu.mit.csail.sls.wami.recognition.exceptions.RecognizerException;
import edu.mit.csail.sls.wami.recognition.lm.LanguageModel;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelCompilationException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelServerException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.UnsupportedLanguageModelException;
import edu.mit.csail.sls.wami.synthesis.ISynthesizer;
import edu.mit.csail.sls.wami.synthesis.SpeakLogEvent;
import edu.mit.csail.sls.wami.synthesis.SynthesizerException;
import edu.mit.csail.sls.wami.util.AudioUtils;
import edu.mit.csail.sls.wami.util.ServletUtils;
import edu.mit.csail.sls.wami.util.XmlUtils;

public class WamiRelay implements IApplicationController {
	private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<String>();

	private WamiConfig wc = null;

	private long pollTimeout = -1;

	private long timeLastMessageSent = System.currentTimeMillis();

	private long timeLastPollEnded = System.currentTimeMillis();

	private boolean isCurrentlyPolling = false;

	private ISynthesizer synthesizer;

	private IRecognizer recognizer;

	private IEventPlayer logplayer;

	private IWamiApplication wamiApp;

	protected IEventLogger eventLogger;

	private IAudioRetriever audioRetriever = null;

	private byte[] lastAudioBytes = null;

	AudioFormat lastAudioFormat = null;

	private Object lastAudioLock = new Object();

	private ServletContext sc;

	private HttpSession session;

	private String wsessionid;

	public void initialize(HttpServletRequest request, String wsessionid)
			throws InitializationException {
		this.wsessionid = wsessionid; // Must be first!

		session = request.getSession();
		this.sc = request.getSession().getServletContext();

		wc = WamiConfig.getConfiguration(session.getServletContext());
		pollTimeout = wc.getPollTimeout();

		eventLogger = createEventLogger(request);

		logEvent(new RequestHeadersLogEvent(request), System
				.currentTimeMillis());

		synthesizer = wc.createSynthesizer(this);
		try {
			recognizer = wc.createRecognizer(request.getSession()
					.getServletContext(), this);
		} catch (RecognizerException e) {
			System.err.println("There was an error creating the recognizer");
			InitializationException ie = new InitializationException(e);
			ie.setRelay(this);
			throw ie;
		}

		audioRetriever = wc.createAudioRetriever(sc);

		// If you specified a log player, you might want to start it in the app.
		logplayer = wc.createLogPlayer(request);

		// This will be null if there is no application set in the config file.
		wamiApp = wc.createWamiApplication(this, session);
	}

	private IEventLogger createEventLogger(HttpServletRequest request)
			throws InitializationException {
		IEventLogger logger;

		try {
			logger = wc.createEventLogger(request.getSession()
					.getServletContext());
			if (logger != null) {

				addEventLoggerListeners();

				final String serverAddress = (String) request.getSession()
						.getAttribute("serverAddress");

				String clientAddress = ServletUtils.getClientAddress(request);

				long timestamp = System.currentTimeMillis();
				final String recDomain = (String) session
						.getAttribute("recDomain");

				logger.createSession(serverAddress, clientAddress, wsessionid,
						timestamp, recDomain);
			}
		} catch (EventLoggerException e) {
			System.err.println("There was an error creating the logger");
			throw new InitializationException(e);
		}
		return logger;
	}

	protected void addEventLoggerListeners() {
		// Add logger listener in subclass
	}

	/**
	 * Wait for a message to be sent from server to client via sendMessage().
	 * Will block indefinitely if pollTimeout=0 (set via config.xml), or it will
	 * wait only as long as pollTimeout() and return null if nothing found
	 */
	public String waitForMessage() throws InterruptedException {
		try {
			isCurrentlyPolling = true;
			System.out.println("Waiting for message: " + pollTimeout);
			return (pollTimeout > 0) ? messageQueue.poll(pollTimeout,
					TimeUnit.MILLISECONDS) : messageQueue.take();
		} finally {
			isCurrentlyPolling = false;
			timeLastPollEnded = System.currentTimeMillis();
		}
	}

	public void sendMessage(Document xmlMessage) {
		sendMessage(XmlUtils.toXMLString(xmlMessage));
	}

	public void sendMessage(String message) {
		try {
			System.out.println("Sending message: " + message);
			long timestampMillis = System.currentTimeMillis();
			timeLastMessageSent = timestampMillis;
			logEvent(new SentMessageLogEvent(message), timestampMillis);
			messageQueue.put(message);
		} catch (InterruptedException e) {
			// LinkedBlockingQueues are not bounded, so this shouldn't occur
			e.printStackTrace();
		}
	}

	public long getTimeLastMessageSent() {
		return timeLastMessageSent;
	}

	public long getPolltimeout() {
		return pollTimeout;
	}

	/**
	 * If the client is still polling, return 0. Otherwise, return the number of
	 * milliseconds since the previous poll by the client ended
	 */
	public long getTimeSinceLastPollEnded() {
		return isCurrentlyPolling ? 0
				: (System.currentTimeMillis() - timeLastPollEnded);
	}

	/**
	 * Default timeout functionality: send a message to the client informing it
	 * of the timeout.
	 */
	public void timeout() {
		Document document = XmlUtils.newXMLDocument();
		Element root = document.createElement("reply");
		root.setAttribute("type", "timeout");
		document.appendChild(root);
		sendMessage(XmlUtils.toXMLString(document));
	}

	private class AudioElement {
		public InputStream stream;

		public AudioElement(InputStream stream) {
			this.stream = stream;
		}
	}

	BlockingQueue<AudioElement> audioQueue = new LinkedBlockingQueue<AudioElement>();

	public boolean playedAudio = false;

	/**
	 * Returns an input stream for audio. The stream should have
	 * header-information already encoded in it.
	 */
	public InputStream waitForAudio(int timeInSeconds)
			throws InterruptedException {
		if (playedAudio && wamiApp != null) {
			// Not the first time waiting...
			// this means that we just finished playing.
			onFinishedPlayingAudio();
			playedAudio = false;
		}

		System.out.println("Waiting for audio for " + timeInSeconds
				+ " seconds.");
		AudioElement e = audioQueue.poll(timeInSeconds, TimeUnit.SECONDS);
		InputStream ais = null;
		if (e != null) {
			ais = e.stream;
			playedAudio = true;
		}
		return ais;
	}

	public String getWamiSessionID() {
		return wsessionid;
	}

	protected void onFinishedPlayingAudio() {
		if (wamiApp != null) {
			wamiApp.onFinishedPlayingAudio();
		}
	}

	public void speak(String ttsString) {
		// TODO: error checking for null synthesizer
		try {
			logEvent(new SpeakLogEvent(ttsString), System.currentTimeMillis());
			System.out.println("First Synthesizing.");
			InputStream stream = synthesizer.synthesize(ttsString);
			System.out.println("Attempting to play stream: " + stream);
			play(stream);
		} catch (SynthesizerException e) {
			e.printStackTrace();
		}
	}

	public void play(InputStream audio) {
		if (audio == null) {
			System.out.println("WARNING: Attempted to play NULL audio");
			return;
		}
		try {
			audioQueue.put(new AudioElement(audio));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This delegates recognition to the {@link IRecognizer} associated with
	 * this relay, providing the appropriate callbacks
	 * 
	 * @param audioIn
	 *            The audio input stream to recognizer
	 * @throws RecognizerException
	 *             On recognition error
	 * @throws IOException
	 *             on error reading from the audioIn stream
	 */
	public void recognize(AudioInputStream audioIn) throws RecognizerException,
			IOException {
		final ByteArrayOutputStream audioByteStream = new ByteArrayOutputStream();
		final AudioFormat audioFormat = audioIn.getFormat();
		TeeInputStream tee = new TeeInputStream(audioIn, audioByteStream, true);
		AudioInputStream forkedStream = new AudioInputStream(tee, audioIn
				.getFormat(), AudioSystem.NOT_SPECIFIED);

		if (recognizer == null) {
			throw new RecognizerException("No recognizer specified!");
		} else if (wamiApp == null) {
			throw new RecognizerException("No wami app specified!");
		}

		recognizer.recognize(forkedStream, new IRecognitionListener() {
			private long startedTimestamp;

			public void onRecognitionResult(final IRecognitionResult result) {
				// if the result is final, then before we delegate it
				// we switch over our audio stream so that
				// getLastRecordedAudio() works properly inside of
				// on RecognitionResult
				long timestampMillis = System.currentTimeMillis();
				if (!result.isIncremental()) {
					try {
						audioByteStream.close();
					} catch (IOException e) {
						e.printStackTrace(); // shouldn't occur
					}

					synchronized (lastAudioLock) {
						lastAudioBytes = audioByteStream.toByteArray();
						lastAudioFormat = audioFormat;
					}
				}
				wamiApp.onRecognitionResult(result);
				logEvent(result, timestampMillis);

				if (!result.isIncremental()) {
					logUtterance(audioByteStream.toByteArray(), audioFormat,
							startedTimestamp);
				}
			}

			public void onRecognitionStarted() {
				startedTimestamp = System.currentTimeMillis();
				logEvent(new RecognitionStartedLogEvent(), startedTimestamp);
				wamiApp.onRecognitionStarted();
			}

		});
	}

	public void setLanguageModel(LanguageModel lm)
			throws LanguageModelCompilationException,
			UnsupportedLanguageModelException, LanguageModelServerException {

		if (recognizer == null) {
			throw new LanguageModelServerException(
					"no recognizer to compile language models!");
		}

		try {
			recognizer.setLanguageModel(lm);
		} catch (RecognizerException e) {
			if (e instanceof UnsupportedLanguageModelException) {
				throw (UnsupportedLanguageModelException) e;
			} else if (e instanceof LanguageModelServerException) {
				throw (LanguageModelServerException) e;
			} else if (e instanceof LanguageModelCompilationException) {
				throw (LanguageModelCompilationException) e;
			} else {
				throw new LanguageModelServerException(e);
			}
		}
	}

	public void handleClientUpdate(HttpSession session, String xmlUpdate) {
		Document doc = XmlUtils.toXMLDocument(xmlUpdate);
		Element root = (Element) doc.getFirstChild();
		logClientEvent(xmlUpdate, root);

		String type = root.getAttribute("type");

		if ("hotswap".equals(type)) {
			hotswapComponent(WamiConfig.getConfiguration(session
					.getServletContext()), root);
		} else if (wamiApp != null) {
			wamiApp.onClientMessage(root);
		} else {
			System.err
					.println("warming: handleClientUpdate called with null wami app");
		}
	}

	private void hotswapComponent(WamiConfig config, Element el) {
		String component = el.getAttribute("component");
		String className = el.getAttribute("class");
		if ("recognizer".equals(component)) {
			hotswapRecognizer(config, className, el);
		} else if ("synthesizer".equals(component)) {
			hotswapSynthesizer(config, className, el);
		} else if ("wamiapp".equals(component)) {
			hotswapWamiApplication(config, className, el);
		}
	}

	private void hotswapWamiApplication(WamiConfig config, String className,
			Element el) {
		this.wamiApp = config.createWamiApplication(this, session, className,
				WamiConfig.getParameters(el));
	}

	private void hotswapSynthesizer(WamiConfig config, String className,
			Element el) {
		ISynthesizer synthesizer = config.createSynthesizer(this, className,
				WamiConfig.getParameters(el));
		setSynthesizer(synthesizer);
	}

	private void hotswapRecognizer(WamiConfig config, String className,
			Element el) {
		try {
			IRecognizer recognizer = config.createRecognizer(sc, this,
					className, WamiConfig.getParameters(el));
			setRecognizer(recognizer);
		} catch (RecognizerException e) {
			sendErrorMessage("Hot Swap Error",
					"Encountered error swapping to the specified recognizer.",
					ServletUtils.getStackTrace(e));
		}
	}

	private void sendErrorMessage(String type, String message, String details) {
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("reply");
		root.setAttribute("type", "error");
		root.setAttribute("error_type", type);
		root.setAttribute("message", message);
		root.setAttribute("details", details);
		doc.appendChild(root);
		sendMessage(doc);
	}

	private void logClientEvent(String xmlUpdate, Element root) {
		String type = root.getAttribute("type");
		if ("logevents".equals(type)) {
			NodeList eventNodes = root.getElementsByTagName("event");
			System.out.println("unpacking events");
			for (int i = 0; i < eventNodes.getLength(); i++) {
				Element event = (Element) eventNodes.item(i);
				long timeInMillis = System.currentTimeMillis();
				logEvent(new ClientMessageLogEvent(event), timeInMillis);
			}
		} else {
			System.out.println("logging client event");
			long timestampMillis = System.currentTimeMillis();
			logEvent(new ClientMessageLogEvent(xmlUpdate), timestampMillis);
		}
	}

	public synchronized void close() {
		// Run this on a separate thread to avoid delays/errors
		
		(new Thread(new Runnable() {
			@Override
			public void run() {
				WamiRelay.this.stopPolling();
				// The poison pill
				WamiRelay.this.audioQueue.add(new AudioElement(null));

				sc.log("ClosingRelay: " + wsessionid);
				if (wamiApp != null) {
					wamiApp.onClosed();
					wamiApp = null;
				}

				if (recognizer != null) {
					try {
						sc.log("DestroyingRecognizer");
						recognizer.destroy();
						recognizer = null;
					} catch (RecognizerException e) {
						e.printStackTrace();
					}
				}

				if (synthesizer != null) {
					try {
						sc.log("DestroyingSynthesizer");
						synthesizer.destroy();
						synthesizer = null;
					} catch (SynthesizerException e) {
						e.printStackTrace();
					}
				}

				if (eventLogger != null) {
					try {
						sc.log("DestroyingEventLogger");
						eventLogger.close();
						eventLogger = null;
					} catch (EventLoggerException e) {
						e.printStackTrace();
					}
				}

				// Sometimes the session is already invalid
				// Not sure how to check, so run in new thread.
				WamiRelay.this.session.setAttribute("relay", null);
				sc.log("DoneClosingRelay: " + wsessionid);
			}
		})).start();

	}

	public void stopPolling() {
		this.sendMessage("<reply type='stop_polling' />");
	}

	public void logEvent(final ILoggable logEvent, final long timestampMillis) {
		if (eventLogger != null) {
			try {
				eventLogger.logEvent(logEvent, timestampMillis);
			} catch (EventLoggerException e) {
				e.printStackTrace();
			}
		}
	}

	public IWamiApplication getWamiApplication() {
		return wamiApp;
	}

	public IRecognizer getRecognizer() {
		return recognizer;
	}

	public void setRecognizer(IRecognizer rec) {
		recognizer = rec;
	}

	private void setSynthesizer(ISynthesizer synth) {
		this.synthesizer = synth;
	}

	/**
	 * returns the synthesizer in use (may be null)
	 */
	public ISynthesizer getSynthesizer() {
		return synthesizer;
	}

	public InputStream getLastRecordedAudio() {
		synchronized (lastAudioLock) {
			AudioInputStream audioIn = AudioUtils.createAudioInputStream(
					lastAudioBytes, lastAudioFormat);
			try {
				return AudioUtils.createInputStreamWithWaveHeader(audioIn);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	private void logUtterance(final byte[] bytes,
			final AudioFormat audioFormat, final long audioTimestampMillis) {
		if (eventLogger != null) {
			try {
				eventLogger.logUtterance(AudioUtils.createAudioInputStream(
						bytes, audioFormat), audioTimestampMillis);
			} catch (EventLoggerException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public IEventPlayer getLogPlayer() {
		return logplayer;
	}

	public WamiConfig getWamiConfig() {
		return wc;
	}

	public void sendReadyMessage() {
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("reply");
		root.setAttribute("type", "wami_ready");
		doc.appendChild(root);
		sendMessage(doc);
	}

	@Override
	public InputStream getRecording(String fileName) {
		if (audioRetriever == null) {
			throw new RuntimeException(
					"If you are going to try to retrieve recorded utts, you must specify a valid IAudioRetriever in the config.xml file.");
		}
		return audioRetriever.retrieveAudio(fileName);
	}

	@Override
	public void setEventLogger(IEventLogger logger) {
		this.eventLogger = logger;
	}

	public void forceRepoll() {
		try {
			long timestampMillis = System.currentTimeMillis();
			String message = "<reply />";
			logEvent(new SentMessageLogEvent(message), timestampMillis);
			messageQueue.put(message);
		} catch (InterruptedException e) {
			// LinkedBlockingQueues are not bounded, so this shouldn't occur
			e.printStackTrace();
		}
	}

}
