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

import java.util.List;

import edu.mit.csail.sls.wami.log.ILoggable;

/**
 * Interface to recognition results. For logging, implementors must implement
 * toXml() and fromXml(). They must also provide a no-argument constructor to be
 * used to instantiate the class via reflection
 * 
 * Implementors MUST have an empty constructor
 * 
 * @author alexgru
 * 
 */
public interface IRecognitionResult extends ILoggable {

	/**
	 * Returns the hypotheses in this result
	 */
	public List<String> getHyps();

	/**
	 * Returns true if this is an incremental result, false otherwise
	 */
	public boolean isIncremental();

	/**
	 * If an error occurred, returns a string representation of that error. If no
	 * error occurred, returns null.
	 */
	public String getError();

}