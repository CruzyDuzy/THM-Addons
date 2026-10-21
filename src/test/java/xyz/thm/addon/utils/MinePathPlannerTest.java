/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.MinePathPlanner.Move;
import xyz.thm.addon.utils.MinePathPlanner.Step;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinePathPlannerTest {
    private interface Kinds { int at(int x, int y, int z); }

    private static MinePathPlanner.World world(Kinds kinds) {
        return new MinePathPlanner.World() {
            @Override public int kind(int x, int y, int z) { return kinds.at(x, y, z); }
            @Override public float breakTicks(int x, int y, int z) { return 6f; }
            @Override public boolean falls(int x, int y, int z) { return false; }
            @Override public boolean loaded(int x, int z) { return Math.abs(x) <= 200 && Math.abs(z) <= 200; }
        };
    }

    private static MinePathPlanner.Search solve(Kinds kinds, int gx, int gz, int gy) {
        MinePathPlanner.Search search = new MinePathPlanner.Search(world(kinds), 0, 64, 0, gx, gz, gy, 2, 1, 126, 30_000, 5f);
        while (!search.step(600)) { /* keep searching */ }
        return search;
    }

    private static final Kinds ROCK = (x, y, z) -> y < 1 || y > 126 ? MinePathPlanner.WALL : MinePathPlanner.SOLID;
    private static final Kinds POCKET = (x, y, z) -> x == 0 && z == 0 && (y == 64 || y == 65) ? MinePathPlanner.AIR : ROCK.at(x, y, z);

    @Test
    void tunnelsStraightThroughRock() {
        MinePathPlanner.Search search = solve(POCKET, 40, 0, MinePathPlanner.NO_Y);
        assertTrue(search.reachedGoal());
        List<Step> path = search.result();
        assertTrue(path.size() <= 45, "path should be nearly straight, was " + path.size());
    }

    @Test
    void goesAroundUnbreakableWall() {
        Kinds wall = (x, y, z) -> x == 20 && Math.abs(z) > 3 ? MinePathPlanner.WALL : POCKET.at(x, y, z);
        MinePathPlanner.Search search = solve(wall, 40, 30, MinePathPlanner.NO_Y);
        assertTrue(search.reachedGoal());
        for (Step step : search.result()) assertTrue(wall.at(step.x(), step.y(), step.z()) != MinePathPlanner.WALL);
    }

    @Test
    void neverEntersOrMinesNextToLiquid() {
        Kinds lava = (x, y, z) -> x >= 10 && x <= 14 && Math.abs(z) <= 30 && y >= 60 && y <= 70 ? MinePathPlanner.LIQUID : POCKET.at(x, y, z);
        MinePathPlanner.Search search = solve(lava, 40, 0, MinePathPlanner.NO_Y);
        assertTrue(search.reachedGoal());

        List<Step> path = search.result();
        for (int i = 1; i < path.size(); i++) {
            for (int[] c : MinePathPlanner.cellsToClear(path.get(i - 1), path.get(i))) {
                if (lava.at(c[0], c[1], c[2]) != MinePathPlanner.SOLID) continue;
                assertTrue(lava.at(c[0] + 1, c[1], c[2]) != MinePathPlanner.LIQUID);
                assertTrue(lava.at(c[0] - 1, c[1], c[2]) != MinePathPlanner.LIQUID);
                assertTrue(lava.at(c[0], c[1] + 1, c[2]) != MinePathPlanner.LIQUID);
                assertTrue(lava.at(c[0], c[1] - 1, c[2]) != MinePathPlanner.LIQUID);
                assertTrue(lava.at(c[0], c[1], c[2] + 1) != MinePathPlanner.LIQUID);
                assertTrue(lava.at(c[0], c[1], c[2] - 1) != MinePathPlanner.LIQUID);
            }
        }
    }

    @Test
    void digsDownToTargetY() {
        MinePathPlanner.Search search = solve(POCKET, 0, 0, 40);
        assertTrue(search.reachedGoal());
        Step last = search.result().get(search.result().size() - 1);
        assertEquals(40, last.y());
        assertEquals(Move.DIG_DOWN, last.move());
    }

    @Test
    void farGoalGivesPartialPathTowardIt() {
        MinePathPlanner.Search search = solve(POCKET, 500, 0, MinePathPlanner.NO_Y);
        assertTrue(!search.reachedGoal());
        Step last = search.result().get(search.result().size() - 1);
        assertTrue(last.x() > 100, "partial path should head to the goal, ended at x=" + last.x());
    }
}
