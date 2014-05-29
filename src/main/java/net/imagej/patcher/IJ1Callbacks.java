/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.imagej.patcher;

import ij.IJ;
import ij.ImageJ;
import ij.gui.ProgressBar;

import java.util.HashSet;
import java.util.Set;

import net.imagej.patcher.LegacyHooks.SetProgress;

/**
 * An adapter for all the callback interfaces defined in {@link LegacyHooks}.
 * 
 * @author Johannes Schindelin
 */
public class IJ1Callbacks implements SetProgress {

	private static final Set<String> implementedInterfaces;

	static {
		implementedInterfaces = new HashSet<String>();
		for (final Class<?> iface : IJ1Callbacks.class.getInterfaces()) {
			implementedInterfaces.add(iface.getName());
		}
	}

	private static IJ1Callbacks instance;

	private final ProgressBar progressBar;

	public static boolean instanceOf(final String interfaceName) {
		return implementedInterfaces.contains(interfaceName);
	}

	public synchronized static IJ1Callbacks get() {
		if (instance == null) instance = new IJ1Callbacks();
		return instance;
	}

	public IJ1Callbacks() {
		final ImageJ ij1 = IJ.getInstance();
		if (ij1 == null) {
			progressBar = null;
		} else {
			progressBar = ij1.getProgressBar();
		}
	}

	@Override
	public void show(double progress) {
		if (progressBar != null) progressBar.show(progress, false);
	}

	@Override
	public void show(int currentIndex, int finalIndex) {
		if (progressBar != null) progressBar.show(currentIndex, finalIndex);
	}

}
