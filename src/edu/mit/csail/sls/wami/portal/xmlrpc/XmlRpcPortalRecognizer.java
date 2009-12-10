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
package edu.mit.csail.sls.wami.portal.xmlrpc;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.ServletContext;
import javax.sound.sampled.AudioInputStream;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfig;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory;

import edu.mit.csail.sls.wami.recognition.IRecognitionListener;
import edu.mit.csail.sls.wami.recognition.IRecognitionResult;
import edu.mit.csail.sls.wami.recognition.IRecognizer;
import edu.mit.csail.sls.wami.recognition.RecognitionResult;
import edu.mit.csail.sls.wami.recognition.exceptions.LanguageModelNotSetException;
import edu.mit.csail.sls.wami.recognition.exceptions.RecognizerException;
import edu.mit.csail.sls.wami.recognition.exceptions.RecognizerUnreachableException;
import edu.mit.csail.sls.wami.recognition.lm.LanguageModel;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelCompilationException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.UnsupportedLanguageModelException;
import edu.mit.csail.sls.wami.recognition.lm.jsgf.JsgfGrammar;

public class XmlRpcPortalRecognizer implements IRecognizer {
    private XmlRpcClient client;

    private String sessionId = null;

    private boolean hasSetLanguageModel = false;

    private boolean incrementalResults = true;

    private boolean recordOnly = false;

    private ExecutorService recognizeCallbackExecutor = Executors
	    .newSingleThreadExecutor();

    private ServletContext sc;

    private URL serverAddress;

    private static XmlRpcClientConfig createClientConfig(URL serverAddress,
	    int connectionTimeout, int replyTimeout) {
	XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
	config.setServerURL(serverAddress);
	// the server supports extensions, so allow it for efficiency
	config.setEnabledForExtensions(true);

	config.setConnectionTimeout(connectionTimeout);
	config.setReplyTimeout(replyTimeout);
	return config;
    }

    public void setDynamicParameter(String name, String value)
	    throws RecognizerException {
	if ("incrementalResults".equals(name)) {
	    incrementalResults = Boolean.parseBoolean(value);
	} else if ("recordOnly".equals(name)) {
	    recordOnly = Boolean.parseBoolean(value);
	} else {
	    throw new RecognizerException("Unknown parameter: '" + name + "'");
	}
    }

    public void setParameters(ServletContext sc, Map<String, String> map)
	    throws RecognizerException {
	this.sc = sc;
	String recDomain = (String) sc.getAttribute("recDomain");
	String developerEmail = map.get("developerEmail");
	String developerKey = map.get("developerKey");
	String recordFormat = map.get("recordFormat");
	int recordSampleRate = Integer.parseInt(map.get("recordSampleRate"));
	boolean recordIsLittleEndian = Boolean.parseBoolean(map
		.get("recordIsLittleEndian"));
	String incrementalResultsStr = map.get("incrementalResults");
	incrementalResults = (incrementalResultsStr == null || Boolean
		.parseBoolean(incrementalResultsStr));

	serverAddress = null;
	try {
	    serverAddress = new URL(map.get("url"));
	} catch (MalformedURLException e) {
	    throw new RecognizerException("Invalid recognizer url", e);
	}

	XmlRpcClientConfig config = createClientConfig(serverAddress,
		10 * 1000, 10 * 1000);
	client = new XmlRpcClient();
	client.setTransportFactory(new XmlRpcCommonsTransportFactory(client));
	client.setConfig(config);

	Object[] createParams = { developerEmail, developerKey, recordFormat,
		recordSampleRate, recordIsLittleEndian, recDomain };

	try {
	    sessionId = (String) client.execute(
		    "Portal.createRecognizerSession", createParams);
	} catch (XmlRpcException e) {
	    if (e.code == ErrorCodes.SERVER_ERROR_CODE) {
		throw new RecognizerException(e);
	    } else {
		throw new RecognizerUnreachableException(e);
	    }
	}

	String jsgfGrammarPath = map.get("jsgfGrammarPath");
	String jsgfGrammarLanguage = map.get("jsgfGrammarLanguage");
	if (jsgfGrammarPath != null) {
	    InputStream in = sc.getResourceAsStream(jsgfGrammarPath);
	    if (in == null) {
		throw new RecognizerException("Couldn't find grammar: "
			+ jsgfGrammarPath);
	    }

	    String language = (jsgfGrammarLanguage != null) ? jsgfGrammarLanguage
		    : "en-us";

	    try {
		JsgfGrammar grammar = new JsgfGrammar(in, language);
		setLanguageModel(grammar);
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
    }

    public void recognize(AudioInputStream audioIn,
	    final IRecognitionListener listener) throws RecognizerException,
	    IOException {
	ArrayList<Future<?>> futures = new ArrayList<Future<?>>();

	synchronized (this) {
	    checkSessionId();
	    Object[] sessionIdParams = { sessionId };

	    if (!recordOnly) {
		if (!hasSetLanguageModel) {
		    throw new LanguageModelNotSetException();

		}

		try {
		    client.execute("Portal.openUtterance", sessionIdParams);
		} catch (XmlRpcException e) {
		    throw new RecognizerException(e);
		}
	    }

	    // we will always wait for the last task in the queue to be finished
	    futures.add(recognizeCallbackExecutor.submit(new Runnable() {
		public void run() {
		    listener.onRecognitionStarted();
		}
	    }));

	    try {
		List<String> hyps = new ArrayList<String>();

		if (recordOnly) {
		    pretendToRecognize(audioIn);
		} else {
		    hyps = getRecognitionResults(audioIn, futures, listener);
		}

		final IRecognitionResult finalResult = new RecognitionResult(
			false, hyps);
		futures.add(recognizeCallbackExecutor.submit(new Runnable() {
		    public void run() {
			listener.onRecognitionResult(finalResult);
		    }
		}));
		// System.out.println("XmlRpcPortaleRecognizer sent: " +
		// totalSent
		// + " bytes");

	    } catch (XmlRpcException e) {
		e.printStackTrace();
	    }
	}

	// wait for futures outside of synchronized block
	for (Future<?> future : futures) {
	    try {
		future.get();
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    } catch (ExecutionException e) {
		e.printStackTrace();
	    }
	}
    }

    private void pretendToRecognize(AudioInputStream audioIn)
	    throws IOException {
	int chunkSize = 1024 * 10;
	byte[] buffer = new byte[chunkSize];

	while (true) {
	    int nRead = audioIn.read(buffer);
	    System.out.println("Read Bytes:" + nRead);

	    if (nRead <= 0) {
		break;
	    }
	}
    }

    private List<String> getRecognitionResults(AudioInputStream audioIn,
	    ArrayList<Future<?>> futures, final IRecognitionListener listener)
	    throws XmlRpcException, IOException {
	int chunkSize = 1024 * 10;
	// int chunkSize = 2 << 10; // kB
	byte[] buffer = new byte[chunkSize];
	byte[] sendBuffer = new byte[chunkSize];
	String lastPartial = null;

	Object[] sessionIdParams = { sessionId };
	int totalSent = 0;
	while (true) {
	    int nRead = audioIn.read(buffer);
	    System.out.println("Read Bytes:" + nRead);

	    if (nRead <= 0) {
		break;
	    }

	    if (nRead > 0) {
		// reuse buffer when possible
		byte[] toSend = (nRead == chunkSize) ? sendBuffer
			: new byte[nRead];
		System.arraycopy(buffer, 0, toSend, 0, nRead);
		Object[] writeParams = { sessionId, toSend };
		totalSent += nRead;

		if (incrementalResults) {
		    final String partial = (String) client.execute(
			    "Portal.writePartial", writeParams);

		    if (!partial.equals(lastPartial)) {
			// System.out.println("partial: " + partial);
			futures.add(recognizeCallbackExecutor
				.submit(new Runnable() {
				    public void run() {
					List<String> hyps = Collections
						.singletonList(partial);
					final IRecognitionResult result = new RecognitionResult(
						true, hyps);
					listener.onRecognitionResult(result);
				    }
				}));
			// listener.result(result, isIncremental)
			lastPartial = partial;
		    }
		} else {
		    client.execute("Portal.write", writeParams);
		}
	    }
	}

	Object[] nbest = (Object[]) client.execute("Portal.closeUtterance",
		sessionIdParams);
	ArrayList<String> hyps = new ArrayList<String>(nbest.length);
	for (Object o : nbest) {
	    Map<String, String> map = (Map<String, String>) o;
	    hyps.add(map.get("text"));
	}

	// System.out.println("hyps:");
	// System.out.println(hyps);

	return hyps;
    }

    public synchronized void setLanguageModel(LanguageModel model)
	    throws RecognizerException {
	if (model instanceof JsgfGrammar) {
	    JsgfGrammar jsgf = (JsgfGrammar) model;
	    Object[] grammarParams = { sessionId, jsgf.getGrammar(),
		    jsgf.getDictionaryLanguage() };

	    try {
		client.execute("Portal.setJsgfGrammar", grammarParams);
		hasSetLanguageModel = true;
	    } catch (XmlRpcException e) {
		if (e.code == ErrorCodes.GRAMMAR_COMPILATION_ERROR) {
		    throw new LanguageModelCompilationException(e.getMessage());
		} else if (e.code == ErrorCodes.SERVER_ERROR_CODE
			|| e.code == ErrorCodes.INVALID_SESSION_CODE) {
		    throw new RecognizerException(e);
		} else {
		    throw new RecognizerUnreachableException(e);
		}
	    }
	} else {
	    throw new UnsupportedLanguageModelException(
		    "Only jsgf language models are currently supported");
	}
    }

    public synchronized void destroy() throws RecognizerException {
	System.out.println("destroying XmlRpcPortalRecognizer instance");
	recognizeCallbackExecutor.shutdownNow();

	if (sessionId != null) {
	    Object[] sessionIdParams = { sessionId };
	    try {
		// need to close up quickly when we are destroyed
		XmlRpcClientConfig config = createClientConfig(serverAddress,
			2 * 1000, 2 * 1000);
		client.execute(config, "Portal.closeRecognizerSession",
			sessionIdParams);
	    } catch (XmlRpcException e) {
		if (e.code == ErrorCodes.SERVER_ERROR_CODE
			|| e.code == ErrorCodes.INVALID_SESSION_CODE) {
		    throw new RecognizerException(e);
		}
	    }
	}

    }

    private void checkSessionId() throws RecognizerException {
	if (sessionId == null) {
	    throw new RecognizerException("Not connected");
	}
    }

}
