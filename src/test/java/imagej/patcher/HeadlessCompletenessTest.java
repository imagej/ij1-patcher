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

package imagej.patcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static imagej.patcher.TestUtils.construct;
import static imagej.patcher.TestUtils.invokeStatic;
import ij.ImageJ;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests whether the headless functionality is complete.
 * 
 * @author Johannes Schindelin
 */
public class HeadlessCompletenessTest {

	static {
		try {
			LegacyInjector.preinit();
		}
		catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException("Got exception (see error log)");
		}
	}

	private String threadName;
	private ClassLoader threadLoader;

	@Before
	public void saveThreadName() {
		threadName = Thread.currentThread().getName();
		threadLoader = Thread.currentThread().getContextClassLoader();
	}

	@After
	public void restoreThreadName() {
		if (threadName != null) Thread.currentThread().setName(threadName);
		if (threadLoader != null) Thread.currentThread().setContextClassLoader(threadLoader);
	}

	@Test
	public void missingGenericDialogMethods() throws Exception {
		final ClassPool pool = new ClassPool();
		pool.appendClassPath(new ClassClassPath(getClass()));
		final String originalName = "ij.gui.GenericDialog";
		final CtClass original = pool.get(originalName);
		final String headlessName = HeadlessGenericDialog.class.getName();
		final CtClass headless = pool.get(headlessName);

		final Map<String, CtMethod> methods = new HashMap<String, CtMethod>();
		for (final CtMethod method : headless.getMethods()) {
			if (headless != method.getDeclaringClass()) continue;
			String name = method.getLongName();
			assertTrue(name.startsWith(headlessName + "."));
			name = name.substring(headlessName.length() + 1);
			methods.put(name, method);
		}

		// these do not need to be overridden
		for (final String name : new String[] {
				"actionPerformed(java.awt.event.ActionEvent)",
				"adjustmentValueChanged(java.awt.event.AdjustmentEvent)",
				"getInsets(int,int,int,int)",
				"getValue(java.lang.String)",
				"isMatch(java.lang.String,java.lang.String)",
				"itemStateChanged(java.awt.event.ItemEvent)",
				"keyPressed(java.awt.event.KeyEvent)",
				"keyReleased(java.awt.event.KeyEvent)",
				"keyTyped(java.awt.event.KeyEvent)",
				"paint(java.awt.Graphics)",
				"parseDouble(java.lang.String)",
				"setCancelLabel(java.lang.String)",
				"textValueChanged(java.awt.event.TextEvent)",
				"windowActivated(java.awt.event.WindowEvent)",
				"windowClosed(java.awt.event.WindowEvent)",
				"windowClosing(java.awt.event.WindowEvent)",
				"windowDeactivated(java.awt.event.WindowEvent)",
				"windowDeiconified(java.awt.event.WindowEvent)",
				"windowIconified(java.awt.event.WindowEvent)",
				"windowOpened(java.awt.event.WindowEvent)"
		}) {
			methods.put(name, null);
		}

		for (final CtMethod method : original.getMethods()) {
			if (original != method.getDeclaringClass()) continue;
			String name = method.getLongName();
			assertTrue(name.startsWith(originalName + "."));
			name = name.substring(originalName.length() + 1);
			assertTrue(name + " is not overridden", methods.containsKey(name));
		}
	}

	@Test
	public void testMenuStructure() throws Exception {
		assumeTrue(!GraphicsEnvironment.isHeadless());
		final LegacyEnvironment headlessIJ1 = new LegacyEnvironment(null, true);
		headlessIJ1.runMacro("", "");
		final Map<String, String> menuItems =
			new LinkedHashMap<String, String>(headlessIJ1.getMenuStructure());


		final LegacyEnvironment ij1 = new LegacyEnvironment(null, false);
		final Frame ij1Frame = construct(ij1.getClassLoader(), "ij.ImageJ", ImageJ.NO_SHOW);
		final MenuBar menuBar = ij1Frame.getMenuBar();

		final Hashtable<String, String> commands =
			invokeStatic(ij1.getClassLoader(), "ij.Menus", "getCommands");
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			final Menu menu = menuBar.getMenu(i);
			assertMenuItems(menuItems, commands, menu.getLabel() + ">", menu);
		}
		assertTrue("Left-over menu items: " + menuItems.keySet(),
			menuItems.size() == 0);
	}

	private void assertMenuItems(final Map<String, String> menuItems,
		final Hashtable<String, String> commands, final String prefix,
		final Menu menu)
	{
		int separatorCounter = 0;
		for (int i = 0; i < menu.getItemCount(); i++) {
			final MenuItem item = menu.getItem(i);
			final String label = item.getLabel();
			String menuPath = prefix + label;
			if (item instanceof Menu) {
				assertMenuItems(menuItems, commands, menuPath + ">", (Menu) item);
			}
			else if ("-".equals(label)) {
				final String menuPath2 = menuPath + ++separatorCounter;
				assertTrue("Not found: " + menuPath2, menuItems.containsKey(menuPath2));
				menuItems.remove(menuPath2);
			}
			else if (!menuPath.startsWith("File>Open Recent>")){
				assertTrue("Not found: " + menuPath, menuItems.containsKey(menuPath));
				assertEquals("Command for menu path: " + menuPath, commands.get(label),
					menuItems.get(menuPath));
				menuItems.remove(menuPath);
			}
		}
	}
}
