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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import edu.mit.csail.sls.wami.WamiConfig;
import edu.mit.csail.sls.wami.WamiServlet;
import edu.mit.csail.sls.wami.relay.WamiRelay;
import edu.mit.csail.sls.wami.util.ServletUtils;

/**
 * This servlet is polled for audio by the applet. Audio may be posted, a URL
 * passed, or the "play" called on the relay.
 * 
 * @author imcgraw
 * 
 */
@SuppressWarnings("serial")
public class PlayServlet extends HttpServlet {

	public static final AudioFormat playFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 16000, false);

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		System.out.println("Request: "
				+ WamiConfig.reconstructRequestURLandParams(request));
		System.out.println("Audio Session ID: " + request.getSession().getId());

		if (isPollRequest(request)) {
			// Returns audio whenever some is posted to the same audio session
			doPollRequest(request, response);
			return;
		}

		if (isForwardRequest(request)) {
			// Forward audio from a particular URL to anyone polling.
			doForwardRequest(request, response);
		}
	}

	/**
	 * Someone, somewhere is posting some audio. Pass this streaming audio on to
	 * whoever is polling on the same audio session id (i.e. the applet).
	 * 
	 * @param request
	 * @param response
	 */
	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) {
		response.setHeader("Cache-control", "no-cache");
		response.setContentType("text/xml; charset=UTF-8");

		String pinyin = request.getParameter("synth_string");
		System.out.println(pinyin);

		AudioInputStream input;
		boolean success = true;
		try {
			input = new AudioInputStream(request.getInputStream(), playFormat,
					AudioSystem.NOT_SPECIFIED);
			getRelay(request).play(input);
		} catch (IOException e) {
			success = false;
			e.printStackTrace();
		}

		try {
			response.getWriter().write(
					"<reply success='" + success + "' session='"
							+ request.getSession().getId() + "'></reply>");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void doForwardRequest(HttpServletRequest request,
			HttpServletResponse response) {

		String urlstr = request.getParameter("url");

		AudioInputStream ais = getWavFromURL(urlstr);
		getRelay(request).play(ais);
	}

	public static AudioInputStream getWavFromURL(String urlstr) {
		URL url;
		AudioInputStream ais = null;

		try {
			url = new URL(urlstr);

			URLConnection c = url.openConnection();
			c.connect();
			InputStream stream = c.getInputStream();

			ais = new AudioInputStream(stream, playFormat,
					AudioSystem.NOT_SPECIFIED);
			System.out.println("Getting audio from URL: " + urlstr);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ais;
	}

	private boolean isForwardRequest(HttpServletRequest request) {
		return request.getParameter("url") != null;
	}

	private boolean isPollRequest(HttpServletRequest request) {
		return request.getParameter("poll") != null
				&& Boolean.parseBoolean(request.getParameter("poll"));
	}

	private void doPollRequest(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		try {

			String id = request.getParameter("sessionid");

			if (id == null) {
				// Default ID is this request's session ID.
				id = request.getSession().getId();
			}

			WamiRelay relay = getRelay(request);

			if (relay == null) {
				response.sendError(1);
				return;
			}

			ServletContext sc = request.getSession().getServletContext();
			WamiConfig config = WamiConfig.getConfiguration(sc);
			int playPollTimeout = config.getPlayPollTimeout(request);

			InputStream in = relay.waitForAudio(playPollTimeout);

			if (in != null) {
				response.setContentType("audio/wav");

				// InputStream in =
				// AudioUtils.createInputStreamWithWaveHeader(audio);
				OutputStream out = response.getOutputStream();

				ServletUtils.sendStream(in, out);
			} else {
				// System.out
				// .println("Wait for audio timeout, not sending back audio");
				response.setContentType("text/xml");
				response.getOutputStream().close();
				// response.sendError(1);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private WamiRelay getRelay(HttpServletRequest request) {
		return (WamiRelay) WamiServlet.getRelay(request);
	}

}
