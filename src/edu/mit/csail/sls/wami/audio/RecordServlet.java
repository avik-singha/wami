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
package edu.mit.csail.sls.wami.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import edu.mit.csail.sls.wami.util.ContentType;
import edu.mit.csail.sls.wami.WamiConfig;
import edu.mit.csail.sls.wami.WamiServlet;
import edu.mit.csail.sls.wami.relay.WamiRelay;

/**
 * Receives audio bytes from the server and passes them on to the portal
 * associated with this session
 * 
 * @author alexgru
 * 
 */
public class RecordServlet extends HttpServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		System.out.println("Pinging the record servlet.");

		WamiRelay relay = (WamiRelay) WamiServlet.getRelay(request);
		relay.sendReadyMessage();
	}

	@Override
	public void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// Need PUT protocol for some mobile device audio controllers.
		doPost(request, response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		//System.out.println("Request: "
		//		+ WamiConfig.reconstructRequestURLandParams(request));
		System.out.println("Handling portal recognize() post on session: "
				+ request.getSession().getId());

		// BufferedReader reader = new BufferedReader(new InputStreamReader(
		// request.getInputStream()));
		// String line;
		// System.out.println("Reading lines:");
		// while ((line = reader.readLine()) != null) {
		// System.out.println(line);
		// }
		// if (line == null) {
		// return;
		// }

		// The audio format of the recording device (and thus the sound coming
		// in)
		AudioFormat audioFormat = getAudioFormatFromParams(request,
				"recordAudioFormat", "recordSampleRate", "recordIsLittleEndian");

		System.out.println("RecordServlet; audioFormat=" + audioFormat);

		AudioInputStream audioIn = new AudioInputStream(
				new BufferedInputStream(request.getInputStream()), audioFormat,
				AudioSystem.NOT_SPECIFIED);

		AudioFormat requiredFormat = getRecognizerRequiredAudioFormat();
		if (audioFormat.getEncoding() != requiredFormat.getEncoding()
				|| audioFormat.getSampleRate() != requiredFormat
						.getSampleRate()
				|| audioFormat.getSampleSizeInBits() != requiredFormat
						.getSampleSizeInBits()
				|| audioFormat.getChannels() != requiredFormat.getChannels()
				|| audioFormat.getFrameSize() != requiredFormat.getFrameSize()
				|| audioFormat.getFrameRate() != requiredFormat.getFrameRate()
				|| audioFormat.isBigEndian() != requiredFormat.isBigEndian()) {
			System.out.println("Resampling");
			audioIn = new WamiResampleAudioInputStream(
					getRecognizerRequiredAudioFormat(), audioIn);
		}

		WamiRelay relay = (WamiRelay) WamiServlet.getRelay(request);
		try {
			relay.recognize(audioIn);
		} catch (Exception e) {
			// TODO: something smarter? We should really notify the application
			// that an error occurred
			throw new ServletException(e);
		}
	}

	private AudioFormat getRecognizerRequiredAudioFormat() {
		return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 8000, 16, 1, 2,
				8000, false);
	}

	public static String slurp(InputStream in) throws IOException {
		StringBuffer out = new StringBuffer();
		byte[] b = new byte[4096];
		for (int n; (n = in.read(b)) != -1;) {
			out.append(new String(b, 0, n));
		}
		return out.toString();
	}

	private AudioFormat getAudioFormatFromParams(HttpServletRequest request,
			String formatParam, String sampleRateParam,
			String isLittleEndianParam) {
		System.out.println("Record Content-Type " + request.getContentType());
		ContentType contentType = ContentType.parse(request.getContentType());
		String contentMajor = contentType.getMajor();
		String contentMinor = contentType.getMinor();

		// If Content-Type is valid, go with it
		if ("AUDIO".equals(contentMajor)) {
			if (contentMinor.equals("L16")) {
				// Content-Type = AUDIO/L16; CHANNELS=1; RATE=8000; BIG=false
				int rate = contentType.getIntParameter("RATE", 8000);
				int channels = contentType.getIntParameter("CHANNELS", 1);
				boolean big = contentType.getBooleanParameter("BIG", true);
				return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate,
						16, channels, 2, rate, big);
			}
		}

		// One of the clients that does not specify ContentType, or
		// sets it to something bogus
		String audioFormatStr = request.getParameter(formatParam);
		int sampleRate = Integer
				.parseInt(request.getParameter(sampleRateParam));
		boolean isLittleEndian = Boolean.parseBoolean(request
				.getParameter(isLittleEndianParam));

		if ("MULAW".equals(audioFormatStr)) {
			return new AudioFormat(AudioFormat.Encoding.ULAW, sampleRate, 8, 1,
					2, 8000, !isLittleEndian);
		} else if ("LIN16".equals(audioFormatStr)) {
			return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate,
					16, 1, 2, sampleRate, !isLittleEndian);
		}
		throw new UnsupportedOperationException("Unsupported audio format: '"
				+ audioFormatStr + "'");
	}

}
