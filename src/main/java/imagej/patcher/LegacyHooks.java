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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension points for ImageJ 1.x.
 * <p>
 * These extension points will be patched into ImageJ 1.x by the
 * {@link CodeHacker}. To override the behavior of ImageJ 1.x, a new instance of
 * this interface needs to be installed into <code>ij.IJ._hooks</code>.
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
	 * Return the current context, if any.
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
	 * Disposes and prepares for quitting.
	 * 
	 * @return whether ImageJ 1.x should be allowed to call System.exit()
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
	 * Updates the progress bar, where 0 <= progress <= 1.0.
	 * 
	 * @param value between 0.0 and 1.0
	 */
	public void showProgress(final double progress) {}

	/**
	 * Updates the progress bar, where the length of the bar is set to (
	 * <code>currentValue + 1) / finalValue</code> of the maximum bar length. The
	 * bar is erased if <code>currentValue &gt;= finalValue</code>.
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
	 * @param imagej the image
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

	/**
	 * Extension point to add to ImageJ 1.x' PluginClassLoader's class path.
	 * 
	 * @return a list of class path elements to add
	 */
	public List<File> handleExtraPluginJars() {
		final List<File> result = new ArrayList<File>();
		final String extraPluginDirs = System.getProperty("ij1.plugin.dirs");
		if (extraPluginDirs != null) {
			for (final String dir : extraPluginDirs.split(File.pathSeparator)) {
				handleExtraPluginJars(new File(dir), result);
			}
			return result;
		}
		final String userHome = System.getProperty("user.home");
		if (userHome != null) handleExtraPluginJars(new File(userHome, ".plugins"),
			result);
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
	 * @param e the exception to handle
	 * @return true if the error was handled by the legacy hook
	 */
	public boolean handleNoSuchMethodError(final NoSuchMethodError e) {
		return false; // not handled
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
	 * First extension point to run just after ImageJ 1.x spun up.
	 */
	public void initialized() {
		// do nothing by default
	}
}
