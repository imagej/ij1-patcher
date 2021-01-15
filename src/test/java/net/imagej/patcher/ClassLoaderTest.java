/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2021 ImageJ developers.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

/**
 * Tests potential class loader issues with ij1-patcher. 
 * 
 * @author Johannes Schindelin
 */
public class ClassLoaderTest {

	static {
		try {
			LegacyInjector.preinit();
		}
		catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

	@Test
	public void test() throws Exception {
		final LegacyClassLoader loader = new LegacyClassLoader();
		final ClassLoader child = new URLClassLoader(new URL[0], loader);
		assertFalse(LegacyInjector.alreadyPatched(child));
		assertFalse(LegacyInjector.alreadyPatched(loader));
		LegacyInjector.preinit(child);
		assertTrue(LegacyInjector.alreadyPatched(loader));
		assertTrue(LegacyInjector.alreadyPatched(child));
		Class<?> ij = child.loadClass("ij.IJ");
		assertEquals(loader, ij.getClassLoader());
	}
}
