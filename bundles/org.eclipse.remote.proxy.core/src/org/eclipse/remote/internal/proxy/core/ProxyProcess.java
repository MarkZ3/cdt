/*******************************************************************************
 * Copyright (c) 2016 Oak Ridge National Laboratory and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.remote.internal.proxy.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.remote.core.IRemoteProcess;
import org.eclipse.remote.core.IRemoteProcessControlService;
import org.eclipse.remote.core.IRemoteProcessSignalService;
import org.eclipse.remote.core.IRemoteProcessTerminalService;
import org.eclipse.remote.proxy.protocol.core.Protocol;
import org.eclipse.remote.proxy.protocol.core.StreamChannel;

public class ProxyProcess implements IRemoteProcessControlService, IRemoteProcessTerminalService {
	private IRemoteProcess remoteProcess;
	
	private final StreamChannel stdIOChan;
	private final StreamChannel stdErrChan;
	private final StreamChannel controlChan;
	private final DataOutputStream cmdStream;
	private final DataInputStream resultStream;
	private final Thread cmdThread;

	private volatile int exitValue;
	private volatile boolean isCompleted;
	
	public static class Factory implements IRemoteProcess.Service.Factory {
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.remote.core.IRemoteProcess.Service.Factory#getService(org.eclipse.remote.core.IRemoteProcess,
		 * java.lang.Class)
		 */
		@SuppressWarnings("unchecked")
		@Override
		public <T extends IRemoteProcess.Service> T getService(IRemoteProcess remoteProcess, Class<T> service) {
			if (ProxyProcess.class.equals(service)) {
				return (T) new ProxyProcess(remoteProcess);
			}
			if (IRemoteProcessControlService.class.equals(service) || IRemoteProcessSignalService.class.equals(service)
					|| IRemoteProcessTerminalService.class.equals(service)) {
				return (T) remoteProcess.getService(ProxyProcess.class);
			}
			return null;
		}
	}

	protected ProxyProcess(IRemoteProcess process) {
		remoteProcess = process;
		ProxyProcessBuilder builder = (ProxyProcessBuilder)process.getProcessBuilder();
		List<StreamChannel> streams = builder.getStreams();
		controlChan = streams.get(0);
		stdIOChan = streams.get(1);
		stdErrChan = streams.size() > 2 ? streams.get(2) : null;
		cmdStream = new DataOutputStream(controlChan.getOutputStream());
		resultStream = new DataInputStream(controlChan.getInputStream());
		isCompleted = false;
		exitValue = 0;
		
		cmdThread = new Thread("process result reader") { //$NON-NLS-1$
			@Override
			public void run() {
				try {
					exitValue = resultStream.readInt();
				} catch (IOException e) {
					// Finish
				}
				isCompleted = true;
				try {
					stdIOChan.close();
				} catch (IOException e1) {
					// Ignore
				}
				try {
					if (stdErrChan != null) {
						stdErrChan.close();
					}
				} catch (IOException e1) {
					// Ignore
				}
				try {
					controlChan.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		};
		cmdThread.start();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Process#destroy()
	 */
	@Override
	public void destroy() {
		try {
			cmdStream.writeByte(Protocol.CONTROL_KILL);
			cmdStream.flush();
		} catch (IOException e) {
			isCompleted = true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Process#exitValue()
	 */
	@Override
	public int exitValue() {
		if (!isCompleted) {
			throw new IllegalThreadStateException();
		}
		return exitValue;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Process#getErrorStream()
	 */
	@Override
	public InputStream getErrorStream() {
		if (stdErrChan == null) {
			return new  InputStream() {
				@Override
				public int read() throws IOException {
					return -1;
				}

				@Override
				public int available() {
					return 0;
				}
			};
		}
		return stdErrChan.getInputStream();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Process#getInputStream()
	 */
	@Override
	public InputStream getInputStream() {
		return stdIOChan.getInputStream();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Process#getOutputStream()
	 */
	@Override
	public OutputStream getOutputStream() {
		return stdIOChan.getOutputStream();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Process#waitFor()
	 */
	@Override
	public int waitFor() throws InterruptedException {
		cmdThread.join();
		return exitValue;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.remote.core.RemoteProcess#isCompleted()
	 */
	@Override
	public boolean isCompleted() {
		return isCompleted;
	}

	@Override
	public IRemoteProcess getRemoteProcess() {
		return remoteProcess;
	}

	@Override
	public void setTerminalSize(int cols, int rows, int pwidth, int pheight) {
		try {
			cmdStream.writeByte(Protocol.CONTROL_SETTERMINALSIZE);
			cmdStream.writeInt(cols);
			cmdStream.writeInt(rows);
			cmdStream.writeInt(pwidth);
			cmdStream.writeInt(pheight);
			cmdStream.flush();
		} catch (IOException e) {
			// Dealt with somewhere else hopefully
		}
	}
}
