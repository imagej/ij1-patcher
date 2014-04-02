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

package imagej.patcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import ij.Macro;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that the legacy headless code works as expected.
 * 
 * @author Johannes Schindelin
 */
public class HeadlessEnvironmentTest {
	static {
		try {
			LegacyInjector.preinit();
		}
		catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

	@Test
	public void testMacro() throws Exception {
		final LegacyEnvironment ij1 = new LegacyEnvironment(null, true);
		final String propertyName = "headless.test.property" + Math.random();
		final String propertyValue = "Hello, world!";
		System.setProperty(propertyName, "(unset)");
		assertFalse(propertyValue.equals(System.getProperty(propertyName)));
		ij1.runMacro("call(\"java.lang.System.setProperty\", \"" + propertyName
				+ "\", getArgument());", propertyValue);
		assertEquals(propertyValue, System.getProperty(propertyName));
	}

	@Test
	public void testEncapsulation() throws Exception {
		/*
		 * ImageJ 1.x' ij.Macro.getOptions() always returns null unless the
		 * thread name starts with "Run$_".
		 */
		final Thread thread = Thread.currentThread();
		final String savedName = thread.getName();
		thread.setName("Run$_" + savedName);
		try {
			Macro.setOptions("(unset)");
			assertEquals("(unset) ", Macro.getOptions());
			final LegacyEnvironment ij1 = new LegacyEnvironment(null, true);
			ij1.runMacro("call(\"ij.Macro.setOptions\", \"Hello, world!\");",
					null);
			assertEquals("(unset) ", Macro.getOptions());
			final Method getOptions = ij1.getClassLoader()
					.loadClass("ij.Macro").getMethod("getOptions");
			assertEquals("Hello, world! ", getOptions.invoke(null));
		} finally {
			thread.setName(savedName);
		}
	}

	private String threadName;

	@Before
	public void saveThreadName() {
		threadName = Thread.currentThread().getName();
	}

	@After
	public void restoreThreadName() {
		if (threadName != null) Thread.currentThread().setName(threadName);
	}

	@Test
	public void testHeadless() throws Exception {
		assertTrue(runExampleDialogPlugin(true));
	}

	@Test
	public void testPatchIsRequired() throws Exception {
		assumeTrue(GraphicsEnvironment.isHeadless());
		assertFalse(runExampleDialogPlugin(false));
	}

	@Test
	public void saveDialog() throws Exception {
		assertTrue(runExamplePlugin(true, "SaveDialog", "file=README.txt", "true"));
	}

	@Test
	public void booleanTest() throws Exception {
		runExamplePlugin(true, "BooleanParameter", "key=[This is the key!] key", "This is the key! true");
		runExamplePlugin(true, "BooleanParameter", "key=[This is the key!] key ", "This is the key! true");
		runExamplePlugin(true, "BooleanParameter", "key=[This is the key!] key=1", "This is the key! false");
		runExamplePlugin(true, "BooleanParameter", "key=[This is the key!] key1", "This is the key! false");
		runExamplePlugin(true, "BooleanParameter", "key=[This is the next key!]", "This is the next key! false");
	}

	private static boolean runExampleDialogPlugin(final boolean patchHeadless) throws Exception {
		return runExamplePlugin(patchHeadless, "the argument", "prefix=[*** ]", "*** the argument");
	}

	private static boolean runExamplePlugin(final boolean patchHeadless, final String arg, final String macroOptions, final String expectedValue) throws Exception {
		final LegacyEnvironment ij1 = new LegacyEnvironment(null, patchHeadless);
		ij1.addPluginClasspath(HeadlessEnvironmentTest.class.getClassLoader());
		try {
			ij1.setMacroOptions(macroOptions);
			final String value = ij1.runPlugIn(
					Headless_Example_Plugin.class.getName(), arg).toString();
			assertEquals(expectedValue, value);
			return true;
		} catch (final Throwable t) {
			if (t instanceof Error) {
				throw (Error) t;
			}
			if (!(t instanceof InvocationTargetException)
					|| t.getCause() == null
					|| !(t.getCause() instanceof HeadlessException)) {
				t.printStackTrace();
			}
			return false;
		}
	}

}
