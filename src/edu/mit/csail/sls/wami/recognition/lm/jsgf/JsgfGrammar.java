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
package edu.mit.csail.sls.wami.recognition.lm.jsgf;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import edu.mit.csail.sls.wami.recognition.lm.LanguageModel;

public class JsgfGrammar implements LanguageModel {
	private String grammar;
	private String dictionaryLanguage;

	/**
	 * Reads a JSGF grammar from an {@link InputStream}
	 * 
	 * @param in
	 *            The {@link InputStream} from which to read the grammar
	 */
	public JsgfGrammar(InputStream in, String dictionaryLanguage)
			throws IOException {
		char[] buf = new char[10000];

		Reader r = new InputStreamReader(in);
		StringBuffer sb = new StringBuffer();
		while (true) {
			int nRead = r.read(buf);
			if (nRead == -1) {
				break;
			}
			sb.append(buf, 0, nRead);
		}

		grammar = sb.toString();
		this.dictionaryLanguage = dictionaryLanguage;
	}

	/**
	 * Create a new Jsgf grammar using the grammar contained in the string.
	 * 
	 * @param grammar
	 */
	public JsgfGrammar(String grammar, String dictionaryLanguage) {
		this.grammar = grammar;
		this.dictionaryLanguage = dictionaryLanguage;
	}

	public String getGrammar() {
		return grammar;
	}

	public String getType() {
		return "jsgf";
	}

	public String getDictionaryLanguage() {
		return dictionaryLanguage;
	}
}
