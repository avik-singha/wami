package edu.mit.csail.sls.wami.jsapi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

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
import javax.servlet.http.HttpSession;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.WamiServlet;
import edu.mit.csail.sls.wami.relay.WamiRelay;
import edu.mit.csail.sls.wami.util.XmlUtils;

public class WamiCrossSitePostFilter implements Filter {
	public static final String JAVASCRIPT_POST_ID = "WAMI_JAVASCRIPT_POST_ID_ATTRIBUTE_NAME";
	String desiredEncoding = "UTF-8";
	String defaultEncoding = "ISO8859_1";

	class CustomRequestWrapper extends HttpServletRequestWrapper {

		private CustomServerInputStream in;

		public CustomRequestWrapper(HttpServletRequest request, String str)
				throws IOException {
			super(request);
			in = new CustomServerInputStream(str);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return in;
		}

		@Override
		public String getCharacterEncoding() {
			return desiredEncoding;
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

		public CustomServerInputStream(String str) throws IOException {
			super();
			in = new ByteArrayInputStream(str.getBytes(desiredEncoding));
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

	@Override
	public void destroy() {

	}

	public void printToFile(String str) {
		try {
			FileOutputStream fos = new FileOutputStream("/scratch/test.html");

			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(fos,
					"UTF8"));

			out.write(str);
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		if ("post".equals(request.getParameter("jsxss"))) {
			HttpServletRequest wrapper = doRequestModification(request,
					response);

			if (wrapper == null) {
				response.sendRedirect("about:blank");
				return;
			}

			chain.doFilter(wrapper, res);

			sendMessageProcessedConfirmation(request);
		} else {
			chain.doFilter(req, res);
		}
	}

	private HttpServletRequest doRequestModification(
			HttpServletRequest request, HttpServletResponse response) {

		HttpSession session = request.getSession();

		String lastJavascriptPostID = (String) session
				.getAttribute(JAVASCRIPT_POST_ID);
		String postID = request.getParameter("postID");

		// System.out.println("XSS POST: "
		// + WamiConfig.reconstructRequestURLandParams(request));
		// System.out.println("POST ID: " + postID);

		if (postID.equals(lastJavascriptPostID)) {
			// Ignore posting to duplicate form (happens on refresh)
			// This is the "Post Redirect Get" pattern if you want to Google
			// it. This solves the FF3 refresh bug. But not the back bug.
			return null;
		}

		session.setAttribute(JAVASCRIPT_POST_ID, postID);

		if (request.getParameter("wamiMessage") != null) {
			// This is a hack:
			// http://globalizer.wordpress.com/category/web-applications/
			// All the fixes I've tried have failed.
			String message;
			try {
				String encoding = request.getCharacterEncoding();
				if (encoding == null) {
					encoding = defaultEncoding;
				}
				System.out.println("Converting from: " + encoding + " to "
						+ desiredEncoding);
				message = new String(request.getParameter("wamiMessage")
						.getBytes(encoding), desiredEncoding);
				printToFile(message);
				request = new CustomRequestWrapper(request, message);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return request;
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
