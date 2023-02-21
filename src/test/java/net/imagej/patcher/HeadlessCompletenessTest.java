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

import static net.imagej.patcher.TestUtils.construct;
import static net.imagej.patcher.TestUtils.getTestEnvironment;
import static net.imagej.patcher.TestUtils.invoke;
import static net.imagej.patcher.TestUtils.invokeStatic;
import static net.imagej.patcher.TestUtils.makeJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.scijava.test.TestUtils.createTemporaryDirectory;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imagej.patcher.LegacyInjector.Callback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ij.ImageJ;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

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

	private File tmpDir;
	private String threadName;
	private ClassLoader threadLoader;

	@Before
	public void before() throws IOException {
		threadName = Thread.currentThread().getName();
		threadLoader = Thread.currentThread().getContextClassLoader();
		tmpDir = createTemporaryDirectory("legacy-");
	}

	@After
	public void after() {
		if (threadName != null) Thread.currentThread().setName(threadName);
		if (threadLoader != null) Thread.currentThread().setContextClassLoader(
			threadLoader);
	}

	@Test
	public void missingGenericDialogMethods() throws Exception {
		final ClassPool pool = new ClassPool();
		pool.appendClassPath(new ClassClassPath(getClass()));
		final String originalName = "ij.gui.GenericDialog";
		final CtClass original = pool.get(originalName);
		final String headlessName = HeadlessGenericDialog.class.getName();
		final CtClass headless = pool.get(headlessName);

		final Map<String, CtMethod> methods = new HashMap<>();
		for (final CtMethod method : headless.getMethods()) {
			if (headless != method.getDeclaringClass()) continue;
			String name = method.getLongName();
			assertTrue(name.startsWith(headlessName + "."));
			name = name.substring(headlessName.length() + 1);
			methods.put(name, method);
		}

		// these do not need to be overridden because they are either:
		// a) explicitly stubified in LegacyHeadless
		// b) AWT-inherited methods implicitly stubified in LegacyHeadless
		// c) non-public API
		// d) function correctly while headless
		for (final String name : new String[] {
			"actionPerformed(java.awt.event.ActionEvent)", //
			"addEnumChoice(java.lang.String, java.lang.Enum)", //
			"addToSameRow()", //
			"adjustmentValueChanged(java.awt.event.AdjustmentEvent)", //
			"getInsets(int,int,int,int)", //
			"getInstance()", //
			"getLabel()", //
			"getString(java.awt.dnd.DropTargetDropEvent)", //
			"getValue(java.lang.String)", //
			"isMacro()", //
			"isMatch(java.lang.String,java.lang.String)", //
			"itemStateChanged(java.awt.event.ItemEvent)", //
			"keyPressed(java.awt.event.KeyEvent)", //
			"keyReleased(java.awt.event.KeyEvent)", //
			"keyTyped(java.awt.event.KeyEvent)", //
			"paint(java.awt.Graphics)", //
			"parseDouble(java.lang.String)", //
			"repaint()", //
			"setCancelLabel(java.lang.String)", //
			"setFont(java.awt.Font)", //
			"stripSuffix(java.lang.String,java.lang.String)", //
			"textValueChanged(java.awt.event.TextEvent)", //
			"windowActivated(java.awt.event.WindowEvent)", //
			"windowClosed(java.awt.event.WindowEvent)", //
			"windowClosing(java.awt.event.WindowEvent)", //
			"windowDeactivated(java.awt.event.WindowEvent)", //
			"windowDeiconified(java.awt.event.WindowEvent)", //
			"windowIconified(java.awt.event.WindowEvent)", //
			"windowOpened(java.awt.event.WindowEvent)",
			"addButton(java.lang.String,java.awt.event.ActionListener)"
			})
		{
			methods.put(name, null);
		}

		for (final CtMethod method : original.getMethods()) {
			if (original != method.getDeclaringClass()) continue;
			String name = method.getLongName();
			assertTrue(name.startsWith(originalName + "."));
			name = name.substring(originalName.length() + 1);
			if (name.startsWith("access$")) continue; // skip synthetic methods
			assertTrue(name + " is not overridden", methods.containsKey(name));
		}
	}

	/**
	 * Verifies that the {@link LegacyInjector} adds dummy implementations for as
	 * yet unhandled methods.
	 * <p>
	 * The previous test verifies that the current version of the
	 * {@link HeadlessGenericDialog} has implementations for all of the methods
	 * provided by the current ImageJ 1.x' {@link ij.gui.GenericDialog}. However,
	 * to make things future-proof (i.e. to avoid errors when loading the headless
	 * mode with a future ImageJ 1.x), we need to be extra careful to
	 * auto-generate dummy methods for methods the {@code ij1-patcher} did not
	 * encounter yet.
	 * </p>
	 * 
	 * @throws Exception
	 */
	@Test
	public void autogenerateDummyMethods() throws Exception {
		final LegacyInjector injector = new LegacyInjector();
		injector.before.add(new Callback() {

			@Override
			public void call(CodeHacker hacker) {
				// simulate new method in the generic dialog
				hacker.insertNewMethod("ij.gui.GenericDialog",
					"public java.lang.String intentionalBreakage(java.awt.event.KeyEvent event)",
					"throw new RuntimeException(\"This must be overridden\");");
				// allow instantiating the headless generic dialog outside macros
//				hacker.replaceCallInMethod("net.imagej.patcher.HeadlessGenericDialog",
//					"public <init>()", "ij.Macro", "getOptions", "$_ = \"\";");
			}
		});
		final LegacyEnvironment ij1 = new LegacyEnvironment(null, true, injector);
		// pretend to be running inside a macro
		Thread.currentThread().setName("Run$_Aaaargh!");
		ij1.setMacroOptions("Aaaaaaaaargh!!!");
		final Object dialog = construct(ij1.getClassLoader(), "ij.gui.GenericDialog", "Hello");
		final Object nextString = invoke(dialog, "intentionalBreakage", (Object)null);
		assertNull(nextString);
	}

	@Test
	public void testMenuStructure() throws Exception {
		assumeTrue(!GraphicsEnvironment.isHeadless());

		final File jarFile = new File(tmpDir, "Set_Property.jar");
		makeJar(jarFile, Set_Property.class.getName());

		final LegacyEnvironment headlessIJ1 = getTestEnvironment();
		headlessIJ1.addPluginClasspath(jarFile);
		headlessIJ1.runMacro("", "");
		final Map<String, String> menuItems =
			new LinkedHashMap<>(headlessIJ1.getMenuStructure());

		assertTrue("does not have 'Set Property'", menuItems
			.containsKey("Plugins>Set Property"));

		final LegacyEnvironment ij1 = getTestEnvironment(false, false);
		ij1.addPluginClasspath(jarFile);
		final Frame ij1Frame =
			construct(ij1.getClassLoader(), "ij.ImageJ", ImageJ.NO_SHOW);
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
			if (menuPath.startsWith("Help>Examples>")) {
				// NB: Skip the Help>Examples menu. It is difficult to support in
				// headless mode via runtime patching. And no one needs it headless.
				continue;
			}
			if (item instanceof Menu) {
				assertMenuItems(menuItems, commands, menuPath + ">", (Menu) item);
			}
			else if ("-".equals(label)) {
				final String menuPath2 = menuPath + ++separatorCounter;
				assertTrue("Not found: " + menuPath2, menuItems.containsKey(menuPath2));
				menuItems.remove(menuPath2);
			}
			else if (!menuPath.startsWith("File>Open Recent>")) {
				assertTrue("Not found: " + menuPath, menuItems.containsKey(menuPath));
				assertEquals("Command for menu path: " + menuPath, commands.get(label),
					menuItems.get(menuPath));
				menuItems.remove(menuPath);
			}
		}
	}
}
