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

import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;

class UpSample2InputStream extends InputStream {
    AudioInputStream in;
    ByteBuffer byteBuffer;
    ShortBuffer sb;
    int channels;
    int padding; 		// Number of 0s to add
    boolean eof = false;
    AudioFormat audioFormat;

    static final int SIZE = 1024;

    final float[] f;
    final int fl2;
    final int fl4;
    final int si0;
    final int si1;
    final int presigSize;

    float[] sigb;
    int sigp;;
    int presig;

    UpSample2InputStream(float[] f, AudioInputStream in){
	this.f = f;
	this.in = in;
	fl2 = f.length/2;
	fl4 = f.length/4;
	si0 = -((f.length-1)/2);
	si1 = -((f.length-2)/2);
	presigSize = fl4;
	sigp = 0;
	presig = presigSize;

	audioFormat = in.getFormat();
	channels = audioFormat.getChannels();
	byteBuffer = ByteBuffer.allocate(SIZE);
	byteBuffer.order(audioFormat.isBigEndian() 
			 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
	sb = byteBuffer.asShortBuffer();
	sigb = new float[f.length*channels];
	for(int i=0; i<sigb.length; i++){
	    sigb[i] = 0f;
	}
    }

    boolean refill() throws IOException {
	if (eof)
	    return false;
	byteBuffer.position(sb.position()*2);
	byteBuffer.compact();
	
	// Make sure at least one n-channel sample is available
	byte[] array = byteBuffer.array();
	int position = byteBuffer.position();
	int offset = byteBuffer.arrayOffset();
	int avail = byteBuffer.capacity();
	while (!eof && position < channels*2){
	    int n = in.read(array, offset+position, avail-position);
	    if (n == -1){
		padding = fl2*channels;
		eof = true;
	    } else {
		position+=n;
	    }
	}
	byteBuffer.limit(position);
	sb.position(0);
	sb.limit(position/2);

	return position > 0;
    }

    @Override
	public int available() throws IOException {
	return (in.available()+
		sb.remaining()*2+
		padding)*2;
    }

    @Override
	public void close() throws IOException {
	in.close();
    }

    @Override
	public void mark(int readLimit){}
    @Override
	public boolean markSupported(){
	return false;
    }
    @Override
	public void reset(){}

    @Override
	public long skip(long n2) throws IOException {
	long n = n2/2;
	long result = 0;
	if (padding > 0 || n < fl2+1){
	    while(n > 0 && padding > 0){
		readSample();
		result++;
	    }
	    return result;
	}
	
	long reallySkip = n - (fl2+1);
	int sbbytes = sb.remaining()*2;
	int sbskip = (int)Math.min((long)sbbytes, reallySkip);
	result+=sbskip;
	sb.position(sb.position()+sbskip/2);
	reallySkip -= sbskip;

	if (reallySkip > 0){
	    result+=in.skip(reallySkip);
	}
	// 
	presig = fl2+1;
	while(presig-- > 0 && (!eof || padding > 0)){
	    readSample();
	    result++;
	}
	return result*2;
    }

    @Override
	public int read() throws IOException {
	throw new IOException("Attempt to read less than one frame");
    }

    @Override
	public int read(byte[] b) throws IOException {
	return read(b, 0, b.length);
    }

    void readSample() throws IOException {
	if (sb.remaining() < channels && !eof)
	    refill();
	if (eof && padding==0)
	    return;

	for(int c=0; c<channels; c++){
	    sigb[sigp+c] = (padding-- > 0) ? 0f : sb.get();
	}
    }

    @Override
	public int read(byte[] b, int off, int len) throws IOException {
	while(presig-- > 0){
	    readSample();
	}

	ByteBuffer bout = ByteBuffer.wrap(b, off, len);
	bout.order(audioFormat.isBigEndian() 
		   ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
	ShortBuffer outShortBuffer = bout.asShortBuffer();
	while(outShortBuffer.hasRemaining() && (!eof || padding > 0)){
	    readSample();
	    for(int c=0; c<channels; c++){

		float o = 0.5f;	// round sum
		int fi = 0;	// filter index
		int si = sigp+si0*channels; // signal index
		if (si < 0){
		    si+= sigb.length;
		}
		while(fi < f.length){
		    o+= f[fi]*sigb[si];
		    fi+=2;
		    si+=channels;
		    if (si >= sigb.length)
			si -= sigb.length;
		}
		outShortBuffer.put((short)o);

		o = 0.5f;	// round sum
		fi = 1;		// filter index
		si = sigp+si1*channels; // signal index
		if (si < 0){
		    si+= sigb.length;
		}
		while(fi < f.length){
		    o+= f[fi]*sigb[si];
		    fi+=2;
		    si+=channels;
		    if (si >= sigb.length)
			si -= sigb.length;
		}
		outShortBuffer.put((short)o);
		sigp++;
	    }
	    if (sigp == sigb.length)
		sigp = 0;
	}
	if (outShortBuffer.position() == 0)
	    return -1;
	return outShortBuffer.position()*2;
	
    }
}

class DownSample2InputStream extends InputStream {
    AudioInputStream in;
    ByteBuffer byteBuffer;
    ShortBuffer sb;
    int channels;
    int padding; 		// Number of 0s to add
    boolean eof = false;
    AudioFormat audioFormat;

    static final int SIZE = 1024;

    final float[] f;
    final int fl2;
    final int fl4;
    final int presigSize;

    float[] sigb;
    int sigp;;
    int presig;

    DownSample2InputStream(float[] f, AudioInputStream in){
	this.f = f;
	this.in = in;
	fl2 = f.length/2;
	fl4 = f.length/4;
	presigSize = fl2;
	sigp = 0;
	presig = presigSize;

	audioFormat = in.getFormat();
	channels = audioFormat.getChannels();
	byteBuffer = ByteBuffer.allocate(SIZE);
	byteBuffer.order(audioFormat.isBigEndian() 
			 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
	sb = byteBuffer.asShortBuffer();
	sigb = new float[f.length*channels];
	for(int i=0; i<sigb.length; i++){
	    sigb[i] = 0f;
	}
    }

    boolean refill() throws IOException {
	if (eof)
	    return false;
	byteBuffer.position(sb.position()*2);
	byteBuffer.compact();
	
	// Make sure at least one n-channel sample is available
	byte[] array = byteBuffer.array();
	int position = byteBuffer.position();
	int offset = byteBuffer.arrayOffset();
	int avail = byteBuffer.capacity();
	while (!eof && position < channels*2){
	    int n = in.read(array, offset+position, avail-position);
	    if (n == -1){
		padding = fl2*channels;
		eof = true;
	    } else {
		position+=n;
	    }
	}
	byteBuffer.limit(position);
	sb.position(0);
	sb.limit(position/2);

	return position > 0;
    }

    @Override
	public int available() throws IOException {
	return (in.available()+
		sb.remaining()*2+
		padding)/2;
    }

    @Override
	public void close() throws IOException {
	in.close();
    }

    @Override
	public void mark(int readLimit){}
    @Override
	public boolean markSupported(){
	return false;
    }
    @Override
	public void reset(){}

    @Override
	public long skip(long n2) throws IOException {
	long n = n2*2;
	long result = 0;
	if (padding > 0 || n < fl2+1){
	    while(n > 0 && padding > 0){
		readSample();
		result++;
	    }
	    return result;
	}
	
	long reallySkip = n - (fl2+1);
	int sbbytes = sb.remaining()*2;
	int sbskip = (int)Math.min((long)sbbytes, reallySkip);
	result+=sbskip;
	sb.position(sb.position()+sbskip/2);
	reallySkip -= sbskip;

	if (reallySkip > 0){
	    result+=in.skip(reallySkip);
	}
	// 
	presig = f.length;
	while(presig-- > 0 && (!eof || padding > 0)){
	    readSample();
	    result++;
	}
	return result/2;
    }

    @Override
	public int read() throws IOException {
	throw new IOException("Attempt to read less than one frame");
    }

    @Override
	public int read(byte[] b) throws IOException {
	return read(b, 0, b.length);
    }

    void readSample() throws IOException {
	if (sb.remaining() < channels && !eof)
	    refill();
	if (eof && padding==0)
	    return;

	for(int c=0; c<channels; c++){
	    sigb[sigp+c] = (padding-- > 0) ? 0f : sb.get();
	}
    }

    @Override
	public int read(byte[] b, int off, int len) throws IOException {
	while(presig-- > 0){
	    readSample();
	}

	ByteBuffer bout = ByteBuffer.wrap(b, off, len);
	bout.order(audioFormat.isBigEndian() 
		   ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
	ShortBuffer outShortBuffer = bout.asShortBuffer();
	while(outShortBuffer.hasRemaining() && (!eof || padding > 0)){
	    readSample();
	    for(int c=0; c<channels; c++){

		float o = 0.5f;	// round sum
		int fi = 0;	// filter index
		int si = sigp-f.length*channels; // signal index
		if (si < 0){
		    si+= sigb.length;
		}
		while(fi < f.length){
		    o+= f[fi]*sigb[si];
		    fi++;
		    si+=channels;
		    if (si >= sigb.length)
			si -= sigb.length;
		}
		outShortBuffer.put((short)o);
		sigp++;
	    }
	    if (sigp == sigb.length)
		sigp = 0;
	}
	if (outShortBuffer.position() == 0)
	    return -1;
	return outShortBuffer.position()*2;
	
    }
}

/** Upsample a 16-bit signed stream by 2:1
 *
 **/
public class UpSample2 extends AudioInputStream {
    static final float[] filter = new float[]{
	-0.01395067863f,
	-0.01254265751f,
	0.005888123203f,
	0.02567507422f,
	0.01752509353f,
	-0.0139434597f,
	-0.02352647899f,
	0.01344585544f,
	0.04994074383f,
	0.01788352979f,
	-0.06279615233f,
	-0.07069303465f,
	0.08035806069f,
	0.3083767822f,
	0.4187405136f,
	0.3083767822f,
	0.08035806069f,
	-0.07069303465f,
	-0.06279615233f,
	0.01788352979f,
	0.04994074383f,
	0.01344585544f,
	-0.02352647899f,
	-0.0139434597f,
	0.01752509353f,
	0.02567507422f,
	0.005888123203f,
	-0.01254265751f,
	-0.01395067863f
    };


    static AudioFormat newFormat(AudioInputStream in){
	AudioFormat inFormat = in.getFormat();
	return new AudioFormat(inFormat.getEncoding(),
			       2*inFormat.getSampleRate(),
			       inFormat.getSampleSizeInBits(),
			       inFormat.getChannels(),
			       inFormat.getFrameSize(),
			       2*inFormat.getFrameRate(),
			       inFormat.isBigEndian());
    }

    static long newFrameLength(AudioInputStream in){
	long inFrameLength = in.getFrameLength();
	return inFrameLength == AudioSystem.NOT_SPECIFIED ?
	    AudioSystem.NOT_SPECIFIED : 2*inFrameLength;
    }

    public UpSample2(AudioInputStream in){
	super(new UpSample2InputStream(filter, in),
	      newFormat(in),
	      newFrameLength(in));
    }
}
