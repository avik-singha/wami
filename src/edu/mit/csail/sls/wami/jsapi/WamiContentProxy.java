package edu.mit.csail.sls.wami.jsapi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.WamiConfig;
import edu.mit.csail.sls.wami.util.Parameter;
import edu.mit.csail.sls.wami.util.ServletUtils;
import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * This is not standard, but hopefully it will make it easier to distribute
 * WAMI. This servlet proxies resources which would normally be found under
 * WebContent from locations that would be otherwise inaccessible via a URL.
 * 
 * The audio applet, for instance, is proxied from
 * /WEB-INF/lib/wami_audio_applet.jar. The javascript necessary for the JSAPI is
 * also accessible through this servlet.
 * 
 * @author imcgraw
 * 
 */
public class WamiContentProxy extends HttpServlet {

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException {
		response.setCharacterEncoding("UTF-8");

		response.setContentType("text/html; char-set:UTF-8");
		String requestURL = request.getRequestURL().toString();
		String resource = requestURL.substring(requestURL.indexOf(request
				.getContextPath())
				+ request.getContextPath().length());
		resource = resource.replace("//", "/"); // Replace any oddities

		try {
			if (Parameter.get(request, "debug", false)) {
				response.setContentType("text/html");
				response.getWriter().write(
						"The requested resource is: " + resource);
			} else {
				InputStream stream = getRequestedResource(request, resource);
				if (stream == null) {
					throw new RuntimeException(
							"Could not find requested resource: " + resource);
				}
				
				ServletUtils.sendStream(stream, response.getOutputStream());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected InputStream getRequestedResource(HttpServletRequest request,
			String resource) {
		if ("/wami.js".equals(resource)) {
			return new ByteArrayInputStream(handleWamiJSAPIRequest(request)
					.getBytes());
		} else if ("/content/wami_audio_applet.jar".equals(resource)) {
			return WamiConfig.getResourceAsStream(request.getSession()
					.getServletContext(), "/WEB-INF/lib/wami_audio_applet.jar");
		} else if ("/wami.xml".equals(resource)
				|| "/content/wami.xml".equals(resource)) {
			return new ByteArrayInputStream(getUrls(request).getBytes());
		} else if (resource.startsWith("/content")) {
			String result = "";

			if (resource.endsWith(".js")) {
				result += getUtils(request);
			}

			resource = resource.replaceFirst("/content/", "");
			result += getContentResourceAsString(resource);

			return new ByteArrayInputStream(result.getBytes());
		}

		return null;
	}

	private String getUrls(HttpServletRequest request) {
		WamiConfig wc = WamiConfig.getConfiguration(request.getSession()
				.getServletContext());

		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);

		String wsessionid = createWamiSessionID(request);
		Element recordE = doc.createElement("record");
		recordE.setAttribute("url", wc.recordServletURL(request, wsessionid));
		root.appendChild(recordE);

		Element playE = doc.createElement("play");
		playE.setAttribute("url", wc.audioServletURL(request, wsessionid));
		root.appendChild(playE);

		Element controlE = doc.createElement("control");
		controlE.setAttribute("url", wc.controlServletURL(request, wsessionid));
		root.appendChild(controlE);

		return XmlUtils.toXMLString(doc);
	}

	public String getContentResourceAsString(String resource) {
		return "\n"
				+ ServletUtils.convertStreamToString(WamiContentProxy.class
						.getClassLoader().getResourceAsStream(
								"edu/mit/csail/sls/wami/content/" + resource))
				+ "\n";
	}

	public String createWamiSessionID(HttpServletRequest request) {
		return ServletUtils.getClientAddress(request) + ":"
				+ System.currentTimeMillis();
	}

	public String handleWamiJSAPIRequest(HttpServletRequest request) {
		HttpSession session = request.getSession();

		Enumeration names = request.getHeaderNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			String value = request.getHeader(name);
			System.out.println("HEADER: " + name + " " + value);
		}

		// no browser test
		session.setAttribute("passedBrowserTest", new Boolean(true));

		String jsapi = "";
		String wsessionid = createWamiSessionID(request);

		String serveraddress = WamiConfig
				.reconstructRequestURLandParams(request);
		request.getSession().setAttribute("serverAddress", serveraddress);

		try {
			jsapi += getJSAPI(request, wsessionid);
		} catch (IOException e) {
			jsapi += getAlert("Error setting up WAMI javascript.");
		}

		return jsapi;
	}

	protected String getJSAPI(HttpServletRequest request, String wsessionid)
			throws IOException {
		String result = getUtils(request);

		result += getConfigurationJSON(request, wsessionid);
		result += getContentResourceAsString("app.js");

		return result;
	}

	protected String getUtils(HttpServletRequest request) {
		String baseURL = WamiConfig.getBaseURL(request);

		String result = "";
		result += getContentResourceAsString("utils.js");

		result += "\n\n";
		result += "Wami.getBaseURL = function () { return '" + baseURL + "'}";
		result += "\n\n";

		return result;
	}

	protected String getAlert(String message) {
		return "alert('" + message + "');\n\n";
	}

	private Map<String, String> getAppletParams(HttpServletRequest request,
			String wsessionid) {
		HttpSession session = request.getSession();
		WamiConfig wc = WamiConfig.getConfiguration(getServletContext());
		String baseUrl = WamiConfig.getBaseURL(request);

		String allowStopPlayingStr = request.getParameter("allowStopPlaying");
		boolean allowStopPlaying = allowStopPlayingStr == null
				|| "true".equalsIgnoreCase(allowStopPlayingStr);

		// todo: this should be done via WamiConfig, but I think it returns
		// relative paths
		String appletArchives = baseUrl + "/content/wami_audio_applet.jar";

		Map<String, String> params = new HashMap<String, String>();

		params.put("CODE", wc.getAudioAppletClass(request));
		params.put("ARCHIVE", appletArchives);
		params.put("NAME", "AudioApplet");

		params.put("type", "application/x-java-applet;version=1.5");
		params.put("scriptable", "true");
		params.put("mayscript", "true");
		params.put("location", (String) session
				.getAttribute("hubLocationString"));
		params.put("vision", "false");
		params.put("layout", "stacked");
		params.put("httpOnly", "true");
		params.put("recordUrl", wc.recordServletURL(request, wsessionid));
		params.put("recordAudioFormat", wc.getRecordAudioFormat());
		params.put("recordSampleRate", Integer.toString(wc
				.getRecordSampleRate()));
		params.put("recordIsLittleEndian", Boolean.toString(wc
				.getRecordIsLittleEndian()));
		params.put("greenOnEnableInput", Boolean.toString(wc
				.getGreenOnEnableInput(request)));
		params.put("allowStopPlaying", Boolean.toString(allowStopPlaying));
		params.put("hideButton", Boolean.toString(wc
				.getHideAudioButton(request)));
		params.put("playRecordTone", Boolean.toString(wc
				.getPlayRecordTone(request)));
		params.put("useSpeechDetector", Boolean.toString(wc
				.getUseSpeechDetector(request)));
		params.put("playUrl", wc.audioServletURL(request, wsessionid));

		return params;
	}

	private String getConfigurationJSON(HttpServletRequest request,
			String wsessionid) throws IOException {
		WamiConfig wc = WamiConfig.getConfiguration(getServletContext());

		String result = "var _wamiParams = {\n";
		result += "\t\"wsessionid\":\"" + wsessionid + "\",\n";
		result += "\t\"controlUrl\":\""
				+ wc.controlServletURL(request, wsessionid) + "\",\n";
		result += "\t\"playUrl\":\"" + wc.audioServletURL(request, wsessionid)
				+ "\",\n";
		result += "\t\"recordUrl\":\""
				+ wc.recordServletURL(request, wsessionid) + "\",\n";
		result += getAppletJSON(request, wsessionid);
		result += "}\n\n";

		// System.out.println("JSON FOR CONFIGURATION: \n" + result);
		return result;
	}

	private String getAppletJSON(HttpServletRequest request, String wsessionid) {
		WamiConfig wc = WamiConfig.getConfiguration(getServletContext());
		Map<String, String> appletParams = getAppletParams(request, wsessionid);
		String result = "";
		// TODO: add back in playurl and recordurl for iphone
		result += "\t\"applet\" : {\n";
		result += "\t\t\"code\" : \"" + appletParams.get("CODE") + "\",\n";
		result += "\t\t\"archive\" : \"" + appletParams.get("ARCHIVE")
				+ "\",\n";
		result += "\t\t\"name\" : \"" + appletParams.get("NAME") + "\",\n";
		result += "\t\t\"width\" : \""
				+ Integer.toString(wc.getAppletWidth(request)) + "\",\n";
		result += "\t\t\"height\" : \""
				+ Integer.toString(wc.getAppletHeight(request)) + "\",\n";
		result += "\t\t\"params\" : ";
		result += getParamsJSON(appletParams);
		result += "\t}\n";

		return result;
	}

	private String getParamsJSON(Map<String, String> params) {
		String result = "[\n";

		Object[] paramNames = params.keySet().toArray();

		for (int i = 0; i < paramNames.length; i++) {
			String name = (String) paramNames[i];
			String value = params.get(name);

			result += "\t\t\t{ \"name\" : \"" + name + "\" , \"value\" : \""
					+ value + "\" }";

			if (i < paramNames.length - 1) {
				result += ",\n";
			}
		}
		return result += "]\n";
	}

}
