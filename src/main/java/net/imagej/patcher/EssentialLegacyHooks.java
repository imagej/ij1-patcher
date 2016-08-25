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

import ij.IJ;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

/**
 * The base {@link LegacyHooks} to be used in the patched ImageJ 1.x.
 * <p>
 * This is the minimal implementation of {@link LegacyHooks} and will be
 * installed by default after patching in the extension points into ImageJ 1.x.
 * On its own, it does not allow to override the extension points (such as the
 * editor) with different implementations; one needs to install different hooks
 * using the
 * {@link net.imagej.patcher.LegacyInjector#installHooks(ClassLoader, LegacyHooks)}
 * method.
 * </p>
 * <p>
 * This class is also the perfect base class for all implementations of the
 * {@link LegacyHooks} interface, e.g. to offer "real" extension mechanisms such
 * as the SciJava-common plugin framework.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class EssentialLegacyHooks extends LegacyHooks {

	@Override
	public void error(Throwable t) {
		IJ.handleException(t);
	}

	@Override
	public void initialized() {
		runInitializer();
	}

	private void runInitializer() {
		final String property = System.getProperty("ij1.patcher.initializer");
		try {
			final ClassLoader loader = IJ.getClassLoader();
			Thread.currentThread().setContextClassLoader(loader);
			Class<?> runClass;
			if (property != null) {
				runClass = loader.loadClass(property);
			}
			else {
				try {
					runClass = loader.loadClass("net.imagej.legacy.plugin.LegacyInitializer");
				} catch (ClassNotFoundException e) {
					runClass = loader.loadClass("imagej.legacy.plugin.LegacyInitializer");
				}
			}
			final Runnable run = (Runnable)runClass.newInstance();
			run.run();
		} catch (ClassNotFoundException e) {
			// ignore
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * Intended for sole use with patches in {@link ij.IJ}. DO NOT USE.
	 */
	public static ClassLoader missingSubdirs(final ClassLoader loader, final boolean addPluginsDir) {
		if (loader == null || !(loader instanceof URLClassLoader)) return null;
		final URLClassLoader classLoader = (URLClassLoader) loader;
		final Set<File> directories = new HashSet<File>();
		for (final URL url : classLoader.getURLs()) {
			if (!"file".equals(url.getProtocol())) continue;
			final File dir = new File(url.getPath());
			if (!dir.isDirectory()) continue;
			directories.add(dir);
		}
		final Set<File> missing = new HashSet<File>();
		if (addPluginsDir) {
			String pluginsDir = IJ.getDirectory("plugins");
			if (pluginsDir == null) {
				pluginsDir = System.getProperty("plugins.dir");
			}
			if (pluginsDir != null) {
				final File plugins = new File(pluginsDir);
				if (!directories.contains(plugins)) {
					directories.add(plugins);
					missing.add(plugins);
				}
			}
		}
		try {
			for (final File dir : new HashSet<File>(directories)) {
				missingSubdirectories(dir, directories, missing);
			}
			if (missing.isEmpty()) return null;
			final URL[] urls = new URL[missing.size()];
			int i = 0;
			for (final File dir : missing) {
				urls[i++] = dir.toURI().toURL();
			}
			return new URLClassLoader(urls, loader);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static boolean missingSubdirectories(final File dir, final Set<File> already, final Set<File> missing) {
		boolean containsClassFiles = false;
		for (final File sub : dir.listFiles()) {
			if (sub.isDirectory()) {
				if (already.contains(sub) || missing.contains(sub)) continue;
				if (missingSubdirectories(sub, already, missing)) try {
					containsClassFiles = true;
					missing.add(sub);
				}
				catch (Exception e) {
					if (e instanceof RuntimeException) throw (RuntimeException) e;
					throw new RuntimeException(e);
				}
			}
			else if (sub.getName().endsWith(".class")) {
				containsClassFiles = true;
			}
		}
		return containsClassFiles;
	}

	/**
	 * Intercepts LegacyInitializer's Context creation.
	 * <p>
	 * One of the most critical code paths in <a href="http://fiji.sc/">Fiji</a>
	 * is how its runtime patches get installed. It calls the
	 * {@link LegacyEnvironment}, of course, but the idea is for said environment
	 * to install the essential {@link LegacyHooks} which then give ImageJ's
	 * {@code LegacyService} a chance to spin up all of the legacy patches,
	 * including Fiji's (which are add-ons on top of {@code imagej-legacy}).
	 * </p>
	 * <p>
	 * If this critical code path fails, Fiji fails to start up properly. Ideally,
	 * this should never happen, but this is not an ideal world: corrupt
	 * {@code .jar} files and version skews caused by forgotten updates,
	 * locally-modified files or simply by forward-incompatible downstream updates
	 * can break any number of things in that path. So let's bend over another
	 * inch (the existence of the {@code ij1-patcher} speaks volumes about our
	 * learned ability to do so) and try extra hard to keep things working even
	 * under very unfavorable circumstances.
	 * </p>
	 * 
	 * @param className the class name
	 * @param arg the argument passed to the {@code runPlugIn} method
	 * @return the object to return, or null to let ImageJ 1.x handle the call
	 */
	@Override
	public Object interceptRunPlugIn(final String className, final String arg) {
		if ("org.scijava.Context".equals(className)) {
			try {
				final Class<?> contextClass = IJ.getClassLoader().loadClass(className);
				try {
					return contextClass.newInstance();
				}
				catch (Throwable t) {
					// "Something" failed... Darn.
					t.printStackTrace();

					// Try again, with the bare minimum of services: LegacyService and friends
					Class<?> legacyServiceClass;
					try {
						// NB: Old concrete LegacyService class, which no longer exists.
						legacyServiceClass = IJ.getClassLoader().loadClass(
							"net.imagej.legacy.DefaultLegacyService");
					}
					catch (Throwable t2) {
						try {
							// NB: Current concrete LegacyService class.
							legacyServiceClass = IJ.getClassLoader().loadClass(
								"net.imagej.legacy.LegacyService");
						}
						catch (Throwable t3) {
							// NB: _Really_ old concrete LegacyService class, long gone.
							legacyServiceClass = IJ.getClassLoader().loadClass(
								"imagej.legacy.DefaultLegacyService");
						}
					}
					Constructor<?> ctor = contextClass.getConstructor((Class<?>) Class[].class);
					return ctor.newInstance((Object) new Class<?>[] { legacyServiceClass });
				}
			}
			catch (Throwable t) {
				t.printStackTrace();
				System.err.println("Giving up to create a SciJava Context!");
			}
		}
		return null;
	}

}
