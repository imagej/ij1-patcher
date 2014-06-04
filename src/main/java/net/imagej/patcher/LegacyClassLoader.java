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

import ij.ImagePlus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

/**
 * A special purpose class loader to encapsulate ImageJ 1.x "instances" from
 * each other.
 * 
 * @see LegacyEnvironment
 * @author Johannes Schindelin
 */
public class LegacyClassLoader extends URLClassLoader {

	/*
	 * Shared classes are classes we share with the class loader in which the
	 * LegacyClassLoader class itself is defined. That way, code inside
	 * the LegacyClassLoader can refer to the very same classes as code outside.
	 *
	 * Known classes contain the shared classes, but also classes whose bytecode
	 * comes from outside the URLs known to the LegacyClassLoader. The non-shared
	 * known classes *cannot* be used to communicate between classes inside and
	 * outside of the LegacyClassLoader.
	 */
	private final static Map<String, Class<?>> knownClasses, sharedClasses;

	static {
		sharedClasses = new HashMap<String, Class<?>>();
		sharedClasses.put(LegacyHooks.class.getName(), LegacyHooks.class);
		for (final Class<?> clazz : LegacyHooks.class.getClasses()) {
			sharedClasses.put(clazz.getName(), clazz);
		}
		knownClasses = new HashMap<String, Class<?>>(sharedClasses);
		knownClasses.put(EssentialLegacyHooks.class.getName(),
			EssentialLegacyHooks.class);
		knownClasses.put(HeadlessGenericDialog.class.getName(),
			HeadlessGenericDialog.class);
	}

	public LegacyClassLoader(final boolean headless)
		throws ClassNotFoundException
	{
		this();
		new LegacyInjector().injectHooks(this, headless);
	}

	public LegacyClassLoader() throws ClassNotFoundException {
		super(getImageJ1Jar(), determineParent());
	}

	@Override
	public URL getResource(final String name) {
		final Class<?> knownClass = !name.endsWith(".class")? null :
			knownClasses.get(name.substring(0, name.length() - 6).replace('/', '.'));
		if (knownClass != null) {
			return knownClass.getResource("/" + name);
		}
		return super.getResource(name);
	}

	@Override
	public Class<?> findClass(final String className)
		throws ClassNotFoundException
	{
		final Class<?> knownClass = knownClasses.get(className);
		if (knownClass != null) try {
			final Class<?> sharedClass = sharedClasses.get(className);
			if (sharedClass != null) return sharedClass;
			final ProtectionDomain domain = knownClass.getProtectionDomain();
			final InputStream in = knownClass.getResourceAsStream("/" + className.replace('.', '/') + ".class");
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[65536];
			for (;;) {
				int count = in.read(buffer);
				if (count < 0) break;
				out.write(buffer, 0, count);
			}
			in.close();
			buffer = out.toByteArray();
			out.close();
			return defineClass(className, buffer, 0, buffer.length, domain);
		} catch (IOException e) {
			throw new ClassNotFoundException("Could not read bytecode for " + className, e);
		}
		return super.findClass(className);
	}

	private static ClassLoader determineParent() {
		ClassLoader loader = ClassLoader.getSystemClassLoader();
		for (;;) try {
			if (loader.loadClass("ij.IJ") == null) {
				return loader;
			}
			loader = loader.getParent();
			if (loader == null) {
				throw new RuntimeException("Cannot find bootstrap class loader");
			}
		} catch (ClassNotFoundException e) {
			return loader;
		}
	}

	private static URL[] getImageJ1Jar() {
		return new URL[] { Utils.getLocation(ImagePlus.class) };
	}
}
