/* -*- Java -*-
 *
 * Copyright (c) 2008
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

package edu.mit.csail.sls.wami.applet.sound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.TreeMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import edu.mit.csail.sls.wami.applet.sound.ByteFifo;

public class ResampleInputStream extends InputStream {

    private static final int APPROXIMATION = 100;
    private static final int FILTER_SCALE = 25;

    private AudioFormat targetFormat;
    private AudioFormat sourceFormat;

    private AudioInputStream sourceStream;

    private int L = 0; // interpolation factor
    private int M = 0; // decimation factor
    private float h[] = null; // resampling filter
    private short z[] = null; // delay line
    private int pZ = 0;
    private int phase = 0;

    private ByteFifo sourceByteFifo;
    private ByteFifo targetByteFifo;

    // For source byte to short conversion.  Byte order set in initialize().
    //
    private byte[] sourceBytes = new byte[1024];
    private ByteBuffer sourceByteBuffer = ByteBuffer.wrap(sourceBytes);
    private ShortBuffer sourceShortBuffer = null;

    // For target short to byte conversion.  Byte order set in initialize().
    //
    private byte[] targetBytes = new byte[2];
    private ByteBuffer targetByteBuffer = ByteBuffer.wrap(targetBytes);
    private ShortBuffer targetShortBuffer = null;

    // For caching interpolation filter.
    private static Map<Integer,float[]> filterCache = new TreeMap<Integer,float[]>();
    
    int gcd(int x, int y){
	int r;
	while((r=x%y)>0){
	    x = y;
	    y = r;
	}
	return y;
    }

    public ResampleInputStream(AudioFormat targetFormat,
			       AudioInputStream sourceStream) {
	this.targetFormat = targetFormat;
	this.sourceFormat = sourceStream.getFormat();
	this.sourceStream = sourceStream;

	int targetRate = Math.round(targetFormat.getSampleRate()/APPROXIMATION)*APPROXIMATION;
	int sourceRate = Math.round(sourceFormat.getSampleRate()/APPROXIMATION)*APPROXIMATION;
	int gcd = gcd(targetRate, sourceRate);

	this.L = targetRate / gcd;
	this.M = sourceRate / gcd;

	sourceByteFifo = new ByteFifo(sourceRate/5);
	targetByteFifo = new ByteFifo(targetRate/5);
	initialize();

    }

    private void initialize() {
	int f = Math.max(L, M);
	int n = FILTER_SCALE*f;
	int mod = n % L;
	if (mod != 0)
	    n += L - mod;
	z = new short[n / L];

	boolean cached = true;
	Integer cacheKey = new Integer(f);
	h = filterCache.get(cacheKey);
	if (h == null) {
	    h = InterpolationFilter.design(f, n, InterpolationFilter.HAMMING);
	    filterCache.put(cacheKey, h);
	    cached = false;
	}
	System.err.println("ResampleInputStream:" +
			   " L=" + L +
			   " M=" + M +
			   " taps=" + h.length +
			   " perPhase=" + z.length +
			   " cached=" + cached);

	// Cause the delay buffer z to be fully loaded before first output.
	//
	phase = z.length * L;

	// Finish initializing byte-swapping buffers now that we know the byte orders.
	//
	sourceByteBuffer.order(byteOrder(sourceFormat));
	sourceShortBuffer = sourceByteBuffer.asShortBuffer();
	targetByteBuffer.order(byteOrder(targetFormat));
	targetShortBuffer = targetByteBuffer.asShortBuffer();
    }

    private ByteOrder byteOrder(AudioFormat format) {
	return format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    }

    @Override
	public int available() throws IOException {
	convert(false);
	return targetByteFifo.size();
    }

    @Override
	public int read() throws IOException {
	throw new Error("not supported");
    }

    // This must read at least one byte, blocking until something is available
    @Override
	public int read(byte[] b, int offset, int length) throws IOException {
	if (targetByteFifo.size() == 0){
	    convert(true);
	}
	if (targetByteFifo.size() < length)
	    convert(false);
	int m = Math.min(length, targetByteFifo.size());
	if (m > 0) {
	    targetByteFifo.pop(b, offset, m);
	}
	return m;
    }

    @Override
	public void close() {
	h = null;
	z = null;
	sourceByteFifo = null;
	targetByteFifo = null;
    }

    // Convert as many samples as possible without blocking.
    //
    private void convert(boolean wait) throws IOException {
     
	// Return if not operational (e.g., bad sample rates during initialization).
	//
	if (h == null)
	    return;
	
	if (wait){
	    int nRead = sourceStream.read(sourceBytes, 0, sourceBytes.length);
	    if (nRead > 0){
		sourceByteFifo.push(sourceBytes, 0, nRead);
	    }
	}

	// Read some source bytes without blocking.
	if (sourceStream.available() > 0){
	    while(true){
		int nRead = sourceStream.read(sourceBytes);
		if (nRead <= 0)
		    break;
		sourceByteFifo.push(sourceBytes, 0, nRead);
	    }
	}

	// Perform conversion from sourceByteFifo to targetByteFifo.
	//
	int thisWhack = sourceByteFifo.size();
	if (thisWhack > 1024){
	    //System.err.format("Backlog: %d%n", thisWhack-1024);
	    thisWhack = 1024;
	}
	while (thisWhack > 1) {

	    // Shift source samples into delay buffer z.
	    //
	    while (phase >= L) {
		phase -= L;
		sourceByteFifo.pop(sourceBytes, 0, 2);
		thisWhack-=2;
		short sourceSample = sourceShortBuffer.get(0);
		z[pZ++] = sourceSample;
		if (pZ == z.length)
		    pZ = 0;
		if (thisWhack < 2) {
		    break;
		}
	    }

	    // Possibly generate output samples.
	    //
	    while (phase < L) {
		int pH = L - 1 - phase;
		phase += M;
		float sum = 0;
		for (int t = pZ; t < z.length; t++) {
		    sum += h[pH]*z[t];
		    pH += L;
		}
		for (int t = 0; t < pZ; t++) {
		    sum += h[pH]*z[t];
		    pH += L;
		}
		short targetSample = (short) Math.round(L*sum);
		targetShortBuffer.put(0, targetSample);
		targetByteFifo.push(targetBytes);
	    }
	}
	
    }


}

