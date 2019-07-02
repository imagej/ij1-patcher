/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.awt.GraphicsEnvironment;

import org.junit.Test;

/**
 * Tests various {@link ij.ImagePlus}-related patches for ImageJ 1.x.
 * 
 * @author Johannes Schindelin
 */
public class ImagePlusIT {
	@Test
	public void testAddSliceTo2DCompositeImage() throws ClassNotFoundException {
		assumeTrue(!GraphicsEnvironment.isHeadless());

		final String property = "window.type";
		System.clearProperty(property);

		final LegacyEnvironment ij1 = TestUtils.getTestEnvironment(false, false);
		final String javascript =
			"importClass(Packages.ij.CompositeImage);" + //
			"importClass(Packages.ij.IJ);" + //
			"importClass(Packages.ij.gui.ImageWindow);" + //
			"importClass(Packages.java.lang.System);" + //
			"var imp = IJ.createImage('composite', 16, 16, 1, 16);" + //
			"var composite = new CompositeImage(imp, CompositeImage.COMPOSITE);" + //
			"composite.setWindow(new ImageWindow(composite));" + //;
			"IJ.run('Add Slice', 'add=channel');" + //
			"System.setProperty('" + property + "', composite.getWindow().getClass().getName());";
		final String macro =
			"setBatchMode(true);" + //
			"eval('script', '" + javascript.replaceAll("'", "\"") + "');";
		ij1.runMacro(macro, "");
		assertEquals("ij.gui.StackWindow", System.getProperty(property));
	}
}
