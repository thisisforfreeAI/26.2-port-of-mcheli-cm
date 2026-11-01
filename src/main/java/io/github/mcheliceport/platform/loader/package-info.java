/**
 * Split classes: each class here exists once per loader with the same name and public API. The
 * NeoForge versions live in {@code src/main}; the Fabric build leaves this folder out and compiles
 * its own versions from {@code fabric/src/main}. Use this only where a seam method cannot work,
 * such as a superclass that differs per loader.
 */
@NullMarked
package io.github.mcheliceport.platform.loader;

import org.jspecify.annotations.NullMarked;
