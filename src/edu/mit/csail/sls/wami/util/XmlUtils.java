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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Basic utils for XML manipulation.
 */
public class XmlUtils {
	// //////////////////////////
	// // XML STUFF ////////
	// //////////////////////////
	private static final DocumentBuilderFactory factory = DocumentBuilderFactory
			.newInstance();

	private static final TransformerFactory tFactory = TransformerFactory
			.newInstance();

	public static Document toXMLDocument(String xmlString) {
		return toXMLDocument(new InputSource(new StringReader(xmlString)));
	}

	public static Document toXMLDocument(InputStream in) {
		return toXMLDocument(new InputSource(in));
	}

	public static Document toXMLDocument(InputSource source) {
		Document xmlDoc = null;

		try {
			xmlDoc = getBuilder().parse(source);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return xmlDoc;
	}

	public static DocumentBuilder getBuilder() {
		try {
			return factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			return null;
		}
	}

	public static Document newXMLDocument() {
		Document document = getBuilder().newDocument();
		return document;
	}

	/**
	 * Convert an XML node to a string. Node can be a document or an element.
	 * 
	 * @param node
	 * @return
	 */
	public static String toXMLString(Node node) {
		Transformer transformer = null;
		try {
			transformer = tFactory.newTransformer();
			// System.err.format("Using transformer: %s%n", transformer);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		}
		DOMSource source = new DOMSource(node);
		StringWriter xmlWriter = new StringWriter();
		StreamResult result = new StreamResult(xmlWriter);
		try {
			transformer.transform(source, result);
		} catch (TransformerException e) {
			e.printStackTrace();
		}
		return xmlWriter.toString();
	}

	public static class ValidationErrorHandler implements ErrorHandler {
		public void error(SAXParseException e) throws SAXException {
			System.out.println(e);
		}

		public void fatalError(SAXParseException e) throws SAXException {
			System.out.println(e);
		}

		public void warning(SAXParseException e) throws SAXException {
			System.out.println(e);
		}
	}

}
