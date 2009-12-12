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
package edu.mit.csail.sls.wami.relay;

import java.util.Map;

import edu.mit.csail.sls.wami.log.IEventLogger;
import edu.mit.csail.sls.wami.log.ILoggable;
import edu.mit.csail.sls.wami.util.WamiXmlRpcUtils;

/**
 * For logging the creation of instantiatableT items from the config.xml
 * 
 * @author alexgru
 * 
 */
public class InstantiationEvent implements ILoggable {
	private String componentType;
	private String className;
	private Map<String, String> params;

	public String getComponentType() {
		return componentType;
	}

	public String getClassName() {
		return className;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public InstantiationEvent(String componentType, String className,
			Map<String, String> params) {
		this.componentType = componentType;
		this.className = className;
		this.params = params;
	}

	/**
	 * Empty constructor for reflection
	 */
	public InstantiationEvent() {
	}

	public String getEventType() {
		return IEventLogger.Instantiation;
	}

	public void fromLogEvent(String logStr, String eventType) {
		Object[] items = (Object[]) WamiXmlRpcUtils.parseResponse(logStr);
		componentType = (String) items[0];
		className = (String) items[1];
		params = (Map<String, String>) items[2];
	}

	public String toLogEvent() {
		Object[] items = { componentType, className, params };
		return WamiXmlRpcUtils.serializeResponse(items);
	}
	
	@Override
	public String toString() {
		return toLogEvent();
	}
}
