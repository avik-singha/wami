package edu.mit.csail.sls.wami.util;

import javax.servlet.http.HttpServletRequest;

public class Parameter {
	public static String get(HttpServletRequest request, String param,
			String def) {
		String result = request.getParameter(param);
		return result == null ? def : result;
	}

	public static boolean get(HttpServletRequest request, String param,
			boolean def) {
		return get(request.getParameter(param), def);
	}

	public static boolean get(String param, boolean def) {
		if (param == null) {
			return def;
		}

		return Boolean.parseBoolean(param);
	}

	public static int get(String param, int def) {
		if (param == null) {
			return def;
		}

		return Integer.parseInt(param);
	}
}
