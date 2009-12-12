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

import java.io.InputStream;
import java.util.Map;

public interface ISynthesizer {

	void setParameters(Map<String, String> map);

	/**
	 * Some synthesizer parameters may be dynamically configurable and can be
	 * configured here
	 * 
	 * @param name
	 *            The name of the parameter to set
	 * @param value
	 *            It's value, represented as a string
	 */
	void setDynamicParameter(String name, String value)
			throws SynthesizerException;

	/**
	 * Synthesize an utterance and return an input stream representing that
	 * utterance. Appropriate audio header information should appear in the
	 * stream
	 * 
	 * @param ttsString
	 *            The string to synthesize
	 * @throws SynthesizerException
	 */
	InputStream synthesize(String ttsString) throws SynthesizerException;

	/**
	 * Destroys this synthesizer. After this call, it is improper to use the
	 * synthesizer again.
	 */
	void destroy() throws SynthesizerException;
}
