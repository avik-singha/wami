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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.mit.csail.sls.wami.relay.InitializationException;
import edu.mit.csail.sls.wami.relay.ReachedCapacityException;
import edu.mit.csail.sls.wami.relay.RelayManager;
import edu.mit.csail.sls.wami.relay.WamiRelay;
import edu.mit.csail.sls.wami.util.XmlUtils;
import edu.mit.csail.sls.wami.validation.IValidator;

/**
 * <p>
 * WamiServlet is the HttpServlet that which "controls" the interaction between
 * galaxy and the various GUI clients. There is only one WamiServlet and it
 * follows the general servlet life-cycle conventions. The {@link WamiRelay} is
 * maintained on a per-client basis, and manages the specifics of each
 * connection. This WamiServlet servlet is the gateway that allows a client to
 * perform operations within its relay.
 * </p>
 * <p>
 * Note that we expect the clients to be "polling" where on each poll the relay
 * will sit and block until we have a new message from the relay to return to
 * the client. This means that the polling is not inefficient, however it does
 * mean that we effectively have a connection to each client open all the time.
 * </p>
 * 
 * @author alexgru
 * 
 */
public class WamiServlet extends HttpServlet {

	WamiConfig ac = null;

	@Override
	public void init() throws ServletException {
		ServletContext sc = getServletContext();
		ac = WamiConfig.getConfiguration(sc);
		super.init();
	}

	@Override
	public void destroy() {
		System.out.println("Destroying Servlet");
		ServletContext sc = getServletContext();
		RelayManager manager = (RelayManager) sc.getAttribute("relayManager");

		if (manager != null) {
			manager.close();
		}
	}

	/**
	 * Get a message from the control servlet (via polling)
	 */
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		if (isValidateServletRequest(request)) {
			doValidateServlet(request, response);
			return;
		}

		boolean polling = false;

		WamiRelay relay = WamiServlet.getRelay(request);

		if (relay == null) {
			showError(request, response, "null_relay",
					"The relay to the recognizer was not found");
			return;
		}

		// if no xml is passed in, then we try the request parameters
		String pollingStr = request.getParameter("polling");
		polling = pollingStr != null && !pollingStr.equals("")
				&& Boolean.parseBoolean(pollingStr);

		String m = null;

		try {
			if (polling) {
				// System.out.println("polling");

				// polling happens here:
				m = relay.waitForMessage();

				if (m != null) {
					printResponse(m, request, response);
				} else {
					response.getWriter().close();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * XML messages are posted to the control servlet here.
	 */
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		try {
			Document xmlDoc;

			System.err.println(WamiConfig
					.reconstructRequestURLandParams(request));

			InputStream stream;
			stream = request.getInputStream();
			InputSource source = new InputSource(new InputStreamReader(stream,
					request.getCharacterEncoding()));
			
			xmlDoc = XmlUtils.getBuilder().parse(source);
			Element root = (Element) xmlDoc.getFirstChild();

			if (root == null) {
				return;
			}

			clientUpdateMessage(request, response, root);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void clientUpdateMessage(HttpServletRequest request,
			HttpServletResponse response, Element root) throws IOException {
		WamiRelay relay = WamiServlet.getRelay(request);

		if (relay == null) {
			// Can't really send an error when we reply to a post for x-site
			printResponse("<empty />", request, response);
			return;
		}

		String stoppollingStr = root.getAttribute("stoppolling");
		boolean stopPolling = stoppollingStr != null
				&& !stoppollingStr.equals("")
				&& Boolean.parseBoolean(stoppollingStr);

		if (stopPolling) {
			relay.stopPolling();
		} else {
			String clientUpdateXML = XmlUtils.toXMLString(root);
			System.out.println("Update: " + clientUpdateXML);
			relay.handleClientUpdate(request.getSession(), clientUpdateXML);
			printResponse("<empty />", request, response);
		}
	}

	private void showError(HttpServletRequest request,
			HttpServletResponse response, String type, String error) {
		try {
			printResponse("<reply type='error' error_type='" + type
					+ "' message='" + error + "' />", request, response);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void doValidateServlet(HttpServletRequest request,
			HttpServletResponse response) {
		ServletContext sc = request.getSession().getServletContext();
		WamiConfig config = WamiConfig.getConfiguration(sc);

		List<String> errors = new Vector<String>();

		IValidator validator = config.createValidator(sc);
		List<String> relayErrors = validator.validate(request);
		errors.addAll(relayErrors);
		response.setContentType("text/xml; charset=UTF-8");

		Document doc = XmlUtils.newXMLDocument();
		doc.appendChild(doc.createElement("root"));

		for (String error : errors) {
			Element errorE = doc.createElement("error");
			errorE.setAttribute("message", error);
			doc.getFirstChild().appendChild(errorE);
		}

		try {
			PrintWriter out = response.getWriter();
			String xmlString = XmlUtils.toXMLString(doc);
			out.print(xmlString);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isValidateServletRequest(HttpServletRequest request) {
		return "validate".equals(request.getParameter("operation"));
	}

	/**
	 * Prints the response returned by the relay. By default, it will be encoded
	 * as straight xml, however with ?rtype=js it can be returned as javascript
	 * wrapping the xml
	 */
	private void printResponse(String message, HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		// note, you must set content type before getting the writer
		response.setContentType("text/xml; charset=UTF-8");
		PrintWriter out = response.getWriter();
		out.print(message);
		out.close();
	}

	private static WamiRelay newRelay(ServletContext sc)
			throws InitializationException {
		WamiConfig ac = WamiConfig.getConfiguration(sc);
		String className = ac.getRelayClass();
		WamiRelay relay = null;

		if (className == null) {
			throw new InitializationException("No relay class name specified.");
		}

		try {
			System.out.println("Creating new WamiRelay subclass: " + className);
			relay = (WamiRelay) Class.forName(className).newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return relay;
	}

	/**
	 * The relay is lazily created for each session.
	 * 
	 * @param request
	 * @return
	 */
	public static WamiRelay getRelay(HttpServletRequest request) {
		String wsessionid = request.getParameter("wsessionid");
		return getRelay(request, wsessionid);
	}

	public static WamiRelay getRelay(HttpServletRequest request,
			String wsessionid) {
		if (wsessionid == null || "".equals(wsessionid)) {
			wsessionid = request.getSession().getId();
		}

		HttpSession session = request.getSession();

		WamiRelay relay = null;

		RelayManager manager = RelayManager.getManager(session);
		synchronized (manager) {
			relay = manager.getRelay(wsessionid);

			if (relay == null) {
				System.out.println("Relay is null, attempting to initialize");
				try {
					System.out.println("INITIALIZING WAMI RELAY");
					relay = initializeRelay(request, wsessionid);
				} catch (InitializationException e) {
					if (e.getRelay() != null) {
						String message = "Error initializing relay!  Removing the uninitialized relay!";
						System.out.println(message);
						manager.remove(e.getRelay());
					}
					e.printStackTrace();
					return null;
				}
			}
		}

		return manager.getRelay(wsessionid);
	}

	public static String setRelay(HttpServletRequest request, WamiRelay relay,
			String wsessionid) throws ReachedCapacityException {
		if (wsessionid == null) {
			wsessionid = request.getSession().getId();
		}

		System.out.println("Placing session WAMI session: " + wsessionid);

		RelayManager manager = RelayManager.getManager(request.getSession());
		manager.addRelay(relay, wsessionid);
		return wsessionid;
	}

	public static WamiRelay initializeRelay(HttpServletRequest request,
			String wsessionid) throws InitializationException {
		System.out.println("**********************************************");
		System.out.println("Initializing Relay " + wsessionid);
		System.out.println("**********************************************");
		HttpSession session = request.getSession();
		WamiRelay relay;

		relay = newRelay(session.getServletContext());
		wsessionid = setRelay(request, relay, wsessionid);

		relay.initialize(request, wsessionid);
		return relay;
	}

}