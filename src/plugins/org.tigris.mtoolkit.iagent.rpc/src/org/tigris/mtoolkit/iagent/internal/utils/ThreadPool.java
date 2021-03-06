/*******************************************************************************
 * Copyright (c) 2005, 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.iagent.internal.utils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class ThreadPool {

	private static final int MAX_WORKERS = 5;

	private static ThreadPool instance;
	private static int clientsCount = 0;

	public static final int OPTION_AGGRESSIVE = 0x00001;
	public static final int OPTION_NONE = 0;
	
	private static Constructor tssConstructor;
	
	static {
		try {
			tssConstructor = Thread.class.getConstructor(new Class[] { ThreadGroup.class, Runnable.class, String.class, long.class });
		} catch (Throwable t) {
			DebugUtils.info(ThreadPool.class, "VM doesn't support controlling the threads stack size", t);
		}
	}
	
	private volatile boolean running = true;
	private volatile int workers = 0;
	private volatile int working = 0;
	// holds the number of the threads, which are spawned
	private volatile int spawned;

	private final int options;

	private List workUnits = new ArrayList();

	private Object lock = new Object();

	private int maxWorkers;

	private Long threadStackSize = new Long(0);

	public ThreadPool(int maxWorkers, int options) {
		this.maxWorkers = maxWorkers;
		this.options = options;
		
		String stackSizeOption = System.getProperty("iagent.threads.stackSize");
		if (stackSizeOption != null) {
			try {
				threadStackSize = new Long(stackSizeOption);
			} catch (NumberFormatException e) {
				DebugUtils.error(ThreadPool.class, "Thread stack option has invalid value: " + threadStackSize + ". It will be ignored.");
			}
		}
	}

	public void stop() {
		running = false;
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	public synchronized static ThreadPool getPool() {
		if (instance == null)
			instance = new ThreadPool(MAX_WORKERS, OPTION_NONE);
		clientsCount++;
		return instance;
	}

	public synchronized static void releasePool(ThreadPool pool) {
		if (pool == null)
			throw new IllegalArgumentException();
		if (instance == pool) {
			clientsCount--;
			if (clientsCount == 0) {
				instance.stop();
				instance = null;
			}
		}
	}

	public boolean isEmpty() {
		return workUnits.isEmpty() && working == 0;
	}

	public void join() {
		synchronized (lock) {
			while ((!workUnits.isEmpty() || working != 0) && running) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	public void enqueueWork(Runnable runnable) {
		if (!running)
			throw new IllegalStateException();
		if (runnable == null)
			throw new NullPointerException();
		synchronized (lock) {
			workUnits.add(runnable);
			lock.notify();
			boolean isAggressive = (options & OPTION_AGGRESSIVE) != 0;
			if (workers == (isAggressive ? working + spawned : working) && workers < maxWorkers) {
				// spawn new worker
				spawned++;
				Worker worker = new Worker();
				Thread th = createThread(worker, threadStackSize);
				worker.start(th);
			}
		}
	}
	
	private static Thread createThread(Runnable runnable, Long threadStackSize) {
		if (tssConstructor != null)
			try {
				return (Thread) tssConstructor.newInstance(new Object[] { null, runnable, "Uninitialized mToolkit Worker", threadStackSize });
			} catch (Throwable t) {
				DebugUtils.error(ThreadPool.class, "Failed to create thread with specified stack size", t);
				// ignore the request if failed
			}
		return new Thread(runnable);
	}

	private class Worker implements Runnable {
		private final int workerId;
		private boolean initialized;

		public Worker() {
			synchronized (lock) {
				workerId = workers++;
			}
		}
		
		public void start(Thread thread) {
			thread.setName("mToolkit Worker #" + workerId);
			thread.setDaemon(true);
			thread.start();
		}

		public void run() {
			while (true) {
				Runnable unit;
				synchronized (lock) {
					if (!initialized) {
						initialized = true;
						spawned--;
					}
					while (workUnits.isEmpty() && running) {
						try {
							lock.wait(1000);
						} catch (InterruptedException e) {
						}
					}
					if (workUnits.isEmpty() /* && !running */) {
						// stop worker thread if there are no more work units
						// and the pool has been released/stopped
						workers--;
						return;
					}
					unit = (Runnable) workUnits.remove(0);
					working++;
				}
				try {
					unit.run();
				} catch (Throwable e) {
					// TODO: Log exception
					e.printStackTrace();
				} finally {
					synchronized (lock) {
						lock.notifyAll();
						working--;
					}
				}
			}
		}
	}
}
