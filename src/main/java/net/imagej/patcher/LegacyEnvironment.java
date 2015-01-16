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

import static net.imagej.patcher.LegacyInjector.ESSENTIAL_LEGACY_HOOKS_CLASS;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Map;

import net.imagej.patcher.LegacyInjector.Callback;

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
		try {
			this.loader = loader != null ? loader : new LegacyClassLoader();
			injector.injectHooks(loader, headless);
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
		// TODO: if we want to allow calling IJ#run(ImagePlus, String, String),
		// we will need a data translator
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

	/**
	 * Disallows the encapsulated ImageJ 1.x from parsing the directories listed
	 * in {@code ij1.plugin.dirs}.
	 * <p>
	 * The patched ImageJ 1.x has a feature where it interprets the value of the
	 * {@code ij1.plugin.dirs} system property as a list of directories in which
	 * to discover plugins in addition to {@code <imagej.dir>/plugins}. In the
	 * case that the {@code ij1.plugin.dirs} property is not set, the directory
	 * {@code $HOME/.plugins/} -- if it exists -- is inspected instead.
	 * </p>
	 * <p>
	 * This is a convenient behavior when the user starts up ImageJ 1.x as an
	 * application, but it is less than desirable when running in a cluster or
	 * from unit tests. For such use cases, this method needs to be called in
	 * order to disable additional plugin directories.
	 * </p>
	 */
	public void disableIJ1PluginDirs() {
		ensureUninitialized();
		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				hacker.insertAtBottomOfMethod(ESSENTIAL_LEGACY_HOOKS_CLASS,
					"public <init>()", "enableIJ1PluginDirs(false);");
			}
		});
	}

	/**
	 * Disables the execution of the {@code ij1.patcher.initializer}.
	 * <p>
	 * A fully patched ImageJ 1.x will allow an initializer class implementing the
	 * {@link Runnable} interface (and discovered via ImageJ 1.x' own
	 * {@link ij.io.PluginClassLoader}) to run just after ImageJ 1.x was
	 * initialized. If the system property {@code ij1.patcher.initializer} is
	 * unset, it defaults to ImageJ2's {@code LegacyInitializer} class.
	 * </p>
	 * <p>
	 * Users of the LegacyEnvironment class can call this method to disable that
	 * behavior.
	 * </p>
	 */
	public void disableInitializer() {
		ensureUninitialized();
		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				hacker.replaceCallInMethod(ESSENTIAL_LEGACY_HOOKS_CLASS,
					"public void initialized()", ESSENTIAL_LEGACY_HOOKS_CLASS,
					"runInitializer", "");
			}
		});
	}

	/**
	 * Forces ImageJ 1.x to use the same {@link ClassLoader} for plugins as for
	 * ImageJ 1.x itself.
	 * <p>
	 * ImageJ 1.x has a command <i>Help&gt;Refresh Menus</i> that allows users to
	 * ask ImageJ 1.x to parse the {@code plugins/} directory for new, or
	 * modified, plugins, and to remove menu labels corresponding to plugins whose
	 * files were deleted while ImageJ 1.x is running. The intended use case is to
	 * support developing ImageJ 1.x plugins without having to restart ImageJ 1.x
	 * all the time, just to test new iterations of the same plugin.
	 * </p>
	 * <p>
	 * To support this, a {@link ij.io.PluginClassLoader} that loads the plugin
	 * classes is instantiated at initialization, and whenever the user calls
	 * <i>Refresh Menus</i>, essentially releasing the old {@link ClassLoader}.
	 * This is a fragile solution, as no measures are taken to ensure that the
	 * classes loaded by the previous {@link ij.io.PluginClassLoader} are no
	 * longer used, but it works most of the time.
	 * </p>
	 * <p>
	 * With ImageJ2 being developed in a modular manner, it is no longer easy to
	 * have one class loader containing only the ImageJ classes and another class
	 * loader containing all the plugins. Therefore, this method is required to be
	 * able to force ImageJ 1.x to reuse the same class loader for plugins as for
	 * ImageJ classes, implying that the <i>Refresh Menus</i> command needs to be
	 * disabled.
	 * </p>
	 * <p>
	 * Since the advent of powerful Integrated Development Environments such as
	 * Netbeans and Eclipse, it is preferable to develop even ImageJ 1.x plugins
	 * in such environments instead of using a text editor to edit the
	 * {@code .java} source, then running {@code javac} from the command-line,
	 * calling <i>Refresh Menus</i> and finally repeating the manual test
	 * procedure, anyway.
	 * </p>
	 */
	public void noPluginClassLoader() {
		ensureUninitialized();
		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				LegacyExtensions.noPluginClassLoader(hacker);
			}
		});
	}

	/**
	 * Disallows ImageJ 1.x from discovering macros and scripts to put into the
	 * menu structure.
	 * <p>
	 * Some callers -- most notably ImageJ2 and Fiji -- want to improve on the
	 * scripting support, which unfortunately implies overriding the
	 * non-extensible script and macro handling of ImageJ 1.x.
	 * </p>
	 */
	public void suppressIJ1ScriptDiscovery() {
		ensureUninitialized();
		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				LegacyExtensions.suppressIJ1ScriptDiscovery(hacker);
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
		final StringBuilder errors = new StringBuilder();
		final Collection<File> files = LegacyHooks.getClasspathElements(fromClassLoader, errors,
			loader, loader == null ? null : loader.getParent(), getClass()
				.getClassLoader().getParent());
		if (errors.length() > 0) {
			throw new IllegalArgumentException(errors.toString());
		}
		for (final File file : files)
		{
			addPluginClasspath(file);
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
			String quoted = file.getPath().replaceAll("[\\\"\\\\]", "\\\\$0");
			quoted = quoted.replaceAll("\n", "\\n");
			builder.append("addPluginClasspath(new java.io.File(\"").append(quoted)
				.append("\"));");
		}

		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				hacker.insertAtBottomOfMethod(ESSENTIAL_LEGACY_HOOKS_CLASS,
					"public <init>()", builder.toString());
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
	 * Initializes a new ImageJ 1.x instance.
	 * <p>
	 * This method starts up a fully-patched ImageJ 1.x, optionally hidden (in
	 * {@code headless} mode, it <b>must</b> be hidden).
	 * </p>
	 * 
	 * @param hidden whether to hide the ImageJ 1.x main window upon startup
	 * @return the instance of the {@link ij.ImageJ} class, or {@code null} in
	 *         headless mode
	 */
	public Object newImageJ1(final boolean hidden) {
		initialize();
		try {
			if (headless) {
				if (!hidden) {
					throw new IllegalArgumentException(
						"In headless mode, ImageJ 1.x must be hidden");
				}
				runPlugIn("ij.IJ.init", null);
				return null;
			}
			final Class<?> clazz = getClassLoader().loadClass("ij.ImageJ");
			final int mode = hidden ? 2 /* NO_SHOW */: 0 /* STANDALONE */;
			return clazz.getConstructor(Integer.TYPE).newInstance(mode);
		} catch (Throwable t) {
			if (t instanceof RuntimeException) throw (RuntimeException) t;
			if (t instanceof Error) throw (Error) t;
			throw new RuntimeException(t);
		}
	}

	/**
	 * Applies the configuration patches.
	 * <p>
	 * After calling methods to configure the current {@link LegacyEnvironment}
	 * (e.g. {@link #disableIJ1PluginDirs()}), the final step before using the
	 * encapsulated ImageJ 1.x is to apply the configuring patches to the
	 * {@link EssentialLegacyHooks} class. This method needs to be called if the
	 * configuration has to be finalized, but ImageJ 1.x is not run right away,
	 * e.g. to prepare for third-party libraries using ImageJ 1.x classes
	 * directly.
	 * </p>
	 */
	public synchronized void applyPatches() {
		if (isInitialized()) {
			throw new RuntimeException("Already initialized:",
				initializationStackTrace);
		}
		initialize();
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

	/**
	 * Determines whether there is already an ImageJ 1.x instance.
	 * <p>
	 * In contrast to {@link ij.IJ#getInstance()}, this method avoids loading any
	 * ImageJ 1.x class, and is therefore suitable for testing whether a
	 * {@link LegacyEnvironment} needs to be created when the caller wants the
	 * classes to be patched in its own {@link ClassLoader}.
	 * </p>
	 * 
	 * @param loader the class loader in which to look for the ImageJ 1.x instance
	 * @return true if there is an initialized instance
	 */
	public static boolean isImageJ1Initialized(final ClassLoader loader) {
		if (!LegacyInjector.alreadyPatched(loader)) return false;
		try {
			return loader.loadClass("ij.IJ").getMethod("getInstance").invoke(null) != null;
		}
		catch (final Throwable t) {
			throw new IllegalArgumentException(
				"Problem accessing ImageJ 1.x in class loader " + loader, t);
		}
	}

}
