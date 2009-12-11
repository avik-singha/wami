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

import java.nio.channels.ReadableByteChannel;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/** Generic speech detector API
 **/
public interface SpeechDetector extends ReadableByteChannelCreator {

    interface Listener {
	/** Indicates that speech has been detected somewhere after
	 * the specified position.
	 *
	 * @param offsetSample The padded position, where speech has
	 * been detected, in samples
	 *
	 **/
	void speechStart(long offsetSample);

	/** Indicates the end of detected speech.
	 *
	 * @param offsetSample The last byte of detected speech.  Note
	 * that if the detector is reenabled, this offset may be after
	 * the next speechStart position.
	 *
	 **/
	void speechEnd(long offsetSample);

	/** No speech was detected before the end of file marker was
	 * read by the detector.
	 *
	 * @param offsetSample The position of the end of file mark.
	 *
	 **/
	void noSpeech(long offsetSample);
    }

    interface AudioSource extends SampleBuffer.DataReader {
	/**
	 * @return The audio format of the data
	 */
	AudioFormat getFormat();
    }

    /** Listens for speech
     *
     * @param audioSource Audio data
     *
     * @param channel Which channel to use
     *
     * @param useSpeechDetector Whether or not the speech detector
     * should be used.
     *
     **/
    void listen(AudioSource audioSource,
		int channel,
		boolean useSpeechDetector);

    /** Start reading samples looking for speech, calling listeners.
     *
     * @param useSpeechDetector If set, speech detection is used.
     * Otherwise, all samples are passed through to reader.
     **/
    void enable(boolean useSpeechDetector);

    /** Waits for the detector to finish processing all samples
     *
     **/
    void waitDone();

    /** Stop reading samples
     *
     **/
    void disable();

    /** Returns an AudioInputStream that reads the utterance
     *
     **/
    AudioInputStream createReader();

    /** Returns an AudioInputStream that reads a channel of the utterance
     *
     **/
    AudioInputStream createReader(int channel);

    /** Returns a ReadableByteChannel that reads the utterance
     *
     **/
    ReadableByteChannel createReadableByteChannel();

    /** Returns a ReadableByteChannel that reads a channel of the utterance
     *
     **/
    ReadableByteChannel createReadableByteChannel(int channel);

    /** Returns a ReadableByteChannel[] that reads a channel of the utterance
    *
    **/
    ReadableByteChannel[] createReadableByteChannels(int n, int channel);
    
    /** Returns the audio format
     *
     **/
    AudioFormat getFormat();

    /** Add a listener for speech events
     *
     * @param listener The listener to add
     **/
    void addListener(Listener listener);

    /** Remove a listener for speech events
     *
     * @param listener The listener to remove
     **/
    void removeListener(Listener listener);
    
    /** Read the peak level and reset to 0
     *
     * @return The peak level
     **/
    double readPeakLevel();

    /**
     * Get the names of parameters which can be set for this detector
     */
    public String[] getParameterNames();
    
    /**
     * An interface to set parameters particular to different detectors
     * @param parameter
     * @param value
     */
    public void setParameter(String parameter, double value);
    
    /**
     * Gets the value of a named parameter, as a string
     */
    public double getParameter(String parameter);
}

