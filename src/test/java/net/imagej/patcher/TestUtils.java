/*
 * #%L
 * ImageJ2 software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2021 ImageJ2 developers.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class TestUtils {

	/**
	 * Bundles the given classes in a new .jar file.
	 * 
	 * @param jarFile the output file
	 * @param classNames the classes to include
	 * @throws IOException
	 */
	public static void makeJar(final File jarFile, final String... classNames)
		throws IOException
	{
		final JarOutputStream jar =
			new JarOutputStream(new FileOutputStream(jarFile));
		final byte[] buffer = new byte[16384];
		final StringBuilder pluginsConfig = new StringBuilder();
		for (final String className : classNames) {
			final String path = className.replace('.', '/') + ".class";
			final InputStream in = TestUtils.class.getResourceAsStream("/" + path);
			final ZipEntry entry = new ZipEntry(path);
			jar.putNextEntry(entry);
			for (;;) {
				int count = in.read(buffer);
				if (count < 0) break;
				jar.write(buffer, 0, count);
			}
			if (className.indexOf('_') >= 0) {
				final String name =
					className.substring(className.lastIndexOf('.') + 1).replace('_', ' ');
				pluginsConfig.append("Plugins, \"").append(name).append("\", ").append(
					className).append("\n");
			}
			in.close();
		}
		if (pluginsConfig.length() > 0) {
			final ZipEntry entry = new ZipEntry("plugins.config");
			jar.putNextEntry(entry);
			jar.write(pluginsConfig.toString().getBytes());
		}
		jar.close();
	}

	/**
	 * Instantiates a class loaded in the given class loader.
	 * 
	 * @param loader the class loader with which to load the class
	 * @param className the name of the class to be instantiated
	 * @param parameters the parameters to pass to the constructor
	 * @return the new instance
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T construct(final ClassLoader loader,
		final String className, final Object... parameters)
		throws SecurityException, NoSuchMethodException, IllegalArgumentException,
		IllegalAccessException, InvocationTargetException, ClassNotFoundException,
		InstantiationException
	{
		final Class<?> clazz = loader.loadClass(className);
		for (final Constructor<?> constructor : clazz.getConstructors()) {
			if (doParametersMatch(constructor.getParameterTypes(), parameters)) {
				return (T) constructor.newInstance(parameters);
			}
		}
		throw new NoSuchMethodException("No matching method found");
	}

	/**
	 * Invokes a static method of a given class.
	 * <p>
	 * This method tries to find a static method matching the given name and the
	 * parameter list. Just like {@link #newInstance(String, Object...)}, this
	 * works via reflection to avoid a compile-time dependency on ImageJ2.
	 * </p>
	 * 
	 * @param loader the class loader with which to load the class
	 * @param className the name of the class whose static method is to be called
	 * @param methodName the name of the static method to be called
	 * @param parameters the parameters to pass to the static method
	 * @return the return value of the static method, if any
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T
		invokeStatic(final ClassLoader loader, final String className,
			final String methodName, final Object... parameters)
			throws SecurityException, NoSuchMethodException,
			IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException
	{
		final Class<?> clazz = loader.loadClass(className);
		for (final Method method : clazz.getMethods()) {
			if (method.getName().equals(methodName) &&
				doParametersMatch(method.getParameterTypes(), parameters))
			{
				return (T) method.invoke(null, parameters);
			}
		}
		throw new NoSuchMethodException("No matching method found");
	}

	/**
	 * Invokes a method of a given object.
	 * <p>
	 * This method tries to find a method matching the given name and the
	 * parameter list. Just like {@link #newInstance(String, Object...)}, this
	 * works via reflection to avoid a compile-time dependency on ImageJ2.
	 * </p>
	 * 
	 * @param loader the class loader with which to load the class
	 * @param object the object whose method is to be called
	 * @param methodName the name of the static method to be called
	 * @param parameters the parameters to pass to the static method
	 * @return the return value of the method, if any
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T
		invoke(final Object object,
			final String methodName, final Object... parameters)
			throws SecurityException, NoSuchMethodException,
			IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException
	{
		final Class<?> clazz = object.getClass();
		for (final Method method : clazz.getMethods()) {
			if (method.getName().equals(methodName) &&
				doParametersMatch(method.getParameterTypes(), parameters))
			{
				return (T) method.invoke(object, parameters);
			}
		}
		throw new NoSuchMethodException("No matching method found");
	}

	/**
	 * Check whether a list of parameters matches a list of parameter types. This
	 * is used to find matching constructors and (possibly static) methods.
	 * 
	 * @param types the parameter types
	 * @param parameters the parameters
	 * @return whether the parameters match the types
	 */
	private static boolean
		doParametersMatch(Class<?>[] types, Object[] parameters)
	{
		if (types.length != parameters.length) return false;
		for (int i = 0; i < types.length; i++)
			if (parameters[i] != null) {
				Class<?> clazz = parameters[i].getClass();
				if (types[i].isPrimitive()) {
					if (types[i] != Long.TYPE && types[i] != Integer.TYPE &&
						types[i] != Boolean.TYPE) throw new RuntimeException(
						"unsupported primitive type " + clazz);
					if (types[i] == Long.TYPE && clazz != Long.class) return false;
					else if (types[i] == Integer.TYPE && clazz != Integer.class) return false;
					else if (types[i] == Boolean.TYPE && clazz != Boolean.class) return false;
				}
				else if (!types[i].isAssignableFrom(clazz)) return false;
			}
		return true;
	}

	/**
	 * Instantiates a new {@link LegacyEnvironment} for use in unit tests.
	 * <p>
	 * In general, unit tests should not rely on, or be affected by, side
	 * effects such as the presence of plugins in a subdirectory called
	 * <code>.plugins/</code> of the user's home directory. This method
	 * instantiates a legacy environment switching off all such side effects,
	 * insofar supported by the {@link LegacyEnvironment}.
	 * </p>
	 * 
	 * @return the legacy environment
	 * @throws ClassNotFoundException
	 */
	public static LegacyEnvironment getTestEnvironment() throws ClassNotFoundException {
		return getTestEnvironment(true, false);
	}

	/**
	 * Instantiates a new {@link LegacyEnvironment} for use in unit tests.
	 * 
	 * @param headless whether to apply headless support patches
	 * @param enableIJ1PluginDirs whether to allow parsing ${ij1.plugin.dirs} for plugins 
	 * @return the legacy environment
	 * @throws ClassNotFoundException
	 */
	public static LegacyEnvironment getTestEnvironment(final boolean headless,
			final boolean enableIJ1PluginDirs) throws ClassNotFoundException {
		final boolean debugMenus = false;
		final LegacyInjector injector = debugMenus ? new InjectorForDebugging() : new LegacyInjector();
		final LegacyEnvironment result = new LegacyEnvironment(null, headless, injector);
		if (!enableIJ1PluginDirs) result.disableIJ1PluginDirs();
		return result;
	}

}
