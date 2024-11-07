/*
 * #%L
 * ImageJ2 software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2024 ImageJ2 developers.
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

import static net.imagej.patcher.TestUtils.getTestEnvironment;
import static net.imagej.patcher.TestUtils.makeJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.scijava.test.TestUtils.createTemporaryDirectory;
import ij.ImageJ;

import java.applet.Applet;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.util.FileUtils;

/**
 * Tests the support for <i>ij1.plugin.dirs</i> (falling back to the <i>.plugins/</i> subdirectory of <i>user.home</i>).
 * 
 * @author Johannes Schindelin
 */
public class ExtraPluginDirsTest {

	static {
		try {
			LegacyInjector.preinit();
		}
		catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

	private File tmpDir;
	private String threadName;
	private ClassLoader threadLoader;

	@After
	public void after() {
		if (threadName != null) Thread.currentThread().setName(threadName);
		if (threadLoader != null) Thread.currentThread().setContextClassLoader(threadLoader);
	}

	@Before
	public void before() throws IOException {
		threadName = Thread.currentThread().getName();
		threadLoader = Thread.currentThread().getContextClassLoader();
		tmpDir = createTemporaryDirectory("legacy-");
	}

	@Test
	public void findsExtraPluginDir() throws Exception {
		final File jarFile = new File(tmpDir, "Set_Property.jar");
		makeJar(jarFile, Set_Property.class.getName());
		assertTrue(jarFile.getAbsolutePath() + " exists", jarFile.exists());
		System.setProperty("ij1.plugin.dirs", tmpDir.getAbsolutePath());

		final String key = "random-" + Math.random();
		System.setProperty(key, "321");
		final LegacyEnvironment ij1 = getTestEnvironment(true, true);
		ij1.run("Set Property", "key=" + key + " value=123");
		assertEquals("123", System.getProperty(key));
	}

	@Test
	public void knowsAboutJarsDirectory() throws Exception {
		final File pluginsDir = new File(tmpDir, "plugins");
		assertTrue(pluginsDir.mkdirs());
		final File jarsDir = new File(tmpDir, "jars");
		assertTrue(jarsDir.mkdirs());
		LegacyEnvironment ij1 = getTestEnvironment();
		final String helperClassName = TestUtils.class.getName();
		try {
			assertNull(ij1.runPlugIn(helperClassName, null));
		} catch (Throwable t) {
			/* all okay, we did not find the class */
		}
		final File jarFile = new File(jarsDir, "helper.jar");
		makeJar(jarFile, helperClassName);
		System.setProperty("plugins.dir", pluginsDir.getAbsolutePath());
		ij1 = getTestEnvironment();
		try {
			assertNotNull(ij1.runPlugIn(helperClassName, null));
		} catch (Throwable t) {
			t.printStackTrace();
			assertNull("Should have found " + helperClassName + " in " + jarFile);
		}
	}

	@Test
	public void extraDirectory() throws Exception {
		// empty tmpDir
		for (final File file : tmpDir.listFiles()) {
			if (file.isDirectory()) FileUtils.deleteRecursively(file);
			else file.delete();
		}

		// copy plugin and write plugins.config into plain directory
		final String path = Headless_Example_Plugin.class.getName().replace('.', '/') + ".class";
		final File output = new File(tmpDir, path);
		assertTrue(output.getParentFile().mkdirs());
		final OutputStream out = new FileOutputStream(output);
		final InputStream in = getClass().getResource("/" + path).openStream();
		final byte[] buffer = new byte[65536];
		for (;;) {
			int count = in.read(buffer);
			if (count < 0) break;
			out.write(buffer, 0, count);
		}
		in.close();
		out.close();
		final PrintStream print = new PrintStream(new File(tmpDir, "plugins.config"));
		final String menuLabel = "Set Classfile URL property";
		print.println("Plugins, \"" + menuLabel + "\", " + Headless_Example_Plugin.class.getName() + "(\"ClassfileURL\")");
		print.close();

		// run that plugin
		final String property = "ij.patcher.test." + Math.random();
		System.clearProperty(property);
		assertNull(System.getProperty(property));
		final LegacyEnvironment ij1 = getTestEnvironment();
		ij1.addPluginClasspath(tmpDir);
		ij1.run(menuLabel, "property=" + property);
		assertEquals(tmpDir.toURI().toURL().toString() + path, System.getProperty(property));
	}

	@Test
	public void correctSubmenu() throws Exception {
		assumeTrue(!GraphicsEnvironment.isHeadless());
		final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			final File jarFile = new File(tmpDir, "Submenu_Test.jar");
			makeJar(jarFile, "Submenu_Test");
			assertTrue(jarFile.getAbsolutePath() + " exists", jarFile.exists());
			System.setProperty("ij1.plugin.dirs", tmpDir.getAbsolutePath());

			final LegacyEnvironment ij1 = getTestEnvironment(false, true);
			final Class<?> imagej = ij1.getClassLoader().loadClass(ImageJ.class.getName());
			imagej.getConstructor(Applet.class, Integer.TYPE).newInstance(null, ImageJ.NO_SHOW);
			ij1.run("Submenu Test", "menupath=[Plugins>Submenu Test] class=Submenu_Test");
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}
}
