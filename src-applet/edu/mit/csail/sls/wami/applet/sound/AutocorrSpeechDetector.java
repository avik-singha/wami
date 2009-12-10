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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Detects the start and end of speech.
 *
 **/
public class AutocorrSpeechDetector implements Runnable, SpeechDetector {
	static final int IDLE = 0;
	static final int INIT = 1;
	static final int SPEECH = 2;
	static final int AWAIT_ENABLE = 3;
	static final int DISABLED = 4;
	
	static final double invlog10 = 1.0/Math.log(10.0);
	
	volatile boolean close = false;
	volatile int state = IDLE;
	int sampleRate = -1;
	double startPaddingDuration = 1.2; // Padding added before first voicing
	double endPaddingDuration = 1.2; // Padding added after final voicing
	double preSpeechSilence = 0.0; // How much unnvoiced we need before speech
	//double postSpeechSilence = .5; // How much unvoiced we need to end
	double postSpeechSilence = 1.0; // How much unvoiced we need to end
	double voiceDuration = .060; // How much voice before we call it voice
	double silenceDuration = .080; // How much silence before we call it silence
	
	double frameDuration = .020;
	double windowDuration = .080;
	
	double clipLevel = .6;	// Samples below this level are clipped
	double voicingThreshold = .70;
	//double energyRange = 12.0;        //was the original value
	double energyRange = 5.0;	// dbs above min energy reqd for consideration
	
	int minPitch = 70;		// Lowest pitch we must look at
	int maxPitch = 250;		// Highest pitch we must look at
	int autocorrRes = 30;	// Pitch resolution
	
	AudioInputStream ais;
	SampleBuffer sampleBuffer;
	AudioFormat format;
	SampleBuffer.Reader reader;
	SampleBuffer.Reader levelReader;
	ByteBuffer levelBytes;
	short[] levelShorts;
	volatile int maxLevel;
	volatile boolean resetLevel;
	LinkedList<Listener> listeners = new LinkedList<Listener>();
	
	// Samples
	int frameSamples;		// Samples in a frame
	int windowSamples;		// Samples in a window
	int startPaddingSamples;	// Silence before voicing enables speech
	int endPaddingSamples;	// Silence after voicing to end speech
	int preSpeechSamples;	// Padding added before first voicing
	int postSpeechSamples;	// Padding added after final voicing
	int voiceSamples;		// Minimum to trigger voicing
	int silenceSamples;		// Minimum to trigger silence
	
	double[] hamming;
	
	double[] autocorr;
	int[] autocorrIdx;
	int autocorrLength;
	
	short[] windowShorts;	// Before Hamming
	double[] window;		// Windowed points
	double windowSizeBias;	// Energy adjustment for window size
	
	ByteBuffer windowBytes;	// Holds a window worth of samples
	
	long nextUttStartSample;	// uttStartSample for next enable() 
	long uttStartSample;	// Start sample for current detection
	long uttEndSample;		// End sample for current detection
	long transitionStartSample;	// Where current possible transition started
	long segmentStartSample;	// Start of current voiced/unnvoiced segment
	boolean inSpeech;		// In a voiced region
	boolean inTransition;	// Possible transition between voiced/unvoiced
	SampleBuffer.Limit limit = null;
	long limitMax = 0;		// Max limitpos seen
	
	AudioSource sampleBufferWriter;
	
	double maxval;
	double maxeng;
	double mineng;
	
	public AutocorrSpeechDetector(){
		uttStartSample = Long.MAX_VALUE;
		levelShorts = new short[256];
		levelBytes = ByteBuffer.allocate(levelShorts.length);
		
		
		new Thread(this).start();
	}
	
	synchronized void releaseSampleBuffer(){
		if (sampleBuffer != null){
			sampleBuffer = null;
		}
		uttStartSample = Long.MAX_VALUE;
	}
	
	public synchronized void listen(AudioSource audioSource,
			int channel,
			boolean useSpeechDetector){
		
		releaseSampleBuffer();
		sampleBufferWriter = audioSource;
		format = audioSource.getFormat();
		sampleBuffer = 
			new SampleBuffer(format.getChannels(), format.getFrameSize());
		
		int sampleRate = (int)format.getSampleRate();
		reader = sampleBuffer.reader(channel);
		reader.setBlocking(false);
		levelReader = sampleBuffer.reader(channel);
		levelReader.setBlocking(false);
		maxLevel = 0;
		resetLevel = false;
		if (this.sampleRate != sampleRate){
			this.sampleRate = sampleRate;
			initialize();
		}
		maxval = Double.MIN_VALUE;
		maxeng = Double.MIN_VALUE;
		mineng = Double.MAX_VALUE;
		
		nextUttStartSample = sampleForByte(reader.position());
		uttEndSample = Long.MAX_VALUE;
		windowBytes.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN
				: ByteOrder.LITTLE_ENDIAN);
		levelBytes.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN
				: ByteOrder.LITTLE_ENDIAN);
		limit = sampleBuffer.createLimit(0);
		limitMax = 0;
		
		enable(useSpeechDetector);
	}
	
	/** Listens on the specified channel of ais, optionally detecting
	 * speech.
	 *
	 **/
	public void listen(AudioInputStream ais,
			int channel,
			boolean useSpeechDetector) {
		
		listen(new AudioInputStreamSource(ais),
				channel,
				useSpeechDetector);
		
		
	}
	
	public synchronized void enable(boolean useSpeechDetector){
		windowBytes.clear();
		if (useSpeechDetector){
			state = INIT;
			if (sampleBuffer == null)
				return;
			
			sampleBuffer.moveLockPosition(uttStartSample, nextUttStartSample);
			uttStartSample = nextUttStartSample;
			uttEndSample = Long.MAX_VALUE;
			segmentStartSample = uttStartSample;
			
			inSpeech = true;
			inTransition = false;
		} else {
			state = DISABLED;
			sampleBuffer.moveLockPosition(uttStartSample, nextUttStartSample);
			uttStartSample = nextUttStartSample;
			
			limit.limit(Long.MAX_VALUE);
			limitMax = Long.MAX_VALUE;
			uttEndSample = Long.MAX_VALUE;
			
			for(Listener listener : listeners){
				listener.speechStart(0);
			}
			sampleBuffer.unlockPosition(uttStartSample);
		}
		notifyAll();
	}
	
	
	final int samples(double duration){
		return (int)Math.round(duration*sampleRate);
	}
	
	final long byteForSample(long sample){
		return (sample == Long.MAX_VALUE) ? Long.MAX_VALUE : sample*2;
	}
	
	final long sampleForByte(long i){
		return (i == Long.MAX_VALUE) ? Long.MAX_VALUE : i/2;
	}
	
	void initialize(){
		frameSamples = samples(frameDuration);
		windowSamples = samples(windowDuration);
		startPaddingSamples = samples(startPaddingDuration);
		endPaddingSamples = samples(endPaddingDuration);
		preSpeechSamples = samples(preSpeechSilence);
		postSpeechSamples = samples(postSpeechSilence);
		voiceSamples = (samples(voiceDuration)/frameSamples)*frameSamples;
		silenceSamples = (samples(silenceDuration)/frameSamples)*frameSamples;
		
		autocorrIdx = new int[autocorrRes];
		autocorr = new double[autocorrRes];
		
		double interval = Math.log(maxPitch/minPitch)/autocorrRes;
		int lastIndex = 0;
		autocorrLength = 0;
		for(int i=0; i<autocorrRes; i++){
			double pitch = Math.exp(interval*i)*minPitch;
			int idx = (int)Math.round(sampleRate/pitch);
			//System.out.format("Pitch: %3.0f off %3d%n", pitch, idx);
			if (idx != lastIndex){
				autocorrIdx[autocorrLength++] = idx;
				lastIndex = idx;
			}
		}
		windowBytes = ByteBuffer.allocate((int)byteForSample(windowSamples));
		hamming = new double[windowSamples];
		for(int i=0; i<windowSamples; i++){
			hamming[i] = 0.54-0.46*Math.cos(2.0*Math.PI*i/(windowSamples-1));
		}
		windowShorts = new short[windowSamples];
		window = new double[windowSamples*2];
		windowSizeBias = -5.0*Math.log(windowSamples)*invlog10;
	}
	
	public void run(){
		while(true){
			synchronized(this){
				if (close)
					break;
				waitForWork();
			}
			doWork();
		}
	}
	
	/** Returns true if there is work to be done
	 *
	 **/
	public synchronized  boolean hasWork(){
		if (state == IDLE)
			return false;
		
		// If there's data to read, there's something to do
		if (sampleBufferWriter != null)
			return true;
		
		return false;
	}
	
	/** Waits for the detector to have work to do
	 *
	 **/
	public synchronized void waitForWork(){
		while(!hasWork()){
			try {
				wait();
			} catch(InterruptedException e){
			}
		}
	}
	
	/** Wait for all processing to be completed
	 *
	 **/
	public synchronized void waitDone(){
		while(sampleBufferWriter != null && state!=IDLE){
			try {
				wait();
			} catch(InterruptedException e){
			}
		}
	}
	
	
	void getSamples(){
		if (sampleBuffer == null || sampleBufferWriter == null)
			return;
		
		// Add samples to the sample buffer
		synchronized(this){
			try {
				int nWritten = sampleBuffer.write(sampleBufferWriter);
				if (nWritten == -1){
					sampleBufferWriter = null;
					sampleBuffer.close();
					notifyAll();
				}
			} catch(IOException e){
				e.printStackTrace();
				state = IDLE;
				sampleBufferWriter = null;
				notifyAll();
				return;
			}
		}
		
		int levelRemaining = levelReader.remaining();
		while(levelRemaining > 0){
			int max = resetLevel ? 0 : maxLevel;
			levelBytes.clear();
			int nread = levelReader.read(levelBytes);
			if (nread <= 0)
				break;
			levelBytes.flip();
			levelRemaining -= nread;
			
			ShortBuffer shorts = levelBytes.asShortBuffer();
			int n = Math.min(shorts.limit(), levelShorts.length);
			shorts.get(levelShorts, 0, n);
			
			for(int i=0; i<n; i++){
				int s = levelShorts[i];
				if (s<0) 
					s=-s;
				if (s > max)
					max = s;
			}
			maxLevel = max;
		}
	}
	
	/** Reads the current peak, and resets to 0
	 *
	 * @return current peak, in range 0.0 to 1.0
	 **/
	public double readPeakLevel(){
		double result = maxLevel/32768.0;
		resetLevel = true;
		
		return result;
	}
	
	void processWindow(long frameOffsetSample, boolean readEof){
		// 
		reader.position(byteForSample(frameOffsetSample+frameSamples));
		
		// Window the samples
		ShortBuffer shorts = windowBytes.asShortBuffer();
		int n = Math.min(shorts.limit(), windowSamples);
		if (readEof){
			n = 0;
			uttEndSample = frameOffsetSample;
		}
		
		shorts.get(windowShorts, 0, n);
		
		double dcOffset = 0.0;
		for(int i=0; i<n; i++){
			dcOffset+=window[i];
		}
		dcOffset = dcOffset/n;
		
		for(int i=0; i<n; i++){
			window[i] = hamming[i]*(windowShorts[i]-dcOffset);
		}
		for(int i=n; i<windowSamples;i++){
			window[i] = 0.0;
		}
		
		
		/*
		 // Preemphasis
		  for(int i=n-1; i>1; i--){
		  window[i]-=.95*window[i-1];
		  }
		  */
		
		// Determine maximum value and center clip
		double lmaxval = 0;
		for(int i=0; i<windowSamples; i++){
			double val = window[i];
			lmaxval = Math.max(lmaxval, Math.abs(val));
		}
		maxval = Math.max(lmaxval,maxval);
		double pclip = maxval*clipLevel;
		double nclip = -pclip;
		for(int i=0; i<windowSamples; i++){
			double val = window[i];
			if (-nclip < val && val < pclip)
				window[i] = 0.0;
		}
		
		double a0 = 0.0;
		for(int i=0; i<windowSamples; i++){
			double val = window[i];
			a0+=val*val;
		}
		
		// Replicate the samples so we don't need to worry about
		// wrap-around
		for(int i=0; i<windowSamples; i++){
			window[i+windowSamples] = window[i];
		}
		
		int ires = 0;
		for(int idx=0; idx<autocorrLength; idx++){
			double val = 0.0;
			int j = autocorrIdx[idx];
			for(int i=0; i<windowSamples; i++){
				val += window[i]*window[j++];
			}
			autocorr[ires++] = val;
		}
		double energy = windowSizeBias+5.0*(Math.log(a0)*invlog10);
		double periodicity = 0.0;
		//long pitch = 0;
		boolean speech = false;
		if (a0 > 0.0){
			// Don't let initial zeroes cause a problem
			maxeng = Math.max(energy,maxeng);
			mineng = Math.min(energy,mineng);
			double max = Double.MIN_VALUE;
			//int maxi = 0;
			for(int i=0; i<autocorrLength; i++){
				if (autocorr[i] > max){
					//maxi = i;
					max = autocorr[i];
				}
			}
			periodicity = max/a0;
			
			//int maxk = autocorrIdx[maxi];
			//pitch = Math.round(sampleRate/(double)maxk);
			
			
			speech = 
				periodicity > voicingThreshold 
				&& energy-mineng > energyRange;
			
			//System.out.println("periodicity: " + periodicity + "/\t" + voicingThreshold);
			//System.out.println("energy: " + (energy-mineng) + "/\t" + energyRange);
			//System.out.println("speech: " + speech + "\n");

		}
		
		boolean transition = false;
		long lastDuration = 0;
		long duration = 0;
		if (inSpeech != speech){
			if (!inTransition){
				transitionStartSample = frameOffsetSample;
				inTransition = true;
			}
			long transitionSamples = frameOffsetSample-transitionStartSample;
			if ((speech && transitionSamples > voiceSamples)
					|| (!speech && transitionSamples > silenceSamples)){
				transition = true;
				if (!speech)
					nextUttStartSample = transitionStartSample;
				lastDuration = transitionStartSample-segmentStartSample;
				segmentStartSample = transitionStartSample;
				inTransition = false;
				inSpeech = speech;
			}
		} else {
			inTransition = false;
		}
		// Last sample we can safely say is in this voiced/unvoiced
		// segment.  Anything beyond this could go either way
		long segmentEndSample = inTransition? 
				transitionStartSample : frameOffsetSample;
		
		// How many sample we know are in this segment
		duration = segmentEndSample-segmentStartSample;
		
		
		
		
		/*
		 double period = 1.0/sampleRate;
		 System.out.format
		 ("%7d %3.3f p=%3d M=%5.0f [%3.2f %3.2f] e=%3.2f per=%3.0f%% d=%1.3f t=%1.3f %b %b %b%n",
		 frameOffsetSample,
		 period*frameOffsetSample,
		 pitch,
		 maxval,
		 mineng,
		 maxeng,
		 energy-mineng,
		 periodicity*100,
		 period*duration,
		 period*(frameOffsetSample-transitionStartSample),
		 speech,
		 inTransition,
		 inSpeech
		 );
		 */
		boolean eof = reader.eof();
		
		switch(state){
		case INIT:{
			if (eof){
				for(Listener listener : listeners){
					listener.noSpeech(uttEndSample);
				}
				synchronized(this){
					state = IDLE;
					notifyAll();
				}
				
				break;
			}
			/* Wait for sp_padding_duration of silence followed by
			 * speech
			 */
			long sample = 
				Math.max(frameOffsetSample-startPaddingSamples,
						uttStartSample);
			if (speech && transition && lastDuration > preSpeechSamples){
				state = SPEECH;
				limit = sampleBuffer.createLimit(uttStartSample);
				limitMax = sample;
				for(Listener listener : listeners){
					listener.speechStart(uttStartSample);
				}
				sampleBuffer.unlockPosition(uttStartSample);
			} else {
				if (sample >= uttStartSample){
					sampleBuffer.moveLockPosition(uttStartSample ,sample);
					uttStartSample = sample;
				}
			}
			break;
		}
		
		case SPEECH:{
			if (eof || (!inSpeech
					&& !transition
					&& !inTransition
					&& duration > postSpeechSamples)){
				// All done
				long endSample = 
					Math.min(frameOffsetSample+windowSamples+endPaddingSamples,
							uttEndSample);
				limitMax = Math.max(limitMax, endSample);
				limit.eofPosition(limitMax);
				limit.limit(limitMax);
				
				long remaining = limit.byteLimit()-reader.position();
				
				while(hasWork() && reader.remaining() < remaining){
					getSamples();
				}
				
				state = AWAIT_ENABLE;
				for(Listener listener : listeners){
					listener.speechEnd(endSample);
				}
				if (state == AWAIT_ENABLE){
					synchronized(this){
						state = IDLE;
						notifyAll();
					}
				}
			} else {
				limitMax = 
					Math.max(limitMax,
							Math.max(segmentEndSample-postSpeechSamples, 
									segmentStartSample));
				limit.limit(limitMax);
			}
			break;
		}
		
		case IDLE:
			break;
		}
		
	}
	
	void doWork(){
		getSamples();
		if (state == DISABLED)
			return;
		
		while(true){
			if (!hasWork())
				return;
			
			// Why do we get this from the reader??
			long frameOffsetSample = sampleForByte(reader.position());
			
			int nread = reader.read(windowBytes);
			if (nread >= 0 && windowBytes.hasRemaining()){
				// Wait for more data
				return;
			}
			windowBytes.flip();
			
			processWindow(frameOffsetSample, nread < 0);
			windowBytes.clear();
		}
		
	}
	
	// Reads all channels
	public synchronized AudioInputStream createReader(){
		return new AudioInputStream
		(Channels.newInputStream(sampleBuffer.reader(limit)),
				format,
				AudioSystem.NOT_SPECIFIED);
	}
	
	// Reads a single channel
	public synchronized AudioInputStream createReader(int channel){
		return new AudioInputStream
		(Channels.newInputStream(sampleBuffer.reader(limit, channel)),
				new AudioFormat
				(AudioFormat.Encoding.PCM_SIGNED,
						format.getSampleRate(),
						16,
						1,
						2*format.getFrameSize(),
						format.getFrameRate(),
						format.isBigEndian()),
						AudioSystem.NOT_SPECIFIED);
	}
	
	public synchronized ReadableByteChannel createReadableByteChannel(){
		return sampleBuffer.reader(limit);
	}
	
	public synchronized ReadableByteChannel 
	createReadableByteChannel(int channel){
		return sampleBuffer.reader(limit, channel);
	}
	
	public synchronized ReadableByteChannel[] 
	createReadableByteChannels(int n, int channel){
	    ReadableByteChannel[] bcs = new ReadableByteChannel[n];

	    // Grab the data before we move past it
	    for (int i = 0; i < n; i++) {
		bcs[i] = sampleBuffer.reader(limit, channel);
	    }
	    return bcs;
	}
	
	public AudioFormat getFormat(){
		return format;
	}
	
	public synchronized void disable(){
		state = IDLE;
		reader.close();
		if (sampleBufferWriter != null){
			sampleBufferWriter.close();
			sampleBufferWriter = null;
		}
		notifyAll();
	}
	
	public void addListener(Listener listener){
		listeners.add(listener);
	}
	
	public void removeListener(Listener listener){
		listeners.remove(listener);
	}

	public void setParameter(String parameter, double value) {
	    if(parameter.equals("energyRange")) {
		energyRange = value;
	    } else if(parameter.equals("voicingThreshold")) {
		voicingThreshold = value;
	    }
	}
	
	public double getParameter(String parameter) {
	    if(parameter.equals("energyRange")) {
		return energyRange; 
	    } else if(parameter.equals("voicingThreshold")) {
		return voicingThreshold;
	    }
	    
	    return -1;
	}

	public String[] getParameterNames() {
	    String[] params = { "energyRange", "voicingThreshold" };
	    return params;
	}
}
