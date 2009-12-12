package edu.mit.csail.sls.wami.validation;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ValidationUtils {

	public static String stackTraceToString(Throwable e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String stacktrace = sw.toString();
		return stacktrace;
	}

}
