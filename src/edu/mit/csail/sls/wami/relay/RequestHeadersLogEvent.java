package edu.mit.csail.sls.wami.relay;

import java.util.Enumeration;
import java.util.LinkedHashMap;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.ILoggable;
import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * logs the headers of a http request header
 */
public class RequestHeadersLogEvent implements ILoggable {
	private LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();

	/** required empty constructor */
	public RequestHeadersLogEvent() {
	}

	/**
	 * Takes a request and logs all the headers
	 * 
	 * @param request
	 * @param headers
	 */
	public RequestHeadersLogEvent(HttpServletRequest request) {
		Enumeration names = request.getHeaderNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			String value = request.getHeader(name);
			headers.put(name, value);
		}
	}

	public void fromLogEvent(String logStr, String eventType) {
		Document xmlDoc = XmlUtils.toXMLDocument(logStr);
		NodeList headerNodes = xmlDoc.getElementsByTagName("header");
		for (int i = 0; i < headerNodes.getLength(); i++) {
			Element header = (Element) headerNodes.item(i);
			String name = header.getAttribute("name");
			String value = header.getAttribute("value");
			headers.put(name, value);
		}
	}

	public String getEventType() {
		return IEventLogger.RequestHeaders;
	}

	public String toLogEvent() {
		Document xmlDoc = XmlUtils.newXMLDocument();
		Element root = xmlDoc.createElement("headers");
		xmlDoc.appendChild(root);

		for (String name : headers.keySet()) {
			Element node = xmlDoc.createElement("header");
			node.setAttribute("name", name);
			node.setAttribute("value", headers.get(name));
			root.appendChild(node);
		}

		return XmlUtils.toXMLString(xmlDoc);
	}

}
