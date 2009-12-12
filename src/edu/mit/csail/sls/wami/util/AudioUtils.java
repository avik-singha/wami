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
package edu.mit.csail.sls.wami.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class AudioUtils {

	/**
	 * Creates an audio input stream using the passed in byte array and the
	 * given format. Returns null if audioBytes or audioFormat is null
	 * 
	 */
	public static AudioInputStream createAudioInputStream(byte[] audioBytes,
			AudioFormat audioFormat) {
		if (audioBytes != null && audioFormat != null) {
			int frameSize = audioFormat.getFrameSize();
			int length = (int) Math.ceil(audioBytes.length / frameSize);
			return new AudioInputStream(new ByteArrayInputStream(audioBytes),
					audioFormat, length);
		} else {
			return null;
		}
	}

	/**
	 * Creates an InputStream that reads from the audio input stream provided,
	 * and first provides WAVE header information
	 * 
	 * Note, the passed in stream should have length information associated with
	 * it
	 */
	public static InputStream createInputStreamWithWaveHeader(
			AudioInputStream audioIn) throws IOException {
		return new ByteArrayInputStream(createByteArrayWithWaveHeader(audioIn));
	}

	public static byte[] createByteArrayWithWaveHeader(AudioInputStream audioIn)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		AudioSystem.write(audioIn, AudioFileFormat.Type.WAVE, baos);
		byte[] arrayWithHeader = baos.toByteArray();
		return arrayWithHeader;
	}
}
