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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

/**
 * @author cyphers
 * 
 * Controls half-duplex audio, switching between listening, playing, and idle as
 * needed.
 * 
 */
public class AudioDevice implements Runnable {

    static enum DeviceMode { MODE_IDLE, MODE_PLAY, MODE_RECORD };
    
    DeviceMode mode = DeviceMode.MODE_IDLE;

    LinkedList<BasicTask> tasks = new LinkedList<BasicTask>();

    BasicTask task = null;

    IdleTask idleTask = new IdleTask();

    Thread thread;

    AudioPlayer audioPlayer = new AudioPlayer();

    AudioRecorder audioRecorder = new AudioRecorder();

    // Event listeners
    LinkedList<Listener> listeners = new LinkedList<Listener>();

    /**
         * @author cyphers Event listener interface for audio being controlled
         */
    public interface Listener {
	/**
         * Audio output has been initialized, and samples are being played.
         * 
         */
	void playingHasStarted();

	/**
         * The last sample has been played and the audio has been shut down
         */
	void playingHasEnded();

	/**
         * Audio input has been initialized and samples are being received from
         * the device
         */
	void listeningHasStarted();

	/**
         * Audio input has been disabled and samples are no longer being
         * received from the device
         */
	void listeningHasEnded();
    }

    /**
         * Add an event listener
         * 
         * @param l
         *                The listener
         * 
         */
    public void addListener(Listener l) {
	listeners.add(l);
    }

    /**
         * Remove an event listener
         * 
         * @param l
         *                The listener
         * 
         */
    public void removeListener(Listener l) {
	listeners.remove(l);
    }

    void playingHasStarted() {
	for (Listener listener : listeners) {
	    listener.playingHasStarted();
	}
    }

    void playingHasEnded() {
	for (Listener listener : listeners) {
	    listener.playingHasEnded();
	}
    }

    void listeningHasStarted() {
	for (Listener listener : listeners) {
	    listener.listeningHasStarted();
	}
    }

    void listeningHasEnded() {
	for (Listener listener : listeners) {
	    listener.listeningHasEnded();
	}
    }

    /**
         * @author cyphers Ensure that at most one device is active, and that is
         *         performing at most one activity
         */
    abstract class BasicTask implements Runnable {
	volatile boolean active = false; // Started

	volatile boolean complete = false; // Finished

	synchronized void setActive() {
	    active = true;
	    notifyAll();
	}

	synchronized void setComplete() {
	    active = false;
	    complete = true;
	    notifyAll();
	}

	/**
         * Wait for this task to finish
         * 
         */
	synchronized void waitComplete() {
	    while (!complete) {
		try {
		    wait();
		} catch (InterruptedException e) {
		}
	    }
	}

	int getFramePosition() {
	    return 0;
	}

	abstract void finish();

	abstract void abort();
    }

    /**
         * @author cyphers This task "runs" when there is nothing to do. It just
         *         waits for another task to show up
         */
    class IdleTask extends BasicTask {
	public void run() {
	    setMode(DeviceMode.MODE_IDLE);
	    synchronized (AudioDevice.this) {
		while (tasks.isEmpty()) {
		    try {
			AudioDevice.this.wait();
		    } catch (InterruptedException e) {
		    }
		}
	    }
	}

	@Override
	void finish() {
	}

	@Override
	void abort() {
	}
    }

    /**
         * @author cyphers Play some audio
         */
    class PlayTask extends BasicTask {
	AudioInputStream ais;

	boolean setStart;

	boolean last;

	LineUnavailableException exception;

	PlayTask(AudioInputStream ais, boolean setStart, boolean last) {
	    this.ais = ais;
	    this.setStart = setStart;
	    this.last = last;
	}

	public void run() {
	    setMode(DeviceMode.MODE_PLAY);
	    if (setStart)
		playingHasStarted();
	    try {
		audioPlayer.play(ais, setStart, last);
	    } catch (LineUnavailableException e) {
		exception = e;
	    }
	    if (last) {
		playingHasEnded();
	    }
	}

	@Override
	int getFramePosition() {
	    return audioPlayer.getFramePosition();
	}

	@Override
	synchronized void abort() {
	    if (active) {
		audioPlayer.stopPlaying();
		waitComplete();
	    }
	}

	@Override
	void finish() {
	    waitComplete();
	}
    }

    /**
         * @author cyphers Record something
         */
    class RecordTask extends BasicTask {
	AudioFormat desiredAudioFormat;

	AudioInputStream ais = null;

	LineUnavailableException exception = null;

	volatile boolean ready = false;

	RecordTask(AudioFormat desiredAudioFormat) {
	    this.desiredAudioFormat = desiredAudioFormat;
	}

	public void run() {
	    setMode(DeviceMode.MODE_RECORD);
	    listeningHasStarted();
	    try {
		ais = audioRecorder.getAudioInputStream(desiredAudioFormat);
	    } catch (LineUnavailableException e) {
		exception = e;
	    }
	    synchronized (this) {
		ready = true;
		notifyAll();
	    }
	    waitComplete();
	    System.out.println("Waiting for record task is complete");
	    listeningHasEnded();
	    System.out.println("Fired all listening has ended stuff.");
	}

	synchronized AudioInputStream getAudioInputStream()
		throws LineUnavailableException {
	    while (!ready) {
		try {
		    wait();
		} catch (InterruptedException e) {
		}
	    }
	    if (ais == null)
		throw exception;
	    else
		return ais;
	}

	@Override
	void abort() {
	    finish();
	}

	@Override
	synchronized void finish() {
	    System.out.println("FINISH RECORDING: ");
	    if (active) {
		System.out.println("set to idle");
		setMode(DeviceMode.MODE_IDLE);
		setComplete();
	    }
	}
    }

    public AudioDevice() {
	thread = new Thread(this);
	thread.start();
    }

    /**
         * Play an audio input stream
         * 
         * @param ais
         *                The audio input stream
         * 
         * @param setStart
         *                The first frame of this stream is frame 0 for
         *                getFramePosition()
         * 
         * @param last
         *                If true, playingHasEnded() will be called when the
         *                stream finishes playing.
         * 
         */
    public void play(AudioInputStream ais, boolean setStart, boolean last) {
	PlayTask task = new PlayTask(ais, setStart, last);
	addTask(task);
    }

    /**
         * Play an audio input stream
         * 
         * @param ais
         *                The audio input stream
         * 
         */
    public void play(AudioInputStream ais) {
	play(ais, true, true);
    }

    /**
         * Wait for audio to become available and return an audio input stream
         * close to the specified format
         * 
         * @param format
         *                The desired audio format
         * 
         */
    public AudioInputStream getAudioInputStream(AudioFormat format)
	    throws LineUnavailableException {
	RecordTask task = new RecordTask(format);
	addTask(task);
	return task.getAudioInputStream();
    }

    synchronized void addTask(BasicTask task) {
	tasks.add(task);
	notifyAll();
    }

    public void run() {
	while (true) {
	    synchronized (this) {
		task = (tasks.isEmpty()) ? idleTask : (BasicTask) tasks
			.removeFirst();
		task.setActive();
		notifyAll();
	    }
	    task.run();
	    task.setComplete();
	}
    }

    /**
         * Finish everything and return
         * 
         */
    public void finish() {
	while (true) {
	    synchronized (this) {
		task.finish();
		if (tasks.isEmpty()) {
		    return;
		} else {
		    try {
			wait();
		    } catch (InterruptedException e) {
		    }
		}
	    }
	}
    }

    /**
         * Abort everything and return
         * 
         */
    public synchronized void abort() {
	tasks.clear();
	task.abort();
	notifyAll();
    }

    public int getFramePosition() {
	synchronized (this) {
	    return task.getFramePosition();
	}
    }

    public int supportedPlaySampleRate(int desiredSampleRate)
	    throws LineUnavailableException {
	return audioPlayer.supportedSampleRate(desiredSampleRate);
    }

    // Make sure only one direction is going at once
    void setMode(DeviceMode newMode) {
	if (mode == newMode)
	    return;

	switch (mode) {
	case MODE_PLAY:
	    audioPlayer.closeLine();
	    break;

	case MODE_RECORD:
	    audioRecorder.closeLine();
	    break;
	}

	mode = newMode;
    }

    /**
         * Set the preferred target (input) mixer to use. Note, the system may
         * ignore this preference if the line does not support a suitable format
         * 
         * @param mInfo
         *                Description of mixer
         */
    public void setPreferredTargetMixer(Mixer.Info mInfo) {
	audioRecorder.setPreferredMixer(mInfo);
    }

    /**
     * Set the preferred target (input) mixer to use. Note, the system may
     * ignore this preference if the line does not support a suitable format
     * 
     * @param mInfo
     *                Description of mixer
     */
    public void setPreferredTargetMixer(String mInfo) {
	audioRecorder.setPreferredMixer(mInfo);
    }
    
    public Mixer.Info getPreferredTargetMixer() {
	return audioRecorder.getPreferredMixer();
    }

    /**
         * Set the preferred source (output) mixer to use.
         * 
         * @param mInfo
         *                Description of mixer
         */
    public void setPreferredSourceMixer(Mixer.Info mInfo) {
	audioPlayer.setPreferredMixer(mInfo);
    }
    
    public void setPreferredSourceMixer(String minfo){
	audioPlayer.setPreferredMixer(minfo);
    }

    public Mixer.Info getPreferredSourceMixer() {
	return audioPlayer.getPreferredMixer();
    }
    
    /**
     * returns a list of target mixers (which also have data lines)
     * 
     * @return
     */
    public static Mixer.Info[] getAvailableTargetMixers() {
	return getAvailableMixers(true);
    }

    public static Mixer.Info[] getAvailableSourceMixers() {
	return getAvailableMixers(false);
    }

    private static Mixer.Info[] getAvailableMixers(boolean isTarget) {
	ArrayList<Mixer.Info> mixers = new ArrayList<Mixer.Info>(Arrays
		.asList((Mixer.Info[]) AudioSystem.getMixerInfo()));
	for (Iterator<Mixer.Info> it = mixers.iterator(); it.hasNext();) {
	    Mixer.Info minfo = it.next();
	    Mixer mixer = AudioSystem.getMixer(minfo);

	    Line.Info[] linfo = (isTarget) ? mixer.getTargetLineInfo() : mixer
		    .getSourceLineInfo();
	    boolean hasDataLine = false;
	    for (int j = 0; j < linfo.length; j++) {
		if (linfo[j] instanceof DataLine.Info) {
		    hasDataLine = true;
		    break;
		}
	    }
	    if (!hasDataLine) {
		it.remove();
	    }
	}

	return mixers.toArray(new Mixer.Info[mixers.size()]);
    }


}
