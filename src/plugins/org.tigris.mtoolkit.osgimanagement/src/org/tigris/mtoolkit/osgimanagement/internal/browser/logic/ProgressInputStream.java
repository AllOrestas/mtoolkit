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
package org.tigris.mtoolkit.osgimanagement.internal.browser.logic;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.IProgressMonitor;

public class ProgressInputStream extends InputStream {

  byte b[] = new byte[1];
  private InputStream source;
  private IProgressMonitor monitor;
  
  public ProgressInputStream(InputStream source, IProgressMonitor monitor) {
    this.source = source;
    this.monitor = monitor;
  }
  
  public int read() throws IOException {
    return read(b);
  }
  
  public int read(byte b[]) throws IOException {
    return read(b, 0, b.length);
  }

  public int read(byte b[], int off, int len) throws IOException {
    int read = source.read(b, off, len);
    if (read != -1) {
      monitor.worked(read);
    }
    return read;
  }
  
  public void close() throws IOException {
    monitor.done();
    source.close();
  }

}