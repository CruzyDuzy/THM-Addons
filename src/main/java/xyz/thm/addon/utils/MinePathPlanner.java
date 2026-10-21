/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Baritone-style ground planner that may dig: every block that has to be broken adds its break time to the
 * path cost, so the search prefers caves and soft blocks but tunnels straight through rock when that is cheaper.
 * Moves: walk, ascend one block (stair-dig), fall up to {@link #MAX_FALL}, and dig straight down.
 */
public final class MinePathPlanner {
    public static final int AIR = 0;
    public static final int SOLID = 1;
    public static final int WALL = 2;
    public static final int LIQUID = 3;
    public static final int DANGER = 4;

    public static final int MAX_FALL = 3;
    public static final int NO_Y = Integer.MIN_VALUE;

    private static final float INF = Float.MAX_VALUE;
    private static final float WALK = 4.633f;
    private static final float ASCEND = 7.0f;
    private static final float FALL_ONE = 3.0f;
    private static final float DIG_DOWN = 4.0f;
    private static final float MINE_PENALTY = 2.0f;
    private static final float GRAVITY_PENALTY = 30.0f;
    private static final float VERTICAL = 4.0f;

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    public interface World {
        /** AIR, SOLID (breakable), WALL (unbreakable or protected), LIQUID or DANGER (fire, web, magma, ...). */
        int kind(int x, int y, int z);

        /** Ticks needed to break this SOLID cell with the best tool. */
        float breakTicks(int x, int y, int z);

        /** True for sand/gravel-like blocks that drop when the block under them is mined. */
        boolean falls(int x, int y, int z);

        boolean loaded(int x, int z);
    }

    public enum Move { START, WALK, ASCEND, FALL, DIG_DOWN }

    public record Step(int x, int y, int z, Move move) {}

    /** Cells (x, y, z) that must be air before {@code to} can be entered from {@code from}, in breaking order. */
    public static int[][] cellsToClear(Step from, Step to) {
        switch (to.move()) {
            case ASCEND:
                return new int[][]{
                    {from.x(), from.y() + 2, from.z()},
                    {to.x(), from.y() + 2, to.z()},
                    {to.x(), from.y() + 1, to.z()}
                };
            case DIG_DOWN:
                return new int[][]{{from.x(), from.y() - 1, from.z()}};
            case FALL:
            case WALK:
                return new int[][]{
                    {to.x(), from.y() + 1, to.z()},
                    {to.x(), from.y(), to.z()}
                };
            default:
                return new int[0][];
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static final class Search {
        private final World w;
        private final int goalX, goalZ, goalY, minY, maxY, maxNodes;
        private final double goalRadius;
        private final float weight;

        private final ElytraPathPlanner.LongMap index = new ElytraPathPlanner.LongMap();
        private final ElytraPathPlanner.LongMap kinds = new ElytraPathPlanner.LongMap();
        private final ElytraPathPlanner.LongMap costs = new ElytraPathPlanner.LongMap();

        private int size;
        private int[] nx = new int[1024], ny = new int[1024], nz = new int[1024], parent = new int[1024];
        private byte[] nmove = new byte[1024];
        private float[] ng = new float[1024], nf = new float[1024];
        private boolean[] closed = new boolean[1024];

        private int[] heap = new int[1024];
        private int heapSize;

        private int expanded;
        private int best;
        private float bestH = INF;
        private int goalNode = -1;
        private boolean done;
        private List<Step> result;

        public Search(World world, int sx, int sy, int sz, int goalX, int goalZ, int goalY, double goalRadius,
                      int minY, int maxY, int maxNodes, float weight) {
            this.w = world;
            this.goalX = goalX;
            this.goalZ = goalZ;
            this.goalY = goalY;
            this.goalRadius = goalRadius;
            this.minY = minY;
            this.maxY = maxY;
            this.maxNodes = maxNodes;
            this.weight = weight;

            int start = add(sx, sy, sz, -1, Move.START, 0f);
            best = start;
            bestH = h(sx, sy, sz);
            push(start);
        }

        public boolean isDone() { return done; }
        public boolean reachedGoal() { return goalNode != -1; }
        public int expanded() { return expanded; }

        /** The path including the start cell, or null while still searching. */
        public List<Step> result() { return result; }

        /** Expands up to {@code budget} nodes; returns true once the search has finished. */
        public boolean step(int budget) {
            if (done) return true;

            int n = 0;
            while (n < budget) {
                int id = pop();
                if (id == -1) {
                    finish();
                    return true;
                }
                if (closed[id]) continue;
                closed[id] = true;
                n++;
                expanded++;

                if (atGoal(nx[id], ny[id], nz[id])) {
                    goalNode = id;
                    finish();
                    return true;
                }

                float hv = h(nx[id], ny[id], nz[id]);
                if (hv < bestH) {
                    bestH = hv;
                    best = id;
                }

                expand(id);
                if (expanded >= maxNodes) {
                    finish();
                    return true;
                }
            }
            return false;
        }

        private void finish() {
            done = true;
            int end = goalNode != -1 ? goalNode : best;
            List<Step> out = new ArrayList<>();
            for (int i = end; i != -1; i = parent[i]) out.add(new Step(nx[i], ny[i], nz[i], Move.values()[nmove[i]]));
            Collections.reverse(out);
            result = out;
        }

        private boolean atGoal(int x, int y, int z) {
            if (goalY != NO_Y && y != goalY) return false;
            double dx = x - goalX, dz = z - goalZ;
            return dx * dx + dz * dz <= goalRadius * goalRadius;
        }

        private float h(int x, int y, int z) {
            double horizontal = Math.max(0.0, Math.abs(x - goalX) + Math.abs(z - goalZ) - goalRadius);
            double vertical = goalY == NO_Y ? 0 : Math.abs(y - goalY);
            return (float) ((horizontal * WALK + vertical * VERTICAL) * weight);
        }

        // ---- world access (cached) ----

        private int kind(int x, int y, int z) {
            long key = pack(x, y, z);
            int cached = kinds.get(key);
            if (cached != -1) return cached;
            int k = w.kind(x, y, z);
            kinds.put(key, k);
            return k;
        }

        private float breakTicks(int x, int y, int z) {
            long key = pack(x, y, z);
            int cached = costs.get(key);
            if (cached != -1) return Float.intBitsToFloat(cached);
            float c = w.breakTicks(x, y, z);
            costs.put(key, Float.floatToIntBits(c));
            return c;
        }

        private boolean liquidAround(int x, int y, int z) {
            return kind(x, y + 1, z) == LIQUID || kind(x, y - 1, z) == LIQUID
                || kind(x + 1, y, z) == LIQUID || kind(x - 1, y, z) == LIQUID
                || kind(x, y, z + 1) == LIQUID || kind(x, y, z - 1) == LIQUID;
        }

        /** Cost to make the cell passable: 0 for air, the break time for safe solids, INF otherwise. */
        private float clear(int x, int y, int z) {
            int k = kind(x, y, z);
            if (k == AIR) return 0f;
            if (k != SOLID) return INF;
            if (liquidAround(x, y, z)) return INF;

            float c = breakTicks(x, y, z);
            if (c >= INF) return INF;
            c += MINE_PENALTY;
            if (w.falls(x, y + 1, z)) c += GRAVITY_PENALTY;
            return c;
        }

        private boolean standable(int x, int y, int z) {
            int k = kind(x, y, z);
            return k == SOLID || k == WALL;
        }

        // ---- expansion ----

        private void expand(int id) {
            int x = nx[id], y = ny[id], z = nz[id];
            float g = ng[id];

            for (int d = 0; d < 4; d++) {
                int ax = x + DX[d], az = z + DZ[d];
                if (!w.loaded(ax, az)) continue;

                walkOrFall(id, g, x, y, z, ax, az);
                ascend(id, g, x, y, z, ax, az);
            }

            digDown(id, g, x, y, z);
        }

        private void walkOrFall(int from, float g, int x, int y, int z, int ax, int az) {
            float c = clear(ax, y + 1, az);
            if (c >= INF) return;
            float c2 = clear(ax, y, az);
            if (c2 >= INF) return;
            c += c2;

            for (int fy = y - 1; fy >= y - 1 - MAX_FALL; fy--) {
                int k = kind(ax, fy, az);
                if (k == SOLID || k == WALL) {
                    int landing = fy + 1;
                    if (landing <= minY) return;
                    int h = y - landing;
                    if (h == 0) relax(from, ax, y, az, g + WALK + c, Move.WALK);
                    else relax(from, ax, landing, az, g + WALK + c + FALL_ONE * h, Move.FALL);
                    return;
                }
                if (k != AIR) return;
            }
        }

        private void ascend(int from, float g, int x, int y, int z, int ax, int az) {
            if (y + 3 > maxY) return;
            if (!standable(ax, y, az)) return;

            float c = clear(x, y + 2, z);
            if (c >= INF) return;
            float c2 = clear(ax, y + 2, az);
            if (c2 >= INF) return;
            float c3 = clear(ax, y + 1, az);
            if (c3 >= INF) return;

            relax(from, ax, y + 1, az, g + ASCEND + c + c2 + c3, Move.ASCEND);
        }

        private void digDown(int from, float g, int x, int y, int z) {
            if (y - 2 <= minY) return;
            if (kind(x, y - 1, z) != SOLID) return;
            if (!standable(x, y - 2, z)) return;

            float c = clear(x, y - 1, z);
            if (c >= INF) return;
            relax(from, x, y - 1, z, g + DIG_DOWN + c, Move.DIG_DOWN);
        }

        private void relax(int from, int x, int y, int z, float g, Move move) {
            if (y < minY || y > maxY) return;

            long key = pack(x, y, z);
            int id = index.get(key);
            if (id == -1) {
                id = add(x, y, z, from, move, g);
                index.put(key, id);
                push(id);
                return;
            }

            if (g >= ng[id] || closed[id]) return;
            parent[id] = from;
            nmove[id] = (byte) move.ordinal();
            ng[id] = g;
            nf[id] = g + h(x, y, z);
            push(id);
        }

        private int add(int x, int y, int z, int from, Move move, float g) {
            if (size == nx.length) grow();
            int id = size++;
            nx[id] = x;
            ny[id] = y;
            nz[id] = z;
            parent[id] = from;
            nmove[id] = (byte) move.ordinal();
            ng[id] = g;
            nf[id] = g + h(x, y, z);
            closed[id] = false;
            if (from == -1) index.put(pack(x, y, z), id);
            return id;
        }

        private void grow() {
            int n = nx.length * 2;
            nx = Arrays.copyOf(nx, n);
            ny = Arrays.copyOf(ny, n);
            nz = Arrays.copyOf(nz, n);
            parent = Arrays.copyOf(parent, n);
            nmove = Arrays.copyOf(nmove, n);
            ng = Arrays.copyOf(ng, n);
            nf = Arrays.copyOf(nf, n);
            closed = Arrays.copyOf(closed, n);
        }

        // ---- binary heap on nf ----

        private void push(int id) {
            if (heapSize == heap.length) heap = Arrays.copyOf(heap, heap.length * 2);
            int i = heapSize++;
            heap[i] = id;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (nf[heap[p]] <= nf[heap[i]]) break;
                int t = heap[p]; heap[p] = heap[i]; heap[i] = t;
                i = p;
            }
        }

        private int pop() {
            if (heapSize == 0) return -1;
            int top = heap[0];
            heap[0] = heap[--heapSize];
            int i = 0;
            while (true) {
                int l = 2 * i + 1, r = l + 1, m = i;
                if (l < heapSize && nf[heap[l]] < nf[heap[m]]) m = l;
                if (r < heapSize && nf[heap[r]] < nf[heap[m]]) m = r;
                if (m == i) break;
                int t = heap[m]; heap[m] = heap[i]; heap[i] = t;
                i = m;
            }
            return top;
        }
    }
}
