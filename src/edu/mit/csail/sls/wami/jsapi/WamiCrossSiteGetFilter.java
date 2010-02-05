package edu.mit.csail.sls.wami.jsapi;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import edu.mit.csail.sls.wami.WamiConfig;

public class WamiCrossSiteGetFilter implements Filter {

	class CharResponseWrapper extends HttpServletResponseWrapper {
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

		if ("get".equals(request.getParameter("jsxss"))) {
			// System.out.println("XXS GET: " +
			// WamiConfig.reconstructRequestURLandParams(request));
			CharResponseWrapper wrapper = new CharResponseWrapper(
					(HttpServletResponse) res);

			chain.doFilter(req, wrapper);

			doResponseModifiction(request, response, wrapper);
		} else {
			chain.doFilter(req, res);
		}
	}

	private void doResponseModifiction(HttpServletRequest request,
			HttpServletResponse response, HttpServletResponse wrapper)
			throws IOException {

		PrintWriter out = response.getWriter();
		String message = wrapper.toString();

		// note, you must set content type before getting the writer
		response.setContentType("text/javascript; charset=UTF-8");
		message = message.replace("\\", "\\\\").replace("'", "\\'");
		String callback = request.getParameter("callback");
		String command = callback + "('" + message + "');";
		System.out.println("Script: " + command);
		out.print(command);

		response.setContentLength(command.length());
		out.close();
	}

	@Override
	public void init(FilterConfig config) throws ServletException {

	}

}
