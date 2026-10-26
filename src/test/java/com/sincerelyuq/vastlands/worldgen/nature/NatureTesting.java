package com.sincerelyuq.vastlands.worldgen.nature;

/**
 * Shapes of formations in a synthetic world, for galleries and shape tests: flat ground with its
 * top at Y -1 (water over a bed at -3 beyond x = 2 for the kinds of the shore, everywhere for
 * islets), or a box cave open from Y 0 to {@code span - 1}.
 */
public final class NatureTesting {
    private NatureTesting() {
    }

    public static FormationShapes.World world(boolean surface, boolean shore, int span, boolean islet) {
        return new FormationShapes.World() {
            @Override
            public int groundTop(int x, int z) {
                return surface && (islet || shore && x >= 3) ? -3 : -1;
            }

            @Override
            public int waterTop(int x, int z) {
                return surface && (islet || shore && x >= 3) ? -1 : Integer.MIN_VALUE;
            }

            @Override
            public boolean isCave(int x, int y, int z) {
                return !surface && y >= 0 && y < span;
            }
        };
    }

    public static FormationShape shape(FormationSpec spec, boolean surface, boolean shore, int span) {
        return FormationShapes.shape(spec, world(surface, shore, span, spec.kind() == FormationKind.ISLET));
    }
}
