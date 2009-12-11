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
package edu.mit.csail.sls.wami.recognition;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.sound.sampled.AudioInputStream;

import edu.mit.csail.sls.wami.recognition.exceptions.RecognizerException;
import edu.mit.csail.sls.wami.recognition.lm.LanguageModel;
import edu.mit.csail.sls.wami.recognition.lm.exceptions.LanguageModelCompilationException;

public interface IRecognizer {

	/**
	 * Called once, just after the constructor, when the recognizer is
	 * initialized
	 * 
	 * @param sc
	 *            Servlet context
	 * @param map
	 *            A map containing the parameter values
	 * @throws RecognizerException
	 */
	void setParameters(ServletContext sc, Map<String, String> map)
			throws RecognizerException;

	/**
	 * Some recognizer parameters may be dynamically configurable and can be
	 * configured here
	 * 
	 * @param name The name of the parameter to set
	 * @param value It's value, represented as a string
	 */
	void setDynamicParameter(String name, String value)
			throws RecognizerException;

	/**
	 * Perform speech recognition on the passed in audio input stream.
	 * 
	 * @param audioIn
	 *            An audio input strema for the recognizer
	 * @throws IOException
	 *             on error reading from audioIn
	 * @throws RecognizerException
	 *             for various recognition errors.
	 */
	public void recognize(AudioInputStream audioIn,
			IRecognitionListener listener) throws RecognizerException,
			IOException;

	/**
	 * Set the language model to be used by the recognizer
	 * 
	 * @param model
	 *            The new language model
	 * @throws RecognizerException
	 *             Of note, throws {@link LanguageModelCompilationException} if
	 *             there is an error compiling the language model.
	 */
	public void setLanguageModel(LanguageModel model)
			throws RecognizerException;

	/**
	 * Destroys this recognizer, once this has been called the recognizer can't
	 * be used again
	 */
	public void destroy() throws RecognizerException;
	
}
