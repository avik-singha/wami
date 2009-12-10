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
package edu.mit.csail.sls.wami.synthesis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.mit.csail.sls.wami.util.XmlUtils;

/**
 * This is an *example* implementation of the synthesizer interface that
 * requests audio from a URL. The URL is provided as a "param" to the
 * synthesizer in the config.xml file. The string to be synthesized is placed on
 * the request as "synth_string".
 * 
 * One really easy way to test out a dummy "synthesizer" is to set the URL to
 * point to a .wav. Then, the "synthesizer" will ignore the input text and just
 * play the wav.
 * 
 * A real synthesizer would actually examine the properties of the request and
 * produce audio based on the "synth_string" property. We leave it to you to
 * plug in the synthesizer of your choice.
 * 
 * @author imcgraw
 */
public class URLSynthesizer implements ISynthesizer {
	String urlstr;

	Map<String, String> map = null;

	boolean asXMLPost = false;

	String asUrlParam = null;

	public InputStream synthesize(String input) throws SynthesizerException {
		URL url;

		try {
			InputStream stream = null;

			if (asUrlParam != null) {
				stream = getStreamGivenUrlParam(asUrlParam, urlstr, input);
				return stream;
			}

			url = new URL(urlstr);
			if (url == null) {
				throw new SynthesizerException("URL is null for synthesizer!");
			}

			if (asXMLPost) {
				stream = getStreamViaXMLPost(input, url);
			} else {
				stream = getStreamViaNormalUrlConnection(input, url);
			}

			return stream;
		} catch (MalformedURLException e) {
			throw new SynthesizerException("Bad synthesizer URL: " + urlstr);
		} catch (IOException e) {
			throw new SynthesizerException(
					"Bad audio input stream returned from URL: " + urlstr);
		}
	}

	private InputStream getStreamGivenUrlParam(String asParam, String urlstr,
			String input) throws IOException {
		input = input.replace(" ", "%20");
		URL url = new URL(urlstr + "?" + asParam + "=" + input);

		URLConnection c = url.openConnection();
		c.addRequestProperty("Content-Type", "text/xml;charset=UTF-8");
		c.addRequestProperty("synth_string", input);

		// Place the parameters on the request to the synthesizer
		for (String key : map.keySet()) {
			String value = map.get(key);
			c.addRequestProperty(key, value);
		}
		c.connect();

		return c.getInputStream();
	}

	private InputStream getStreamViaNormalUrlConnection(String input, URL url)
			throws IOException {
		URLConnection c = url.openConnection();
		c.addRequestProperty("Content-Type", "text/xml;charset=UTF-8");
		c.addRequestProperty("synth_string", input);

		// Place the parameters on the request to the synthesizer
		for (String key : map.keySet()) {
			String value = map.get(key);
			System.out.println("Synthe kv pair: " + key + "," + value);
			c.addRequestProperty(key, value);
		}
		c.connect();

		return c.getInputStream();
	}

	private InputStream getStreamViaXMLPost(String input, URL url)
			throws IOException {
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		c.setRequestMethod("POST");
		c.addRequestProperty("Content-Type", "text/xml;charset=UTF-8");
		Document doc = XmlUtils.newXMLDocument();
		Element root = doc.createElement("root");
		doc.appendChild(root);

		map.put("synth_string", input);

		// Place the parameters on the request to the synthesizer
		for (String key : map.keySet()) {
			Element param = doc.createElement("param");
			param.setAttribute("name", key);
			param.setAttribute("value", map.get(key));
			root.appendChild(param);
		}

		c.setDoOutput(true);
		c.setDoInput(true);

		OutputStreamWriter osw = new OutputStreamWriter(c.getOutputStream(),
				"UTF-8");
		String xmlstr = XmlUtils.toXMLString(doc);
		System.out.println(xmlstr);
		osw.write(xmlstr);
		osw.close();

		return c.getInputStream();
	}

	public void setParameters(Map<String, String> map) {
		this.map = map;
		updateParameters();
	}

	public void updateParameters() {
		if (map.get("asParam") != null) {
			asUrlParam = map.get("asParam");
		}
		System.out.println("asParam: " + asUrlParam);

		String urlString = map.get("url");
		if (urlString != null) {
			urlstr = urlString;
		}

		String asXMLString = map.get("asXML");
		if (asXMLString != null) {
			asXMLPost = Boolean.parseBoolean(asXMLString);
		}
	}

	public void destroy() throws SynthesizerException {

	}

	public void setDynamicParameter(String name, String value)
			throws SynthesizerException {
		if (map == null) {
			throw new SynthesizerException(
					"Synthesizer not yet configured, cannot set dynamic parameter!");
		}

		map.put(name, value);
		updateParameters();
	}
}
