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

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javassist.ClassPool;
import javassist.NotFoundException;

/**
 * Overrides class behavior of ImageJ1 classes using bytecode manipulation. This
 * class uses the {@link CodeHacker} (which uses Javassist) to inject method
 * hooks, which are implemented in the {@link net.imagej.patcher} package.
 * 
 * @author Curtis Rueden
 */
public class LegacyInjector {

	/**
	 * This constant is used in place of EssentialLegacyHooks.class.getName() to
	 * make sure that the class is <b>not</b> yet loaded.
	 */
	final static String ESSENTIAL_LEGACY_HOOKS_CLASS =
		"net.imagej.patcher.EssentialLegacyHooks";

	/**
	 * Overrides class behavior of ImageJ1 classes by injecting method hooks.
	 * 
	 * @param classLoader the class loader into which to load the patched classes
	 */
	public void injectHooks(final ClassLoader classLoader) {
		injectHooks(classLoader, GraphicsEnvironment.isHeadless());
	}

	interface Callback {
		void call(CodeHacker hacker);
	}

	List<Callback> before = new ArrayList<Callback>();
	List<Callback> after = new ArrayList<Callback>();

	/**
	 * Determines the ImageJ 1.x version without loading the ImageJ 1.x classes.
	 * 
	 * @param hacker the {@link CodeHacker} instance to use
	 * @return the ImageJ 1.x version
	 */
	public static String getImageJ1Version(final CodeHacker hacker) {
		try {
			return hacker.getConstant("ij.ImageJ", "VERSION");
		}
		catch (final NotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Overrides class behavior of ImageJ1 classes by injecting method hooks.
	 * 
	 * @param classLoader the class loader into which to load the patched classes
	 * @param headless whether to include headless patches
	 */
	public void injectHooks(final ClassLoader classLoader, boolean headless) {
		if (alreadyPatched(classLoader)) return;

		final CodeHacker hacker = inject(classLoader, headless);

		for (final Callback callback : after) {
			callback.call(hacker);
		}

		// commit patches
		hacker.loadClasses();
	}

	/**
	 * Overrides class behavior of ImageJ1 classes by injecting method hooks.
	 * 
	 * @param classLoader the class loader into which to load the patched classes
	 * @param headless whether to include headless patches
	 * @return the CodeHacker instance for further patching or .jar writing
	 */
	private CodeHacker inject(final ClassLoader classLoader,
			final boolean headless) {
		final CodeHacker hacker = new CodeHacker(classLoader, new ClassPool(false));
		if (hacker.hasField("ij.IJ", "_hooks")) return hacker; // pre-patched

		for (final Callback callback : before) {
			callback.call(hacker);
		}

		// NB: Override class behavior before class loading gets too far along.
		hacker.insertPublicStaticField("ij.IJ", LegacyHooks.class, "_hooks", null);
		hacker.commitClass(LegacyHooks.class);
		hacker.commitClass(LegacyHooks.FatJarNameComparator.class);
		hacker.commitClass(ESSENTIAL_LEGACY_HOOKS_CLASS);
		final String legacyHooksClass = LegacyHooks.class.getName();

		final StringBuilder builder = new StringBuilder();
		for (final Field field : LegacyHooks.class.getDeclaredFields()) {
			builder.append("try {").append("java.lang.reflect.Field field = ")
				.append(legacyHooksClass).append(".class.getDeclaredField(\")").append(
					field.getName()).append("\"); ").append("field.setAccessible(true);")
				.append("field.set(hooks, field.get(_hooks));").append(
					"} catch (Throwable t) {").append(
					"if (ij.IJ.debugMode) t.printStackTrace();").append("}");
		}

		final String essentialHooksClass = ESSENTIAL_LEGACY_HOOKS_CLASS;
		hacker.insertNewMethod("ij.IJ",
				"public static " + legacyHooksClass + " _hooks(" + legacyHooksClass + " hooks)",
				legacyHooksClass + " previous = _hooks;"
				+ "if (previous != null && hooks != null) {" + builder + "}"
				+ "if (previous != null) previous.dispose();"
				+ "_hooks = $1 == null ? new " + essentialHooksClass + "() : $1;"
				+ "_hooks.installed();"
				+ "return previous;");
		hacker.addToClassInitializer("ij.IJ", "_hooks(null);");

		if (headless) {
			new LegacyHeadless(hacker).patch();
		}

		// override behavior of ij.ImageJ
		hacker.insertAtTopOfMethod("ij.ImageJ", "public void quit()",
			"if (!ij.IJ._hooks.quit()) return;");
		// intercept key pressed handling
		hacker.insertAtTopOfMethod("ij.ImageJ",
			"public void keyPressed(java.awt.event.KeyEvent e)",
			"if (ij.IJ._hooks.interceptKeyPressed($1)) return;");

		// override behavior of ij.IJ
		hacker.insertAtBottomOfMethod("ij.IJ",
			"public static void showProgress(double progress)",
			"ij.IJ._hooks.showProgress($1);");
		hacker.insertAtBottomOfMethod("ij.IJ",
			"public static void showProgress(int currentIndex, int finalIndex)",
			"ij.IJ._hooks.showProgress($1, $2);");
		hacker.insertAtBottomOfMethod("ij.IJ",
			"public static void showStatus(java.lang.String status)",
			"ij.IJ._hooks.showStatus($1);");
		hacker.insertAtTopOfMethod("ij.IJ",
				"public static Object runPlugIn(java.lang.String commandName, java.lang.String className, java.lang.String arg)",
				" if (classLoader != null) Thread.currentThread().setContextClassLoader(classLoader);"
				+ "Object o = _hooks.interceptRunPlugIn($2, $3);"
				+ "if (o != null) return o;"
				+ "if (\"ij.IJ.init\".equals($2)) {"
				+ " ij.IJ.init();"
				+ " return null;"
				+ "}");
		hacker.insertAtTopOfMethod("ij.IJ",
				"public static void log(java.lang.String message)",
				"ij.IJ._hooks.log($1);");
		hacker.insertAtTopOfMethod("ij.IJ",
			"static java.lang.Object runUserPlugIn(java.lang.String commandName, java.lang.String className, java.lang.String arg, boolean createNewLoader)",
			"if (classLoader != null) Thread.currentThread().setContextClassLoader(classLoader);");
		hacker.insertAtBottomOfMethod("ij.IJ",
				"static void init()",
				"ij.IJ._hooks.initialized();");
		hacker.insertAtBottomOfMethod("ij.IJ",
				"static void init(ij.ImageJ imagej, java.applet.Applet theApplet)",
				"ij.IJ._hooks.initialized();");

		// override behavior of ij.ImagePlus
		hacker.insertAtBottomOfMethod("ij.ImagePlus",
			"public void updateAndDraw()",
			"ij.IJ._hooks.registerImage(this);");
		hacker.insertAtBottomOfMethod("ij.ImagePlus",
			"public void repaintWindow()",
			"ij.IJ._hooks.registerImage(this);");
		hacker.insertAtBottomOfMethod("ij.ImagePlus",
			"public void show(java.lang.String statusMessage)",
			"ij.IJ._hooks.registerImage(this);");
		hacker.insertAtBottomOfMethod("ij.ImagePlus",
			"public void hide()",
			"ij.IJ._hooks.unregisterImage(this);");
		hacker.insertAtBottomOfMethod("ij.ImagePlus",
			"public void close()",
			"ij.IJ._hooks.unregisterImage(this);");

		// override behavior of ij.gui.ImageWindow
		hacker.insertNewMethod("ij.gui.ImageWindow",
			"public void setVisible(boolean vis)",
			"if ($1) ij.IJ._hooks.registerImage(this.getImagePlus());"
			+ "if (ij.IJ._hooks.isLegacyMode()) { super.setVisible($1); }");
		hacker.insertNewMethod("ij.gui.ImageWindow",
			"public void show()",
			"ij.IJ._hooks.registerImage(this.getImagePlus());"
			+ "if (ij.IJ._hooks.isLegacyMode()) { super.show(); }");
		hacker.insertAtTopOfMethod("ij.gui.ImageWindow",
			"public void close()",
			"ij.IJ._hooks.unregisterImage(this.getImagePlus());");

		// override behavior of PluginClassLoader
		hacker.insertNewMethod("ij.io.PluginClassLoader",
			"void addRecursively(java.io.File directory)",
			"java.lang.String[] list = ij.IJ._hooks.addPluginDirectory($1, $1.list());"
			+ "if (list == null) return;"
			+ "for (int i = 0; i < list.length; i++) {"
			+ "  java.io.File file = new java.io.File($1, list[i]);"
			+ "  if (file.isDirectory()) addRecursively(file);"
			+ "  else if (file.getName().endsWith(\".jar\")) addURL(file.toURI().toURL());"
			+ "}");
		hacker.insertAtTopOfMethod("ij.io.PluginClassLoader",
			"void init(java.lang.String path)",
			"java.io.File plugins = new java.io.File($1);"
			+ "if (plugins.getName().equals(\"plugins\")) {"
			+ "  java.io.File root = plugins.getParentFile();"
			+ "  if (root != null) addRecursively(new java.io.File(root, \"jars\"));"
			+ "}"
			+ "final java.util.Iterator iter = ij.IJ._hooks.handleExtraPluginJars().iterator();"
			+ "while (iter.hasNext()) {"
			+ "  addURL(((java.io.File) iter.next()).toURL());"
			+ "}");
		hacker.insertAtBottomOfMethod("ij.io.PluginClassLoader",
			"void init(java.lang.String path)",
			"ij.IJ._hooks.newPluginClassLoader(this);");
		// handle fat .jar files in jars/ by demoting them to the end
		hacker.replaceCallInMethod("ij.io.PluginClassLoader",
			"private void addDirectory(java.io.File f)",
			"java.io.File", "list",
			"$_ = ij.IJ._hooks.addPluginDirectory($0, $proceed($$));");

		// fix NullPointerException
		hacker.replaceCallInMethod("ij.Menus",
			"void installJarPlugin(java.lang.String jar, java.lang.String s)",
			"java.lang.String", "startsWith",
			"if ($1 == null) $_ = false;" +
			"else $_ = $proceed($$);");

		// avoid duplicate menu entries
		hacker.insertAtTopOfMethod("ij.Menus",
			"void installJarPlugins()",
			"if (jarFiles != null) {" +
			"  java.util.HashSet seen = new java.util.HashSet();" +
			"  for (java.util.Iterator iter = jarFiles.iterator(); iter.hasNext(); ) {" +
			"    Object jar = iter.next();" +
			"    if (seen.contains(jar)) iter.remove();" +
			"    else seen.add(jar);" +
			"  }" +
			"  seen = null;" +
			"}");

		// override behavior of MacAdapter, if needed
		if (Utils.hasClass("com.apple.eawt.ApplicationListener")) {
			// NB: If com.apple.eawt package is present, override IJ1's MacAdapter.
			hacker.insertAtTopOfMethod("MacAdapter",
				"public void run(java.lang.String arg)",
				"if (!ij.IJ._hooks.isLegacyMode()) return;");
		}

		// override behavior of ij.plugin.frame.RoiManager
		hacker.insertNewMethod("ij.plugin.frame.RoiManager",
			"public void show()",
			"if (ij.IJ._hooks.isLegacyMode()) { super.show(); }");
		hacker.insertNewMethod("ij.plugin.frame.RoiManager",
			"public void setVisible(boolean b)",
			"if (ij.IJ._hooks.isLegacyMode()) { super.setVisible($1); }");

		// avoid NullPointerException upon duplicate dispose()
		hacker.replaceCallInMethod("ij.Prefs",
			"public static void savePreferences()",
			"ij.ImageJ", "savePreferences",
			"if ($0 != null) $_ = $proceed($$);");
		LegacyExtensions.injectHooks(hacker, headless);

		// avoid ClassCastException when ImageJ 1.x can carelessly cast an ImageWindow to a StackWindow
		hacker.guardCast("ij.ImagePlus",
			"public void setStack(java.lang.String title, ij.ImageStack newStack)",
			"ij.gui.StackWindow");
		hacker.replaceCallInMethod("ij.ImagePlus",
			"public void setStack(java.lang.String title, ij.ImageStack newStack)",
			"ij.gui.StackWindow", "validDimensions",
			"$_ = $0 == null ? false : $proceed($$);");

		// avoid showing newly-created StackWindow instances in batch mode
		hacker.replaceCallInMethod("ij.gui.StackWindow",
			"public <init>(ij.ImagePlus imp, ij.gui.ImageCanvas ic)",
			"ij.gui.StackWindow",
			"show",
			"if (!ij.macro.Interpreter.batchMode) show();");

		return hacker;
	}

	/**
	 * Writes a .jar file with the patched classes.
	 * 
	 * @param outputJar the .jar file to write to
	 * @param headless whether to include the headless patches
	 * @param fullIJJar whether to include unpatched ImageJ 1.x classes and
	 *          resources, too
	 * @throws ClassNotFoundException
	 * @throws IOException
	 * @throws NotFoundException
	 */
	public static void writeJar(final File outputJar, final boolean headless,
		final boolean fullIJJar) throws ClassNotFoundException,
		IOException, NotFoundException
	{
		final File parentDirectory = outputJar.getParentFile();
		if (parentDirectory != null && !parentDirectory.isDirectory() &&
			!parentDirectory.mkdirs())
		{
			throw new IOException("Could not make directory: " + parentDirectory);
		}
		final LegacyInjector injector = new LegacyInjector();
		final ClassLoader loader = new LegacyClassLoader(headless);
		final CodeHacker hacker = injector.inject(loader, headless);
		if (!fullIJJar) {
			hacker.writeJar(outputJar);
		}
		else {
			URL location = Utils.getLocation(loader.loadClass("ij.IJ"));
			if (location.getPath().endsWith(".jar")) {
				location = new URL("jar:" + location.toString() + "!/");
			}
			hacker.writeJar(location, outputJar);
		}
	}

	public static void preinit() {
		preinit(Thread.currentThread().getContextClassLoader());
	}

	public static void preinit(ClassLoader classLoader) {
		if (alreadyPatched(classLoader)) return;

		// find the appropriate class loader in the loader chain
		for (;;) {
			final ClassLoader parent = classLoader.getParent();
			if (parent == null || parent.getResource("ij/IJ.class") == null) {
				break;
			}
			classLoader = parent;
		}

		final boolean headless = GraphicsEnvironment.isHeadless();
		try {
			final LegacyEnvironment ij1 = new LegacyEnvironment(classLoader, headless);
			ij1.disableInitializer();
			ij1.noPluginClassLoader();
			ij1.suppressIJ1ScriptDiscovery();
			ij1.applyPatches();
		} catch (final ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	static boolean alreadyPatched(final ClassLoader classLoader) {
		Class<?> ij = null;
		try {
			final Method findLoadedClass = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
			findLoadedClass.setAccessible(true);
			for (ClassLoader loader = classLoader; loader != null; loader = loader.getParent()) {
				ij = (Class<?>)findLoadedClass.invoke(loader, "ij.IJ");
				if (ij != null) break;
			}
			if (ij == null) return false;
		} catch (Exception e) {
			// fall through
		}

		if (ij == null) try {
			ij = classLoader.loadClass("ij.IJ");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Did not find ij.IJ in " + classLoader);
		}

		try {
			final Field hooks = ij.getField("_hooks");
			if (hooks.getType() != LegacyHooks.class) {
				throw new RuntimeException("Unexpected type of ij.IJ._hooks: " +
					hooks.getType() + " (loader: " + hooks.getType().getClassLoader() +
					")");
			}
			return true;
		}
		catch (Exception e) {
			throw CodeHacker.javaAgentHint("No _hooks field found in ij.IJ", e);
		}
	}

	public static LegacyHooks installHooks(final ClassLoader classLoader, LegacyHooks hooks) throws UnsupportedOperationException {
		try {
			final Method hooksSetter = classLoader.loadClass("ij.IJ").getMethod("_hooks", LegacyHooks.class);
			return (LegacyHooks) hooksSetter.invoke(null, hooks);
		}
		catch (Throwable t) {
			throw new UnsupportedOperationException(t);
		}
	}

}
