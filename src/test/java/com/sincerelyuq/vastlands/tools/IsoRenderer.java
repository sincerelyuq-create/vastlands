package com.sincerelyuq.vastlands.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * A small isometric voxel renderer for previews: unit cubes seen from the south-east and above,
 * three shaded faces each (top, east, south), faces between two cubes culled, painted back to
 * front. Not the game's renderer: colours are flat averages of vanilla textures.
 */
final class IsoRenderer {
    private long[] keys = new long[1 << 16];
    private int[] colors = new int[1 << 16];
    private int count;
    private final java.util.HashMap<Long, Integer> index = new java.util.HashMap<>();

    private static long key(int x, int y, int z) {
        return ((long) (x + (1 << 20)) << 42) | ((long) (y + (1 << 10)) << 21) | (z + (1 << 20));
    }

    void put(int x, int y, int z, int rgb) {
        long k = key(x, y, z);
        Integer at = this.index.get(k);
        if (at != null) {
            this.colors[at] = rgb;
            return;
        }
        if (this.count == this.keys.length) {
            this.keys = Arrays.copyOf(this.keys, this.count * 2);
            this.colors = Arrays.copyOf(this.colors, this.count * 2);
        }
        this.keys[this.count] = k;
        this.colors[this.count] = rgb;
        this.index.put(k, this.count);
        this.count++;
    }

    boolean has(int x, int y, int z) {
        return this.index.containsKey(key(x, y, z));
    }

    int size() {
        return this.count;
    }

    /** Renders with cubes {@code s} pixels across half a tile; the image fits the voxels. */
    BufferedImage render(int s, int background) {
        int[] xs = new int[this.count];
        int[] ys = new int[this.count];
        int[] zs = new int[this.count];
        Integer[] order = new Integer[this.count];
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (int i = 0; i < this.count; i++) {
            long k = this.keys[i];
            xs[i] = (int) (k >>> 42) - (1 << 20);
            ys[i] = (int) ((k >>> 21) & ((1 << 21) - 1)) - (1 << 10);
            zs[i] = (int) (k & ((1L << 21) - 1)) - (1 << 20);
            order[i] = i;
            int u = (xs[i] - zs[i]) * s;
            int v = (xs[i] + zs[i]) * s / 2 - ys[i] * s;
            minU = Math.min(minU, u - s);
            maxU = Math.max(maxU, u + s);
            minV = Math.min(minV, v - s);
            maxV = Math.max(maxV, v + 2 * s);
        }
        Arrays.sort(order, (a, b) -> {
            int da = xs[a] + ys[a] + zs[a];
            int db = xs[b] + ys[b] + zs[b];
            if (da != db) {
                return Integer.compare(da, db);
            }
            return Integer.compare(ys[a], ys[b]);
        });
        int width = Math.max(1, maxU - minU + 4);
        int height = Math.max(1, maxV - minV + 4);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(background));
        g.fillRect(0, 0, width, height);
        int ou = -minU + 2;
        int ov = -minV + 2;
        int[] px = new int[4];
        int[] py = new int[4];
        for (int n = 0; n < this.count; n++) {
            int i = order[n];
            int x = xs[i];
            int y = ys[i];
            int z = zs[i];
            int c = this.colors[i];
            if (!this.has(x, y + 1, z)) {
                proj(px, py, 0, x, y + 1, z, s, ou, ov);
                proj(px, py, 1, x + 1, y + 1, z, s, ou, ov);
                proj(px, py, 2, x + 1, y + 1, z + 1, s, ou, ov);
                proj(px, py, 3, x, y + 1, z + 1, s, ou, ov);
                g.setColor(new Color(shade(c, 1.0)));
                g.fillPolygon(px, py, 4);
            }
            if (!this.has(x + 1, y, z)) {
                proj(px, py, 0, x + 1, y, z, s, ou, ov);
                proj(px, py, 1, x + 1, y, z + 1, s, ou, ov);
                proj(px, py, 2, x + 1, y + 1, z + 1, s, ou, ov);
                proj(px, py, 3, x + 1, y + 1, z, s, ou, ov);
                g.setColor(new Color(shade(c, 0.78)));
                g.fillPolygon(px, py, 4);
            }
            if (!this.has(x, y, z + 1)) {
                proj(px, py, 0, x, y, z + 1, s, ou, ov);
                proj(px, py, 1, x + 1, y, z + 1, s, ou, ov);
                proj(px, py, 2, x + 1, y + 1, z + 1, s, ou, ov);
                proj(px, py, 3, x, y + 1, z + 1, s, ou, ov);
                g.setColor(new Color(shade(c, 0.6)));
                g.fillPolygon(px, py, 4);
            }
        }
        g.dispose();
        return image;
    }

    private static void proj(int[] px, int[] py, int k, int x, int y, int z, int s, int ou, int ov) {
        px[k] = (x - z) * s + ou;
        py[k] = (x + z) * s / 2 - y * s + ov;
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * f);
        int b = (int) Math.min(255, (rgb & 255) * f);
        return r << 16 | g << 8 | b;
    }
}
