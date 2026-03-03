/*
 * #%L
 * ImageJ2 software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2025 ImageJ2 developers.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
	 * Local classes have their bytecode bundled in the same jar as
	 * LegacyClassLoader (ij1-patcher.jar), but are NOT shared — each
	 * LegacyClassLoader instance gets its own definition. We store only their
	 * names here (not Class<?> references) so that loading LegacyClassLoader
	 * does NOT eagerly load those classes into the system classloader, which
	 * would prevent CodeHacker from later defining patched versions of them.
	 */
	private final static Map<String, Class<?>> sharedClasses;
	private final static Set<String> localClassNames;

	/**
	 * Patched class bytes pre-registered by {@link CodeHacker} to be defined
	 * when first requested, avoiding the need to call {@code ClassLoader.defineClass()}
	 * via reflection (which is blocked by Java 17+'s strong encapsulation).
	 */
	private final Map<String, byte[]> patchedClasses = new HashMap<>();

	static {
		sharedClasses = new HashMap<String, Class<?>>();
		sharedClasses.put(LegacyHooks.class.getName(), LegacyHooks.class);
		for (final Class<?> clazz : LegacyHooks.class.getClasses()) {
			sharedClasses.put(clazz.getName(), clazz);
		}
		// NB: EssentialLegacyHooks and HeadlessGenericDialog are intentionally
		// stored by name only. Referencing their Class<?> objects here would
		// eagerly load them into the system classloader, which would prevent
		// CodeHacker from later defining patched versions of those classes.
		localClassNames = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			LegacyInjector.ESSENTIAL_LEGACY_HOOKS_CLASS,
			"net.imagej.patcher.HeadlessGenericDialog"
		)));
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
		if (name.endsWith(".class")) {
			final String className = name.substring(0, name.length() - 6).replace('/', '.');
			final Class<?> sharedClass = sharedClasses.get(className);
			if (sharedClass != null) {
				return sharedClass.getResource("/" + name);
			}
			if (localClassNames.contains(className)) {
				return LegacyClassLoader.class.getResource("/" + name);
			}
		}
		return super.getResource(name);
	}

	/**
	 * Pre-registers patched bytecode for a class so it is used when the class is
	 * first loaded via {@link #findClass}. This lets {@link CodeHacker} inject
	 * patched classes without calling {@code ClassLoader.defineClass()} via
	 * reflection, bypassing Java 17+'s strong-encapsulation restriction.
	 */
	void storePatchedClass(final String name, final byte[] bytes) {
		patchedClasses.put(name, bytes);
	}

	@Override
	public Class<?> findClass(final String className)
		throws ClassNotFoundException
	{
		final byte[] patched = patchedClasses.remove(className);
		if (patched != null) {
			return defineClass(className, patched, 0, patched.length);
		}
		final Class<?> sharedClass = sharedClasses.get(className);
		if (sharedClass != null) return sharedClass;
		if (localClassNames.contains(className)) try {
			// Load bytecode from ij1-patcher.jar resources; no Class<?> reference
			// needed, so these classes are not eagerly loaded into the system CL.
			final ProtectionDomain domain = LegacyClassLoader.class.getProtectionDomain();
			final InputStream in = LegacyClassLoader.class.getResourceAsStream(
				"/" + className.replace('.', '/') + ".class");
			if (in == null) {
				throw new ClassNotFoundException("Resource not found for " + className);
			}
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
