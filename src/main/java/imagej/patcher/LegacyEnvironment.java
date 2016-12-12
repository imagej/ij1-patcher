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

package imagej.patcher;

import imagej.patcher.LegacyInjector.Callback;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Encapsulates an ImageJ 1.x "instance".
 * <p>
 * This class is a partner to the {@link LegacyClassLoader}, intended to make
 * sure that the ImageJ 1.x contained in a given class loader is patched and can
 * be accessed conveniently.
 * </p>
 * 
 * @author "Johannes Schindelin"
 */
@Deprecated
public class LegacyEnvironment {

	final private boolean headless;
	final private LegacyInjector injector;
	private Throwable initializationStackTrace;
	private ClassLoader loader;
	private Method setOptions, run, runMacro, runPlugIn, main;
	private Field _hooks;

	/**
	 * Constructs a new legacy environment.
	 * 
	 * @param loader the {@link ClassLoader} to use for loading the (patched)
	 *          ImageJ 1.x classes; if {@code null}, a {@link LegacyClassLoader}
	 *          is constructed.
	 * @param headless whether to patch in support for headless operation
	 *          (compatible only with "well-behaved" plugins, i.e. plugins that do
	 *          not use graphical components directly)
	 * @throws ClassNotFoundException
	 */
	public LegacyEnvironment(final ClassLoader loader, final boolean headless)
		throws ClassNotFoundException
	{
		this(loader, headless, new LegacyInjector());
	}

	LegacyEnvironment(final ClassLoader loader, final boolean headless,
		final LegacyInjector injector) throws ClassNotFoundException
	{
		this.headless = headless;
		this.loader = loader;
		this.injector = injector;
	}

	private boolean isInitialized() {
		return _hooks != null;
	}

	private synchronized void initialize() {
		if (isInitialized()) return;
		initializationStackTrace = new Throwable("Initialized here:");
		if (loader != null) {
			injector.injectHooks(loader, headless);
		}
		try {
			this.loader =
				loader != null ? loader : new LegacyClassLoader(headless, injector);
			final Class<?> ij = this.loader.loadClass("ij.IJ");
			final Class<?> imagej = this.loader.loadClass("ij.ImageJ");
			final Class<?> macro = this.loader.loadClass("ij.Macro");
			_hooks = ij.getField("_hooks");
			setOptions = macro.getMethod("setOptions", String.class);
			run = ij.getMethod("run", String.class, String.class);
			runMacro = ij.getMethod("runMacro", String.class, String.class);
			runPlugIn = ij.getMethod("runPlugIn", String.class, String.class);
			main = imagej.getMethod("main", String[].class);
		}
		catch (final Exception e) {
			throw new RuntimeException("Found incompatible ij.IJ class", e);
		}
		// TODO: if we want to allow calling IJ#run(ImagePlus, String, String), we
		// will need a data translator
	}

	private void ensureUninitialized() {
		if (isInitialized()) {
			final StringWriter string = new StringWriter();
			final PrintWriter writer = new PrintWriter(string);
			initializationStackTrace.printStackTrace(writer);
			writer.close();
			throw new RuntimeException(
				"LegacyEnvironment was already initialized:\n\n" +
					string.toString().replaceAll("(?m)^", "\t"));
		}
	}

	public void disableIJ1PluginDirs() {
		ensureUninitialized();
		injector.after.add(new Callback() {
			@Override
			public void call(final CodeHacker hacker) {
				hacker.insertAtBottomOfMethod(EssentialLegacyHooks.class.getName(),
					"public <init>()",
					"enableIJ1PluginDirs(false);");
			}
		});
	}

	/**
	 * Adds the class path of a given {@link ClassLoader} to the plugin class
	 * loader.
	 * <p>
	 * This method is intended to be used in unit tests as well as interactive
	 * debugging from inside an Integrated Development Environment where the
	 * plugin's classes are not available inside a {@code .jar} file.
	 * </p>
	 * <p>
	 * At the moment, the only supported parameters are {@link URLClassLoader}s.
	 * </p>
	 * 
	 * @param fromClassLoader the class path donor
	 */
	public void addPluginClasspath(final ClassLoader fromClassLoader) {
		if (fromClassLoader == null) return;
		ensureUninitialized();
		for (ClassLoader loader = fromClassLoader; loader != null; loader =
			loader.getParent())
		{
			if (loader == this.loader) {
				break;
			}
			if (this.loader != null && loader == this.loader.getParent()) {
				break;
			}
			if (this.loader == null &&
				loader == getClass().getClassLoader().getParent())
			{
				break;
			}
			if (!(loader instanceof URLClassLoader)) {
				if (loader != fromClassLoader) continue;
				throw new IllegalArgumentException(
					"Cannot add class path from ClassLoader of type " +
						fromClassLoader.getClass().getName());
			}

			for (final URL url : ((URLClassLoader) loader).getURLs()) {
				if (!"file".equals(url.getProtocol())) {
					throw new RuntimeException("Not a file URL! " + url);
				}
				addPluginClasspath(new File(url.getPath()));
				final String path = url.getPath();
				if (path.matches(".*/target/surefire/surefirebooter[0-9]*\\.jar")) try {
					final JarFile jar = new JarFile(path);
					final Manifest manifest = jar.getManifest();
					if (manifest != null) {
						final String classPath =
							manifest.getMainAttributes().getValue(Name.CLASS_PATH);
						if (classPath != null) {
							for (final String element : classPath.split(" +"))
								try {
									final URL url2 = new URL(element);
									if (!"file".equals(url2.getProtocol())) continue;
									addPluginClasspath(new File(url2.getPath()));
								}
								catch (final MalformedURLException e) {
									e.printStackTrace();
								}
						}
					}
				}
				catch (final IOException e) {
					System.err
						.println("Warning: could not add plugin class path due to ");
					e.printStackTrace();
				}

			}
		}
	}

	/**
	 * Adds extra elements to the class path of ImageJ 1.x' plugin class loader.
	 * <p>
	 * The typical use case for a {@link LegacyEnvironment} is to run specific
	 * plugins in an encapsulated environment. However, in the case of multiple
	 * one wants to use multiple legacy environments with separate sets of plugins
	 * enabled, it becomes impractical to pass the location of the plugins'
	 * {@code .jar} files via the {@code plugins.dir} system property (because of
	 * threading issues).
	 * </p>
	 * <p>
	 * In other cases, the plugins' {@code .jar} files are not located in a single
	 * directory, or worse: they might be contained in a directory among
	 * {@code .jar} files one might <i>not</i> want to add to the plugin class
	 * loader's class path.
	 * </p>
	 * <p>
	 * This method addresses that need by allowing to add individual {@code .jar}
	 * files to the class path of the plugin class loader and ensuring that their
	 * {@code plugins.config} files are parsed.
	 * </p>
	 * 
	 * @param classpathEntries the class path entries containing ImageJ 1.x
	 *          plugins
	 */
	public void addPluginClasspath(final File... classpathEntries) {
		if (classpathEntries.length == 0) return;
		ensureUninitialized();

		final StringBuilder builder = new StringBuilder();
		for (final File file : classpathEntries) {
			final String quoted = file.getPath().replaceAll("[\\\"\\\\]", "\\\\$0").replaceAll("\n", "\\n");
			builder.append("addPluginClasspath(new java.io.File(\"").append(quoted).append("\"));");
		}

		injector.after.add(new Callback() {
			@Override
			public void call(final CodeHacker hacker) {
				hacker.insertAtBottomOfMethod(EssentialLegacyHooks.class.getName(),
					"public <init>()",
					builder.toString());
			}
		});
	}

	/**
	 * Sets the macro options.
	 * <p>
	 * Both {@link #run(String, String)} and {@link #runMacro(String, String)}
	 * take an argument that is typically recorded by the macro recorder. For
	 * {@link #runPlugIn(String, String)}, however, only the {@code arg} parameter
	 * that is to be passed to the plugins {@code run()} or {@code setup()} method
	 * can be specified. For those use cases where one wants to call a plugin
	 * class directly, but still provide macro options, this method is the
	 * solution.
	 * </p>
	 * 
	 * @param options the macro options to use for the next call to
	 *          {@link #runPlugIn(String, String)}
	 */
	public void setMacroOptions(final String options) {
		initialize();
		try {
			setOptions.invoke(null, options);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Runs {@code IJ.run(command, options)} in the legacy environment.
	 * 
	 * @param command the command to run
	 * @param options the options to pass to the command
	 */
	public void run(final String command, final String options) {
		initialize();
		final Thread thread = Thread.currentThread();
		final ClassLoader savedLoader = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		try {
			run.invoke(null, command, options);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			thread.setContextClassLoader(savedLoader);
		}
	}

	/**
	 * Runs {@code IJ.runMacro(macro, arg)} in the legacy environment.
	 * 
	 * @param macro the macro code to run
	 * @param arg an optional argument (which can be retrieved in the macro code
	 *          via {@code getArgument()})
	 */
	public void runMacro(final String macro, final String arg) {
		initialize();
		final Thread thread = Thread.currentThread();
		final String savedName = thread.getName();
		thread.setName("Run$_" + savedName);
		final ClassLoader savedLoader = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		try {
			runMacro.invoke(null, macro, arg);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			thread.setName(savedName);
			thread.setContextClassLoader(savedLoader);
		}
	}

	/**
	 * Runs {@code IJ.runPlugIn(className, arg)} in the legacy environment.
	 * 
	 * @param className the plugin class to run
	 * @param arg an optional argument (which get passed to the {@code run()} or
	 *          {@code setup()} method of the plugin)
	 */
	public Object runPlugIn(final String className, final String arg) {
		initialize();
		final Thread thread = Thread.currentThread();
		final String savedName = thread.getName();
		thread.setName("Run$_" + savedName);
		final ClassLoader savedLoader = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		try {
			return runPlugIn.invoke(null, className, arg);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			thread.setName(savedName);
			thread.setContextClassLoader(savedLoader);
		}
	}

	/**
	 * Runs {@code ImageJ.main(args)} in the legacy environment.
	 * 
	 * @param args the arguments to pass to the main() method
	 */
	public void main(final String... args) {
		initialize();
		Thread.currentThread().setContextClassLoader(loader);
		try {
			main.invoke(null, (Object) args);
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Gets the class loader containing the ImageJ 1.x classes used in this legacy
	 * environment.
	 * 
	 * @return the class loader
	 */
	public ClassLoader getClassLoader() {
		initialize();
		return loader;
	}

	/**
	 * Gets the ImageJ 1.x menu structure as a map
	 */
	public Map<String, String> getMenuStructure() {
		initialize();
		try {
			final LegacyHooks hooks = (LegacyHooks) _hooks.get(null);
			return hooks.getMenuStructure();
		}
		catch (final RuntimeException e) {
			throw e;
		}
		catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Launches a fully-patched, self-contained ImageJ 1.x.
	 * 
	 * @throws ClassNotFoundException
	 */
	public static LegacyEnvironment getPatchedImageJ1()
		throws ClassNotFoundException
	{
		final boolean headless = GraphicsEnvironment.isHeadless();
		return new LegacyEnvironment(new LegacyClassLoader(headless), headless);
	}
}
