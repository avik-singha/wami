package edu.mit.csail.sls.wami.log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.sound.sampled.AudioInputStream;

public class EventLoggerDaemonAdapter implements IEventLogger {
	private ExecutorService eventLoggerService;
	private IEventLogger logger;
	private ServletContext sc;

	static class LogThreadFactory implements ThreadFactory {
		public Thread newThread(Runnable runnable) {
			Thread t = new Thread(runnable, "Event Logger");
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		}
	}

	public EventLoggerDaemonAdapter(IEventLogger logger) {
		this.logger = logger;

		this.eventLoggerService = Executors
				.newSingleThreadExecutor(new LogThreadFactory());
	}

	@Override
	public void setParameters(ServletContext sc, Map<String, String> map)
			throws EventLoggerException {
		this.sc = sc;
		logger.setParameters(sc, map);
	}

	@Override
	public void createSession(final String serverAddress,
			final String clientAddress, final String wsessionid,
			final long timestampMillis, final String recDomain)
			throws EventLoggerException {
		eventLoggerService.submit(new Runnable() {
			public void run() {
				try {
					logger.createSession(serverAddress, clientAddress,
							wsessionid, timestampMillis, recDomain);
				} catch (EventLoggerException e) {
					e.printStackTrace();
					sc.log("Event Logger Error", e);
				}
			}
		});
	}

	@Override
	public void addSessionCreatedListener(IEventLoggerListener l) {
		logger.addSessionCreatedListener(l);
	}

	@Override
	public void close() throws EventLoggerException {
		eventLoggerService.submit(new Runnable() {
			public void run() {
				try {
					logger.close();
					logger = null;
				} catch (Exception e) {
					sc.log("Event Logger Error", e);
					e.printStackTrace();
				}
			}
		});

		eventLoggerService.shutdown();

		try {
			eventLoggerService.awaitTermination(60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		eventLoggerService.shutdownNow();
		eventLoggerService = null;
	}

	@Override
	public void logEvent(final ILoggable logEvent, final long timestampMillis)
			throws EventLoggerException {
		eventLoggerService.submit(new Runnable() {
			public void run() {
				try {
					logger.logEvent(logEvent, timestampMillis);
				} catch (Exception e) {
					e.printStackTrace();
					sc.log("Event Logger Error", e);
				}
			}
		});
	}

	@Override
	public void logUtterance(final AudioInputStream audioIn,
			final long timestampMillis) throws EventLoggerException,
			IOException {
		eventLoggerService.submit(new Runnable() {
			public void run() {
				try {
					logger.logUtterance(audioIn, timestampMillis);
				} catch (Exception e) {
					e.printStackTrace();
					sc.log("Event Logger Error", e);
				}
			}
		});
	}
}
