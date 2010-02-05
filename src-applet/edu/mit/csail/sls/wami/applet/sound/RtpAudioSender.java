/** -*- Java -*-
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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;

/**
 * Sends audio data over TCP in an RTP-like format
 * 
 * @author cyphers
 * 
 */
public class RtpAudioSender implements Runnable {
    ByteBuffer outByteBuffer = ByteBuffer.allocate(1500);

    AudioFormat dformat;

    boolean littleEndian = false;

    ByteBuffer inByteBuffer;

    ShortBuffer inShortBuffer;

    ShortBuffer outShortBuffer;

    InetSocketAddress addr;

    SocketChannel destinationSC = null;

    int cookie;

    ReadableByteChannel bc;

    File file;

    ByteBuffer intByteBuffer = ByteBuffer.allocate(4);

    ByteBuffer headerByteBuffer;

    int sequenceNumber = 0;

    Charset charset = Charset.forName("UTF-8");

    boolean reclog = false;
    
    RtpAudioSender(AudioFormat dformat, ReadableByteChannel bc, File file,
	    InetSocketAddress addr, int cookie, boolean reclog) {
	this.dformat = dformat;
	this.bc = bc;
	this.addr = addr;
	this.cookie = cookie;
	this.file = file;
	this.reclog = reclog;

	headerByteBuffer = ByteBuffer.allocate(12);
	headerByteBuffer.order(ByteOrder.BIG_ENDIAN);
	intByteBuffer.order(ByteOrder.BIG_ENDIAN);
    }

    void sendData(SocketChannel sc, int pt, ByteBuffer dataByteBuffer)
	    throws IOException {
	headerByteBuffer.clear();
	headerByteBuffer.putInt(2 << 30 | pt << 16 | sequenceNumber);
	sequenceNumber = (sequenceNumber+1)&0xFFFF;
	headerByteBuffer.putInt((int) (System.currentTimeMillis() & 0xFFFFFFFF));
	headerByteBuffer.putInt(1);
	headerByteBuffer.flip();

	intByteBuffer.clear();
	int nbytes = headerByteBuffer.limit() + dataByteBuffer.limit();
	intByteBuffer.putInt(nbytes);
	intByteBuffer.flip();
	ByteBuffer[] bbs = new ByteBuffer[] { intByteBuffer, headerByteBuffer, dataByteBuffer };
	sc.write(bbs);
    }

    void sendData(SocketChannel sc, AudioFormat audioFormat,
	    ByteBuffer dataByteBuffer) throws IOException {
	int pt = Rtp.RTP_PT_PCMU;
	AudioFormat.Encoding encoding = audioFormat.getEncoding();
	if (encoding == AudioFormat.Encoding.ULAW)
	    pt = Rtp.RTP_PT_PCMU;
	else if (encoding == AudioFormat.Encoding.PCM_SIGNED) {
	    pt = Rtp.RTP_PT_L16_8K;
	} else if (encoding == Encodings.AMR) {
	    pt = Rtp.RTP_PT_AMR;
	}
	sendData(sc, pt, dataByteBuffer);
    }

    void sendText(SocketChannel sc, String text) throws IOException {
	headerByteBuffer.clear();
	ByteBuffer bytes = charset.encode(text);
	sendData(sc, Rtp.RTP_PT_TXT, bytes);
    }

    public void run() {
	try {

	    destinationSC = SocketChannel.open(addr);

	    // Send the cookie
	    ByteBuffer bb = ByteBuffer.allocate(4);
	    bb.order(ByteOrder.BIG_ENDIAN);
	    bb.putInt(cookie);
	    bb.flip();
	    destinationSC.write(bb);

	    if (reclog && file != null) {
		sendText(destinationSC, "logfile:" + file.toString());
	    }

	    outByteBuffer.order(ByteOrder.BIG_ENDIAN);
	    if (dformat.getFrameSize() == 2 && !dformat.isBigEndian()) {
		littleEndian = true;
		inByteBuffer = ByteBuffer.allocate(outByteBuffer.capacity());
		inByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		inShortBuffer = inByteBuffer.asShortBuffer();
		outShortBuffer = outByteBuffer.asShortBuffer();
	    } else {
		inByteBuffer = outByteBuffer;
	    }

	    while (true) {
		inByteBuffer.clear();
		int n = bc.read(inByteBuffer);
		if (n < 0)
		    break;
		if (littleEndian) {
		    inShortBuffer.position(0);
		    inShortBuffer.limit(inByteBuffer.position() / 2);
		    outShortBuffer.clear();
		    outShortBuffer.put(inShortBuffer);
		    outByteBuffer.limit(outShortBuffer.position() * 2);
		    outByteBuffer.position(0);
		} else {
		    outByteBuffer.flip();
		}

		sendData(destinationSC, dformat, outByteBuffer);
	    }
	    destinationSC.close();
	    destinationSC = null;
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
    
    /**
         * Create senders to destinations in waveURL for data in detector
         * 
         * @param waveURL
         *                Contains the destinations for the audio data
         * @param file
         *                Log file. First destination will log if provided.
         * @param detector
         *                The speech detector with the data
         * @param channel
         *                The channel of the data to send
         * @param reclog
         *                Whether the recognizer should do the logging
         * @return The senders
         * @throws IOException
         */
    public static RtpAudioSender[] createSenders(String waveURL, File file,
	    ReadableByteChannelCreator channelCreator, int channel, boolean reclog)
	    throws IOException {
	String protocolHeader = "galaxy_rtp://";
	if (!waveURL.startsWith(protocolHeader))
	    return new RtpAudioSender[0];
	
	AudioFormat dformat = channelCreator.getFormat();
	String[] urls = waveURL.substring(protocolHeader.length()).split("//");

	File sfile = file;
	ReadableByteChannel[] bcs = channelCreator.createReadableByteChannels(
		urls.length, channel);
	RtpAudioSender[] senders = new RtpAudioSender[urls.length];
	Matcher audioDestinationMatcher = Pattern.compile(
		"^([^:]*):(\\d*)/([0-9a-fA-F]*)$").matcher("");

	for (int i = 0; i < urls.length; i++) {
	    audioDestinationMatcher.reset(urls[i]);
	    if (!audioDestinationMatcher.matches()) {
		bcs[i].close();
		continue;
	    }

	    String destinationHost = audioDestinationMatcher.group(1);
	    int destinationPort = Integer.parseInt(audioDestinationMatcher
		    .group(2));
	    int cookie = Integer.parseInt(audioDestinationMatcher.group(3), 16);

	    InetSocketAddress addr = new InetSocketAddress(destinationHost,
		    destinationPort);

	    RtpAudioSender sender = new RtpAudioSender(dformat, bcs[i], sfile,
		    addr, cookie, reclog);
	    sfile = null;
	    senders[i] = sender;
	}
	return senders;
    }

}