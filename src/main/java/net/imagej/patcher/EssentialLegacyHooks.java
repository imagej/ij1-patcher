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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The base {@link LegacyHooks} to be used in the patched ImageJ 1.x.
 * <p>
 * This is the minimal implementation of {@link LegacyHooks} and will be
 * installed by default after patching in the extension points into ImageJ 1.x.
 * On its own, it does not allow to override the extension points (such as the
 * editor) with different implementations; one needs to install different hooks using
 * the {@link net.imagej.patcher.CodeHacker#installHooks(LegacyHooks)} method.
 * </p>
 * </p>
 * This class is also the perfect base class for all implementations of the
 * {@link LegacyHooks} interface, e.g. to offer "real" extension mechanisms such
 * as the SciJava-common plugin framework.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class EssentialLegacyHooks extends LegacyHooks {

	/** @inherit */
	@Override
	public void error(Throwable t) {
		IJ.handleException(t);
	}

	/** @inherit */
	@Override
	public boolean handleNoSuchMethodError(NoSuchMethodError error) {
		String message = error.getMessage();
		int paren = message.indexOf("(");
		if (paren < 0) return false;
		int dot = message.lastIndexOf(".", paren);
		if (dot < 0) return false;
		String path = message.substring(0, dot).replace('.', '/') + ".class";
		Set<String> urls = new LinkedHashSet<String>();
		try {
			Enumeration<URL> e = IJ.getClassLoader().getResources(path);
			while (e.hasMoreElements()) {
				urls.add(e.nextElement().toString());
			}
			e = IJ.getClassLoader().getResources("/" + path);
			while (e.hasMoreElements()) {
				urls.add(e.nextElement().toString());
			}
		} catch (Throwable t) {
			t.printStackTrace();
			return false;
		}

		if (urls.size() == 0) return false;
		StringBuilder buffer = new StringBuilder();
		buffer.append("There was a problem with the class ");
		buffer.append(message.substring(0, dot));
		buffer.append(" which can be found here:\n");
		for (String url : urls) {
			if (url.startsWith("jar:")) url = url.substring(4);
			if (url.startsWith("file:")) url = url.substring(5);
			int bang = url.indexOf("!");
			if (bang < 0) buffer.append(url);
			else buffer.append(url.substring(0, bang));
			buffer.append("\n");
		}
		if (urls.size() > 1) {
			buffer.append("\nWARNING: multiple locations found!\n");
		}

		StringWriter writer = new StringWriter();
		error.printStackTrace(new PrintWriter(writer));
		buffer.append(writer.toString());

		IJ.log(buffer.toString());
		IJ.error("Could not find method " + message + "\n(See Log for details)\n");
		return true;
	}

	/** @inherit */
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
	 * Intercepts LegacyInitializer's Context creation.
	 * <p>
	 * One of the most critical code paths in <a href="http://fiji.sc/">Fiji</a>
	 * is how its runtime patches get installed. It calls the
	 * {@link LegacyEnvironment}, of course, but the idea is for said environment
	 * to install the essential {@link LegacyHooks} which then give ImageJ's
	 * DefaultLegacyService a chance to spin up all of the legacy patches,
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

					// Try again, with the bare minimum of services: DefaultLegacyService and friends
					Class<?> legacyServiceClass;
					try {
						legacyServiceClass = IJ.getClassLoader().loadClass("net.imagej.legacy.DefaultLegacyService");
					}
					catch (Throwable t2) {
						legacyServiceClass = IJ.getClassLoader().loadClass("imagej.legacy.DefaultLegacyService");
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
