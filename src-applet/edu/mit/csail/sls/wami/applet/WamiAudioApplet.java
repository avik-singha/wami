package edu.mit.csail.sls.wami.applet;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.Box;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import edu.mit.csail.sls.wami.applet.sound.AudioDevice;
import edu.mit.csail.sls.wami.applet.sound.AudioInputStreamSource;
import edu.mit.csail.sls.wami.applet.sound.AutocorrSpeechDetector;
import edu.mit.csail.sls.wami.applet.sound.SpeechDetector;

public class WamiAudioApplet extends JApplet implements AudioDevice.Listener,
		SpeechDetector.Listener {
	private JButton button;

	private Timer levelTimer;

	private JProgressBar levelMeter;

	private boolean useSpeechDetector;

	private MouseListener mouseListener;

	private boolean allowStopPlaying;

	private boolean repollOnTimeout = true;

	private volatile boolean connected = false;

	private URL recordUrl;
	private URL playUrl;
	private boolean playRecordTone;

	private AudioDevice audioDevice = new AudioDevice();
	private SpeechDetector detector = new AutocorrSpeechDetector();
	private AudioFormat recordFormat;

	private boolean initialized = false;
	private boolean isDestroyed = false;

	private volatile boolean isPlaying;
	private volatile boolean isRecording;
	private volatile boolean isListening;
	private volatile boolean audioFailure;

	@Override
	public void init() {
		System.out.println("Initializing WAMI Audio Applet 5");
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					createGUI();
				}
			});
		} catch (Exception e) {
			System.err.println("Exception caught in applet init()");
			e.printStackTrace();
		}
	}

	@Override
	public void start() {
		isPlaying = false;
		isRecording = false;
		isListening = false;
		audioFailure = false;
	}

	@Override
	public void destroy() {
		initialized = false;
		isDestroyed = true;
	}

	/**
	 * Visible to javascript: starts listening / recording
	 */
	public void startListening() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				startAudioListening();
			}
		});
	}

	/**
	 * Visible to javascript: stops listening / recording
	 */
	public void stopRecording() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				audioDevice.finish();
				isRecording = false;
				isListening = false;
			}
		});
	}

	/**
	 * Visible to javascript: stops playing
	 */
	public void stopPlaying() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (isPlaying) {
					audioDevice.abort();
				}
			}
		});
	}

	/**
	 * Initializes the applet. Must be called from the swing thread
	 */
	private void createGUI() {
		if (initialized)
			return;
		useSpeechDetector = getBooleanParameter("useSpeechDetector", true);
		allowStopPlaying = getBooleanParameter("allowStopPlaying", true);
		boolean hideButton = getBooleanParameter("hideButton", false);
		recordUrl = urlParameter("recordUrl");
		playUrl = urlParameter("playUrl");
		recordFormat = getAudioFormatFromParams("recordAudioFormat",
				"recordSampleRate", "recordIsLittleEndian");
		playRecordTone = getBooleanParameter("playRecordTone", false);
		mouseListener = new MouseListener();

		button = new JButton("Listen");
		button.setText("Initializing");
		button.setEnabled(false);
		button.addMouseListener(mouseListener);

		Container cp = getContentPane();
		cp.setBackground(Color.WHITE);

		JButton settings = new JButton("...");
		settings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showSettings();
			}
		});

		levelMeter = new JProgressBar(JProgressBar.HORIZONTAL, 0, 1024);
		levelMeter.setPreferredSize(new Dimension(
				levelMeter.getPreferredSize().width, settings
						.getPreferredSize().height));
		settings.setText("settings");
		levelMeter.setStringPainted(false);
		levelMeter.setIndeterminate(false);

		// javax.swing.timer runs events on swing event thread, so this is safe
		levelTimer = new Timer(50, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				double peak = detector.readPeakLevel();
				levelMeter.setValue((int) (peak * 1024 + .5));
			}
		});

		cp.setLayout(new BorderLayout());

		System.out.println("Hide Button: " + hideButton);
		if (!hideButton) {
			cp.add(button, BorderLayout.CENTER);
		}

		Box box = Box.createHorizontalBox();
		cp.add(box, BorderLayout.SOUTH);
		box.add(settings);
		box.add(Box.createHorizontalStrut(5));
		box.add(levelMeter);

		audioDevice.addListener(this);
		detector.addListener(this);
		pingURL(recordUrl);
		startPollingForAudio();
		showStatus();
		initialized = true;
	}

	/**
	 * Audio device is listening
	 */
	public void listeningHasStarted() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				levelTimer.start();
				showStatus();
			}
		});
	}

	/**
	 * Audio device has stopped listening
	 */
	public void listeningHasEnded() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				isRecording = false;
				isListening = false;
				showStatus();
				levelTimer.stop();
				levelMeter.setValue(0);
			}
		});
	}

	/**
	 * audio device is playing
	 */
	public void playingHasStarted() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				isPlaying = true;
				showStatus();
			}
		});
	}

	/**
	 * audio device has finished playing
	 */
	public void playingHasEnded() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				isPlaying = false;
				showStatus();
			}
		});
	}

	/**
	 * Speech detection is not sensing speech
	 */
	public void noSpeech(long offsetSample) {
	}

	/**
	 * Samples are ready for capture
	 */
	public void speechStart(long offsetSample) {
		isRecording = true;
		recordAudio();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				showStatus();
			}
		});
	}

	/**
	 * End of samples to be captured
	 */
	public void speechEnd(long offsetSample) {
		audioDevice.finish();
	}

	/**
	 * Starts "listening" if useSpeechDetector is true, otherwise it starts
	 * recording immediately
	 */
	void startAudioListening() {
		AudioInputStream audioIn;
		try {
			if (playRecordTone) {
				playResource("start_tone.wav");
			}

			// The following line is necessary to fix a weird bug on the Mac
			// whereby recording works once, but not a second time unless this
			// method gets called in between. I have no idea why. imcgraw

			AudioDevice.getAvailableTargetMixers();
			audioIn = audioDevice.getAudioInputStream(recordFormat);
			detector.listen(new AudioInputStreamSource(audioIn), 0,
					useSpeechDetector);
			System.out.println("Detector is listening");
			showStatus();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			audioFailure();
		}
		isListening = true;
	}

	private AudioFormat getAudioFormatFromParams(String formatParam,
			String sampleRateParam, String isLittleEndianParam) {
		String audioFormatStr = getParameter(formatParam);
		int sampleRate = Integer.parseInt(getParameter(sampleRateParam));
		boolean isLittleEndian = Boolean
				.parseBoolean(getParameter(isLittleEndianParam));

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

	private void pingURL(final URL recordUrl) {
		if (recordUrl == null)
			return;
		new Thread(new Runnable() {
			public void run() {
				for (int i = 0; i < 5; i++) {
					HttpURLConnection c;
					try {
						c = (HttpURLConnection) recordUrl.openConnection();
						c.setConnectTimeout(1000);
						c.connect();
						if (c.getResponseCode() != 200) {
							System.out.println("WARNING: Ping failed for URL:"
									+ recordUrl);
							setConnectionStatus(false);
							Thread.sleep(1000);
						} else {
							setConnectionStatus(true);
							break;
						}
					} catch (IOException e) {
						setConnectionStatus(false);
						break;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	void setConnectionStatus(boolean value) {
		connected = value;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				showStatus();
			}
		});
	}

	/**
	 * Must be called from the swing thread
	 */
	private void showStatus() {
		System.out.format("Conn: %s, playing: %s, list %s, rec %s%n",
				connected, isPlaying, isListening, isRecording);
		if (!connected) {
			setListeningStatus("Error: Connection Failure", Color.RED);
			button.setEnabled(false);
		} else if (audioFailure) {
			setListeningStatus("Error: Audio Failure", Color.RED);
			button.setEnabled(false);
		} else if (isPlaying) {
			if (allowStopPlaying) {
				setListeningStatus("Stop playing", Color.GREEN);
				button.setEnabled(true);
			} else {
				setListeningStatus("Playing", Color.GREEN);
				button.setEnabled(false);
			}
		} else if (isListening) {
			if (useSpeechDetector) {
				button.setEnabled(true);
				if (isRecording) {
					setListeningStatus("Recording: Click to stop", Color.CYAN);
				} else {
					setListeningStatus("Listening: Click to stop", Color.CYAN);
				}
			} else {
				button.setEnabled(true);
				setListeningStatus("Recording", Color.CYAN);
			}
		} else {
			button.setEnabled(true);
			if (useSpeechDetector) {
				setListeningStatus("Click to talk", Color.GREEN);
			} else {
				setListeningStatus("Hold to talk", Color.GREEN);
			}
		}
	}

	/**
	 * Must be called from the swing thread
	 * 
	 * @param status
	 * @param color
	 */
	private void setListeningStatus(String status, Color color) {
		button.setText(status);
		button.setBackground(color);
	}

	private boolean getBooleanParameter(String paramName, boolean defaultValue) {
		String value = getParameter(paramName);
		return (value != null) ? Boolean.parseBoolean(value) : defaultValue;
	}

	private URL urlParameter(String paramName) {
		System.out.println("Getting URL from parameter");
		String urlString = getParameter(paramName);
		if (urlString != null && !"".equals(urlString)
				&& !"null".equals(urlString)) {
			try {
				URI uri = new URI(urlString);
				return uri.toURL();
			} catch (MalformedURLException e) {
				e.printStackTrace();
				System.err.println("Invalid url: " + urlString);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private class MouseListener extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			if (button.isEnabled()) {
				if (!isPlaying) {
					if (isListening && useSpeechDetector) {
						audioDevice.finish();
					} else if (!isRecording) {
						startAudioListening();
					}
				}
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (button.isEnabled()) {
				if (!isPlaying) {
					if (!useSpeechDetector) {
						audioDevice.finish();
					}
				}
			}
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (button.isEnabled()) {
				if (isPlaying) {
					if (allowStopPlaying) {
						audioDevice.abort();
					}
				} else if (isRecording) {
					if (useSpeechDetector) {
						audioDevice.finish();
					}
				}
			}
		}
	}

	/**
	 * shows a window where audio settings can be adjusted
	 */
	private void showSettings() {
		Mixer.Info[] sourceMixers = AudioDevice.getAvailableSourceMixers();
		Mixer.Info[] targetMixers = AudioDevice.getAvailableTargetMixers();

		Mixer.Info preferredSource = audioDevice.getPreferredSourceMixer();
		Mixer.Info preferredTarget = audioDevice.getPreferredTargetMixer();

		Vector<Object> vSource = new Vector<Object>(Arrays.asList(sourceMixers));
		Vector<Object> vTarget = new Vector<Object>(Arrays.asList(targetMixers));

		vSource.add(0, "Default");
		vTarget.add(0, "Default");
		final JComboBox comboSource = new JComboBox(vSource);
		final JComboBox comboTarget = new JComboBox(vTarget);
		if (preferredSource != null) {
			comboSource.setSelectedItem(preferredSource);
		}
		if (preferredTarget != null) {
			comboTarget.setSelectedItem(preferredTarget);
		}

		Box audioBox = Box.createVerticalBox();
		Box topBox = Box.createHorizontalBox();
		Box bottomBox = Box.createHorizontalBox();
		audioBox.add(topBox);
		audioBox.add(bottomBox);
		getContentPane().add(audioBox);

		topBox.add(new JLabel("Audio Out"));
		topBox.add(comboSource);
		bottomBox.add(new JLabel("Audio In "));
		bottomBox.add(comboTarget);

		String[] empty = {};
		final String[] params = useSpeechDetector ? detector
				.getParameterNames() : empty;
		final ArrayList<JTextField> paramFields = new ArrayList<JTextField>();
		if (useSpeechDetector) {
			// detector params
			for (String param : params) {
				Box paramBox = Box.createHorizontalBox();
				final JTextField textField = new JTextField();
				final JLabel label = new JLabel(param);
				paramBox.add(label);
				paramBox.add(textField);
				paramFields.add(textField);
				textField.setText("" + detector.getParameter(param));
				textField.setEditable(true);
				audioBox.add(paramBox);
			}
		}

		final JFrame frame = new JFrame("Settings");
		Box cp = Box.createVerticalBox();
		cp.add(audioBox);

		JButton okButton = new JButton("OK");
		JButton cancelButton = new JButton("Cancel");

		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object selected = comboSource.getSelectedItem();
				audioDevice
						.setPreferredSourceMixer((selected instanceof Mixer.Info) ? (Mixer.Info) selected
								: null);

				selected = comboTarget.getSelectedItem();
				audioDevice
						.setPreferredTargetMixer((selected instanceof Mixer.Info) ? (Mixer.Info) selected
								: null);

				if (useSpeechDetector) {
					for (int i = 0; i < params.length; i++) {
						String param = params[i];
						try {
							double value = Double.parseDouble(paramFields
									.get(i).getText());
							detector.setParameter(param, value);
						} catch (NumberFormatException eN) {
							eN.printStackTrace();
						}
					}
				}

				frame.dispose();
			}
		});

		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.dispose();
			}
		});

		Box buttonBox = Box.createHorizontalBox();
		buttonBox.add(okButton);
		buttonBox.add(cancelButton);
		cp.add(buttonBox);

		frame.setContentPane(cp);
		frame.pack();
		frame.setVisible(true);

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

	}

	void audioFailure() {
		audioFailure = true;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				showStatus();
			}
		});
	}

	/**
	 * records audio by sending it to the server, until the stream is closed
	 */
	void recordAudio() {
		// must do this immediately in the same thread
		final AudioInputStream in = detector.createReader(0);

		new Thread(new Runnable() {
			public void run() {
				try {
					System.out.println("Posting audio to " + recordUrl);
					System.out.println("Format: " + recordFormat);

					HttpURLConnection conn = (HttpURLConnection) recordUrl
							.openConnection();
					
					conn.setRequestProperty("Content-Type",
							getContentType(recordFormat));

					conn.setDoInput(true);
					conn.setDoOutput(true);
					conn.setRequestMethod("POST");
					conn.setChunkedStreamingMode(2048);
					conn.connect();

					OutputStream out = conn.getOutputStream();

					byte[] buffer = new byte[10240];
					int totalRead = 0;
					while (true) {
						int numRead = in.read(buffer);
						if (numRead < 0) {
							break;
						}
						out.write(buffer, 0, numRead);
						out.flush();
						totalRead += numRead;
					}

					out.close();
					in.close();
					if (playRecordTone) {
						playResource("end_tone.wav");
					}
					System.out.println("Posted total of  " + totalRead
							+ " audio bytes");
					System.out.println("Http response line: "
							+ conn.getResponseMessage());
				} catch (IOException e) {
					e.printStackTrace();
					setConnectionStatus(false);
				}
			}

		}).start();

	}

	private String getContentType(AudioFormat format) {
		String encoding = null;
		if (format.getEncoding() == AudioFormat.Encoding.ULAW) {
			encoding = "MULAW";
		} else if (format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
			encoding = "L16";
		}

		return "AUDIO/" + encoding + "; CHANNELS=" + format.getChannels()
				+ "; RATE=" + (int) format.getSampleRate() + "; BIG="
				+ format.isBigEndian();
	}

	void playResource(String resourceName) {
		InputStream in = (getClass().getResourceAsStream(resourceName));
		if (in != null) {
			try {
				AudioInputStream ais = AudioSystem
						.getAudioInputStream(new BufferedInputStream(in));
				audioDevice.play(ais);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("playResource(): can't find resource named: "
					+ resourceName);
		}
	}

	/**
	 * Polls a url for audio, plays it when it is returned
	 * 
	 * @param playUrl
	 *            The url to poll
	 */
	private void startPollingForAudio() {
		if (playUrl == null)
			return;

		Thread thread = new Thread() {
			@Override
			public void run() {
				int READ_TIMEOUT = 1000 * 60 * 5; // 5 minutes
				while (!isDestroyed) {
					boolean repoll = pollForAudio(READ_TIMEOUT);
					if (!repoll) {
						setConnectionStatus(false);
						return;
					}

				}
			}

		};
		thread.setDaemon(true);
		thread.start();
	}

	boolean pollForAudio(int connectionTimeout) {
		try {
			HttpURLConnection c;
			c = (HttpURLConnection) playUrl.openConnection();

			// Spend some time polling before timing out
			c.setReadTimeout(connectionTimeout);
			c.connect();

			System.out.println("Polling for audio on: " + playUrl);

			if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("Polling failed.");
			}

			System.out.println("Connected.");

			if ("audio/wav".equals(c.getContentType())) {
				InputStream stream = c.getInputStream();
				AudioInputStream ais;

				// assume the audio has header information in the stream
				// to tell us what it is
				try {
					// must be a bufferedinputstream b/c mark must be
					// supported to to read the header and determine the audio
					// format
					ais = AudioSystem
							.getAudioInputStream(new BufferedInputStream(stream));
					System.out.println("Playing");
					audioDevice.play(ais);
					System.out.println("Sleeping");

					return true;
				} catch (UnsupportedAudioFileException e) {
					e.printStackTrace();
				}
			} else {
				System.out
						.println("Connection was OK, but there was no audio, polling again.");
			}
		} catch (IOException e) {
			if (e instanceof SocketTimeoutException) {
				System.out.println("Socket Timeout while polling for audio.");
			} else {
				setConnectionStatus(false);
				System.out.println("WARNING: Failed to poll for audio on: "
						+ playUrl);
				e.printStackTrace();
				return false;
			}
		}
		return repollOnTimeout;
	}

}
