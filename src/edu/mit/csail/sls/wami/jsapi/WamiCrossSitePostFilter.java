package edu.mit.csail.sls.wami.jsapi;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.WamiConfig;
import edu.mit.csail.sls.wami.WamiServlet;
import edu.mit.csail.sls.wami.relay.WamiRelay;
import edu.mit.csail.sls.wami.util.XmlUtils;

public class WamiCrossSitePostFilter implements Filter {
	public static final String JAVASCRIPT_POST_ID = "WAMI_JAVASCRIPT_POST_ID_ATTRIBUTE_NAME";

	class CustomRequestWrapper extends HttpServletRequestWrapper {

		private CustomServerInputStream in;

		public CustomRequestWrapper(HttpServletRequest request)
				throws IOException {
			super(request);
			InputStream is = request.getInputStream();
			int ch;
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			while ((ch = is.read()) != -1) {
				buffer.write((byte) ch);
			}
			in = new CustomServerInputStream(buffer);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return in;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			final String enc = getCharacterEncoding();
			final InputStream istream = getInputStream();
			final Reader r = new InputStreamReader(istream, enc);
			return new BufferedReader(r);
		}
	}

	class CustomServerInputStream extends ServletInputStream {
		private InputStream in;

		public CustomServerInputStream(ByteArrayOutputStream baos)
				throws IOException {
			super();
			in = new ByteArrayInputStream(baos.toByteArray());
		}

		@Override
		public int read() throws IOException {
			return in.read();
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}

	private class CharResponseWrapper extends HttpServletResponseWrapper {
		private CharArrayWriter output;

		@Override
		public String toString() {
			return output.toString();
		}

		public CharResponseWrapper(HttpServletResponse response) {
			super(response);
			output = new CharArrayWriter();
		}

		@Override
		public PrintWriter getWriter() {
			return new PrintWriter(output);
		}
	}

	@Override
	public void destroy() {

	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		if ("post".equals(request.getParameter("jsxss"))) {
			boolean success = doRequestModification(request, response);

			if (!success) {
				response.sendRedirect("about:blank");
				return;
			}

			chain.doFilter(req, res);

			sendMessageProcessedConfirmation(request);
		} else {
			chain.doFilter(req, res);
		}
	}

	private boolean doRequestModification(HttpServletRequest request,
			HttpServletResponse response) {

		HttpSession session = request.getSession();

		String lastJavascriptPostID = (String) session
				.getAttribute(JAVASCRIPT_POST_ID);
		String postID = request.getParameter("postID");

		// System.out.println("XSS POST: "
		// 		+ WamiConfig.reconstructRequestURLandParams(request));
		// System.out.println("POST ID: " + postID);

		if (postID.equals(lastJavascriptPostID)) {
			// Ignore posting to duplicate form (happens on refresh)
			// This is the "Post Redirect Get" pattern if you want to Google
			// it. This solves the FF3 refresh bug.
			return false;
		}

		session.setAttribute(JAVASCRIPT_POST_ID, postID);

		return true;
	}

	private void sendMessageProcessedConfirmation(HttpServletRequest request) {
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("reply");
		root.setAttribute("type", "update_processed");
		root.setAttribute("postID", request.getParameter("postID"));
		doc.appendChild(root);
		WamiRelay relay = WamiServlet.getRelay(request);
		relay.sendMessage(doc);
	}

	@Override
	public void init(FilterConfig config) throws ServletException {

	}

}
