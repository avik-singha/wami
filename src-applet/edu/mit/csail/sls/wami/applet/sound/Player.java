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

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;

/**
 * @author cyphers Interface to something that can play an AudioinputStream
 */
public interface Player {
    /**
         * A listener for play events
         * 
         */
    public interface Listener {

	/**
         * Called when playing starts
         * 
         */
	void playingHasStarted();

	/**
         * Called when playing completes
         * 
         */
	void playingHasEnded();
    }

    /**
         * Add a listener
         * 
         * @param listener
         *                The listener
         * 
         */
    public void addListener(Listener listener);

    /**
         * Remove a listener
         * 
         * @param listener
         *                The listener
         * 
         */
    public void removeListener(Listener listener);

    /**
         * Play the stream. This is the same as play(stream, true, true).
         * 
         * @param stream
         *                The stream to play
         * 
         */
    public void play(AudioInputStream stream) throws LineUnavailableException;

    /**
         * Play the stream
         * 
         * @param stream
         *                The stream to play
         * 
         * @param setStart
         *                If true, getFramePosition() will consider the start of
         *                this stream as frame 0.
         * 
         */
    void play(AudioInputStream stream, boolean setStart, boolean last)
	    throws LineUnavailableException;

    /**
         * Returns true if in the play loop
         * 
         */
    boolean isPlaying();

    /**
         * Return the frame position in the currently playing stream.
         * 
         * Can be called from any thread
         * 
         */
    int getFramePosition();

    /**
         * Wait for playing to complete, and then close the line.
         * 
         */
    void closeLine();

    /**
         * Break out of playing
         * 
         * Can be called from any thread
         */
    void stopPlaying();

    /**
         * Return the closest sample rate that can be used.
         * 
         */
    int supportedSampleRate(int desiredSampleRate)
	    throws LineUnavailableException;
}
