/* -*- java -*-
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
import javax.sound.sampled.*;

/** Makes an AudioSource from an AudioInputStream.
 *
 **/
public class AudioInputStreamSource implements SpeechDetector.AudioSource {
    AudioInputStream ais;

    public AudioInputStreamSource(AudioInputStream aisin){
	AudioFormat aisf = aisin.getFormat();
	ais =
	    (aisf.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
	     && aisf.getSampleSizeInBits() == 16) ?
	    aisin :
	    AudioSystem
	    .getAudioInputStream(new AudioFormat
				 (AudioFormat.Encoding.PCM_SIGNED,
				  aisf.getSampleRate(),
				  16,
				  aisf.getChannels(),
				  2*aisf.getFrameSize()*aisf.getChannels(),
				  aisf.getFrameRate(),
				  aisf.isBigEndian()),
				 aisin);
    }

    public int read(byte[] dest, int off, int len) throws IOException {
	return ais.read(dest, off, len);
    }

    public void close(){
	try {
	    ais.close();
	} catch(IOException e){
	}
    }
	
    public AudioFormat getFormat(){
	return ais.getFormat();
    }
}
