package edu.mit.csail.sls.wami.util;

import java.util.Map;
import javax.servlet.ServletContext;

public interface Instantiable {
	public void setParameters(ServletContext sc, Map<String, String> params);
}
