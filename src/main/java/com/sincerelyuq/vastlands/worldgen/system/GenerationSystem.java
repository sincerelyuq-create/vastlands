package com.sincerelyuq.vastlands.worldgen.system;

/**
 * Common contract of the four Vastlands generation systems.
 *
 * <p>One instance of each system exists per world (per {@code RandomState}), created by
 * {@link GenerationSystems#create}. Instances are shared by all worldgen threads, so they must be
 * thread-safe, and their results must depend only on the world seed and the queried position,
 * never on the order in which chunks are generated.
 */
public interface GenerationSystem {
    /** Short identifier shown in logs and on the F3 screen, e.g. {@code "passthrough"}. */
    String id();
}
