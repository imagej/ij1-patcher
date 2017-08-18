/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
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

import ij.gui.ImageWindow;

import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Extension points for ImageJ 1.x.
 * <p>
 * These extension points will be patched into ImageJ 1.x by the
 * {@link CodeHacker}. To override the behavior of ImageJ 1.x, a new instance of
 * this class needs to be installed into <code>ij.IJ._hooks</code>.
 * </p>
 * <p>
 * The essential functionality of the hooks is provided in the
 * {@link EssentialLegacyHooks} class, which makes an excellent base class for
 * project-specific implementations.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public abstract class LegacyHooks {

	/**
	 * Determines whether the image windows should be displayed or not.
	 * 
	 * @return false if ImageJ 1.x should be prevented from opening image windows.
	 */
	public boolean isLegacyMode() {
		return true;
	}

	/**
	 * Returns the current context, if any.
	 * <p>
	 * For ImageJ2-specific hooks, the returned object will be the current SciJava
	 * context, or null if the context is not yet initialized.
	 * </p>
	 * 
	 * @return the context, or null
	 */
	public Object getContext() {
		return null;
	}

	/**
	 * Allows interception of ImageJ 1.x's {@link ij.ImageJ#quit()} method.
	 * 
	 * @return whether ImageJ 1.x should proceed with its usual quitting routine
	 */
	public boolean quit() {
		return true;
	}

	/**
	 * Runs when the hooks are installed into an existing legacy environment.
	 */
	public void installed() {
		// ignore
	}

	/**
	 * Disposes of the hooks.
	 * <p>
	 * This method is called when ImageJ 1.x is quitting or when new hooks are
	 * installed.
	 * </p>
	 */
	public void dispose() {
		// ignore
	}

	/**
	 * Intercepts the call to {@link ij.IJ#runPlugIn(String, String)}.
	 * 
	 * @param className the class name
	 * @param arg the argument passed to the {@code runPlugIn} method
	 * @return the object to return, or null to let ImageJ 1.x handle the call
	 */
	public Object interceptRunPlugIn(final String className, final String arg) {
		return null;
	}

	/**
	 * Updates the progress bar, where {@code 0 <= progress <= 1.0}.
	 * 
	 * @param progress between 0.0 and 1.0
	 */
	public void showProgress(final double progress) {}

	/**
	 * Updates the progress bar, where the length of the bar is set to
	 * {@code (currentValue + 1) / finalValue} of the maximum bar length. The bar
	 * is erased if {@code currentValue >= finalValue}.
	 * 
	 * @param currentIndex the step that was just started
	 * @param finalIndex the final step.
	 */
	public void showProgress(final int currentIndex, final int finalIndex) {}

	/**
	 * Shows a status message.
	 * 
	 * @param status the message
	 */
	public void showStatus(final String status) {}

	/**
	 * Logs a message.
	 * 
	 * @param message the message
	 */
	public void log(final String message) {}

	/**
	 * Registers an image (possibly not seen before).
	 * 
	 * @param image the new image
	 */
	public void registerImage(final Object image) {}

	/**
	 * Releases an image.
	 * 
	 * @param image the image
	 */
	public void unregisterImage(final Object image) {}

	/**
	 * Logs a debug message (to be shown only in debug mode).
	 * 
	 * @param string the debug message
	 */
	public void debug(final String string) {
		System.err.println(string);
	}

	/**
	 * Shows an exception.
	 * 
	 * @param t the exception
	 */
	public void error(final Throwable t) {
		// ignore
	}

	/**
	 * Returns the name to use in place of "ImageJ".
	 * 
	 * @return the application name
	 */
	public String getAppName() {
		return "ImageJ";
	}

	/**
	 * Returns the version to use in place of the legacy version.
	 * 
	 * @return the application version, or null if we do not override
	 */
	public String getAppVersion() {
		return null;
	}

	/**
	 * Returns the icon to use in place of the ImageJ microscope.
	 * 
	 * @return the URL to the icon to use, or null
	 */
	public URL getIconURL() {
		return null;
	}

	/**
	 * Extension point to override ImageJ 1.x' editor.
	 * 
	 * @param path the path to the file to open
	 * @return true if the hook opened a different editor
	 */
	public boolean openInEditor(final String path) {
		return false;
	}

	/**
	 * Extension point to override ImageJ 1.x' editor.
	 * 
	 * @param fileName the name of the new file
	 * @param content the initial content
	 * @return true if the hook opened a different editor
	 */
	public boolean createInEditor(final String fileName, final String content) {
		return false;
	}

	private boolean enableIJ1PluginDirs = true;
	protected void enableIJ1PluginDirs(final boolean enable) {
		enableIJ1PluginDirs = enable;
	}

	final private Collection<File> pluginClasspath = new LinkedHashSet<File>();
	protected void addPluginClasspath(final File file) {
		pluginClasspath.add(file);
	}

	/**
	 * Extension point to add to ImageJ 1.x' PluginClassLoader's class path.
	 * 
	 * @return a list of class path elements to add
	 */
	public List<File> handleExtraPluginJars() {
		final List<File> result = new ArrayList<File>();
		result.addAll(pluginClasspath);
		if (!enableIJ1PluginDirs) return result;
		final String extraPluginDirs = System.getProperty("ij1.plugin.dirs");
		if (extraPluginDirs != null) {
			for (final String dir : extraPluginDirs.split(File.pathSeparator)) {
				final File directory = new File(dir);
				if (directory.isDirectory()) {
					result.add(directory);
					handleExtraPluginJars(directory, result);
				}
			}
			return result;
		}
		final String userHome = System.getProperty("user.home");
		if (userHome != null) {
			final File dir = new File(userHome, ".plugins");
			if (dir.isDirectory()) {
				result.add(dir);
				handleExtraPluginJars(dir, result);
			}
		}
		return result;
	}

	private void handleExtraPluginJars(final File directory,
		final List<File> result)
	{
		final File[] list = directory.listFiles();
		if (list == null) return;
		for (final File file : list) {
			if (file.isDirectory()) handleExtraPluginJars(file, result);
			else if (file.isFile() && file.getName().endsWith(".jar")) {
				result.add(file);
			}
		}
	}

	/**
	 * Extension point to run after <i>Help&gt;Refresh Menus</i>
	 */
	public void runAfterRefreshMenus() {
		// ignore
	}

	/**
	 * Extension point to enhance ImageJ 1.x' error reporting upon
	 * {@link NoSuchMethodError}.
	 * 
	 * @param error the exception to handle
	 * @return true if the error was handled by the legacy hook
	 */
	public boolean handleNoSuchMethodError(final NoSuchMethodError error) {
		String message = error.getMessage();
		int paren = message.indexOf("(");
		if (paren < 0) return false;
		int dot = message.lastIndexOf(".", paren);
		if (dot < 0) return false;
		String path = message.substring(0, dot).replace('.', '/') + ".class";
		Set<String> urls = new LinkedHashSet<String>();
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try {
			Enumeration<URL> e = loader.getResources(path);
			while (e.hasMoreElements()) {
				urls.add(e.nextElement().toString());
			}
			e = loader.getResources("/" + path);
			while (e.hasMoreElements()) {
				urls.add(e.nextElement().toString());
			}
		} catch (Throwable t) {
			t.printStackTrace();
			return false;
		}

		if (urls.size() == 0) return false;
		StringBuilder buffer = new StringBuilder();
		buffer.append("There was a problem with the class ");
		buffer.append(message.substring(0, dot));
		buffer.append(" which can be found here:\n");
		for (String url : urls) {
			if (url.startsWith("jar:")) url = url.substring(4);
			if (url.startsWith("file:")) url = url.substring(5);
			int bang = url.indexOf("!");
			if (bang < 0) buffer.append(url);
			else buffer.append(url.substring(0, bang));
			buffer.append("\n");
		}
		if (urls.size() > 1) {
			buffer.append("\nWARNING: multiple locations found!\n");
		}

		StringWriter writer = new StringWriter();
		error.printStackTrace(new PrintWriter(writer));
		buffer.append(writer.toString());

		System.out.println(buffer.toString());
		final NoSuchMethodException throwable =
			new NoSuchMethodException("Could not find method " + message +
				"\n" + buffer);
		throwable.setStackTrace(error.getStackTrace());
		error(throwable);
		return true;
	}

	/**
	 * Extension point to run after a new PluginClassLoader was initialized.
	 * 
	 * @param loader the PluginClassLoader instance
	 */
	public void newPluginClassLoader(final ClassLoader loader) {
		// do nothing
	}

	/**
	 * Extension point to modify the order in which .jar files are added to the
	 * PluginClassLoader.
	 * <p>
	 * There is a problem which only strikes large distributions of ImageJ such as
	 * Fiji: some .jar files try to be helpful and bundle classes which are
	 * actually not theirs, causing problems when newer versions of those .jar
	 * files which they shadow are present in the <i>plugins/</i> or <i>jars/</i>
	 * directory but are not respected by the class loader.
	 * </p>
	 * <p>
	 * The default hook of this extension point therefore hard-codes a few file
	 * names of known offenders (which we politely will call fat .jar files
	 * normally) and just pushes them back to the end of the list.
	 * </p>
	 * 
	 * @param directory the directory which ImageJ 1.x looked at
	 * @param names the list of file names in the order ImageJ 1.x discovered them
	 * @return the ordered, filtered and/or augmented list
	 */
	public String[]
		addPluginDirectory(final File directory, final String[] names)
	{
		if (names != null) {
			/*
			Note that this code is replicated in imagej-launcher's ClassLoaderPlus
			class. Improvements to this Pattern string should also be mirrored there.
			*/
			final Pattern pattern =
				Pattern.compile("(batik|jython|jython-standalone|jruby)(-[0-9].*)?\\.jar");
			Arrays.sort(names, new FatJarNameComparator(pattern));
		}
		return names;
	}

	/**
	 * Comparator to ensure that problematic fat JARs are sorted <em>last</em>.
	 * It is intended to be used with a {@link Pattern} that filters things this
	 * way.
	 */
	public final static class FatJarNameComparator implements Comparator<String> {

		private final Pattern pattern;

		private FatJarNameComparator(Pattern pattern) {
			this.pattern = pattern;
		}

		@Override
		public int compare(final String a, final String b) {
			return (pattern.matcher(a).matches() ? 1 : 0) -
				(pattern.matcher(b).matches() ? 1 : 0);
		}
	}

	/**
	 * First extension point to run just after ImageJ 1.x spun up.
	 */
	public void initialized() {
		// do nothing by default
	}

	public InputStream autoGenerateConfigFile(final File directory) {
		// skip unpacked ImageJ 1.x
		if (new File(directory, "IJ_Props.txt").exists()) return null;
		return new ByteArrayInputStream(autoGenerateConfigFile(directory,
			directory, "Plugins", "", new StringBuilder()).toString().getBytes());
	}

	protected StringBuilder autoGenerateConfigFile(final File topLevelDirectory,
		final File directory, final String menuPath, final String packageName,
		final StringBuilder builder)
	{
		final File[] list = directory.listFiles();
		if (list == null) return builder;
		// make order consistent
		Arrays.sort(list);
		for (final File file : list) {
			String name = file.getName();
			if (name.startsWith("_")) continue;
			if (file.isDirectory()) {
				autoGenerateConfigFile(topLevelDirectory, file, menuPath + ">" +
					name.replace('_', ' '), packageName + name + ".", builder);
			}
			else if (name.endsWith(".class") && name.contains("_") &&
				!name.contains("$"))
			{
				if (topLevelDirectory == directory &&
					Character.isLowerCase(name.charAt(0))) continue;
				final String className = packageName + name.substring(0, name.length() - 6);
				name = name.substring(0, name.length() - 6).replace('_', ' ');
				builder.append(menuPath + ", \"" + name + "\", " + className + "\n");
			}
		}
		return builder;
	}

	private Map<String, String> menuStructure = new LinkedHashMap<String, String>();

	/**
	 * Callback for ImageJ 1.x' menu parsing machinery.
	 * <p>
	 * This method is called whenever ImageJ 1.x adds a command to the menu structure.
	 * </p>
	 * 
	 * @param menuPath the menu path of the menu item, or null when reinitializing
	 * @param command the command associated with the menu item, or null when reinitializing
	 */
	public void addMenuItem(final String menuPath, final String command) {
		if (menuPath == null) {
			menuStructure.clear();
		}
		else if (menuPath.endsWith(">-")) {
			int i = 1;
			while (menuStructure.containsKey(menuPath + i)) {
				i++;
			}
			menuStructure.put(menuPath + i, command);
		}
		else {
			menuStructure.put(menuPath, command);
		}
	}

	/**
	 * Returns ImageJ 1.x' menu structure as a map.
	 * 
	 * @return the menu structure
	 */
	public Map<String, String> getMenuStructure() {
		return Collections.unmodifiableMap(menuStructure);
	}

	/**
	 * Optionally override opening resources via legacy hooks.
	 * <p>
	 * This is intended as a "HandleExtraFileTypesPlus".
	 * </p>
	 * 
	 * @param path the path to the resource to open, or {@code null} if a dialog
	 *          needs to be shown
	 * @param planeIndex
	 *            If applicable - the index of plane to open or -1 for all planes
	 * @param display
	 *            if true, the opened object should be displayed before returning
	 * @return The opened object, or {@code null} to let ImageJ 1.x open the path.
	 * @deprecated this will be removed before ij1-patcher 1.0.0
	 */
	@Deprecated
	public Object interceptOpen(final String path, final int planeIndex,
		final boolean display) {
		return null;
	}

	/**
	 * Optionally override opening resources via legacy hooks.
	 * <p>
	 * This is intended as a "HandleExtraFileTypesPlus".
	 * </p>
	 * 
	 * @param path the path to the resource to open, or {@code null} if a dialog
	 *          needs to be shown
	 * @return The opened object, or {@code null} to let ImageJ 1.x open the resource.
	 */
	public Object interceptFileOpen(final String path) {
		return null;
	}

	/**
	 * Optionally override opening images via legacy hooks.
	 * <p>
	 * This is intended as a "HandleExtraFileTypesPlus".
	 * </p>
	 * 
	 * @param path the path to the image to open, or {@code null} if a dialog
	 *          needs to be shown
	 * @param planeIndex
	 *            If applicable - the index of plane to open or -1 for all planes
	 * @return The opened image, or {@code null} to let ImageJ 1.x open the image.
	 */
	public Object interceptOpenImage(final String path, final int planeIndex) {
		return null;
	}

	/**
	 * Optionally override opening recent images via legacy hooks.
	 * 
	 * @param path the path to the recent image to open
	 * @return The opened object, or {@code null} to let ImageJ 1.x open the image.
	 */
	public Object interceptOpenRecent(final String path) {
		return null;
	}

	/**
	 * Optionally override opening drag-and-dropped files via legacy hooks.
	 * 
	 * @param f the file that was dragged onto the IJ UI
	 * @return The opened object, or {@code null} to let ImageJ 1.x open the file
	 *         as normal.
	 */
	public Object interceptDragAndDropFile(final File f) {
		return null;
	}

	/**
	 * Do not use: for internal use only.
	 */
	public static Collection<File> getClasspathElements(
		final ClassLoader fromClassLoader, final StringBuilder errors,
		final ClassLoader... excludeClassLoaders)
	{
		final Set<ClassLoader> exclude =
			new HashSet<ClassLoader>(Arrays.asList(excludeClassLoaders));
		final List<File> result = new ArrayList<File>();
		for (ClassLoader loader = fromClassLoader; loader != null; loader =
				loader.getParent()) {
			if (exclude.contains(loader)) break;

			if (!(loader instanceof URLClassLoader)) {
				errors.append("Cannot add class path from ClassLoader of type ")
				.append(fromClassLoader.getClass().getName()).append("\n");
				continue;
			}

			for (final URL url : ((URLClassLoader) loader).getURLs()) {
				if (!"file".equals(url.getProtocol())) {
					errors.append("Not a file URL! ").append(url).append("\n");
					continue;
				}
				result.add(new File(url.getPath()));
				final String path = url.getPath();
				if (path.matches(".*/target/surefire/surefirebooter[0-9]*\\.jar")) try {
					final JarFile jar = new JarFile(path);
					final Manifest manifest = jar.getManifest();
					if (manifest != null) {
						final String classPath =
							manifest.getMainAttributes().getValue(Name.CLASS_PATH);
						if (classPath != null) {
							for (final String element : classPath.split(" +"))
								try {
									final URL url2 = new URL(element);
									if (!"file".equals(url2.getProtocol())) {
										errors.append("Not a file URL! ").append(url2).append("\n");
										continue;
									}
									result.add(new File(url2.getPath()));
								}
								catch (final MalformedURLException e) {
									e.printStackTrace();
								}
						}
					}
				}
				catch (final IOException e) {
					System.err
						.println("Warning: could not add plugin class path due to ");
					e.printStackTrace();
				}

			}
		}
		return result;
	}

	/**
	 * Intercepts keyboard events sent to ImageJ 1.x.
	 * 
	 * @param e the keyboard event
	 * @return whether the event was intercepted
	 */
	public boolean interceptKeyPressed(final KeyEvent e) {
		return false;
	}

	/**
	 * Iterates through the current thread's ancestors.
	 * <p>
	 * ImageJ 1.x' macro options are thread-local. Unfortunately, this does not
	 * take into account thread relationships e.g. when threads are spawned in
	 * parallel.
	 * </p>
	 * <p>
	 * By overriding this method, legacy hooks can force ImageJ 1.x to look harder
	 * for macro options.
	 * </p>
	 * 
	 * @return the ancestor(s) of the current thread, or null
	 */
	public Iterable<Thread> getThreadAncestors() {
		return null;
	}

	/**
	 * Allows closing additional windows at the end of
	 * {@link ij.WindowManager#closeAllWindows()}.
	 * <p>
	 * When returning {@code false}, ImageJ 1.x will be disallowed from quitting.
	 * </p>
	 * 
	 * @return whether it is okay to quit
	 */
	public boolean interceptCloseAllWindows() {
		return true;
	}

	/**
	 * Hook to ensure {@link ij.gui.ImageWindow}s are fully cleaned up when
	 * they are closed.
	 */
	public void interceptImageWindowClose(final Object window) {
		// nothing to do
	}

	/**
	 * Allows interception of ImageJ 1.x's disposal routine while quitting.
	 * <p>
	 * This method is called after it has been confirmed that quitting should
	 * proceed. That is, the user OKed all the windows being closed, etc.
	 * This method provides one final chance to cancel the quit operation by
	 * returning false; otherwise, it performs any needed disposal and cleanup.
	 * </p>
	 * 
	 * @return whether ImageJ 1.x should proceed in quitting
	 * @see ij.ImageJ#run() which is where ImageJ 1.x actually quits
	 */
	public boolean disposing() {
		return true;
	}
}
