package edu.mit.csail.sls.wami.util;

import java.util.HashMap;

public class ContentType {
	String major;
	String minor;
	HashMap<String, String> parameters = new HashMap<String, String>();

	public static ContentType parse(String contentType) {
		return new ContentType().initialize(contentType);
	}

	protected ContentType initialize(String contentType) {
		if (contentType == null) {
			return this;
		}
		contentType = contentType.toUpperCase();
		int length = contentType.length();
		StringBuffer buf = new StringBuffer();
		char c;
		int i = 0;
		while (i < length) {
			c = contentType.charAt(i++);
			if (c == '/' || c == ';')
				break;
			buf.append(c);
		}
		major = buf.toString().trim();
		buf.delete(0, buf.length());

		while (i < length) {
			c = contentType.charAt(i++);
			if (c == ';')
				break;
			buf.append(c);
		}
		minor = buf.toString().trim();
		buf.delete(0, buf.length());

		while (true) {
			String key;
			String value;

			while (i < length) {
				c = contentType.charAt(i++);
				if (c == '=')
					break;
				buf.append(c);
			}
			key = buf.toString().trim();
			buf.delete(0, buf.length());

			// Values can be quoted (see RFC 2045 sec. 5.1) but for now
			// we just handle what we need for audio.
			while (i < length) {
				c = contentType.charAt(i++);
				if (c == ';')
					break;
				buf.append(c);
			}
			value = buf.toString().trim();
			buf.delete(0, buf.length());
			if (key.length() == 0)
				break;

			parameters.put(key, value);
		}
		return this;
	}

	public String getMajor() {
		return major;
	}

	public String getMinor() {
		return minor;
	}

	public String getParameter(String key) {
		return parameters.get(key.toUpperCase());
	}

	public int getIntParameter(String key, int def) {
		String value = getParameter(key);
		return value == null ? def : Integer.parseInt(value);
	}

	public boolean getBooleanParameter(String key, boolean def) {
		String value = getParameter(key);
		return value == null ? def : Boolean.parseBoolean(value);
	}

}
