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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.Cast;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.Handler;
import javassist.expr.Instanceof;
import javassist.expr.MethodCall;
import javassist.expr.NewArray;
import javassist.expr.NewExpr;

import org.junit.Test;

/**
 * Verifies that ImageJ 1.x classes are not used everywhere.
 * 
 * @author Johannes Schindelin
 */
public class ImageJ1EncapsulationTest {

	static {
		try {
			LegacyInjector.preinit();
		}
		catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

	@SuppressWarnings("deprecation")
	@Test
	public void verifyEncapsulation() throws Exception {
		final ClassPool pool = ClassPool.getDefault();

		final Set<String> exceptions = new HashSet<String>();
		exceptions.add(EssentialLegacyHooks.class.getName());
		exceptions.add(HeadlessGenericDialog.class.getName());
		exceptions.add(imagej.patcher.EssentialLegacyHooks.class.getName());
		exceptions.add(imagej.patcher.HeadlessGenericDialog.class.getName());

		final URL directory = Utils.getLocation(Utils.class);
		final int prefixLength = directory.toString().length();
		for (final URL url : Utils.listContents(directory)) {
			final String path = url.toString().substring(prefixLength);
			if (!path.endsWith(".class")) continue;
			final String className = path.substring(0, path.length() - 6).replace('/', '.');

			if (exceptions.contains(className)) {
				exceptions.remove(className);
			}
			else try {
				final CtClass clazz = pool.get(className);
				clazz.instrument(new ImageJ1UsageTester());
			} catch (final Exception e) {
				throw new RuntimeException("Problem with class " + className, e);
			}
		}

		assertEquals(exceptions.toString(), 0, exceptions.size());
	}

	private final class ImageJ1UsageTester extends ExprEditor {

		private void test(final CtClass c) {
			if (c != null && c.getName().startsWith("ij.")) {
				throw new RuntimeException("ImageJ 1.x class used: " + c.getName());
			}
		}

		@Override
		public void edit(Cast c) {
			try {
				test(c.getType());
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(ConstructorCall c) {
			try {
				test(c.getConstructor().getDeclaringClass());
				final CtConstructor c2 = c.getConstructor();
				for (final CtClass c3 : c2.getExceptionTypes()) {
					test(c3);
				}
				for (final CtClass c3 : c2.getParameterTypes()) {
					test(c3);
				}
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(FieldAccess f) {
			try {
				final CtField field = f.getField();
				test(field.getDeclaringClass());
				test(field.getType());
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(Handler h) {
			try {
				test(h.getType());
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(Instanceof i) {
			try {
				test(i.getType());
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(MethodCall m) {
			try {
				final CtMethod m2 = m.getMethod();
				test(m2.getDeclaringClass());
				test(m2.getReturnType());
				for (final CtClass c2 : m2.getExceptionTypes()) {
					test(c2);
				}
				for (final CtClass c2 : m2.getParameterTypes()) {
					test(c2);
				}
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(NewArray a) {
			try {
				test(a.getComponentType());
			}
			catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void edit(NewExpr e) {
			try {
				final CtConstructor c = e.getConstructor();
				for (final CtClass c2 : c.getExceptionTypes()) {
					test(c2);
				}
				for (final CtClass c2 : c.getParameterTypes()) {
					test(c2);
				}
			}
			catch (NotFoundException e2) {
				throw new RuntimeException(e2);
			}
		}
	}

	@Test
	public void testVersionCompare() {
		final String[] versions = { "1.48", "1.49a2", "1.49a12", "1.49a", "1.49e5", "1.49g", "1.49" };
		for (int i = 0; i < versions.length; i++) {
			for (int j = 0; j < versions.length; j++) {
				int compare = Utils.ij1VersionCompare(versions[i], versions[j]);
				if (i < j) assertTrue(compare < 0);
				else if (i > j) assertTrue(compare > 0);
				else assertTrue(compare == 0);
			}
		}
	}
}
