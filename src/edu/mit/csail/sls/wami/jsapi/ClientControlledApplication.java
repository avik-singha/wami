package edu.mit.csail.sls.wami.jsapi;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.mit.csail.sls.wami.app.IApplicationController;
import edu.mit.csail.sls.wami.app.IWamiApplication;
import edu.mit.csail.sls.wami.audio.PlayServlet;
import edu.mit.csail.sls.wami.recognition.IRecognitionResult;
import edu.mit.csail.sls.wami.recognition.IRecognizer;
import edu.mit.csail.sls.wami.recognition.exceptions.RecognizerException;
import edu.mit.csail.sls.wami.recognition.lightweight.JSGFIncrementalAggregator;
import edu.mit.csail.sls.wami.recognition.lightweight.JSGFIncrementalAggregatorListener;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelCompilationException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelServerException;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.UnsupportedLanguageModelException;
import edu.mit.csail.sls.wami.recognition.lm.jsgf.JsgfGrammar;
import edu.mit.csail.sls.wami.relay.WamiRelay;
import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * This is an example implementation which allows all the logic for the
 * application to reside on the client. A JSGF Grammar can be updated via an
 * XMLHttpRequest. Recognition results are passed to the client in the form of
 * key-value pairs.
 * 
 * Events can be logged in the following format:
 * 
 * <reply type="logevents"> <event type="MyEvent" /> </reply>
 * 
 * The event element can contain arbitrary XML (as long as you don't include
 * sub-elements called 'event'.)
 * 
 * @author imcgraw
 * 
 */
public class ClientControlledApplication implements IWamiApplication,
		JSGFIncrementalAggregatorListener {

	private JSGFIncrementalAggregator aggregator;
	private IApplicationController appController;

	private String splitTag = JSGFIncrementalAggregator.DEFAULT_SPLIT_TAG;
	private boolean sendIncrementalResults = true;
	private boolean sendAggregates = false;

	/** incremented each time a new final rec result is received */
	private int utteranceId = 0;

	/** incremented each time a new aggregate is completed (isPartial=false) */
	private int aggregateIndex = 0;

	/** incremented each time a new incremental result is proposed */
	private int incrementalIndex = 0;

	/**
	 * used to make sure we don't send the same exact message more than once in
	 * a row
	 **/
	private String lastMessageSent = null;

	private IRecognitionResult currentRecResult = null;

	LinkedHashMap<String, String> currentAggregate = null;

	private boolean currentAggregateIsPartial = false;
	private IRecognizer recognizer;

	public static enum ErrorType {
		grammar_compilation, configuration, unknown_client_message, recording_not_found, not_implemented, synthesis_error
	};

	public void initialize(IApplicationController appController,
			HttpSession session, Map<String, String> paramMap) {
		this.appController = appController;
		WamiRelay wamiRelay = (WamiRelay) appController;
		recognizer = wamiRelay.getRecognizer();
		configure(paramMap);
	}

	/**
	 * Incoming recognition hypotheses are received in this method, both
	 * "partial" results and "complete" results
	 */
	public void onRecognitionResult(IRecognitionResult result) {
		currentRecResult = result;

		if (sendIncrementalResults || !result.isIncremental()) {
			if (sendAggregates) {
				aggregator.update(result);
			} else {
				// Send the rec results directly
				Document recresult = getRecognitionResultDoc(result, false);
				sendMessage(recresult);
			}

			incrementalIndex++;
		}

		// Finally, increment the utterance ID and clear incremental/aggregate
		// indices
		if (!result.isIncremental()) {
			utteranceId++;
			incrementalIndex = 0;
			aggregateIndex = 0;
		}
	}

	/**
	 * This method will be called by the JSGFIncrementalAggregator when there
	 * are new commands to process
	 */
	public void processIncremental(LinkedHashMap<String, String> kvs,
			boolean isPartial) {
		currentAggregate = kvs;
		currentAggregateIsPartial = isPartial;

		Document doc = getRecognitionResultDoc(currentRecResult, isPartial);
		sendMessage(doc);

		if (!isPartial) {
			aggregateIndex++;
		}
	}

	public void sendMessage(Document doc) {
		String thisMessage = XmlUtils.toXMLString(doc);
		if (!thisMessage.equals(lastMessageSent)) {
			appController.sendMessage(doc);
			lastMessageSent = thisMessage;
		}
	}

	/**
	 * Called when the "client" (i.e. the GUI in the web browser) sends a
	 * message to the server.
	 */
	public void onClientMessage(Element xmlRoot) {
		String type = xmlRoot.getAttribute("type");
		System.out.println("Got client message in ClientControlledApp...");

		if ("configure".equals(type)) {
			handleConfigure(xmlRoot);
		} else if ("speak".equals(type)) {
			handleSpeak(xmlRoot);
		} else if ("replay".equals(type)) {
			handleReplay(xmlRoot);
		} else if ("repoll".equals(type)) {
			handleRepoll(xmlRoot);
		} else if ("playrecording".equals(type)) {
			handlePlayRecording(xmlRoot);
		} else if ("playurl".equals(type)) {
			handlePlayURL(xmlRoot);
		} else if ("logevents".equals(type)) {
			// Nothing todo (logging is done in WamiRelay)
		} else {
			sendError(ErrorType.unknown_client_message,
					"Unknown client update type: "
							+ xmlRoot.getAttribute("type"));
		}
	}

	private void handleRepoll(Element xmlRoot) {
		System.out.println("Repolling");
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("reply");
		root.setAttribute("type", "repoll");
		root.setAttribute("timeout", xmlRoot.getAttribute("timeout"));
		doc.appendChild(root);
		appController.sendMessage(doc);
	}

	private void handlePlayURL(Element xmlRoot) {
		String urlstr = xmlRoot.getAttribute("url");
		appController.play(PlayServlet.getWavFromURL(urlstr));
	}

	protected void handleConfigure(Element xmlRoot) {
		List<Element> paramElems = extractElementList(xmlRoot, "param");
		Map<String, String> params = new HashMap<String, String>();
		for (Element e : paramElems) {
			String name = e.getAttribute("name");
			String value = e.getAttribute("value");
			params.put(name, value);
		}

		List<Element> jsgfElems = extractElementList(xmlRoot, "jsgfgrammar");

		if (jsgfElems.size() > 1) {
			sendError(ErrorType.grammar_compilation,
					"Configuration cannot contain multiple jsgfgrammar elements.");
		} else if (jsgfElems.size() == 1) {
			boolean checkVocab = params.get("checkVocabulary") != null
					&& Boolean.parseBoolean(params.get("checkVocabulary"));
			configureGrammarFromElement(jsgfElems.get(0), checkVocab);
		}

		configure(params);
	}

	protected void handleSpeak(Element xmlRoot) {
		NodeList list = xmlRoot.getElementsByTagName("synth_string");
		if (list == null || list.getLength() == 0) {
			sendError(ErrorType.synthesis_error, "Cannot find string to speak!");
		} else {
			Element e = (Element) list.item(0);
			appController.speak(e.getTextContent());
		}
	}

	protected void handlePlayRecording(Element xmlRoot) {
		// You can implement an IAudioRetriever to handle the following:
		String wsessionid = xmlRoot.getAttribute("wsessionid");
		int utt_id = Integer.parseInt(xmlRoot.getAttribute("uttid"));
		String fileName = "wami---" + wsessionid + "---" + utt_id;
		InputStream stream = appController.getRecording(fileName);

		if (stream == null) {
			sendError(ErrorType.recording_not_found,
					"The recording specified by wsessionid " + fileName
							+ " was not found.");
		} else {
			appController.play(stream);
		}
	}

	protected void handleReplay(Element xmlRoot) {
		InputStream stream = appController.getLastRecordedAudio();
		if (stream != null) {
			appController.play(stream);
		}
	}

	protected void configureGrammarFromElement(Element elem, boolean checkVocab) {
		String text = elem.getTextContent();

		String language = elem.getAttribute("language");
		if ("".equals(language) || language == null) {
			language = "en-us";
		}

		JsgfGrammar jsgf = new JsgfGrammar(text, language);
		try {
			appController.setLanguageModel(jsgf);
		} catch (LanguageModelCompilationException e) {
			sendError(ErrorType.grammar_compilation, e.getMessage());
			e.printStackTrace();
		} catch (UnsupportedLanguageModelException e) {
			sendError(ErrorType.grammar_compilation, e.getMessage());
			e.printStackTrace();
		} catch (LanguageModelServerException e) {
			sendError(ErrorType.grammar_compilation, e.getMessage());
			e.printStackTrace();
		}
	}

	public void onRecognitionStarted() {

	}

	public void onFinishedPlayingAudio() {
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("reply");
		root.setAttribute("type", "finishedplayingaudio");
		doc.appendChild(root);
		appController.sendMessage(doc);
	}

	public void onClosed() {
		this.appController = null;
	}

	protected void configure(Map<String, String> paramMap) {
		System.out.println("Configuring with params: " + paramMap);

		if (paramMap.get("splitTag") != null) {
			String tag = paramMap.get("splitTag");
			if (!tag.matches("[a-zA-Z0-9]+")) {
				sendError(ErrorType.configuration, "Split tag (" + tag
						+ ") must be alphanumeric.");
			} else {
				splitTag = tag;
			}
		}

		if (paramMap.get("recordOnly") != null) {
			try {
				boolean recordOnly = false; // In case parse fails
				recordOnly = Boolean.parseBoolean(paramMap.get("recordOnly"));
				recognizer.setDynamicParameter("recordOnly", new Boolean(
						recordOnly).toString());
			} catch (RecognizerException e) {
				e.printStackTrace();
				sendError(ErrorType.configuration, e.getMessage());
			}
		}

		if (paramMap.get("sendIncrementalResults") != null) {
			sendIncrementalResults = false; // In case parse fails
			sendIncrementalResults = Boolean.parseBoolean(paramMap
					.get("sendIncrementalResults"));
			try {
				recognizer.setDynamicParameter("incrementalResults",
						new Boolean(sendIncrementalResults).toString());
			} catch (RecognizerException e) {
				e.printStackTrace();
				sendError(ErrorType.configuration, e.getMessage());
			}
		}

		if (paramMap.get("sendAggregates") != null) {
			sendAggregates = Boolean.parseBoolean(paramMap
					.get("sendAggregates"));
		}

		if (sendIncrementalResults) {
			aggregator = new JSGFIncrementalAggregator(this, splitTag);
		}
	}

	protected void sendError(ErrorType errorType, String message) {
		System.err.println("Sending ERROR: " + message);

		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("reply");

		root.setAttribute("type", "error");
		root.setAttribute("error_type", errorType.toString());
		root.setAttribute("message", message);

		doc.appendChild(root);
		appController.sendMessage(doc);
	}

	private List<Element> extractElementList(Element e, String name) {
		NodeList nodes = e.getElementsByTagName(name);

		List<Element> elements = new ArrayList<Element>();
		for (int i = 0; i < nodes.getLength(); i++) {
			elements.add((Element) nodes.item(i));
		}

		return elements;
	}

	protected Document getRecognitionResultDoc(IRecognitionResult result,
			boolean isPartial) {
		Document doc = XmlUtils.newXMLDocument();
		Element root = createRecognitionResultElement(doc, result, utteranceId,
				incrementalIndex);

		for (int i = 0; i < result.getHyps().size(); i++) {
			Element hyp = createHypElement(doc, result, i);
			root.appendChild(hyp);
		}

		doc.appendChild(root);
		return doc;
	}

	private Element createHypElement(Document doc, IRecognitionResult result,
			int hypIndex) {
		if (!sendAggregates) {
			// Send a simple hypothesis without aggregates
			System.out.println("Creating hyp without aggregate.");
			return createBasicHypElement(doc, result, hypIndex);
		} else {
			int startIndex;
			List<LinkedHashMap<String, String>> aggregates;

			if (hypIndex == 0) {
				// For the first hypothesis, send only single aggregates
				aggregates = new ArrayList<LinkedHashMap<String, String>>();
				aggregates.add(currentAggregate);
				startIndex = aggregateIndex;
			} else {
				// For other hypothesis, or if sendAggregates is false
				// Extract all the kvs from the hypothesis
				startIndex = 0;
				aggregates = JSGFIncrementalAggregator.extractCommandSets(
						result.getHyps().get(hypIndex), splitTag, false, false,
						false);
			}

			// Create a hypothesis with one or more aggregates
			return createHypElement(doc, result, hypIndex, aggregates,
					startIndex, currentAggregateIsPartial);
		}
	}

	private static Element createRecognitionResultElement(Document doc,
			IRecognitionResult result, int utteranceId, int incrementalIndex) {
		Element e = doc.createElement("reply");
		e.setAttribute("type", "recresult");
		e.setAttribute("incremental", Boolean.toString(result.isIncremental()));
		e.setAttribute("utt_id", Integer.toString(utteranceId));
		e.setAttribute("incremental_index", Integer.toString(incrementalIndex));
		return e;
	}

	private static Element createBasicHypElement(Document doc,
			IRecognitionResult result, int index) {
		String txtstr = result.getHyps().get(index);
		Element hyp = doc.createElement("hyp");
		Element text = doc.createElement("text");
		text.setTextContent(txtstr);
		hyp.setAttribute("index", Integer.toString(index));
		hyp.appendChild(text);
		return hyp;
	}

	private static Element createHypElement(Document doc,
			IRecognitionResult result, int index,
			List<LinkedHashMap<String, String>> aggregates,
			int aggregateStartIndex, boolean lastAggregateIsPartial) {
		Element hyp = createBasicHypElement(doc, result, index);

		for (int i = 0; i < aggregates.size(); i++) {
			Map<String, String> aggregate = aggregates.get(i);
			Element e = doc.createElement("aggregate");

			boolean isLastAggregate = (i == aggregates.size() - 1);
			boolean isPartial = isLastAggregate && lastAggregateIsPartial;

			int aggregateIndex = aggregateStartIndex + i;
			e.setAttribute("index", Integer.toString(aggregateIndex));
			e.setAttribute("partial", Boolean.toString(isPartial));

			for (String key : aggregate.keySet()) {
				Element kv = doc.createElement("kv");
				kv.setAttribute("key", key);
				kv.setAttribute("value", aggregate.get(key));
				e.appendChild(kv);
			}

			hyp.appendChild(e);
		}

		return hyp;
	}

}
