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

import static net.imagej.patcher.TestUtils.getTestEnvironment;
import static org.scijava.test.TestUtils.createTemporaryDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import javassist.ClassPool;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.util.FileUtils;

/**
 * Tests that fat .jar files are put at the end of the class path of the
 * PluginClassLoader.
 * <p>
 * Fat .jar files such as batik.jar (i.e. .jar files bundling waaaay too much)
 * interfere with other fat .jar files, and of course with the stand-alone .jar
 * files they ingested (in the case of Batik, it bundles e.g. xalan and
 * xml-apis, causing problems when said stand-alone files are upgraded).
 * </p>
 * <p>
 * For that reason, we move these files to the end of the class path. This tests
 * ensures that changes in ImageJ 1.x do not break this functionality.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class FatJarTest {

	static {
		try {
			LegacyInjector.preinit();
		}
		catch (NoClassDefFoundError e) {
			// ignore: LegacyInjector not in *this* classpath
		}
		catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

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
	}

	@Test
	public void testFatJars() throws Exception {
		final File tmp = createTemporaryDirectory("fat-jars-");
		final File plugins = new File(tmp, "plugins");
		assertTrue(plugins.exists() || plugins.mkdir());
		final File jars = new File(tmp, "jars");
		if (jars.exists()) {
			FileUtils.deleteRecursively(jars);
		}
		assertTrue(jars.mkdir());

		// put two files into it, xalan.jar and batik.jar, with differing versions
		// of the class "FatJarTest"
		CodeHacker hacker = new CodeHacker(new URLClassLoader(new URL[0], getClass().getClassLoader()), new ClassPool(true));
		hacker.insertNewMethod(getClass().getName(), "public java.lang.String toString()", "return \"1\";");
		hacker.writeJar(new File(jars, "batik.jar"));

		hacker = new CodeHacker(new URLClassLoader(new URL[0], getClass().getClassLoader()), new ClassPool(true));
		hacker.insertNewMethod(getClass().getName(), "public java.lang.String toString()", "return \"2\";");
		hacker.writeJar(new File(jars, "xalan.jar"));

		// At this point, we expect batik.jar to be discovered before xalan.jar
		String expect = "2";
		if (!jars.list()[0].equals("batik.jar")) {
			System.err.println("first entry is " + jars.list()[0]);
			// if not, make it so!
			assertTrue(new File(jars, "batik.jar").renameTo(new File(jars, "xalax.jar")));
			assertTrue(new File(jars, "xalan.jar").renameTo(new File(jars, "batik.jar")));
			System.err.println("Now, first entry is " + jars.list()[0]);
			if (!jars.list()[0].equals("batik.jar")) {
				assertTrue(new File(jars, "xalax.jar").renameTo(new File(jars, "xalaa.jar")));
			}
			assertTrue(jars.list()[0].equals("batik.jar"));
			expect = "1";
		}

		final String savedPluginsDir = System.getProperty("plugins.dir");

		try {
			System.setProperty("plugins.dir", tmp.getAbsolutePath());
			final LegacyEnvironment ij1 = getTestEnvironment();
			assertEquals(expect, ij1.runPlugIn(getClass().getName(), "").toString());
		}
		finally {
			if (savedPluginsDir == null) {
				System.clearProperty("plugins.dir");
			}
			else {
				System.setProperty("plugins.dir", savedPluginsDir);
			}
		}
	}
}
