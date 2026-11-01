package io.github.mcheliceport.platform;

/** One loaded mod, as the loader lists it: id, display name, version and its file's name. */
public record ModDescriptor(String id, String name, String version, String file) {}
