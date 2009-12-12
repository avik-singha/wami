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

// FIFO implemented with a circular buffer

package edu.mit.csail.sls.wami.applet.sound;

public class ByteFifo {
    private byte[] rep;
    private int p = 0;
    private int size = 0;

    public ByteFifo() {
	this.rep = new byte[8];
    }

    public ByteFifo(int capacity) {
	this.rep = new byte[capacity];
    }

    public void push(byte x) {
	grow(size + 1);
	++size;
	rep[p++] = x;
	if (p == rep.length)
	    p = 0;
    }

    public void push(byte[] x) {
	push(x, 0, x.length);
    }

    public void push(byte[] x, int offset, int length) {
	grow(size + length);
	size += length;
	if (p + length > rep.length) {
	    int n = rep.length - p;
	    System.arraycopy(x, offset, rep, p, n);
	    System.arraycopy(x, offset + n, rep, 0, length - n);
	} else {
	    System.arraycopy(x, offset, rep, p, length);
	}
	p += length;
	if (p >= rep.length)
	    p -= rep.length;
    }

    public byte pop() {
	if (size <= 0)
	    throw new Error("empty");
	int q = p - size;
	if (q < 0)
	    q += rep.length;
	byte x = rep[q];
	--size;
	return x;
    }

    public void pop(byte[] x) {
	pop(x, 0, x.length);
    }

    public void pop(byte[] x, int offset, int length) {
	if (length <= 0)
	    return;
	int q = p - size;
	if (q < 0) {
	    if (length < -q) {
		System.arraycopy(rep, rep.length + q, x, offset, length);
	    } else {
		System.arraycopy(rep, rep.length + q, x, offset, -q);
		System.arraycopy(rep, 0, x, offset - q, length + q);
	    }
	} else {
	    System.arraycopy(rep, q, x, offset, length);
	}
	size -= length;
    }

    public int size() {
	return this.size;
    }

    public int capacity(){
	return rep.length;
    }

    public int bytesRemaining(){
	return rep.length-this.size;
    }

    private void grow(int n) {
	int capacity = rep.length;
	while (capacity < n) {
	    capacity *= 2;
	}
	if (capacity > rep.length) {
	    //System.err.format("Capacity: %d/%d%n", n, capacity);
	    byte[] newRep = new byte[capacity];
	    int q = p - size;
	    if (q < 0) {
		q += rep.length;
		System.arraycopy(rep, q, newRep, 0, rep.length - q);
		System.arraycopy(rep, 0, newRep, rep.length - q, p);
	    } else {
		System.arraycopy(rep, q, newRep, 0, size);
	    }
	    p = size;
	    rep = newRep;
	}
    }

}
