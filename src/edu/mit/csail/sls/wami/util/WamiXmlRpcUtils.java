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
package edu.mit.csail.sls.wami.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.TimeZone;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcLocalStreamTransport;
import org.apache.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.apache.xmlrpc.server.XmlRpcLocalStreamServer;

/**
 * Utilities which allow serialization and deserialization into xml appropriate
 * for an xmlrpc server response
 * 
 * @author alexgru
 * 
 */
public class WamiXmlRpcUtils {

	private static final MyXmlRpcLocalStreamServer fakeServer = new MyXmlRpcLocalStreamServer();
	private static final MyXmlRpcLocalStreamTransport fakeTransport = new MyXmlRpcLocalStreamTransport();

	public static Object parseResponse(String xmlStr) {
		return parseResponse(new ByteArrayInputStream(xmlStr.getBytes()));
	}

	public static Object parseResponse(InputStream xmlIn) {
		try {
			return fakeTransport.read(xmlIn);
		} catch (XmlRpcException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Serializes the passed in object to the passed in output stream
	 * 
	 * @param o
	 * @throws XmlRpcException
	 */
	public static void serializeResponse(Object o, OutputStream xmlOut)
			throws XmlRpcException {
		fakeServer.write(o, xmlOut);
	}

	public static String serializeResponse(Object o) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			serializeResponse(o, out);
			return out.toString();
		} catch (XmlRpcException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Simple configuration for serialization
	 * 
	 * @author alexgru
	 * 
	 */
	private static class MyXmlRpcStreamRequestConfig implements
			XmlRpcStreamRequestConfig {
		public String getEncoding() {
			return UTF8_ENCODING;
		}

		public TimeZone getTimeZone() {
			return TimeZone.getDefault();
		}

		public boolean isEnabledForExtensions() {
			return false;
		}

		public boolean isEnabledForExceptions() {
			return false;
		}

		public boolean isGzipCompressing() {
			return false;
		}

		public boolean isGzipRequesting() {
			return false;
		}
	}

	/**
	 * provides access into protected writeResponse method
	 * 
	 * @author alexgru
	 */
	private static class MyXmlRpcLocalStreamServer extends
			XmlRpcLocalStreamServer {
		private final static XmlRpcStreamRequestConfig pConfig = new MyXmlRpcStreamRequestConfig();

		public void write(Object obj, OutputStream out) throws XmlRpcException {
			writeResponse(pConfig, out, obj);
		}
	}

	/**
	 * provides access into protected readResponse method
	 * 
	 * @author alexgru
	 */
	private static class MyXmlRpcLocalStreamTransport extends
			XmlRpcLocalStreamTransport {
		private final static XmlRpcStreamRequestConfig pConfig = new MyXmlRpcStreamRequestConfig();

		public MyXmlRpcLocalStreamTransport() {
			super(new XmlRpcClient(), null);
		}

		public Object read(InputStream xmlIn) throws XmlRpcException {
			return readResponse(pConfig, xmlIn);
		}
	}

	// test main
	public static void main(String[] args) throws XmlRpcException {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("hello", "world");
		map.put("foo", "bar");
		String xml = WamiXmlRpcUtils.serializeResponse(map);
		System.out.println(xml);
		Object obj = WamiXmlRpcUtils.parseResponse(xml);
		System.out.println(obj);
	}

}
