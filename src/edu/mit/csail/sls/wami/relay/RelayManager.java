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
package edu.mit.csail.sls.wami.relay;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import javax.servlet.http.HttpSession;

import edu.mit.csail.sls.wami.WamiConfig;

/**
 * Manages all the relays for the servlet.
 */
public class RelayManager {
	/**
	 * parameterized via xml
	 */
	private HashMap<String, WamiRelay> activeRelays = new HashMap<String, WamiRelay>();

	private int maxActiveRelays;

	private long nextTimeout;

	/**
	 * timeout in milleseconds (set in config.xml);
	 */
	private long timeout;

	private long noPollFromClientTimeout;

	public List<WamiRelay> getActiveRelays() {
		List<WamiRelay> relays = new Vector<WamiRelay>();
		Iterator<WamiRelay> it = activeRelays.values().iterator();
		while (it.hasNext()) {
			relays.add(it.next());
		}
		return relays;
	}

	public RelayManager(int maxActiveRelays, long relayTimeout,
			long noPollFromClientTimeout) {
		System.out.println("New RelayManager started for " + maxActiveRelays
				+ " simulteanous active relays");
		this.maxActiveRelays = maxActiveRelays;
		this.timeout = relayTimeout;
		this.noPollFromClientTimeout = noPollFromClientTimeout;
		new Thread(new TimeoutThread()).start();
	}

	public synchronized void remove(WamiRelay relay) {
		System.out.println("Removing relay: " + relay);
		activeRelays.remove(relay.getWamiSessionID());
	}

	/**
	 * This is *not* perfect, because you should be immediately adding a relay
	 * after asking. But should suffice for now
	 */
	public synchronized boolean isCapacityAvailable() {
		return isCapacityAvailable(null);
	}

	/**
	 * Check if capacity is available, assuming we were to remove the passed in
	 * relay (this is useful for page reloads)
	 */
	public synchronized boolean isCapacityAvailable(WamiRelay relay) {
		int activeSize = activeRelays.size();
		System.out.println("Initial activeSize: " + activeSize);
		if (relay != null && activeRelays.values().contains(relay)) {
			activeSize--; // assume this one will be removed
		}
		System.out.println("Final activeSize: " + activeSize);
		return activeSize < maxActiveRelays;
	}

	public synchronized void addRelay(WamiRelay relay, String wsessionid)
			throws ReachedCapacityException {
		WamiRelay oldRelay = getRelay(wsessionid);

		if (!isCapacityAvailable(oldRelay)) {
			int numUsers = getActiveRelays().size();
			System.out.println("Already reached capacity of " + numUsers
					+ " user(s).");
			throw new ReachedCapacityException(
					"Relay manager reached capacity.", new Date(
							getNextTimeout()));
		}

		System.out.println("Adding relay: " + relay + " at " + wsessionid);
		activeRelays.put(wsessionid, relay);
		long curTime = System.currentTimeMillis();
		nextTimeout = curTime + timeout;
	}

	public WamiRelay getRelay(String wsessionid) {
		System.out.println("Getting relay at: " + wsessionid);
		return activeRelays.get(wsessionid);
	}

	/**
	 * Return the earliest possible time a session might time out
	 */
	public synchronized long getNextTimeout() {
		return nextTimeout;
	}

	/**
	 * closes all active relays
	 */
	public synchronized void close() {
		for (WamiRelay relay : activeRelays.values()) {
			relay.close();
		}
		activeRelays.clear();
	}

	private class TimeoutThread implements Runnable {
		public void run() {
			while (true) {
				long curTime = System.currentTimeMillis();

				long sleepTime = Math.min(noPollFromClientTimeout, timeout);
				synchronized (RelayManager.this) {
					nextTimeout = curTime;
					if (activeRelays.size() > 0) {
						Iterator<Entry<String, WamiRelay>> it = activeRelays
								.entrySet().iterator();

						while (it.hasNext()) {
							Entry<String, WamiRelay> entry = it.next();
							WamiRelay relay = entry.getValue();
							if (timeoutSession(relay, curTime)
									|| timeoutPolling(relay, curTime)) {
								relay.close();
								it.remove();
							}
						}
					}
				}

				try {
					// System.out.println("RelayManager" + this +
					// "sleeping for: "
					// + sleepTime);
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		private boolean timeoutPolling(WamiRelay relay, long curTime) {
			long timeSincePoll = relay.getTimeSinceLastPollEnded();

			System.out.println("timeSincePoll: " + timeSincePoll
					+ " maxtime = " + noPollFromClientTimeout);

			if (timeSincePoll >= noPollFromClientTimeout) {
				System.out.println("TIMING OUT (no polling): " + relay);
				return true;
			} else {
				relay.forceRepoll(); // force repoll
			}

			return false;
		}

		private boolean timeoutSession(WamiRelay relay, long curTime) {
			long lastTime = relay.getTimeLastMessageSent();
			long timeElapsed = curTime - lastTime;
			if (timeElapsed < 0) {
				timeElapsed = 0;
			}

			System.out.println("timeSinceMessage: " + timeElapsed
					+ " maxtime = " + timeout);

			if (timeElapsed >= timeout) {
				System.out.println("TIMEOUT (no messages): " + relay);
				relay.timeout();
				return true;
			}

			return false;
		}
	}

	public static RelayManager getManager(HttpSession session) {
		RelayManager manager = (RelayManager) session.getServletContext()
				.getAttribute("relayManager");

		if (manager == null) {
			WamiConfig wc = WamiConfig.getConfiguration(session
					.getServletContext());

			manager = new RelayManager(wc.getMaxRelays(), wc
					.getRelayTimeout(session), wc.getNoPollFromClientTimeout());

			session.getServletContext().setAttribute("relayManager", manager);
		} 

		return manager;
	}

}
