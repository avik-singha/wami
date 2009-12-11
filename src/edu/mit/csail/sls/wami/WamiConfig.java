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
package edu.mit.csail.sls.wami;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.mit.csail.sls.wami.app.IApplicationController;
import edu.mit.csail.sls.wami.app.IWamiApplication;
import edu.mit.csail.sls.wami.audio.IAudioRetriever;
import edu.mit.csail.sls.wami.log.EventLoggerDaemonAdapter;
import edu.mit.csail.sls.wami.log.EventLoggerException;
import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.IEventPlayer;
import edu.mit.csail.sls.wami.recognition.IRecognizer;
import edu.mit.csail.sls.wami.recognition.exceptions.RecognizerException;
import edu.mit.csail.sls.wami.relay.InstantiationEvent;
import edu.mit.csail.sls.wami.synthesis.ISynthesizer;
import edu.mit.csail.sls.wami.util.Instantiable;
import edu.mit.csail.sls.wami.util.XmlUtils.ValidationErrorHandler;
import edu.mit.csail.sls.wami.validation.IValidator;

/**
 * <p>
 * WamiConfig manages the options that specify the components of the toolkit
 * (and their layouts if applicable). WAMI configuration is managed via a single
 * XML file as validated by the XML Schema generic/config.xsd. A single instance
 * of the WamiConfig class will be stored on the ServletContext during runtime.
 * This instance should never be accessed directly, but through the static
 * method <code>getConfiguration</code> provided by this class.
 * </p>
 * <p>
 * Configuration settings can then be accessed by the
 * <code>get*(HttpServletRequest)</code> methods. The request can override
 * configurations found within the XML file. The request can also simply be
 * null, and the parameter will then default to the value found in the config
 * file.
 * </p>
 * <p>
 * There is actually a further back-off mechanism. Attributes in the XML file
 * that are left unspecified have default values as well. These can be found in
 * the config.xsd file. One way to view all these defaults is to generate an XML
 * file from the XSD file (a feature that Eclipse includes.)
 * </p>
 *  
 * @author imcgraw
 * 
 */
public class WamiConfig {
	protected Document config = null;
	private String xmlname;

	public WamiConfig() {

	}

	/**
	 * <p>
	 * Get the singleton WamiConfig for the servlet. This is perhaps the single
	 * most important method in this class. Any servlet wishing to use the
	 * WamiConfig class must access it through this public method. This method
	 * ensures that the WamiConfig is created once and only once for the
	 * duration of the servlet's life-time.
	 * </p>
	 */
	public static WamiConfig getConfiguration(ServletContext sc) {
		WamiConfig wc = null;
		synchronized (sc) {
			wc = (WamiConfig) sc.getAttribute("ajaxconfig");
			if (wc == null) {
				// A little redundant, but that's ok.
				wc = new WamiConfig(sc);

				if (wc.getDocument() != null) {
					wc = getAppSpecificAjaxConfig(wc, sc);
					sc.setAttribute("ajaxconfig", wc);
				}
			}
		}

		return wc;
	}

	/**
	 * Get the original, validated XML document that represents the
	 * configuration.
	 */
	public Document getDocument() {
		return config;
	}

	/**
	 * Given the className, this returns a new WamiConfig class, constructing it
	 * via reflection.
	 */
	private static WamiConfig getAppSpecificAjaxConfig(WamiConfig ac,
			ServletContext sc) {
		String className = ac.getAjaxConfigClass();
		System.out.println("Creating new WamiConfig class: " + className);

		if (className != null && !ac.getClass().getName().equals(className)) {
			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = { ServletContext.class };
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = { sc };
				ac = (WamiConfig) cons.newInstance(args);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		return ac;
	}

	/**
	 * <p>
	 * This constructor should never be called outside the class. To access the
	 * WamiConfig, one should always use the static method:
	 * <code>getConfiguration(ServletContext)</code>
	 * </p>
	 */
	public WamiConfig(final ServletContext sc) {
		xmlname = sc.getInitParameter("configFileName");

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(true);
			dbf.setNamespaceAware(true);
			dbf.setAttribute(
					"http://java.sun.com/xml/jaxp/properties/schemaLanguage",
					"http://www.w3.org/2001/XMLSchema");

			InputStream xmlIn = getResourceAsStream(sc, xmlname);

			if (xmlIn == null) {
				String error = "ERROR: Unable to load config because couldn't resolve XML file.";
				throw new Exception(error);
			}

			final DocumentBuilder parser = dbf.newDocumentBuilder();
			parser.setErrorHandler(new ValidationErrorHandler());
			parser.setEntityResolver(new EntityResolver() {
				public InputSource resolveEntity(String publicId,
						String systemId) throws SAXException, IOException {
					System.out.println("RESOLVE ENTITY(" + systemId + ")");

					// System.out.println("RESOLVE PUBLIC(" + publicId + ")");
					String name = "edu/mit/csail/sls/wami/content"
							+ systemId.substring(systemId.lastIndexOf("/"));
					System.out.println("Name: " + name);

					return new InputSource(this.getClass().getClassLoader()
							.getResourceAsStream(name));
				}
			});

			config = parser.parse(xmlIn);

			// System.out.println("WamiConfig(): " +
			// XmlUtils.toXMLString(config));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getConfigXMLName() {
		return xmlname;
	}

	public static InputStream getResourceAsStream(ServletContext sc, String name) {
		URL nameURL = null;
		// System.out.print("Name: " + name + " resolves to: ");
		try {
			nameURL = sc.getResource(name);
		} catch (MalformedURLException murle) {
			murle.printStackTrace();
		}

		InputStream result = null;

		if (nameURL != null) {
			result = sc.getResourceAsStream(name);
		} else {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			nameURL = loader.getResource(name);

			if (nameURL != null) {
				result = loader.getResourceAsStream(name);
			}
		}

		System.out.println(nameURL);

		return result;
	}

	public static Element getUniqueDescendant(Node node, String name) {
		if (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
			Element element = (Element) node;
			return (Element) element.getElementsByTagName(name).item(0);
		}

		return null;
	}

	public boolean getDebug(HttpServletRequest request) {
		String debugStr = request.getParameter("debug");
		boolean debugMode = debugStr != null
				&& debugStr.equalsIgnoreCase("true");
		return debugMode;
	}

	/**
	 * Get class name to be used for the relay. If overriding the WamiRelay,
	 * you'll need to specify the class in the config.xml file. Note that if you
	 * override AjaxGalaxyControl, however, this needs to be specified in the
	 * WebContent/WEB-INF/web.xml. This is because AjaxGalaxyControl is an
	 */
	public String getRelayClass() {
		Element relayE = getUniqueDescendant(getDocument().getFirstChild(),
				"relay");
		return relayE.getAttribute("relayClass");
	}

	public boolean getUseRelay() {
		Element relayE = getUniqueDescendant(getDocument().getFirstChild(),
				"relay");
		return relayE != null;
	}

	/*
	 * Build Configuration Accessors
	 */

	/**
	 * Gets the ajax config class specified in the config.xml class. By default,
	 * it's this one.
	 */
	private String getAjaxConfigClass() {
		Element buildE = getUniqueDescendant(config.getFirstChild(), "build");

		if (buildE == null) {
			return this.getClass().getCanonicalName();
		}

		return buildE.getAttribute("ajaxconfig");
	}

	/*
	 * Layout Configuration Accessors
	 */

	/**
	 * The title of the AjaxGUI. This will appear many places in the pages and
	 * emails generated. Typically it is just a short string like "City
	 * Browser", "Shape Game", "Family Dialogue", etc.
	 */
	public String getTitle(HttpServletRequest request) {
		if (request != null && request.getParameter("title") != null) {
			return request.getParameter("title");
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		return getUniqueDescendant(layoutE, "title").getTextContent();
	}

	/**
	 * Return whether or not to test the browser version. If true is returned
	 * then we test the browser to make sure that it is a version known to work
	 * with the AjaxGUI code. The user is allowed to circumvent this test even
	 * if their browser is not a well tested version.
	 */
	public boolean getTestBrowser(HttpServletRequest request) {
		if (request != null && request.getParameter("testBrowser") != null) {
			return Boolean.parseBoolean(request.getParameter("testBrowser"));
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		return Boolean.parseBoolean(layoutE.getAttribute("testBrowser"));
	}

	/**
	 * Returns the URL of a logo to be displayed in the upper-left of the page.
	 */
	public String getLogo(HttpServletRequest request) {
		String prefix = "";
		if (request != null) {
			prefix = request.getContextPath();
			if (request.getParameter("logo") != null) {
				return request.getParameter("logo");
			}
		}

		if (getMobile(request)) {
			return null;
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element logoE = getUniqueDescendant(layoutE, "logo");

		if (logoE == null) {
			return null;
		}

		return prefix + "/" + logoE.getAttribute("src");
	}

	/**
	 * Returns the logo width.
	 */
	public int getLogoWidth(HttpServletRequest request) {
		if (request != null && request.getParameter("logoImgWidth") != null) {
			return Integer.parseInt(request.getParameter("logoImgWidth"));
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element logoE = getUniqueDescendant(layoutE, "logo");

		if (logoE == null) {
			return 0;
		}

		return Integer.parseInt(logoE.getAttribute("width"));
	}

	/**
	 * Returns the logo height.
	 */
	public int getLogoHeight(HttpServletRequest request) {
		if (request != null && request.getParameter("logoImgHeight") != null) {
			return Integer.parseInt(request.getParameter("logoImgHeight"));
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element logoE = getUniqueDescendant(layoutE, "logo");

		if (logoE == null) {
			return 0;
		}

		return Integer.parseInt(logoE.getAttribute("height"));
	}

	/**
	 * Returns the class name for the audio applet. If the audio applet class
	 * needs to be overridden, it can be specified in the XML file. This is not
	 * typically done, however, so the value returned is usually the default
	 * class corresponding to the AudioApplet.
	 */
	public String getAudioAppletClass(HttpServletRequest request) {
		String className = null;

		if (request != null) {
			className = request.getParameter("appletClassName");
		}

		if (className == null) {
			Element layoutE = getUniqueDescendant(
					getDocument().getFirstChild(), "layout");
			Element audioE = getUniqueDescendant(layoutE, "audio");

			if (audioE == null) {
				return null;
			}

			className = audioE.getAttribute("appletClass");
		}

		if (!className.endsWith(".class")) {
			className += ".class";
		}

		return className;
	}

	/**
	 * Returns the archives to be used for the audio applet. If you'd like to
	 * specify multiple archives for the applet, this can be done in the
	 * getDocument().xml file. This is not typically overridden though.
	 */
	public String getAppletArchives(HttpServletRequest request) {
		if (request != null && request.getParameter("appletArchives") != null) {
			return request.getParameter("appletArchives");
		}

		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE == null) {
			return null;
		}

		String result = audioE.getAttribute("archive");

		if (result == null || "".equals(result)) {
			return getBaseURL(request) + "/content/wami_audio_applet.jar";
		}

		NodeList archives = audioE.getElementsByTagName("archive");
		for (int i = 0; i < archives.getLength(); i++) {
			result += ", ";
			Element archive = (Element) archives.item(i);
			result += archive.getAttribute("src");
		}

		return result;
	}

	/**
	 * Specifies the applet height.
	 */
	public int getAppletHeight(HttpServletRequest request) {
		if (request != null && request.getParameter("appletHeight") != null) {
			String height = request.getParameter("appletHeight");
			return Integer.parseInt(height);
		}

		if (!getUseAudio(request)) {
			return 0;
		}

		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		return Integer.parseInt(audioE.getAttribute("height"));
	}

	/**
	 * Specified whether or not to poll for audio on /play servlet (default
	 * true)
	 */
	public boolean getPollForAudio(HttpServletRequest request) {
		if (request != null && request.getParameter("pollForAudio") != null) {
			String height = request.getParameter("pollForAudio");
			return Boolean.parseBoolean(height);
		}

		if (!getUseAudio(request)) {
			return false;
		}

		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		return Boolean.parseBoolean(audioE.getAttribute("pollForAudio"));
	}

	/**
	 * Specifies whether or not to hide the audio button on the applet (default
	 * false)
	 */
	public boolean getHideAudioButton(HttpServletRequest request) {
		if (request != null && request.getParameter("hideAudioButton") != null) {
			String height = request.getParameter("hideAudioButton");
			return Boolean.parseBoolean(height);
		}

		if (!getUseAudio(request)) {
			return false;
		}

		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		return Boolean.parseBoolean(audioE.getAttribute("hideButton"));
	}

	/**
	 * Specifies the applet width.
	 */
	public int getAppletWidth(HttpServletRequest request) {
		if (request != null && request.getParameter("appletWidth") != null) {
			String width = request.getParameter("appletWidth");
			return Integer.parseInt(width);
		}

		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (!getUseAudio(request)) {
			return 0;
		}

		return Integer.parseInt(audioE.getAttribute("width"));
	}

	/**
	 * Determines whether or not we should optimize for a mobile device. Checks
	 * the following in this order. (1) The request parameter "mobile", (2) The
	 * configuration parameter "mobile" (3) the user agent string. Note, the
	 * requirements for mobile devices are still in flux, this method may be
	 * subject to change (alexgru Nov 2007)
	 */
	public boolean getMobile(HttpServletRequest request) {
		if (request != null && request.getParameter("mobile") != null) {
			String mobileStr = request.getParameter("mobile");
			System.out
					.println("Optimizing for mobile device based on request mobile=true");
			return Boolean.parseBoolean(mobileStr);
		}

		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");
		Element mobileE = getUniqueDescendant(layoutE, "mobile");
		if (mobileE != null) {
			System.out
					.println("Optimizing for mobile device based on config file");
			return true;
		}

		String userAgent = request.getHeader("user-agent");
		// for now, we only look for windows CE devices and Nokia N810
		if ((userAgent != null && userAgent.contains("Windows CE"))
				|| isNokiaN810(request)) {
			System.out
					.println("Optimizing for mobile device based on user agent string:");
			System.out.println(userAgent);
			return true;
		}

		return false;

	}

	public int getMobileWidth(HttpServletRequest request) {
		int mobileWidth = -1;
		if (getMobile(request)) {
			java.awt.Dimension d = getMobileDimensions(request);
			if (d != null) {
				mobileWidth = d.width;
			}
		}
		return mobileWidth;
	}

	public int getMobileHeight(HttpServletRequest request) {
		int mobileHeight = -1;
		if (getMobile(request)) {
			java.awt.Dimension d = getMobileDimensions(request);
			if (d != null) {
				mobileHeight = d.height;
			}
		}
		return mobileHeight;
	}

	public boolean isNokiaN810(HttpServletRequest request) {
		// User agent: Mozilla/5.0 (X11; U; Linux armv6l; en-US; rv:1.9a6pre)
		// Gecko/20071128 Firefox/3.0a1 Tablet browser 0.2.2
		// RX-34+RX-44_2008SE_2.2007.51-3

		String userAgent = request.getHeader("user-agent");

		return userAgent != null && userAgent.contains("Mozilla")
				&& userAgent.contains("Linux") && userAgent.contains("Firefox")
				&& userAgent.contains("Tablet browser");
	}

	/**
	 * Looks at the user agent string to try to determine the dimensions of the
	 * mobile device screen. If this isn't a mobile device, or we can't
	 * determine the dimensions, we return null. I'm including this method here
	 * because we may, in the future, want to be able to override what the user
	 * agent string reports. Note, the requirements for mobile devices are still
	 * in flux, this method may be subject to change (alexgru Nov 2007)
	 * 
	 * @param request
	 * @return
	 */
	public Dimension getMobileDimensions(HttpServletRequest request) {
		String userAgent = request.getHeader("user-agent");
		if (userAgent == null) {
			return null;
		}

		// this is probably mildly dangerous, just looks for e.g. 240x320
		// anywhere in the string
		Pattern p = Pattern.compile("(\\d+)x(\\d+)");
		Matcher m = p.matcher(userAgent);
		if (m.find()) {
			Dimension d = new Dimension(Integer.parseInt(m.group(1)), Integer
					.parseInt(m.group(2)));
			System.out
					.println("Mobile dimensions: " + d.width + "x" + d.height);
			return d;
		}
		return null;
	}

	/**
	 * Determines whether or not the AjaxGUI is going to embed the AudioApplet.
	 * The audio applet allows the user to listen and speak to interact with the
	 * application.
	 */
	public boolean getUseAudio(HttpServletRequest request) {
		if (request != null && request.getParameter("audio") != null) {
			String audioStr = request.getParameter("audio");
			return Boolean.parseBoolean(audioStr);
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");
		return (audioE != null);
	}

	/**
	 * Returns whether or not to use end-point detection. If returns true, then
	 * the applet becomes click-to-talk (rather than hold-to-talk) and automatic
	 * end-point detection is used.
	 */
	public boolean getUseSpeechDetector(HttpServletRequest request) {
		if (request != null
				&& request.getParameter("useSpeechDetector") != null) {
			String useSpeechDetector = request
					.getParameter("useSpeechDetector");
			return Boolean.parseBoolean(useSpeechDetector);
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE == null) {
			return false;
		}

		return Boolean.parseBoolean(audioE.getAttribute("useSpeechDetector"));
	}

	public boolean getPlayRecordTone(HttpServletRequest request) {
		if (request != null && request.getParameter("playRecordTone") != null) {
			String playRecordTone = request.getParameter("playRecordTone");
			return Boolean.parseBoolean(playRecordTone);
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE == null) {
			return false;
		}

		return Boolean.parseBoolean(audioE.getAttribute("playRecordTone"));

	}

	/**
	 * Determines if the audio applet should turn green when the hub enables
	 * input.
	 */
	public boolean getGreenOnEnableInput(HttpServletRequest request) {
		if (request != null
				&& request.getParameter("greenOnEnableInput") != null) {
			String greenOnEnableInput = request
					.getParameter("greenOnEnableInput");
			return Boolean.parseBoolean(greenOnEnableInput);
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE == null) {
			return false;
		}

		return Boolean.parseBoolean(audioE.getAttribute("greenOnEnableInput"));
	}

	public String getRecordAudioFormat() {
		return getAudioAttribute("recordAudioFormat");
	}

	public int getRecordSampleRate() {
		return Integer.parseInt(getAudioAttribute("recordSampleRate"));
	}

	public boolean getRecordIsLittleEndian() {
		return Boolean.parseBoolean(getAudioAttribute("recordIsLittleEndian"));
	}

	public boolean getAudioHttpOnly() {
		return Boolean.parseBoolean(getAudioAttribute("httpOnly"));
	}

	private String getAudioAttribute(String attributeName) {
		Element layoutE = getUniqueDescendant(getDocument().getFirstChild(),
				"layout");

		if (layoutE == null) {
			return null;
		}

		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE == null) {
			return null;
		}

		return audioE.getAttribute(attributeName);
	}

	public String getRelaySetting(String name) {
		Element relayE = getUniqueDescendant(getDocument().getFirstChild(),
				"relay");
		String getRelayTag = relayE.getAttribute("initialTag");

		if (relayE != null) {
			NodeList settingsNodes = relayE.getElementsByTagName("settings");

			for (int i = 0; i < settingsNodes.getLength(); i++) {
				Element settingsE = (Element) settingsNodes.item(i);

				if (getRelayTag.equals(settingsE.getAttribute("tag"))) {
					return settingsE.getAttribute(name);
				}
			}
		}

		return null;
	}

	public int getMaxRelays() {
		String maxRelaysString = getRelaySetting("maxRelays");

		if (maxRelaysString == null) {
			throw new RuntimeException("Cannot find maxRelays!");
		}

		return Integer.parseInt(maxRelaysString);
	}

	/**
	 * get relay timeout (in ms)
	 */
	public long getRelayTimeout(HttpSession session) {
		String relayTimeoutString = getRelaySetting("relayTimeout");

		if (!"default".equals(relayTimeoutString)) {
			session
					.getServletContext()
					.log(
							"WARNING: relayTimeout is now the same as session timeout, please remove this attribute from your config file.relayTimeout is now the same as session timeout, please remove this attribute from your config file");
		}

		return session.getMaxInactiveInterval() * 1000;
	}

	/**
	 * get maximum time (in ms) to allow between polls from a client
	 */
	public long getNoPollFromClientTimeout() {
		String noPollFromClientTimeoutString = getRelaySetting("noPollFromClientTimeout");
		if (noPollFromClientTimeoutString == null) {
			throw new RuntimeException("CAnnot find noPollFromClientTimeout!");
		}

		return Long.parseLong(noPollFromClientTimeoutString);
	}

	/** get max time to allow a poll from the client (in ms) */
	public long getPollTimeout() {
		String timoutString = getRelaySetting("pollTimeout");

		if (timoutString == null) {
			throw new RuntimeException("Cannot find pollTimeout!");
		}

		return Long.parseLong(timoutString);
	}

	public String controlServletURL(HttpServletRequest request,
			String wsessionid) {
		String baseUrl = WamiConfig.getBaseURL(request);
		String url = baseUrl + "/ajaxcontrol;jsessionid="
				+ request.getSession().getId() + "?";

		if (wsessionid != null) {
			try {
				url += "wsessionid=" + URLEncoder.encode(wsessionid, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

		return url;
	}

	public String audioServletURL(HttpServletRequest request, String wsessionid) {
		if (!getUseAudio(request)) {
			return null;
		}

		String url = getBaseURL(request, true) + "/play" + ";jsessionid="
				+ request.getSession().getId() + "?poll=true&playPollTimeout="
				+ getPlayPollTimeout(request);

		if (wsessionid != null) {
			try {
				url += "&wsessionid=" + URLEncoder.encode(wsessionid, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

		return url;
	}

	public String recordServletURL(HttpServletRequest request, String wsessionid) {
		if (!getUseAudio(request)) {
			return null;
		}

		String url = getBaseURL(request, true) + "/record" + ";jsessionid="
				+ request.getSession().getId() + "?recordAudioFormat="
				+ getRecordAudioFormat() + "&recordSampleRate="
				+ getRecordSampleRate() + "&recordIsLittleEndian="
				+ getRecordIsLittleEndian();

		if (wsessionid != null) {
			try {
				url += "&wsessionid=" + URLEncoder.encode(wsessionid, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

		return url;
	}

	public int getPlayPollTimeout(HttpServletRequest request) {
		if (request != null && request.getParameter("playPollTimeout") != null) {
			String audioPollTimeout = request.getParameter("playPollTimeout");
			return Integer.parseInt(audioPollTimeout);
		}

		Element layoutE = getUniqueDescendant(config.getFirstChild(), "layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE == null) {
			return 0;
		}

		return Integer.parseInt(audioE.getAttribute("pollTimeout"));
	}

	public static int getAudioPort(HttpServletRequest request,
			boolean refusePort80) {
		WamiConfig wc = WamiConfig.getConfiguration(request.getSession()
				.getServletContext());

		Element layoutE = getUniqueDescendant(wc.getDocument().getFirstChild(),
				"layout");
		Element audioE = getUniqueDescendant(layoutE, "audio");

		if (audioE != null) {
			int port = Integer.parseInt(audioE.getAttribute("port"));
			if (port != -1) {
				return port;
			}
		}

		return request.getServerPort();
	}

	public boolean getUseSynthesizer() {
		return getUniqueDescendant(getDocument().getFirstChild(), "synthesizer") != null;
	}

	/**
	 * AudioApplet can be used, but be invisible. This parameter only available
	 * on the URL.
	 */
	public boolean getAudioAppletVisible(HttpServletRequest request) {
		if (request != null && request.getParameter("audioVisible") != null) {
			String audioStr = request.getParameter("audioVisible");
			return Boolean.parseBoolean(audioStr);
		}

		return true;
	}

	public static String getBaseURL(HttpServletRequest req) {
		return getBaseURL(req, false);
	}

	public static String getBaseURL(HttpServletRequest req, boolean refusePort80) {
		// http://hostname.com:8080/mywebapp/
		String scheme = req.getScheme(); // http
		String serverName = req.getServerName(); // hostname.com
		int serverPort = getAudioPort(req, refusePort80);

		String contextPath = req.getContextPath(); // /mywebapp

		// Reconstruct original requesting URL
		String url = scheme + "://" + serverName + ":" + serverPort
				+ contextPath;
		return url;
	}

	public ISynthesizer createSynthesizer(IApplicationController appController) {
		String className = getClassAttribute("synthesizer");
		Map<String, String> params = getParameters("synthesizer");
		return createSynthesizer(appController, className, params);
	}

	public ISynthesizer createSynthesizer(IApplicationController appController,
			String className, Map<String, String> params) {
		System.out.println("Creating new synthesizer class: " + className);
		ISynthesizer synth = null;

		if (className != null) {
			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = {};
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = {};
				synth = (ISynthesizer) cons.newInstance(args);
				synth.setParameters(params);
				if (appController != null) {
					logInstantiationEvent(appController, "synthesizer",
							className, params);
				}
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		return synth;
	}

	public static Instantiable createInstantiable(ServletContext sc,
			Element elem) {
		String className = elem.getAttribute("class");
		System.out.println("Creating new instance of class: " + className);
		Map<String, String> params = getParameters(elem);

		Instantiable instance = null;

		if (className != null) {
			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = {};
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = {};
				instance = (Instantiable) cons.newInstance(args);
				instance.setParameters(sc, params);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		return instance;
	}

	public IRecognizer createRecognizer(ServletContext sc,
			IApplicationController appController) throws RecognizerException {
		String className = getClassAttribute("recognizer");
		Map<String, String> params = getParameters("recognizer");
		return createRecognizer(sc, appController, className, params);
	}

	public IRecognizer createRecognizer(ServletContext sc,
			IApplicationController appController, String className,
			Map<String, String> params) throws RecognizerException {
		System.out.println("Creating new recognizer class: " + className);
		IRecognizer rec = null;

		if (className != null) {
			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = {};
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = {};
				rec = (IRecognizer) cons.newInstance(args);
				rec.setParameters(sc, params);
				if (appController != null) {
					logInstantiationEvent(appController, "recognizer",
							className, params);
				}
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		return rec;
	}

	public IWamiApplication createWamiApplication(
			IApplicationController appController, HttpSession session) {
		Element appE = getUniqueDescendant(getDocument().getFirstChild(),
				"application");
		if (appE == null) {
			System.err.println("No wami application specified in config file");
			return null;
		}
		String className = appE.getAttribute("class");
		Map<String, String> params = getParameters("application");
		return createWamiApplication(appController, session, className, params);
	}

	public IWamiApplication createWamiApplication(
			IApplicationController appController, HttpSession session,
			String className, Map<String, String> params) {

		if (className != null) {
			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = {};
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = {};
				IWamiApplication app = (IWamiApplication) cons
						.newInstance(args);
				app.initialize(appController, session, params);
				logInstantiationEvent(appController, "application", className,
						params);
				return app;
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public IEventLogger createEventLogger(ServletContext sc)
			throws EventLoggerException {
		String className = getClassAttribute("event_logger");
		System.out.println("Creating new event logger class: " + className);
		IEventLogger logger = null;

		if (className != null) {
			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = {};
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = {};
				logger = (IEventLogger) cons.newInstance(args);
				logger.setParameters(sc, getParameters("event_logger"));
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}

		if (logger == null) {
			return null;
		}

		return new EventLoggerDaemonAdapter(logger);
	}

	public IAudioRetriever createAudioRetriever(ServletContext sc) {
		Element audioRetrieverE = getUniqueDescendant(getDocument()
				.getFirstChild(), "audio_retriever");

		if (audioRetrieverE == null) {
			return null;
		}

		return (IAudioRetriever) createInstantiable(sc, audioRetrieverE);
	}

	public IValidator createValidator(ServletContext sc) {
		Element validatorE = getUniqueDescendant(getDocument().getFirstChild(),
				"validator");

		if (validatorE == null) {
			return null;
		}

		return (IValidator) createInstantiable(sc, validatorE);
	}

	public IEventPlayer createLogPlayer(HttpServletRequest request) {
		ServletContext sc = request.getSession().getServletContext();
		Map<String, String> params = new HashMap<String, String>();

		String className = getClassAttribute("event_player");
		System.out.println("Creating new event logger class: " + className);
		IEventPlayer logplayer = null;

		if (className != null) {
			for (Object key : request.getParameterMap().keySet()) {
				String paramName = (String) key;
				String paramValue = request.getParameter(paramName);
				params.put(paramName, paramValue);
			}

			params.putAll(getParameters("event_player"));

			try {
				Class<?> acClass = Class.forName(className);
				Class<?>[] paramTypes = {};
				Constructor<?> cons = acClass.getConstructor(paramTypes);
				Object[] args = {};
				logplayer = (IEventPlayer) cons.newInstance(args);
				logplayer.setParameters(sc, params);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return logplayer;
	}

	public String getClassAttribute(String elementName) {
		Element e = getUniqueDescendant(getDocument().getFirstChild(),
				elementName);
		if (e == null) {
			return null;
		}

		return e.getAttribute("class");

	}

	/**
	 * Arbitrary XML specific to the application.
	 */
	public Element getSpecifics() {
		return getUniqueDescendant(getDocument().getFirstChild(), "specifics");
	}

	public Map<String, String> getParameters(String elementName) {
		Element parentE = getUniqueDescendant(getDocument().getFirstChild(),
				elementName);

		if (parentE == null) {
			return null;
		}

		return getParameters(parentE);
	}

	public static Map<String, String> getParameters(Element e) {
		Map<String, String> params = new HashMap<String, String>();
		NodeList list = e.getElementsByTagName("param");

		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if (node instanceof Element) {
				Element paramE = (Element) node;
				String name = paramE.getAttribute("name");
				String value = paramE.getAttribute("value");
				params.put(name, value);
			}
		}

		return params;
	}

	public static String reconstructRequestURLandParams(
			HttpServletRequest request) {
		String url = request.getRequestURL().toString();
		String params = "";

		for (Object key : request.getParameterMap().keySet()) {
			String paramName = (String) key;
			String paramValue = request.getParameter(paramName);
			params += "&" + paramName + "=" + paramValue;
		}

		if (!"".equals(params)) {
			params = params.replaceFirst("&", "?");
			url += params;
		}

		return url;
	}

	/**
	 * Logs an instantiation event (e.g. the creation of a recognizer,
	 * synthesizer, or wamiapplication with its relevant parameters)
	 */
	private void logInstantiationEvent(IApplicationController appController,
			String componentType, String className, Map<String, String> params) {
		appController.logEvent(new InstantiationEvent(componentType, className,
				params), System.currentTimeMillis());

	}

	public static String getLocalHostName() {
		String hostname = null;
		try {
			InetAddress addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (UnknownHostException e) {
		}
		return hostname;
	}

}
