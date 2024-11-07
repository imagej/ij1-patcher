package net.imagej.patcher;

/*
 * #%L
 * ImageJ2 software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2024 ImageJ2 developers.
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
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;

/**
 * Tests that {@link Plugin}s with {@link DialogListener}s work correctly in
 * headless mode.
 */
public class Plugin_With_DialogListener implements PlugIn {
	private final StringBuilder builder = new StringBuilder();

	/**
	 * Performs the plugin's functionality.
	 *
	 * @param arg
	 *            ignored
	 */
	@Override
	public void run(final String arg) {
		final GenericDialog gd = new GenericDialog("Let's test this");
		gd.addStringField("Please enter some text", "<change this>");

		/**
		 * Listens for changes in the dialog values.
		 * <p>
		 * Note: any dialog value that is *not* enquired in this listener will
		 * *not* be recorded, either. This is most likely a bug introduced into
		 * <a href=
		 * "https://github.com/imagej/ImageJA/commit/a534b8f#diff-3cda9bab45abece23447b8321c867f67R841"
		 * >version 1.38u of June 15th 2007</a>: The intention was probably to
		 * set <code>recorderOn = false;</code> <it>before</it> running the
		 * dialog listener, and setting it to <code>true</code> afterwards.
		 * Being in effect for almost 8 years as of time of writing, it is
		 * likely that users now rely on that behavior, though, so it should not
		 * be changed now.
		 */
		final DialogListener listener = new DialogListener() {
			@Override
			public boolean dialogItemChanged(final GenericDialog gd,
					final AWTEvent e) {
				builder.append("value: ").append(gd.getNextString())
						.append("\nevent: ").append(e).append("\n");
				return true;
			}
		};
		gd.addDialogListener(listener);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		builder.append("final value: ").append(gd.getNextString()).append("\n");
	}

	@Override
	public String toString() {
		return builder.toString();
	}
}
