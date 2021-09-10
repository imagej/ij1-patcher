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

import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import net.imagej.patcher.LegacyInjector.Callback;

import org.junit.Test;

public class HelpfulExceptionTracesIT {

	@Test
	public void testHelpfulTrace() throws Exception {
		final LegacyInjector injector = new LegacyInjector();
		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				hacker.addToClassInitializer("ij.IJ", "this does not compile");
			}
		});

		final LegacyEnvironment ij1 = new LegacyEnvironment(null, true, injector);
		try {
			ij1.setMacroOptions("");
			assertTrue(false);
		}
		catch (final RuntimeException e) {
			final StringWriter writer = new StringWriter();
			final PrintWriter out = new PrintWriter(writer);
			e.printStackTrace(out);
			out.close();
			assertTrue(writer.toString().contains("this does not compile"));
		}
	}

	@Test
	public void testInvocationTargetException() throws Exception {
		final LegacyInjector injector = new LegacyInjector();
		injector.after.add(new Callback() {

			@Override
			public void call(final CodeHacker hacker) {
				hacker
					.insertAtTopOfMethod(
						"ij.IJ",
						"public static void run(java.lang.String command, java.lang.String options)",
						"throw new NullPointerException(\"must fail!\");");
			}
		});

		final LegacyEnvironment ij1 = new LegacyEnvironment(null, true, injector);
		try {
			ij1.run("", "");
			assertTrue(false);
		}
		catch (final RuntimeException e) {
			final StringWriter writer = new StringWriter();
			final PrintWriter out = new PrintWriter(writer);
			e.printStackTrace(out);
			out.close();
			assertTrue(writer.toString().contains("must fail!"));
		}
	}
}
