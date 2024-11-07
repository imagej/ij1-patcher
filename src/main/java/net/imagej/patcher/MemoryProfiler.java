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

package net.imagej.patcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Loader;
import javassist.NotFoundException;
import javassist.Translator;

/**
 * A very simple memory profiler.
 * <p>
 * This memory profiler instruments all method entries and exists using
 * javassist. At each exit, it reports the relative memory usage, the total
 * memory usage, and the exit point of the current method. Call it like this:
 * </p>
 * 
 * <pre>
 * ./fiji --main-class fiji.MemoryProfiler fiji.Main
 * </pre>
 * <p>
 * Since the memory profiling slows down execution dramatically due to the
 * synchronous output to stderr, you may want to limit the classes to be
 * instrumented by setting the environment variable MEMORY_PROFILE_ONLY to a
 * space-delimited list of classes.
 * </p>
 * <p>
 * If you want to instrument any class handled by IJPatcher, you need to use the
 * slightly more complicated command line:
 * </p>
 * 
 * <pre>
 * ./fiji -Dpatch.ij1=false \
 *        --cp jars/javassist.jar --cp jars/ij1-patcher.jar \
 *        --cp jars/ij.jar --main-class net.imagej.patcher.MemoryProfiler \
 *        -- ij.ImageJ
 * </pre>
 * <p>
 * I wrote this class to profile the sudden resource hunger of Fiji Build and
 * figured that this class might come in handy in the future, or for others, or
 * both.
 * </p>
 *
 * @author Johannes Schindelin
 */
public class MemoryProfiler implements Translator {
	protected static final boolean debug = false;
	protected Set<String> only;

	public MemoryProfiler() {
		this(System.getenv("MEMORY_PROFILE_ONLY"));
	}

	public MemoryProfiler(String only) {
		this(only == null ? null : Arrays.asList(only.split(" +")));
	}

	public MemoryProfiler(Collection<String> only) {
		if (only != null) {
			this.only = new HashSet<String>();
			this.only.addAll(only);
		}
	}

	public void start(ClassPool pool) throws NotFoundException, CannotCompileException {
	}

	public void onLoad(ClassPool pool, String classname) throws NotFoundException {
		// do not instrument yourself
		if (classname.equals(getClass().getName()))
			return;

		if (only != null && !only.contains(classname))
			return;

		if (debug)
			System.err.println("instrumenting " + classname);

		CtClass cc = pool.get(classname);
		if (cc.isFrozen())
			return;

		try {
			// instrument all methods and constructors
			for (CtMethod method : cc.getMethods())
				handle(method);
			for (CtConstructor constructor : cc.getConstructors())
				handle(constructor);
		}
		catch (RuntimeException e) {
			if (!e.getMessage().endsWith(" class is frozen"))
				e.printStackTrace();
		}
	}

	protected void handle(CtBehavior behavior) {
		try {
			if (debug)
				System.err.println("instrumenting " + behavior.getClass().getName() + "." + behavior.getName());
			if (behavior.isEmpty())
				return;
			behavior.addLocalVariable("memoryBefore", CtClass.longType);
			behavior.insertBefore("memoryBefore = fiji.MemoryProfiler.get();");
			behavior.insertAfter("fiji.MemoryProfiler.report(memoryBefore);");
		}
		catch (CannotCompileException e) {
			if (!e.getMessage().equals("no method body"))
				e.printStackTrace();
		}
	}

	protected static Runtime runtime = Runtime.getRuntime();

	public static long get() {
		gc();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	public static void report(long memoryBefore) {
		gc();
		StackTraceElement[] trace = new Exception().getStackTrace();
		StackTraceElement last = trace.length > 1 ? trace[1] : new StackTraceElement("null", "null", "null", -1);
		long current = get();
		System.err.println("MemoryProfiler: " + (current - memoryBefore) + " " + current + " " + last.getClassName() + "." + last.getMethodName() + "(" + last.getFileName() + ":" + last.getLineNumber() + ")");
	}

	public static void gc() {
		System.gc();
		System.gc();
	}

	public static void main(String[] args) throws Throwable {
		Thread.currentThread().setContextClassLoader(MemoryProfiler.class.getClassLoader());

		if (args.length == 0) {
			System.err.println("Usage: java " + MemoryProfiler.class + " <main-class> [<argument>...]");
			System.exit(1);
		}

		String mainClass = args[0];
		String[] mainArgs = new String[args.length - 1];
		System.arraycopy(args, 1, mainArgs, 0, mainArgs.length);

		Loader loader = new Loader();
		loader.addTranslator(ClassPool.getDefault(), new MemoryProfiler());
		gc();
		loader.run(mainClass, mainArgs);
	}
}
