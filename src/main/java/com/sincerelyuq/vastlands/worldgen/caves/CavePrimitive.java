package com.sincerelyuq.vastlands.worldgen.caves;

/**
 * One analytic piece of cave space: a tunnel segment (a capsule with an elliptical cross-section,
 * optionally with a canyon slot cut into its floor, a flat sediment floor or a funnel mouth) or a
 * chamber (an elongated, lobed dome over a flattened floor, with terraced walls). Immutable once its cell is built;
 * shared by the threads that read the cell.
 */
final class CavePrimitive {
    static final int SEGMENT = 0;
    static final int CHAMBER = 1;

    final int type;
    /** Segment: the two end points. Chamber: the centre (a) only. */
    final double ax, ay, az, bx, by, bz;
    /** Segment: radius at a and b. Chamber: horizontal (ra) and vertical (rb) radius. */
    final double ra, rb;
    /**
     * Segment: vertical radius over horizontal radius (below 1: wide low tubes, above 1: tall rifts).
     * Chamber: depth of the floor below the centre, relative to the vertical radius.
     */
    final double shape;
    /** Segment: canyon slot below the tube (depth, half width), 0 for none. */
    final double slotDepth, slotWidth;
    /** Segment: flat floor at this fraction of the vertical radius below the axis (1 = round). */
    final double floorCut;
    /** Segment: the radius widens into a funnel over the 14 blocks below this Y (sinkholes). */
    final double funnelTop;
    /** Entrances: the piece may cut through the cover of rock below the surface. */
    final boolean ignoresCover;
    /** Amplitude of the wall roughness noise, blocks. */
    final double roughness;
    /** Chamber: direction of the long axis (radians) and elongation (0 round .. 0.5). */
    final double angle, elongation;
    final long seed;
    final double minX, maxX, minY, maxY, minZ, maxZ;

    private CavePrimitive(int type, double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, double shape,
                          double slotDepth, double slotWidth, double floorCut, double funnelTop, boolean ignoresCover, double roughness, long seed,
                          double angle, double elongation) {
        this.type = type;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.ra = ra;
        this.rb = rb;
        this.shape = shape;
        this.slotDepth = slotDepth;
        this.slotWidth = slotWidth;
        this.floorCut = floorCut;
        this.funnelTop = funnelTop;
        this.ignoresCover = ignoresCover;
        this.roughness = roughness;
        this.seed = seed;
        this.angle = angle;
        this.elongation = elongation;
        if (type == SEGMENT) {
            double r = Math.max(ra, rb) * (Double.isNaN(funnelTop) ? 1.0 : 3.0) + roughness + 2.0;
            double rv = Math.max(ra, rb) * shape + roughness + 2.0;
            this.minX = Math.min(ax, bx) - r;
            this.maxX = Math.max(ax, bx) + r;
            this.minZ = Math.min(az, bz) - r;
            this.maxZ = Math.max(az, bz) + r;
            this.minY = Math.min(ay, by) - rv - slotDepth;
            this.maxY = Math.max(ay, by) + rv;
        } else {
            double r = ra * (1.0 + elongation) * 1.25 + roughness + 2.0;
            this.minX = ax - r;
            this.maxX = ax + r;
            this.minZ = az - r;
            this.maxZ = az + r;
            this.minY = ay - rb * shape - roughness - 6.0;
            this.maxY = ay + rb + roughness + 2.0;
        }
    }

    static CavePrimitive segment(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, double shape,
                                 double slotDepth, double slotWidth, double floorCut, double roughness, long seed) {
        return new CavePrimitive(SEGMENT, ax, ay, az, bx, by, bz, ra, rb, shape, slotDepth, slotWidth, floorCut, Double.NaN, false, roughness, seed, 0.0, 0.0);
    }

    /** A tunnel that may break through to the surface (adits, sea cave mouths). */
    static CavePrimitive entrance(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, double shape, double roughness, long seed) {
        return new CavePrimitive(SEGMENT, ax, ay, az, bx, by, bz, ra, rb, shape, 0.0, 0.0, 1.0, Double.NaN, true, roughness, seed, 0.0, 0.0);
    }

    /** A vertical shaft widening into a funnel below {@code funnelTop} (sinkholes, dolines). */
    static CavePrimitive funnel(double x, double y0, double z, double y1, double r, double funnelTop, double roughness, long seed) {
        return new CavePrimitive(SEGMENT, x, y0, z, x, y1, z, r, r, 1.0, 0.0, 0.0, 1.0, funnelTop, true, roughness, seed, 0.0, 0.0);
    }

    /**
     * A chamber: an elliptical, lobed outline of mean radius {@code rh} stretched by
     * {@code elongation} along {@code angle}, a dome {@code rv} high over a floor
     * {@code floorDepth × rv} below the centre.
     */
    static CavePrimitive chamber(double x, double y, double z, double rh, double rv, double floorDepth, double angle, double elongation,
                                 double roughness, long seed) {
        return new CavePrimitive(CHAMBER, x, y, z, x, y, z, rh, rv, floorDepth, 0.0, 0.0, 1.0, Double.NaN, false, roughness, seed, angle, elongation);
    }

    boolean covers(double x, double z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }
}
