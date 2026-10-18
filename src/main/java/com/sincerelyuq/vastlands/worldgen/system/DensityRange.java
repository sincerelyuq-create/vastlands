package com.sincerelyuq.vastlands.worldgen.system;

/** Closed interval of density values. */
public record DensityRange(double min, double max) {
    public DensityRange {
        if (!(min <= max)) {
            throw new IllegalArgumentException("Invalid density range [" + min + ", " + max + "]");
        }
    }
}
