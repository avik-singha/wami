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
import java.util.*;

/** Comparator for audio formats that uses relative distance from a
 * desired format as the comparison.
 **/
abstract class AudioFormatComparator implements Comparator<AudioFormat> {
    AudioFormat desiredAudioFormat;
    int channels;
    AudioFormat.Encoding encoding;
    float frameRate;
    float sampleRate;
    int sampleSizeInBits;
    boolean isBigEndian;

    public AudioFormatComparator(AudioFormat desiredAudioFormat){
	this.desiredAudioFormat = desiredAudioFormat;
	channels = desiredAudioFormat.getChannels();
	encoding = desiredAudioFormat.getEncoding();
	frameRate = desiredAudioFormat.getFrameRate();
	sampleRate = desiredAudioFormat.getSampleRate();
	sampleSizeInBits = desiredAudioFormat.getSampleSizeInBits();
	isBigEndian = desiredAudioFormat.isBigEndian();
    }
    
    // Java doesn't provide channel conversions, so when looking for the
    // nearest format, need to ignore channels
    public static AudioFormat channelFormat(AudioFormat f, int n){
	int channels = f.getChannels();
	return new AudioFormat(f.getEncoding(),
			       f.getSampleRate(),
			       f.getSampleSizeInBits(),
			       n,
			       n*f.getFrameSize()/channels,
			       f.getFrameRate(),
			       f.isBigEndian());
    }

    // Compare two formats for conversions.  This has to be different
    // for intput and output since the conversions go in different
    // directions
    abstract int conversionCompare(AudioFormat f1, AudioFormat f2);

    // Compare relative distance to the desiredAudioFormat
    // In linux, 8-bit audio to the device is broken for record and 
    // play, so we need to avoid the 8-bit formats
    public int compare(AudioFormat o1, AudioFormat o2){
	if (o1 == o2)
	    return 0;

	if (o1 == null)
	    return 1;

	if (o2 == null)
	    return -1;

	AudioFormat f1 = (AudioFormat)o1;
	AudioFormat f2 = (AudioFormat)o2;

	{
	    // 8-bit line support is broken in the vm, so push 8-bit
	    // formats to the bottom of the list
	    int b1 = f1.getSampleSizeInBits();
	    int b2 = f2.getSampleSizeInBits();
	    
	    if (b1 != b2){
		if (b1 == 8)
		    return 1;
		if (b2 == 8)
		    return -1;
	    }
	}

	if (o1.equals(o2))
	    return 0;

	if (f1.matches(f2))
	    return 0;
	    
	if (desiredAudioFormat.matches(f1))
	    return -1;
	if (desiredAudioFormat.matches(f2))
	    return 1;

	// Use the closer rate
	{
	    float r1 = f1.getSampleRate();
	    float r2 = f2.getSampleRate();
	    if (r1 != r2){
		
		// If a rate matches, it's closer
		// Java uses AudioSystem.NOT_SPECIFIED for a rate when
		// it's not going to provide any information about what's
		// supports
		if (r1 == sampleRate || r1 == AudioSystem.NOT_SPECIFIED)
		    return -1;
		if (r2 == sampleRate || r2 == AudioSystem.NOT_SPECIFIED)
		    return 1;

		boolean r1m = ((int)r1 % (int)sampleRate) == 0;
		boolean r2m = ((int)r2 % (int)sampleRate) == 0;
		if (r1m && r2m){
		    // If both rates are multiples, use the closer
		    // multiple, while if only one rate is a multiple,
		    // go with that one
		    float m1 = r1/sampleRate;
		    float m2 = r2/sampleRate;
		    if (m1 < m2)
			return -1;
		    else if (m2 < m1)
			return 1;
		} else if (r1m){
		    return -1;
		} else if (r2m){
		    return 1;
		}
	    }
	}

	{
	    AudioFormat.Encoding e1 = f1.getEncoding();
	    AudioFormat.Encoding e2 = f2.getEncoding();

	    if (e1 != e2){
		
		if (e1 == encoding)
		    return -1;
		if (e2 == encoding)
		    return 1;
		
		if (encoding == AudioFormat.Encoding.PCM_SIGNED
		    || encoding == AudioFormat.Encoding.PCM_UNSIGNED){
		    // Prefer lossless encodings
		    if (e1 == AudioFormat.Encoding.PCM_UNSIGNED
			|| e1 == AudioFormat.Encoding.PCM_SIGNED)
			return -1;
		    if (e2 == AudioFormat.Encoding.PCM_UNSIGNED
			|| e2 == AudioFormat.Encoding.PCM_SIGNED)
			return 1;
		} else if (encoding == AudioFormat.Encoding.ULAW ||
			   encoding == AudioFormat.Encoding.ALAW){
		    if (e1 == AudioFormat.Encoding.PCM_SIGNED)
			return -1;
		    else if (e2 == AudioFormat.Encoding.PCM_SIGNED)
			return 1;
		    else if (e1 == AudioFormat.Encoding.PCM_UNSIGNED)
			return -1;
		    else if (e2 == AudioFormat.Encoding.PCM_UNSIGNED)
			return 1;
		    else if (e1 == AudioFormat.Encoding.ULAW ||
			     e1 == AudioFormat.Encoding.ALAW)
			return -1;
		    else if (e2 == AudioFormat.Encoding.ULAW ||
			     e2 == AudioFormat.Encoding.ALAW)
			return 1;
		}
	    }
	}

	// Endianness barely matters
	{
	    boolean b1 = f1.isBigEndian();
	    boolean b2 = f2.isBigEndian();
	    if (b1 != b2){
		if (b1 == isBigEndian)
		    return -1;
		if (b2 == isBigEndian)
		    return 1;
	    }
	}

	// If a conversion exists for only one, then it is nearer
	{
	    int c = conversionCompare(channelFormat(f1,1),
				      channelFormat(f2,1));
	    if (c != 0)
		return c;
	}

	// If the channels are different, best is equal numbers of
	// channels to what we want, less best is the least number
	// of extra, and worst is not enough
	{
	    int c1 = f1.getChannels();
	    int c2 = f2.getChannels();
	    if (c1 != c2){
		if (c1 == channels)
		    return -1;
		if (c2 == channels)
		    return 1;
		if (c1 > channels){
		    if (c2 > channels){
			if (c1 < c2)
			    return -1;
			if (c1 > c2)
			    return 1;
		    } else return -1;
		} else if (c2 > channels){
		    return 1;
		} else if (c1 < c2){
		    return 1;
		} else return -1;
	    }
	}
	
	// They are equally good/bad, so fall back to the hash code
	{
	    int h1 = f1.hashCode();
	    int h2 = f2.hashCode();
	    if (h1 < h2)
		return -1;
	    if (h1 > h2)
		return 1;
	    return 0;
	}

    }
}

