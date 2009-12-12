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

class InterpolationFilter {

    public static final int RECTANGULAR = 1;
    public static final int HANNING     = 2;
    public static final int HAMMING     = 3;
    public static final int BLACKMAN    = 4;

    public static float[] design(float f, int n, int windowType) {
	f = 1/f;
	float[] h = new float[n];
	double c = 0.5*(n - 1);
	for (int i = 0; i < n; ++i) {
	    h[i] = (float) sinc(f*(i - c));
	}
	window(h, windowType);
	normalize(h);
	return h;
    }

    private static double sinc(double x) {
	if (x == 0.0) {
	    return 1.0f;
	} else {
	    double y = Math.PI*x;
	    return Math.sin(y)/y;
	}
    }

    private static void window(float[] h, int windowType) {
	int n = h.length;
	double s;
	switch (windowType) {
	default:
	case RECTANGULAR:
	    break;
	case HANNING:
	    s = 2*Math.PI/(n + 1);
	    for (int i = 0; i < n; ++i)
		h[i] *= (float)(0.5*(1 - Math.cos(s*(i + 1))));
	    break;
	case HAMMING:
	    s = 2*Math.PI/(n - 1);
	    for (int i = 0; i < n; ++i)
		h[i] *= (float)(0.54 - 0.46*Math.cos(s*i));
	    break;
	case BLACKMAN:
	    s = 2*Math.PI/(n - 1);
	    for (int i = 0; i < n; ++i)
		h[i] *= (float)(0.42 - 0.5*Math.cos(s*i) + 0.08*Math.cos(2*s*i));
	    break;
	}
    }

    private static void normalize(float[] h) {
	float s = 0;
	for (int i = 0; i < h.length; ++i)
	    s += h[i];
	s = 1/s;
	for (int i = 0; i < h.length; ++i)
	    h[i] *= s;
    }

    private static float transitionBand(int windowType) {
	switch (windowType) {
	default:
	case RECTANGULAR:
	    return 1.84f;
	case HANNING:
	    return 6.22f;
	case HAMMING:
	    return 6.64f;
	case BLACKMAN:
	    return 11.13f;
	}
    }

    public static int computeN(float f, float r, int w) {
	return (int) Math.round(transitionBand(w)*f/r) + 1;
    }

    public static void _main(String[] args) {
	int a = 0;

	int f = Integer.parseInt(args[a++]);
	float r = Float.parseFloat(args[a++]);
	int w = Integer.parseInt(args[a++]);

	int n = computeN((float) f, r, w);

	System.out.println("n = " + n);

	float[] h = design((float) f, n, w);
	for (int i = 0; i < h.length; i++) {
	    System.out.println(h[i]);
	}
    }

    public static void main(String[] args) {
	int a = 0;

	int f = Integer.parseInt(args[a++]);
	int n = Integer.parseInt(args[a++]);
	int w = Integer.parseInt(args[a++]);

	float[] h = design((float) f, n, w);
	for (int i = 0; i < h.length; i++) {
	    System.out.println(f*h[i]);
	}
    }

}
