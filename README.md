# Welcome to the ImageJ 1.x patcher

## What is it?

The `ij1-patcher` injects extension points into ImageJ 1.x, i.e. code that
allows code outside of ImageJ 1.x to override some functions. For example, it is
possible to open a sophisticated, syntax-highlighting editor instead of the
default editor of ImageJ 1.x.

The patches optionally include (limited) support for so-called "headless" mode,
i.e. running ImageJ 1.x without a graphical user environment. See below for
details.

It also offers a convenient way to encapsulate multiple ImageJ 1.x "instances"
from each other (ImageJ 1.x relies heavily on static settings;
`IJ.getInstance()` is supposed to return *the* only ImageJ 1.x instance): a
`LegacyEnvironment` contains a completely insulated class loader that does not
interfere with ImageJ 1.x instances living in other class loaders. Example:

```java
	// The first parameter is a class loader, asking for a new,
	// special-purpose class loader to be created; the second parameter
	// asks for headless mode.
	LegacyEnvironment ij1 = new LegacyEnvironment(null, true);
	ij1.runMacro("print('Hello, world!');");
```

## How can I use it?

Add the following section to your `pom.xml` file (if you do not use
[Maven](https://maven.apache.org/) yet, [you should](http://fiji.sc/Maven)):

```xml
	<dependencies>
		...
		<dependency>
			<groupId>net.imagej</groupId>
			<artifactId>ij1-patcher</artifactId>
			<version>0.1.0</version>
		</dependency>
	</dependencies>
```

Then, create a `LegacyEnvironment`:

```java
	// The first parameter is a class loader, asking for a new,
	// special-purpose class loader to be created; the second parameter
	// asks for headless mode.
	LegacyEnvironment ij1 = new LegacyEnvironment(null, true);
	ij1.runMacro("open('" + path + "');");
	[... process the image ...]
	ij1.runMacro("saveAs('jpeg', '" + outputPath + "');");
```

Note: it is not currently possible to inject an `ImagePlus` instance into the
legacy environment directly: There will be two different definitions of the
`ImagePlus` class involved -- the one from the calling class loader and the one
in the encapsulated class loader.  It is impossible to assign an instance of one
to a variable of the other.

## How does it work?

The runtime patching works by calling [Javassist](http://www.javassist.org), a
library offering tools to manipulate Java bytecode. This is needed to get the
changes into ImageJ 1.x code.

To offer encapsulated ImageJ 1.x, a new special-purpose class loader is created
that contains only the patched ImageJ 1.x classes, plus a select few classes
required for callbacks. The central class here is `LegacyHooks`, an abstract
base class which gets called by the patched ImageJ 1.x classes at appropriate
times, e.g. when an exception needs to be displayed.

For details about the headless mode, see the section below.

## Where does it come from?

[ImageJ 1.x](http://imagej.net/) is a very successful project that -- as any
major project -- benefits from some refactoring from time to time. A first
attempt at improving ImageJ 1.x was made in the
[`ImageJA`](https://github.com/imagej/ImageJA) project (which now serves as a
Mavenized version of ImageJ 1.x, automatically tracking ImageJ 1.x' releases).

[Fiji](http://fiji.sc/), a related project aiming to provide a distribution of
ImageJ, tapped into the ImageJA project to provide extension points not offered
by ImageJ 1.x, and later also to provide the headless mode (see below). Over
time, maintaining the ImageJA fork became very burdensome.

With [ImageJ2](http://developer.imagej.net/), a major effort was started to
provide a new software architecture.  The benefits to the Fiji project were
immediately obvious and over the course of time, Fiji first imitated ImageJ2's
approach by replacing the ImageJA-specific ImageJ 1.x patches with runtime
patches (see above). Later, the Fiji-specific patches moved from Fiji's
`fiji-compat` component into ImageJ's `ij-legacy` component.

In the last step, the runtime patching code in ImageJ was separated out from the
legacy service code, giving rise to this here `ij1-patcher` project.

## What is this "headless" mode about?

Traditionally, ImageJ 1.x was only ever intended to be a single application for
a single user on one single machine with one single task at each given moment.
That implementation detail shows through the way macros are recorded: in order
to make a plugin recordable, one has to instantiate a `GenericDialog` and show
it to the user. When the same plugin is played back from a macro, the dialog's
values are populated from the macro options and the dialog is not displayed.

The problem is when the dialog cannot be instantiated because Java is running
without a user interface. Which is quite common in today's clouds.

Originally devised in ImageJA (see above), `ij1-patcher`'s headless mode
provides *limited* support to run in a headless environment. It does so by
replacing `GenericDialog`'s superclass with a fake dialog class that does not
require a graphical user environment. This works with well-behaved plugins that
use the `GenericDialog` class in the intended way, but it breaks down when the
plugin tries to display GUI elements or assumes that the dialog itself is a
subclass of `java.awt.Dialog`.

Summary: the headless mode works for most plugins but fails for plugins assuming
that a graphical user environment is readily available.

Please see the [Fiji Wiki](http://fiji.sc/Headless) for more information.
