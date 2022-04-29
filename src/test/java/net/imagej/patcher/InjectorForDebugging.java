/*
 * #%L
 * ImageJ2 software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2022 ImageJ2 developers.
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

import java.awt.Menu;

import javassist.CannotCompileException;
import javassist.CodeConverter;
import javassist.CtClass;
import net.imagej.patcher.debug.MenuForDebugging;

/**
 * A {@link LegacyInjector} intended to debug problems with newer ImageJ 1.x versions 
 * <p>
 * Every once in a while, there are problems with new ImageJ 1.x releases, in
 * particular with the finicky menu structure (there are at least fifteen different
 * code paths adding menu items as of ImageJ 1.48v, most of which do not work at all
 * in headless mode).
 * </p>
 * <p>
 * This class is intended to help with debugging by overriding the AWT Menu instances
 * with instances of a custom subclass in which we can set a breakpoint when specific
 * menu items are added.
 * </p> 
 * 
 * @author Johannes Schindelin
 */
class InjectorForDebugging extends LegacyInjector {
	{
		before.add(new Callback() {
			@Override
			public void call(final CodeHacker hacker) {
				hacker.loadClass(hacker.getClass(MenuForDebugging.class.getName()));
			}
		});

		after.add(new Callback() {
			@Override
			public void call(final CodeHacker hacker) {
				try {
					final CodeConverter converter = new CodeConverter();
					converter.replaceNew(hacker.getClass(Menu.class.getName()),
							hacker.getClass(MenuForDebugging.class.getName()));
					for (final CtClass clazz : hacker.getPatchedClasses()) {
						clazz.instrument(converter);
					}
				} catch (CannotCompileException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
