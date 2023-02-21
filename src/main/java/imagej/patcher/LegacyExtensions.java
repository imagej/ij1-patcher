/*
 * #%L
 * ImageJ2 software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2023 ImageJ2 developers.
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

import java.util.HashSet;
import java.util.Set;


/**
 * Assorted legacy patches / extension points for use in the legacy mode.
 * 
 * <p>
 * The Fiji distribution of ImageJ accumulated patches and extensions to ImageJ
 * 1.x over the years.
 * </p>
 * 
 * <p>
 * However, there was a lot of overlap with the ImageJ2 project, so it was
 * decided to focus Fiji more on the life-science specific part and move all the
 * parts that are of more general use into ImageJ2. That way, it is pretty
 * clear-cut what goes into Fiji and what goes into ImageJ2.
 * </p>
 * 
 * <p>
 * This class contains the extension points (such as being able to override the
 * macro editor) ported from Fiji as well as the code for runtime patching
 * ImageJ 1.x needed both for the extension points and for more backwards
 * compatibility than ImageJ 1.x wanted to provide (e.g. when public methods or
 * classes that were used by Fiji plugins were removed all of a sudden, without
 * being deprecated first).
 * </p>
 * 
 * <p>
 * The code in this class is only used in the legacy mode.
 * </p>
 * 
 * @author Johannes Schindelin
 */
@Deprecated
class LegacyExtensions {

	/*
	 * Extension points
	 */

	/*
	 * Runtime patches (using CodeHacker for patching)
	 */

	/**
	 * Applies runtime patches to ImageJ 1.x for backwards-compatibility and extension points.
	 * 
	 * <p>
	 * These patches enable a patched ImageJ 1.x to call a different script editor or to override
	 * the application icon.
	 * </p>
	 * 
	 * <p>
	 * This method is called by {@link LegacyInjector#injectHooks(ClassLoader)}.
	 * </p>
	 * 
	 * @param hacker the {@link CodeHacker} instance
	 */
	public static void injectHooks(final CodeHacker hacker, boolean headless) {
		//
		// Below are patches to make ImageJ 1.x more backwards-compatible
		//

		// add back the (deprecated) killProcessor(), and overlay methods
		final String[] imagePlusMethods = {
				"public void killProcessor()",
				"{}",
				"public void setDisplayList(java.util.Vector list)",
				"getCanvas().setDisplayList(list);",
				"public java.util.Vector getDisplayList()",
				"return getCanvas().getDisplayList();",
				"public void setDisplayList(ij.gui.Roi roi, java.awt.Color strokeColor,"
				+ " int strokeWidth, java.awt.Color fillColor)",
				"setOverlay(roi, strokeColor, strokeWidth, fillColor);"
		};
		for (int i = 0; i < imagePlusMethods.length; i++) try {
			hacker.insertNewMethod("ij.ImagePlus",
					imagePlusMethods[i], imagePlusMethods[++i]);
		} catch (Exception e) { /* ignore */ }

		// make sure that ImageJ has been initialized in batch mode
		hacker.insertAtTopOfMethod("ij.IJ",
				"public static java.lang.String runMacro(java.lang.String macro, java.lang.String arg)",
				"if (ij==null && ij.Menus.getCommands()==null) init();");

		try {
			hacker.insertNewMethod("ij.CompositeImage",
				"public ij.ImagePlus[] splitChannels(boolean closeAfter)",
				"ij.ImagePlus[] result = ij.plugin.ChannelSplitter.split(this);"
				+ "if (closeAfter) close();"
				+ "return result;");
			hacker.insertNewMethod("ij.plugin.filter.RGBStackSplitter",
				"public static ij.ImagePlus[] splitChannelsToArray(ij.ImagePlus imp, boolean closeAfter)",
				"if (!imp.isComposite()) {"
				+ "  ij.IJ.error(\"splitChannelsToArray was called on a non-composite image\");"
				+ "  return null;"
				+ "}"
				+ "ij.ImagePlus[] result = ij.plugin.ChannelSplitter.split(imp);"
				+ "if (closeAfter)"
				+ "  imp.close();"
				+ "return result;");
		} catch (IllegalArgumentException e) {
			final Throwable cause = e.getCause();
			if (cause != null && !cause.getClass().getName().endsWith("DuplicateMemberException")) {
				throw e;
			}
		}

		// handle mighty mouse (at least on old Linux, Java mistakes the horizontal wheel for a popup trigger)
		for (String fullClass : new String[] {
				"ij.gui.ImageCanvas",
				"ij.plugin.frame.RoiManager",
				"ij.text.TextPanel",
				"ij.gui.Toolbar"
		}) {
			hacker.handleMightyMousePressed(fullClass);
		}

		// tell IJ#runUserPlugIn to catch NoSuchMethodErrors
		final String runUserPlugInSig = "static java.lang.Object runUserPlugIn(java.lang.String commandName, java.lang.String className, java.lang.String arg, boolean createNewLoader)";
		hacker.addCatch("ij.IJ", runUserPlugInSig, "java.lang.NoSuchMethodError",
				"if (ij.IJ._hooks.handleNoSuchMethodError($e))"
				+ "  throw new RuntimeException(ij.Macro.MACRO_CANCELED);"
				+ "throw $e;");

		// tell IJ#runUserPlugIn to be more careful about catching NoClassDefFoundError
		hacker.insertPrivateStaticField("ij.IJ", String.class, "originalClassName");
		hacker.insertAtTopOfMethod("ij.IJ", runUserPlugInSig, "originalClassName = $2;");
		hacker.insertAtTopOfExceptionHandlers("ij.IJ", runUserPlugInSig, "java.lang.NoClassDefFoundError",
				"java.lang.String realClassName = $1.getMessage();"
							+ "int spaceParen = realClassName.indexOf(\" (\");"
							+ "if (spaceParen > 0) realClassName = realClassName.substring(0, spaceParen);"
							+ "if (!originalClassName.replace('.', '/').equals(realClassName)) {"
							+ " if (realClassName.startsWith(\"javax/vecmath/\") || realClassName.startsWith(\"com/sun/j3d/\") || realClassName.startsWith(\"javax/media/j3d/\"))"
							+ "  ij.IJ.error(\"The class \" + originalClassName + \" did not find Java3D (\" + realClassName + \")\\nPlease call Plugins>3D Viewer to install\");"
							+ " else"
							+ "  ij.IJ.handleException($1);"
							+ " return null;"
							+ "}");

		// let the plugin class loader find stuff in $HOME/.plugins, too
		addExtraPlugins(hacker);

		// make sure that the GenericDialog is disposed in macro mode
		if (hacker.hasMethod("ij.gui.GenericDialog", "public void showDialog()")) {
			hacker.insertAtTopOfMethod("ij.gui.GenericDialog", "public void showDialog()", "if (macro) dispose();");
		}

		// make sure NonBlockingGenericDialog does not wait in macro mode
		hacker.replaceCallInMethod("ij.gui.NonBlockingGenericDialog", "public void showDialog()", "java.lang.Object", "wait", "if (isShowing()) wait();");

		// tell the showStatus() method to show the version() instead of empty status
		hacker.insertAtTopOfMethod("ij.ImageJ", "void showStatus(java.lang.String s)", "if ($1 == null || \"\".equals($1)) $1 = version();");

		// make sure that the GenericDialog does not make a hidden main window visible
		if (!headless) {
			hacker.replaceCallInMethod("ij.gui.GenericDialog",
				"public <init>(java.lang.String title, java.awt.Frame f)",
				"java.awt.Dialog", "super",
				"$proceed($1 != null && $1.isVisible() ? $1 : null, $2, $3);");
		}

		// handle custom icon (e.g. for Fiji)
		addIconHooks(hacker);

		// optionally disallow batch mode from calling System.exit()
		hacker.insertPrivateStaticField("ij.ImageJ", Boolean.TYPE, "batchModeMayExit");
		hacker.insertAtTopOfMethod("ij.ImageJ", "public static void main(java.lang.String[] args)",
			"batchModeMayExit = true;"
			+ "for (int i = 0; i < $1.length; i++) {"
			+ "  if (\"-batch-no-exit\".equals($1[i])) {"
			+ "    batchModeMayExit = false;"
			+ "    $1[i] = \"-batch\";"
			+ "  }"
			+ "}");
		hacker.replaceCallInMethod("ij.ImageJ", "public static void main(java.lang.String[] args)", "java.lang.System", "exit",
			"if (batchModeMayExit) System.exit($1);"
			+ "if ($1 == 0) return;"
			+ "throw new RuntimeException(\"Exit code: \" + $1);");

		// do not use the current directory as IJ home on Windows
		String prefsDir = System.getenv("IJ_PREFS_DIR");
		if (prefsDir == null && System.getProperty("os.name").startsWith("Windows")) {
			prefsDir = System.getenv("user.home");
		}
		if (prefsDir != null) {
			hacker.overrideFieldWrite("ij.Prefs", "public static java.lang.String getPrefsDir()",
				"prefsDir", "$_ = \"" + prefsDir + "\";");
		}

		// tool names can be prefixes of other tools, watch out for that!
		hacker.replaceCallInMethod("ij.gui.Toolbar", "public int getToolId(java.lang.String name)", "java.lang.String", "startsWith",
			"$_ = $0.equals($1) || $0.startsWith($1 + \"-\") || $0.startsWith($1 + \" -\");");

		// make sure Rhino gets the correct class loader
		hacker.insertAtTopOfMethod("JavaScriptEvaluator", "public void run()",
			"Thread.currentThread().setContextClassLoader(ij.IJ.getClassLoader());");

		// make sure that the check for Bio-Formats is correct
		hacker.addToClassInitializer("ij.io.Opener",
			"try {"
			+ "    ij.IJ.getClassLoader().loadClass(\"loci.plugins.LociImporter\");"
			+ "    bioformats = true;"
			+ "} catch (ClassNotFoundException e) {"
			+ "    bioformats = false;"
			+ "}");

		// make sure that symbolic links are *not* resolved (because then the parent info in the FileInfo would be wrong)
		hacker.replaceCallInMethod("ij.plugin.DragAndDrop", "public void openFile(java.io.File f)", "java.io.File", "getCanonicalPath",
			"$_ = $0.getAbsolutePath();");

		// make sure no dialog is opened in headless mode
		hacker.insertAtTopOfMethod("ij.macro.Interpreter", "void showError(java.lang.String title, java.lang.String msg, java.lang.String[] variables)",
			"if (ij.IJ.getInstance() == null) {"
			+ "  java.lang.System.err.println($1 + \": \" + $2);"
			+ "  return;"
			+ "}");

		// let IJ.handleException override the macro interpreter's call()'s exception handling
		hacker.insertAtTopOfExceptionHandlers("ij.macro.Functions", "java.lang.String call()", "java.lang.reflect.InvocationTargetException",
			"ij.IJ.handleException($1);"
			+ "return null;");

		// Add back the "Convert to 8-bit Grayscale" checkbox to Import>Image Sequence
		if (!hacker.hasField("ij.plugin.FolderOpener", "convertToGrayscale")) {
			hacker.insertPrivateStaticField("ij.plugin.FolderOpener", Boolean.TYPE, "convertToGrayscale");
			hacker.replaceCallInMethod("ij.plugin.FolderOpener", "public void run(java.lang.String arg)", "ij.io.Opener", "openImage",
				"$_ = $0.openImage($1, $2);"
				+ "if (convertToGrayscale) {"
				+ "  final String saved = ij.Macro.getOptions();"
				+ "  ij.IJ.run($_, \"8-bit\", \"\");"
				+ "  if (saved != null && !saved.equals(\"\")) ij.Macro.setOptions(saved);"
				+ "}");
			hacker.replaceCallInMethod("ij.plugin.FolderOpener", "boolean showDialog(ij.ImagePlus imp, java.lang.String[] list)",
				"ij.plugin.FolderOpener$FolderOpenerDialog", "addCheckbox",
				"$0.addCheckbox(\"Convert to 8-bit Grayscale\", convertToGrayscale);"
				+ "$0.addCheckbox($1, $2);", 1);
			hacker.replaceCallInMethod("ij.plugin.FolderOpener", "boolean showDialog(ij.ImagePlus imp, java.lang.String[] list)",
					"ij.plugin.FolderOpener$FolderOpenerDialog", "getNextBoolean",
					"convertToGrayscale = $0.getNextBoolean();"
					+ "$_ = $0.getNextBoolean();"
					+ "if (convertToGrayscale && $_) {"
					+ "  ij.IJ.error(\"Cannot convert to grayscale and RGB at the same time.\");"
					+ "  return false;"
					+ "}", 1);
		}

		// handle HTTPS in addition to HTTP
		hacker.handleHTTPS("ij.macro.Functions", "java.lang.String exec()");
		hacker.handleHTTPS("ij.plugin.DragAndDrop", "public void drop(java.awt.dnd.DropTargetDropEvent dtde)");
		hacker.handleHTTPS("ij.plugin.ListVirtualStack", "public void run(java.lang.String arg)");
		hacker.handleHTTPS("ij.plugin.ListVirtualStack", "java.lang.String[] open(java.lang.String path)");

		addEditorExtensionPoints(hacker);
		insertAppNameHooks(hacker);
		insertRefreshMenusHook(hacker);
		overrideStartupMacrosForFiji(hacker);
		handleMacAdapter(hacker);
		handleMenuCallbacks(hacker, headless);
		installOpenInterceptor(hacker);
	}

	/**
	 * Install a hook to optionally run a Runnable at the end of Help>Refresh Menus.
	 * 
	 * <p>
	 * See {@link LegacyExtensions#runAfterRefreshMenus(Runnable)}.
	 * </p>
	 * 
	 * @param hacker the {@link CodeHacker} to use for patching
	 */
	private static void insertRefreshMenusHook(CodeHacker hacker) {
		hacker.insertAtBottomOfMethod("ij.Menus", "public static void updateImageJMenus()",
			"ij.IJ._hooks.runAfterRefreshMenus();");
	}

	private static void addEditorExtensionPoints(final CodeHacker hacker) {
		hacker.insertAtTopOfMethod("ij.io.Opener", "public void open(java.lang.String path)",
			"if (isText($1) && ij.IJ._hooks.openInEditor($1)) return;");
		hacker.dontReturnOnNull("ij.plugin.frame.Recorder", "void createMacro()");
		hacker.replaceCallInMethod("ij.plugin.frame.Recorder", "void createMacro()",
			"ij.IJ", "runPlugIn",
			"$_ = null;");
		hacker.replaceCallInMethod("ij.plugin.frame.Recorder", "void createMacro()",
			"ij.plugin.frame.Editor", "createMacro",
			"if ($1.endsWith(\".txt\")) {"
			+ "  $1 = $1.substring($1.length() - 3) + \"ijm\";"
			+ "}"
			+ "if (!ij.IJ._hooks.createInEditor($1, $2)) {"
			+ "  ((ij.plugin.frame.Editor)ij.IJ.runPlugIn(\"ij.plugin.frame.Editor\", \"\")).createMacro($1, $2);"
			+ "}");
		hacker.insertPublicStaticField("ij.plugin.frame.Recorder", String.class, "nameForEditor", null);
		hacker.insertAtTopOfMethod("ij.plugin.frame.Recorder", "void createPlugin(java.lang.String text, java.lang.String name)",
			"this.nameForEditor = $2;");
		hacker.replaceCallInMethod("ij.plugin.frame.Recorder", "void createPlugin(java.lang.String text, java.lang.String name)",
			"ij.IJ", "runPlugIn",
			"$_ = null;"
			+ "new ij.plugin.NewPlugin().createPlugin(this.nameForEditor, ij.plugin.NewPlugin.PLUGIN, $2);"
			+ "return;");
		hacker.replaceCallInMethod("ij.plugin.NewPlugin", "public void createMacro(java.lang.String name)",
			"ij.plugin.frame.Editor", "<init>",
			"$_ = null;");
		hacker.replaceCallInMethod("ij.plugin.NewPlugin",  "public void createMacro(java.lang.String name)",
			"ij.plugin.frame.Editor", "create",
			"if ($1.endsWith(\".txt\")) {"
			+ "  $1 = $1.substring(0, $1.length() - 3) + \"ijm\";"
			+ "}"
			+ "if ($1.endsWith(\".ijm\") && ij.IJ._hooks.createInEditor($1, $2)) return;"
			+ "int options = (monospaced ? ij.plugin.frame.Editor.MONOSPACED : 0)"
			+ "  | (menuBar ? ij.plugin.frame.Editor.MENU_BAR : 0);"
			+ "new ij.plugin.frame.Editor(rows, columns, 0, options).create($1, $2);");
		hacker.dontReturnOnNull("ij.plugin.NewPlugin", "public void createPlugin(java.lang.String name, int type, java.lang.String methods)");
		hacker.replaceCallInMethod("ij.plugin.NewPlugin", "public void createPlugin(java.lang.String name, int type, java.lang.String methods)",
			"ij.IJ", "runPlugIn",
			"$_ = null;");
		hacker.replaceCallInMethod("ij.plugin.NewPlugin", "public void createPlugin(java.lang.String name, int type, java.lang.String methods)",
			"ij.plugin.frame.Editor", "create",
			"if (!ij.IJ._hooks.createInEditor($1, $2)) {"
			+ "  ((ij.plugin.frame.Editor)ij.IJ.runPlugIn(\"ij.plugin.frame.Editor\", \"\")).create($1, $2);"
			+ "}");
	}

	/**
	 * Inserts hooks to replace the application name.
	 */
	private static void insertAppNameHooks(final CodeHacker hacker) {
		final String appName = "ij.IJ._hooks.getAppName()";
		final String replace = ".replace(\"ImageJ\", " + appName + ")";
		hacker.insertAtTopOfMethod("ij.IJ", "public void error(java.lang.String title, java.lang.String msg)",
				"if ($1 == null || $1.equals(\"ImageJ\")) $1 = " + appName + ";");
		hacker.insertAtBottomOfMethod("ij.ImageJ", "public java.lang.String version()", "$_ = $_" + replace + ";");
		hacker.replaceAppNameInCall("ij.ImageJ", "public <init>(java.applet.Applet applet, int mode)", "super", 1, appName);
		hacker.replaceAppNameInNew("ij.ImageJ", "public void run()", "ij.gui.GenericDialog", 1, appName);
		hacker.replaceAppNameInCall("ij.ImageJ", "public void run()", "addMessage", 1, appName);
		if (hacker.hasMethod("ij.plugin.CommandFinder", "public void export()")) {
			hacker.replaceAppNameInNew("ij.plugin.CommandFinder", "public void export()", "ij.text.TextWindow", 1, appName);
		}
		hacker.replaceAppNameInCall("ij.plugin.Hotkeys", "public void removeHotkey()", "addMessage", 1, appName);
		hacker.replaceAppNameInCall("ij.plugin.Hotkeys", "public void removeHotkey()", "showStatus", 1, appName);
		if (hacker.existsClass("ij.plugin.AppearanceOptions")) {
			hacker.replaceAppNameInCall("ij.plugin.AppearanceOptions", "void showDialog()", "showMessage", 2, appName);
		} else {
			hacker.replaceAppNameInCall("ij.plugin.Options", "public void appearance()", "showMessage", 2, appName);
		}
		hacker.replaceAppNameInCall("ij.gui.YesNoCancelDialog", "public <init>(java.awt.Frame parent, java.lang.String title, java.lang.String msg)", "super", 2, appName);
		hacker.replaceAppNameInCall("ij.gui.Toolbar", "private void showMessage(int toolId)", "showStatus", 1, appName);
	}

	private static void addIconHooks(final CodeHacker hacker) {
		final String icon = "ij.IJ._hooks.getIconURL()";
		hacker.replaceCallInMethod("ij.ImageJ", "void setIcon()", "java.lang.Class", "getResource",
			"java.net.URL _iconURL = " + icon + ";\n" +
			"if (_iconURL == null) $_ = $0.getResource($1);" +
			"else $_ = _iconURL;");
		hacker.insertAtTopOfMethod("ij.ImageJ", "public <init>(java.applet.Applet applet, int mode)",
				"if ($2 != 2 /* ij.ImageJ.NO_SHOW */) setIcon();");
		hacker.insertAtTopOfMethod("ij.WindowManager", "public void addWindow(java.awt.Frame window)",
			"java.net.URL _iconURL = " + icon + ";\n"
			+ "if (_iconURL != null && $1 != null) {"
			+ "  java.awt.Image img = $1.createImage((java.awt.image.ImageProducer)_iconURL.getContent());"
			+ "  if (img != null) {"
			+ "    $1.setIconImage(img);"
			+ "  }"
			+ "}");
	}

	/**
	 * Makes sure that the legacy plugin class loader finds stuff in
	 * {@code $HOME/.plugins/}.
	 */
	private static void addExtraPlugins(final CodeHacker hacker) {
		for (final String methodName : new String[] { "addJAR", "addJar" }) {
			if (hacker.hasMethod("ij.io.PluginClassLoader", "private void "
					+ methodName + "(java.io.File file)")) {
				hacker.insertAtTopOfMethod("ij.io.PluginClassLoader",
						"void init(java.lang.String path)",
						extraPluginJarsHandler("if (file.isDirectory()) addDirectory(file);" +
								"else " + methodName + "(file);"));
			}
		}
		// avoid parsing ij.jar for plugins
		hacker.replaceCallInMethod("ij.Menus",
			"InputStream autoGenerateConfigFile(java.lang.String jar)",
			"java.util.zip.ZipEntry", "getName",
			"$_ = $proceed($$);" +
			"if (\"IJ_Props.txt\".equals($_)) return null;");
		// make sure that extra directories added to the plugin class path work, too
		hacker.insertAtTopOfMethod("ij.Menus",
			"InputStream getConfigurationFile(java.lang.String jar)",
			"java.io.File isDir = new java.io.File($1);" +
			"if (!isDir.exists()) return null;" +
			"if (isDir.isDirectory()) {" +
			"  java.io.File config = new java.io.File(isDir, \"plugins.config\");" +
			"  if (config.exists()) return new java.io.FileInputStream(config);" +
			"  return ij.IJ._hooks.autoGenerateConfigFile(isDir);" +
			"}");
		// fix overzealous assumption that all plugins are in plugins.dir
		hacker.insertPrivateStaticField("ij.Menus", Set.class, "_extraJars");
		hacker.insertAtTopOfMethod("ij.Menus",
			"java.lang.String getSubmenuName(java.lang.String jarPath)",
			"if (_extraJars.contains($1)) return null;");
		// add the extra .jar files to the list of plugin .jar files to be processed.
		hacker.insertAtBottomOfMethod("ij.Menus",
				"public static synchronized java.lang.String[] getPlugins()",
				"if (_extraJars == null) _extraJars = new java.util.HashSet();" +
				extraPluginJarsHandler("if (jarFiles == null) jarFiles = new java.util.Vector();" +
						"jarFiles.addElement(file.getAbsolutePath());" +
						"_extraJars.add(file.getAbsolutePath());"));
		// exclude -sources.jar entries generated by Maven.
		hacker.insertAtBottomOfMethod("ij.Menus",
				"public static synchronized java.lang.String[] getPlugins()",
				"if (jarFiles != null) {" +
				"  for (int i = jarFiles.size() - 1; i >= 0; i--) {" +
				"    String entry = (String) jarFiles.elementAt(i);" +
				"    if (entry.endsWith(\"-sources.jar\")) {" +
				"      jarFiles.remove(i);" +
				"    }" +
				"  }" +
				"}");
		// force IJ.getClassLoader() to instantiate a PluginClassLoader
		hacker.replaceCallInMethod(
				"ij.IJ",
				"public static ClassLoader getClassLoader()",
				"java.lang.System",
				"getProperty",
				"$_ = System.getProperty($1);\n"
						+ "if ($_ == null && $1.equals(\"plugins.dir\")) $_ = \"/non-existant/\";");
	}

	private static String extraPluginJarsHandler(final String code) {
		return "for (java.util.Iterator iter = ij.IJ._hooks.handleExtraPluginJars().iterator();\n" +
				"iter.hasNext(); ) {\n" +
				"\tjava.io.File file = (java.io.File)iter.next();\n" +
				code + "\n" +
				"}\n";
	}

	private static void overrideStartupMacrosForFiji(CodeHacker hacker) {
		hacker.replaceCallInMethod("ij.Menus", "void installStartupMacroSet()", "java.io.File", "<init>",
				"if ($1.endsWith(\"StartupMacros.txt\")) {" +
				" java.lang.String fijiPath = $1.substring(0, $1.length() - 3) + \"fiji.ijm\";" +
				" java.io.File fijiFile = new java.io.File(fijiPath);" +
				" $_ = fijiFile.exists() ? fijiFile : new java.io.File($1);" +
				"} else $_ = new java.io.File($1);");
		hacker.replaceCallInMethod("ij.Menus", "void installStartupMacroSet()", "ij.plugin.MacroInstaller", "installFile",
				"if ($1.endsWith(\"StartupMacros.txt\")) {" +
				" java.lang.String fijiPath = $1.substring(0, $1.length() - 3) + \"fiji.ijm\";" +
				" java.io.File fijiFile = new java.io.File(fijiPath);" +
				" $0.installFile(fijiFile.exists() ? fijiFile.getPath() : $1);" +
				"} else $0.installFile($1);");
	}

	private static void handleMacAdapter(final CodeHacker hacker) {
		// Without the ApplicationListener, MacAdapter cannot load, and hence CodeHacker would fail
		// to load it if we patched the class.
		if (!hacker.existsClass("com.apple.eawt.ApplicationListener")) return;

		hacker.insertAtTopOfMethod("MacAdapter", "public void run(java.lang.String arg)",
			"return;");
	}

	private static void handleMenuCallbacks(final CodeHacker hacker, boolean headless) {
		hacker.insertAtTopOfMethod("ij.Menus",
			"java.lang.String addMenuBar()",
			"ij.IJ._hooks.addMenuItem(null, null);");
		hacker.insertPrivateStaticField("ij.Menus", String.class, "_currentMenuPath");
		// so that addSubMenu() has the correct menu path -- even in headless mode
		hacker.insertAtTopOfMethod("ij.Menus",
				"private static java.awt.Menu getMenu(java.lang.String menuName, boolean readFromProps)",
				"_currentMenuPath = $1;");
		// so that addPlugInItem() has the correct menu path -- even in headless mode
		hacker.insertAtBottomOfMethod("ij.Menus",
				"private static java.awt.Menu getMenu(java.lang.String menuName, boolean readFromProps)",
				"_currentMenuPath = $1;");
		hacker.insertAtTopOfMethod("ij.Menus",
				"static java.awt.Menu addSubMenu(java.awt.Menu menu, java.lang.String name)",
				"_currentMenuPath += \">\" + $2.replace('_', ' ');");
		hacker.replaceCallInMethod("ij.Menus",
				"void addPluginsMenu()",
				"ij.Menus",
				"addPluginItem",
				"_currentMenuPath = \"Plugins\";" +
				"$_ = $proceed($$);");
		hacker.replaceCallInMethod("ij.Menus",
				"void addPluginsMenu()",
				"ij.Menus",
				"addSubMenu",
				"_currentMenuPath = \"Plugins\";" +
				"$_ = $proceed($$);");
		hacker.replaceCallInMethod("ij.Menus",
				"java.lang.String addMenuBar()",
				"ij.Menus",
				"addPlugInItem",
				"if (\"Quit\".equals($2) || \"Open...\".equals($2) || \"Close\".equals($2) || \"Revert\".equals($2))" +
				"  _currentMenuPath = \"File\";" +
				"else if(\"Show Info...\".equals($2) || \"Crop\".equals($2))" +
				"  _currentMenuPath = \"Image\";" +
				"else if (\"Image Calculator...\".equals($2))" +
				"  _currentMenuPath = \"Process\";" +
				"else if (\"About ImageJ...\".equals($2))" +
				"  _currentMenuPath = \"Help\";" +
				"$_ = $proceed($$);");
		// Wow. There are so many different ways ImageJ 1.x adds menu entries. See e.g. "Repeat Command".
		hacker.replaceCallInMethod("ij.Menus",
				"java.lang.String addMenuBar()",
				"ij.Menus",
				"addItem",
				"ij.IJ._hooks.addMenuItem(_currentMenuPath + \">\" + $2, null);" +
				"$_ = $proceed($$);");
		hacker.insertPrivateStaticField("ij.Menus",
				HashSet.class, "_separators");
		hacker.insertAtTopOfMethod("ij.Menus",
				"void installJarPlugin(java.lang.String jar, java.lang.String s)",
				"  if (_separators == null) _separators = new java.util.HashSet();" +
				"_currentMenuPath = \"Plugins\";");
		hacker.replaceCallInMethod("ij.Menus",
				"void installJarPlugin(java.lang.String jar, java.lang.String s)",
				"java.lang.String", "substring",
				"$_ = $proceed($$);" +
				"ij.IJ._hooks.addMenuItem(_currentMenuPath, $_);",
				4);
		hacker.insertAtTopOfMethod("ij.Menus",
				"void addPlugInItem(java.awt.Menu menu, java.lang.String label, java.lang.String className, int shortcut, boolean shift)",
				"ij.IJ._hooks.addMenuItem(_currentMenuPath + \">\" + $2, $3);");
		hacker.insertAtTopOfMethod("ij.Menus",
				"static void addPluginItem(java.awt.Menu submenu, java.lang.String s)",
				"int comma = $2.lastIndexOf(',');" +
				"if (comma > 0) {" +
				"  java.lang.String label = $2.substring(1, comma - 1);" +
				"  if (label.endsWith(\"]\")) {" +
				"    int open = label.indexOf(\"[\");" +
				"    if (open > 0 && label.substring(open + 1, comma - 3).matches(\"[A-Za-z0-9]\"))" +
				"      label = label.substring(0, open);" +
				"  }" +
				"  while (comma + 2 < $2.length() && $2.charAt(comma + 1) == ' ')" +
				"    comma++;" +
				"  if (mbar == null && _separators != null) {" +
				"    if (!_separators.contains(_currentMenuPath)) {" +
				"      _separators.add(_currentMenuPath);" +
				"      ij.IJ._hooks.addMenuItem(_currentMenuPath + \">-\", null);" +
				"    }" +
				"  }" +
				"  ij.IJ._hooks.addMenuItem(_currentMenuPath + \">\" + label," +
				"    $2.substring(comma + 1));" +
				"}");
		hacker.insertAtTopOfMethod("ij.Menus",
				"java.awt.CheckboxMenuItem addCheckboxItem(java.awt.Menu menu, java.lang.String label, java.lang.String className)",
				"ij.IJ._hooks.addMenuItem(_currentMenuPath + \">\" + $2, $3);");
		// handle separators (we cannot simply look for java.awt.Menu#addSeparator
		// because we might be running in headless mode)
		hacker.replaceCallInMethod("ij.Menus",
				"java.lang.String addMenuBar()",
				"ij.Menus",
				"addPlugInItem",
				"if (\"Cache Sample Images \".equals($2))" +
				"  ij.IJ._hooks.addMenuItem(\"File>Open Samples>-\", null);" +
				"else if (\"Close\".equals($2) || \"Page Setup...\".equals($2) || \"Cut\".equals($2) ||" +
				"    \"Clear\".equals($2) || \"Crop\".equals($2) || \"Set Scale...\".equals($2) ||" +
				"    \"Dev. Resources...\".equals($2) || \"Update ImageJ...\".equals($2) ||" +
				"    \"Quit\".equals($2)) {" +
				"  int separator = _currentMenuPath.indexOf('>');" +
				"  if (separator < 0) separator = _currentMenuPath.length();" +
				"  ij.IJ._hooks.addMenuItem(_currentMenuPath.substring(0, separator) + \">-\", null);" +
				"}" +
				"$_ = $proceed($$);" +
				"if (\"Tile\".equals($2))" +
				"  ij.IJ._hooks.addMenuItem(\"Window>-\", null);");
		hacker.replaceCallInMethod("ij.Menus",
				"java.lang.String addMenuBar()",
				"ij.Menus",
				"addCheckboxItem",
				"if (\"RGB Stack\".equals($2))" +
				"  ij.IJ._hooks.addMenuItem(_currentMenuPath + \">-\", null);" +
				"$_ = $proceed($$);");
		hacker.replaceCallInMethod("ij.Menus",
				"java.lang.String addMenuBar()",
				"ij.Menus",
				"getMenu",
				"if (\"Edit>Selection\".equals($1) || \"Image>Adjust\".equals($1) || \"Image>Lookup Tables\".equals($1) ||" +
				"    \"Process>Batch\".equals($1) || \"Help>About Plugins\".equals($1)) {" +
				"  int separator = $1.indexOf('>');" +
				"  ij.IJ._hooks.addMenuItem($1.substring(0, separator) + \">-\", null);" +
				"}" +
				"$_ = $proceed($$);");
		hacker.replaceCallInMethod("ij.Menus",
				"static java.awt.Menu addSubMenu(java.awt.Menu menu, java.lang.String name)",
				"java.lang.String",
				"equals",
				"$_ = $proceed($$);" +
				"if ($_ && \"-\".equals($1))" +
				"  ij.IJ._hooks.addMenuItem(_currentMenuPath + \">-\", null);");
		hacker.replaceCallInMethod("ij.Menus",
				"static void addLuts(java.awt.Menu submenu)",
				"ij.IJ",
				"isLinux",
				"ij.IJ._hooks.addMenuItem(_currentMenuPath + \">-\", null);" +
				"$_ = $proceed($$);");
		hacker.replaceCallInMethod("ij.Menus",
				"void addPluginsMenu()",
				"ij.Prefs",
				"getString",
				"$_ = $proceed($$);" +
				"if ($_ != null && $_.startsWith(\"-\"))" +
				"  ij.IJ._hooks.addMenuItem(\"Plugins>-\", null);");
		hacker.insertAtTopOfMethod("ij.Menus",
				"static void addSeparator(java.awt.Menu menu)",
				"ij.IJ._hooks.addMenuItem(_currentMenuPath + \">-\", null);");
		hacker.replaceCallInMethod("ij.Menus", "void installPlugins()",
				"ij.Prefs", "getString",
				"$_ = $proceed($$);" +
				"if ($_ != null && $_.length() > 0) {" +
				"  String className = $_.substring($_.lastIndexOf(',') + 1);" +
				"  if (!className.startsWith(\"ij.\")) _currentMenuPath = null;" +
				"  else {" +
				"    char c = $_.charAt(0);" +
				"    if (c == IMPORT_MENU) _currentMenuPath = \"File>Import\";" +
				"    else if (c == SAVE_AS_MENU) _currentMenuPath = \"File>Save As\";" +
				"    else if (c == SHORTCUTS_MENU) _currentMenuPath = \"Plugins>Shortcuts\";" +
				"    else if (c == ABOUT_MENU) _currentMenuPath = \"Help>About Plugins\";" +
				"    else if (c == FILTERS_MENU) _currentMenuPath = \"Process>Filters\";" +
				"    else if (c == TOOLS_MENU) _currentMenuPath = \"Analyze>Tools\";" +
				"    else if (c == UTILITIES_MENU) _currentMenuPath = \"Plugins>Utilities\";" +
				"    else _currentMenuPath = \"Plugins\";" +
				"  }" +
				"}");
		if (headless) {
			hacker.replaceCallInMethod("ij.Menus", "void installPlugins()",
					"java.lang.String", "substring",
					"$_ = $proceed($$);" +
					"if (_currentMenuPath != null) addPluginItem((java.awt.Menu) null, $_);", 2);
		}
	}

	private static void installOpenInterceptor(CodeHacker hacker) {
		// Intercept ij.IJ open methods
		// If the open method is intercepted, the hooks.interceptOpen method needs
		// to perform any necessary display operations
		hacker.insertAtTopOfMethod("ij.IJ",
			"public static void open(java.lang.String path)",
			"Object result = ij.IJ._hooks.interceptOpen($1, -1, true);" +
			"if (result != null) return;");
		// If openImage is intercepted, we return the opened ImagePlus without displaying it
		hacker.insertAtTopOfMethod("ij.IJ",
				"public static ij.ImagePlus openImage(java.lang.String path)",
				"Object result = ij.IJ._hooks.interceptOpen($1, -1, false);" +
				"if (result != null) return (ij.ImagePlus) result;");
		hacker.insertAtTopOfMethod("ij.IJ",
				"public static ij.ImagePlus openImage(java.lang.String path, int sliceIndex)",
				"Object result = ij.IJ._hooks.interceptOpen($1, $2, false);" +
				"if (result != null) return (ij.ImagePlus) result;");
	}

}
