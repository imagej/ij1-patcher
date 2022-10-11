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

package imagej.patcher;


/**
 * @deprecated Use {@link net.imagej.patcher.LegacyHeadless} instead.
 */
@Deprecated
class LegacyHeadless  {

	private final CodeHacker hacker;

	public LegacyHeadless(final CodeHacker hacker) {
		this.hacker = hacker;
	}

	public void patch() {
		if (hacker.hasSuperclass("ij.gui.GenericDialog", HeadlessGenericDialog.class.getName())) {
			// if we already applied the headless patches, let's not do it again
			return;
		}
		hacker.commitClass(HeadlessGenericDialog.class);
		hacker.replaceWithStubMethods("ij.gui.GenericDialog", "paint", "getInsets", "showHelp");
		hacker.replaceSuperclass("ij.gui.GenericDialog", HeadlessGenericDialog.class.getName());
		hacker.skipAWTInstantiations("ij.gui.GenericDialog");

		hacker.insertAtTopOfMethod("ij.Menus", "void installJarPlugin(java.lang.String jarName, java.lang.String pluginsConfigLine)",
			"int quote = $2.indexOf('\"');"
			+ "if (quote >= 0)"
			+ "  addPluginItem(null, $2.substring(quote));");
		hacker.skipAWTInstantiations("ij.Menus");

		hacker.skipAWTInstantiations("ij.plugin.HyperStackConverter");

		hacker.skipAWTInstantiations("ij.plugin.Duplicator");

		hacker.insertAtTopOfMethod("ij.plugin.filter.ScaleDialog",
			"java.awt.Panel makeButtonPanel(ij.plugin.filter.SetScaleDialog gd)",
			"return null;");
	}

}
