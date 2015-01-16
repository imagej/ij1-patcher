/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2015 Board of Regents of the University of
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import ij.IJ;
import ij.ImageJ;

import java.awt.GraphicsEnvironment;

import org.junit.Test;

/**
 * Tests for verifying that ImageJ shuts down properly under various
 * circumstances.
 * 
 * @author Curtis Rueden
 */
public class LegacyHooksTest {

	static {
		try {
			LegacyInjector.preinit();
		}
		catch (final Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

	/**
	 * Tests that various shutdown-related hooks are working as expected.
	 * Specifically, the methods {@link LegacyHooks#quit()},
	 * {@link LegacyHooks#interceptCloseAllWindows()} and
	 * {@link LegacyHooks#disposing()} are tested.
	 */
	@Test
	public void testShutdownHooks() throws Exception {
		// we really cannot test this in headless mode
		assumeTrue(!GraphicsEnvironment.isHeadless());

		final Boolean[] results = new Boolean[3];
		final Thread[] quitThread = new Thread[1];

		// tweak the legacy hooks to record when shutdown hooks are called
		LegacyInjector.installHooks(Thread.currentThread().getContextClassLoader(),
			new LegacyHooks() {

				@Override
				public boolean quit() {
					results[0] = results[0] == null ? true : false;
					return super.quit();
				}

				@Override
				public boolean interceptCloseAllWindows() {
					results[1] = results[1] == null ? true : false;
					return super.interceptCloseAllWindows();
				}

				@Override
				public boolean disposing() {
					results[2] = results[2] == null ? true : false;
					synchronized (quitThread) {
						quitThread[0] = Thread.currentThread();
						quitThread.notify();
					}
					return super.disposing();
				}
			});

		// NB: It is OK to reference ImageJ1 classes here, as a variable *inside*
		// a method, because the static class initializer (and hence the
		// LegacyInjector.preinit() call) will be run before loading them. This
		// would not be the case if e.g. any class reference leak into the API as
		// method arguments, return values or field types.
		final ImageJ ij = new ImageJ(ImageJ.NO_SHOW);

		ij.exitWhenQuitting(false);
		ij.quit();

		final long timeout = 10; // maximum timeout during ImageJ1 shutdown

		// verify that there is an IJ1 Quit thread now
		synchronized (quitThread) {
			if (quitThread[0] == null) quitThread.wait(1000 * timeout);
		}
		assertNotNull("ImageJ1 Quit thread is not set", quitThread[0]);

		// verify that IJ1 indeed spawned a new thread to quit
		if (quitThread[0] == Thread.currentThread()) {
			fail("ImageJ1 is not quitting on a new thread");
		}

		// wait for the IJ1 Quit thread to terminate
		quitThread[0].join(1000 * timeout);
		if (quitThread[0].isAlive()) {
			fail("ImageJ1 failed to quit after " + timeout + " seconds");
		}

		// verify that ImageJ1 has shut down
		assertNull(IJ.getInstance());

		// verify that legacy hooks methods were called correctly
		assertValid("quit", results[0]);
		assertValid("interceptCloseAllWindows", results[1]);
		assertValid("disposing", results[2]);
	}

	private void assertValid(final String method, final Boolean value) {
		assertNotNull("LegacyHooks#" + method + "() not called", value);
		assertTrue("LegacyHooks#" + method + "() called more than once", value);
	}

}
