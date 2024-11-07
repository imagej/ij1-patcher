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

import static net.imagej.patcher.TestUtils.invokeStatic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.scijava.test.TestUtils.createTemporaryDirectory;

import java.io.File;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

import org.junit.Test;

/**
 * This unit test verifies that bare {@code .class} plugins are picked up correctly.
 * 
 * @author Johannes Schindelin
 */
public class BarePluginsTest {

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

	@Test
	public void testBarePlugin() throws Exception {
		// generate simple plugin
		final File tmp = createTemporaryDirectory("bare-plugins-");
		final File plugins = new File(tmp, "plugins");
		assertTrue(plugins.mkdir());

		final ClassPool pool = new ClassPool();
		pool.appendClassPath(new ClassClassPath(getClass()));
		final CtClass clazz = pool.makeClass("Bare_Test_Plugin");
		clazz.addField(CtField.make("public static java.lang.String value;", clazz));
		clazz.addInterface(pool.get("ij.plugin.PlugIn"));
		clazz.addMethod(CtMethod.make("public void run(java.lang.String arg) {" +
			"  value = \"bare \" + ij.Macro.getOptions().trim();" +
			"}",
			clazz));
		clazz.addMethod(CtMethod.make("public static java.lang.String get() {" +
				"  return value;" +
				"}",
				clazz));
		clazz.writeFile(plugins.getPath());

		final String pluginsDir = System.getProperty("plugins.dir");
		try {
			System.setProperty("plugins.dir", plugins.getPath());
			final LegacyEnvironment ij1 = new LegacyEnvironment(null, true);
			ij1.noPluginClassLoader();
			final String message = "Yep, that's the bare plugin alright!";
			ij1.run("Bare Test Plugin", message);
			final ClassLoader loader = invokeStatic(ij1.getClassLoader(), "ij.IJ", "getClassLoader");
			assertEquals("bare " + message, invokeStatic(loader, "Bare_Test_Plugin", "get"));
		} finally {
			if (pluginsDir == null) {
				System.clearProperty("plugins.dir");
			} else {
				System.setProperty("plugins.dir", pluginsDir);
			}
		}
	}

	/**
	 * Verifies that bare {@code HandleExtraFileTypes.class} files are picked up correctly.
	 * <p>
	 * See http://imagej.1557.x6.nabble.com/Handle-Extra-Files-Types-not-working-tp5008224.html.
	 * </p>
	 * 
	 * @throws Exception
	 */
	@Test
	public void testHandleExtraFileTypes() throws Exception {
		// generate simple plugin
		final File tmp = createTemporaryDirectory("handle-extra-file-types-");
		final File plugins = new File(tmp, "plugins");
		assertTrue(plugins.mkdir());

		final ClassPool pool = new ClassPool();
		pool.appendClassPath(new ClassClassPath(getClass()));
		final CtClass clazz = pool.makeClass("HandleExtraFileTypes");
		clazz.addMethod(CtMethod.make("public java.lang.String toString() {" +
			"  return \"It works!\";" +
			"}",
			clazz));
		clazz.writeFile(plugins.getPath());

		final String pluginsDir = System.getProperty("plugins.dir");
		try {
			System.setProperty("plugins.dir", plugins.getPath());
			final LegacyEnvironment ij1 = new LegacyEnvironment(null, true);
			ij1.noPluginClassLoader();
			//ij1.runPlugIn("ij.IJ.init", "");
			Object handleExtraFileTypes = ij1.runPlugIn("HandleExtraFileTypes", "");
			assertNotNull(handleExtraFileTypes);
			assertEquals("It works!", handleExtraFileTypes.toString());
		} finally {
			if (pluginsDir == null) {
				System.clearProperty("plugins.dir");
			} else {
				System.setProperty("plugins.dir", pluginsDir);
			}
		}
	}
}
