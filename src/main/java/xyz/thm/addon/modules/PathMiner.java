/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.MinePathPlanner;
import xyz.thm.addon.utils.MinePathPlanner.Move;
import xyz.thm.addon.utils.MinePathPlanner.Step;
import xyz.thm.addon.utils.RenderUtilsTHM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Digs and walks to a destination with a pickaxe. Plans like Baritone (break time = path cost) and draws the route. */
public class PathMiner extends Module {
    private static final int LOOKAHEAD_STEPS = 25;
    private static final int RENDER_BREAK_STEPS = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPath = settings.createGroup("Path");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> useElytraDestination = sgGeneral.add(new BoolSetting.Builder()
        .name("use-elytra-destination")
        .description("Uses the destination from Elytra Route.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> destinationX = sgGeneral.add(new IntSetting.Builder()
        .name("destination-x")
        .description("Target X coordinate.")
        .defaultValue(0)
        .noSlider()
        .visible(() -> !useElytraDestination.get())
        .build()
    );

    private final Setting<Integer> destinationZ = sgGeneral.add(new IntSetting.Builder()
        .name("destination-z")
        .description("Target Z coordinate.")
        .defaultValue(0)
        .noSlider()
        .visible(() -> !useElytraDestination.get())
        .build()
    );

    private final Setting<Boolean> useY = sgGeneral.add(new BoolSetting.Builder()
        .name("use-y")
        .description("Also digs to a target Y level.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> destinationY = sgGeneral.add(new IntSetting.Builder()
        .name("destination-y")
        .description("Target Y level.")
        .defaultValue(64)
        .range(-64, 320)
        .sliderRange(0, 128)
        .visible(useY::get)
        .build()
    );

    private final Setting<Integer> arrivalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("arrival-radius")
        .description("Stops when this close to the target.")
        .defaultValue(2)
        .range(0, 50)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Looks at blocks while breaking them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoidContainers = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-containers")
        .description("Never breaks chests, spawners and other block entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDurability = sgGeneral.add(new IntSetting.Builder()
        .name("min-durability")
        .description("Pickaxes with less durability left are not used.")
        .defaultValue(20)
        .range(1, 500)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Prints path and replan messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> pathGreed = sgPath.add(new DoubleSetting.Builder()
        .name("path-greed")
        .description("Higher digs straighter, lower searches for easier ground.")
        .defaultValue(5.0)
        .range(1.0, 10.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Integer> maxNodes = sgPath.add(new IntSetting.Builder()
        .name("max-nodes")
        .description("Search size per path segment.")
        .defaultValue(20000)
        .range(2000, 100000)
        .sliderRange(5000, 50000)
        .build()
    );

    private final Setting<Integer> nodesPerTick = sgPath.add(new IntSetting.Builder()
        .name("nodes-per-tick")
        .description("Search work per tick. Lower is smoother.")
        .defaultValue(500)
        .range(50, 3000)
        .sliderRange(100, 1500)
        .build()
    );

    private final Setting<Integer> stuckTicks = sgPath.add(new IntSetting.Builder()
        .name("stuck-ticks")
        .description("Ticks without progress before replanning.")
        .defaultValue(80)
        .range(20, 400)
        .sliderRange(40, 200)
        .build()
    );

    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Draws the planned route.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderSteps = sgRender.add(new IntSetting.Builder()
        .name("render-steps")
        .description("How many upcoming steps to draw.")
        .defaultValue(150)
        .range(10, 1000)
        .sliderRange(20, 400)
        .visible(renderPath::get)
        .build()
    );

    private final Setting<SettingColor> walkColor = sgRender.add(new ColorSetting.Builder()
        .name("walk-color")
        .description("Route segments that are already open.")
        .defaultValue(new SettingColor(0, 200, 255, 255))
        .visible(renderPath::get)
        .build()
    );

    private final Setting<SettingColor> mineColor = sgRender.add(new ColorSetting.Builder()
        .name("mine-color")
        .description("Route segments that need digging.")
        .defaultValue(new SettingColor(255, 140, 0, 255))
        .visible(renderPath::get)
        .build()
    );

    private final Setting<Boolean> renderBreak = sgRender.add(new BoolSetting.Builder()
        .name("render-break")
        .description("Highlights the next blocks to break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> breakSide = sgRender.add(new ColorSetting.Builder()
        .name("break-side-color")
        .description("Fill of blocks to break.")
        .defaultValue(new SettingColor(255, 80, 0, 40))
        .visible(renderBreak::get)
        .build()
    );

    private final Setting<SettingColor> breakLine = sgRender.add(new ColorSetting.Builder()
        .name("break-line-color")
        .description("Outline of blocks to break.")
        .defaultValue(new SettingColor(255, 80, 0, 255))
        .visible(renderBreak::get)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Lines from the screen centre to the next step and the destination.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> stepTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("step-tracer-color")
        .description("Tracer to the current step.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(tracers::get)
        .build()
    );

    private final Setting<SettingColor> goalTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("goal-tracer-color")
        .description("Tracer to the destination.")
        .defaultValue(new SettingColor(80, 255, 80, 200))
        .visible(tracers::get)
        .build()
    );

    private final Terrain terrain = new Terrain();
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    private List<Step> path;
    private int idx;
    private boolean partial;
    private MinePathPlanner.Search search;
    private boolean searchAppend;
    private int searchFails;
    private int stuckCount;

    private BlockPos mineTarget;
    private int mineTicks;
    private int tick;
    private int lastProgressTick;
    private Vec3 lastPos = Vec3.ZERO;

    private ItemStack toolStack = ItemStack.EMPTY;
    private int toolSlot = -1;
    private int toolCheckTick;

    private int goalX, goalZ, goalY;
    private int stepLimit;
    private int stepsDone;

    public PathMiner() {
        super(THMAddon.MAIN, "path-miner", "Digs to a destination with a pickaxe, planning the route like Baritone and drawing it.");
    }

    @Override
    public void onActivate() {
        resetRuntime();
    }

    @Override
    public void onDeactivate() {
        release();
        InvUtils.swapBack();
        stepLimit = 0;
        resetRuntime();
    }

    /** Digs toward the Elytra Route destination for at most this many steps, then switches itself off. */
    public void runLimited(int steps) {
        useElytraDestination.set(true);
        stepLimit = steps;
        if (!isActive()) toggle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @Override
    public String getInfoString() {
        if (path == null) return search != null ? "planning" : null;
        return Math.min(idx, path.size() - 1) + "/" + (path.size() - 1) + (partial ? "+" : "");
    }

    private void resetRuntime() {
        path = null;
        idx = 0;
        partial = false;
        search = null;
        searchAppend = false;
        searchFails = 0;
        stuckCount = 0;
        stepsDone = 0;
        mineTarget = null;
        mineTicks = 0;
        tick = 0;
        lastProgressTick = 0;
        toolStack = ItemStack.EMPTY;
        toolSlot = -1;
        toolCheckTick = -100;
        if (mc.player != null) lastPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    // ---- main loop ----

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        tick++;
        terrain.newTick();

        if (mc.gui.screen() != null) {
            release();
            return;
        }

        resolveGoal();
        if (arrived()) {
            info("Arrived.");
            toggle();
            return;
        }

        if (!updateTool()) {
            error("No usable pickaxe in your inventory.");
            toggle();
            return;
        }

        advanceSearch();
        if (!isActive()) return;

        if (path == null) {
            release();
            startSearchFromPlayer();
            return;
        }

        follow();
    }

    private void resolveGoal() {
        if (useElytraDestination.get() && ElytraRoute.INSTANCE != null) {
            goalX = ElytraRoute.INSTANCE.getDestinationX();
            goalZ = ElytraRoute.INSTANCE.getDestinationZ();
        } else {
            goalX = destinationX.get();
            goalZ = destinationZ.get();
        }
        goalY = destinationY.get();
    }

    private boolean arrived() {
        double dx = mc.player.getX() - (goalX + 0.5);
        double dz = mc.player.getZ() - (goalZ + 0.5);
        double r = arrivalRadius.get() + 0.5;
        if (dx * dx + dz * dz > r * r) return false;
        return !useY.get() || Math.abs(mc.player.getY() - goalY) <= 1.0;
    }

    // ---- planning ----

    private MinePathPlanner.Search newSearch(int x, int y, int z) {
        return new MinePathPlanner.Search(
            terrain, x, y, z, goalX, goalZ,
            useY.get() ? goalY : MinePathPlanner.NO_Y,
            arrivalRadius.get(),
            mc.level.getMinY() + 1, mc.level.getMaxY() - 3,
            maxNodes.get(), pathGreed.get().floatValue()
        );
    }

    private void startSearchFromPlayer() {
        if (search != null) return;
        if (!mc.player.onGround() && mc.player.getDeltaMovement().y < -0.1 && tick % 100 != 0) return;

        search = newSearch(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        searchAppend = false;
    }

    private void advanceSearch() {
        if (search == null || !search.step(nodesPerTick.get())) return;

        List<Step> result = search.result();
        boolean reached = search.reachedGoal();
        int expanded = search.expanded();
        search = null;

        boolean append = searchAppend;
        searchAppend = false;

        if (result.size() <= 1) {
            if (append) partial = false;
            if (!reached && ++searchFails >= 3) {
                error("No way to the destination was found.");
                toggle();
            }
            return;
        }

        searchFails = 0;
        if (append && path != null) {
            path.addAll(result.subList(1, result.size()));
        } else {
            path = new ArrayList<>(result);
            idx = 0;
            markProgress();
        }
        partial = !reached;

        if (chatInfo.get()) info("Path: %d steps (%d nodes)%s.", path.size() - 1, expanded, partial ? ", partial" : "");
    }

    private void replan(String reason) {
        if (chatInfo.get()) info("Replanning: " + reason);
        path = null;
        search = null;
        searchAppend = false;
        mineTarget = null;
        mineTicks = 0;
        release();
    }

    // ---- following ----

    private void follow() {
        if (partial && search == null && path.size() - 1 - idx <= LOOKAHEAD_STEPS) {
            Step end = path.get(path.size() - 1);
            search = newSearch(end.x(), end.y(), end.z());
            searchAppend = true;
        }

        if (idx >= path.size() - 1) {
            release();
            if (!partial) path = null;
            else if (search == null) path = null;
            return;
        }

        syncIndex();

        Step from = path.get(idx);
        Step to = path.get(idx + 1);

        if (deviated(from, to)) {
            replan("left the route");
            return;
        }

        if (reachedStep(to)) {
            idx++;
            mineTarget = null;
            mineTicks = 0;
            markProgress();

            if (stepLimit > 0 && ++stepsDone >= stepLimit) {
                if (chatInfo.get()) info("Segment finished.");
                toggle();
            }
            return;
        }

        if (to.move() == Move.DIG_DOWN && horizontalDistance(from.x() + 0.5, from.z() + 0.5) > 0.25) {
            walkToward(from.x() + 0.5, from.z() + 0.5, false);
            checkStuck();
            return;
        }

        if (clearCells(from, to)) {
            checkStuck();
            return;
        }

        move(to);
        checkStuck();
    }

    private void syncIndex() {
        int last = Math.min(path.size() - 1, idx + 4);
        for (int j = last; j >= idx + 2; j--) {
            Step s = path.get(j);
            if (Math.abs(mc.player.getX() - (s.x() + 0.5)) < 0.5
                && Math.abs(mc.player.getZ() - (s.z() + 0.5)) < 0.5
                && Math.abs(mc.player.getY() - s.y()) < 1.0) {
                idx = j - 1;
                return;
            }
        }
    }

    private boolean deviated(Step from, Step to) {
        return distanceTo(from) > 3.5 && distanceTo(to) > 3.5;
    }

    private double distanceTo(Step s) {
        double dx = mc.player.getX() - (s.x() + 0.5);
        double dy = mc.player.getY() - s.y();
        double dz = mc.player.getZ() - (s.z() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double horizontalDistance(double x, double z) {
        return Math.hypot(mc.player.getX() - x, mc.player.getZ() - z);
    }

    private boolean reachedStep(Step to) {
        if (horizontalDistance(to.x() + 0.5, to.z() + 0.5) > 0.4) return false;
        double dy = Math.abs(mc.player.getY() - to.y());
        return to.move() == Move.WALK ? dy < 0.9 : dy < 0.6 && mc.player.onGround();
    }

    /** Breaks the next blocking cell of this step. Returns true while the step still has work to do. */
    private boolean clearCells(Step from, Step to) {
        for (int[] c : MinePathPlanner.cellsToClear(from, to)) {
            int kind = terrain.kindAt(c[0], c[1], c[2]);
            if (kind == MinePathPlanner.AIR) continue;

            if (kind != MinePathPlanner.SOLID) {
                replan("blocked by a hazard");
                return true;
            }

            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            if (terrain.liquidNextTo(pos)) {
                replan("liquid behind a block");
                return true;
            }

            mine(pos);
            return true;
        }

        mineTarget = null;
        return false;
    }

    private void mine(BlockPos pos) {
        if (!pos.equals(mineTarget)) {
            mineTarget = pos;
            mineTicks = 0;
        }

        if (++mineTicks > 400) {
            replan("a block could not be broken");
            return;
        }

        release();
        selectTool();
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos));
        BlockUtils.breakBlock(pos, true);
        markProgress();
    }

    private void move(Step to) {
        if (to.move() == Move.DIG_DOWN) {
            release();
            return;
        }

        double cx = to.x() + 0.5;
        double cz = to.z() + 0.5;
        boolean jump = to.move() == Move.ASCEND && mc.player.onGround() && horizontalDistance(cx, cz) < 1.4;
        walkToward(cx, cz, jump);
    }

    private void walkToward(double x, double z, boolean jump) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        if (dx * dx + dz * dz > 0.0025) mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(jump);
    }

    private void release() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private void markProgress() {
        lastProgressTick = tick;
        if (mc.player != null) lastPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void checkStuck() {
        double mx = mc.player.getX() - lastPos.x;
        double my = mc.player.getY() - lastPos.y;
        double mz = mc.player.getZ() - lastPos.z;
        if (mx * mx + my * my + mz * mz >= 0.01) markProgress();
        if (tick - lastProgressTick <= stuckTicks.get()) return;

        if (++stuckCount >= 4) {
            error("Stuck. Stopping.");
            toggle();
            return;
        }

        markProgress();
        replan("stuck");
    }

    // ---- tool ----

    private static boolean isPickaxe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE
            || item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
    }

    private boolean updateTool() {
        if (toolSlot != -1 && tick - toolCheckTick < 20) {
            ItemStack current = mc.player.getInventory().getItem(toolSlot);
            if (isPickaxe(current) && current.getMaxDamage() - current.getDamageValue() >= minDurability.get()) return true;
        }

        toolCheckTick = tick;
        int best = -1;
        float bestSpeed = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isPickaxe(stack) || stack.getMaxDamage() - stack.getDamageValue() < minDurability.get()) continue;

            float speed = stack.getDestroySpeed(Blocks.OBSIDIAN.defaultBlockState());
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }

        toolSlot = best;
        toolStack = best == -1 ? ItemStack.EMPTY : mc.player.getInventory().getItem(best);
        return best != -1;
    }

    private void selectTool() {
        if (toolSlot == -1) return;

        if (toolSlot >= 9) {
            int hotbar = mc.player.getInventory().getSelectedSlot();
            InvUtils.move().from(toolSlot).toHotbar(hotbar);
            toolSlot = hotbar;
            toolStack = mc.player.getInventory().getItem(hotbar);
        }

        InvUtils.swap(toolSlot, true);
    }

    // ---- terrain adapter ----

    private final class Terrain implements MinePathPlanner.World {
        private final Map<Long, Boolean> chunks = new HashMap<>();

        void newTick() {
            chunks.clear();
        }

        @Override
        public boolean loaded(int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            Boolean cached = chunks.get(key);
            if (cached != null) return cached;

            boolean loaded = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null;
            chunks.put(key, loaded);
            return loaded;
        }

        int kindAt(int x, int y, int z) {
            return kind(x, y, z);
        }

        boolean liquidNextTo(BlockPos pos) {
            return kind(pos.getX(), pos.getY() + 1, pos.getZ()) == MinePathPlanner.LIQUID
                || kind(pos.getX(), pos.getY() - 1, pos.getZ()) == MinePathPlanner.LIQUID
                || kind(pos.getX() + 1, pos.getY(), pos.getZ()) == MinePathPlanner.LIQUID
                || kind(pos.getX() - 1, pos.getY(), pos.getZ()) == MinePathPlanner.LIQUID
                || kind(pos.getX(), pos.getY(), pos.getZ() + 1) == MinePathPlanner.LIQUID
                || kind(pos.getX(), pos.getY(), pos.getZ() - 1) == MinePathPlanner.LIQUID;
        }

        @Override
        public int kind(int x, int y, int z) {
            if (y < mc.level.getMinY() || y > mc.level.getMaxY()) return MinePathPlanner.WALL;

            BlockState state = mc.level.getBlockState(scratch.set(x, y, z));
            if (state.isAir()) return MinePathPlanner.AIR;
            if (!state.getFluidState().isEmpty()) return MinePathPlanner.LIQUID;

            Block block = state.getBlock();
            if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.COBWEB || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POWDER_SNOW
                || block == Blocks.WITHER_ROSE) {
                return MinePathPlanner.DANGER;
            }

            if (state.getCollisionShape(mc.level, scratch).isEmpty()) return MinePathPlanner.AIR;
            if (state.getDestroySpeed(mc.level, scratch) < 0) return MinePathPlanner.WALL;
            if (avoidContainers.get() && state.hasBlockEntity()) return MinePathPlanner.WALL;
            if (!BlockUtils.canBreak(scratch, state)) return MinePathPlanner.WALL;
            return MinePathPlanner.SOLID;
        }

        @Override
        public float breakTicks(int x, int y, int z) {
            BlockState state = mc.level.getBlockState(scratch.set(x, y, z));
            float hardness = state.getDestroySpeed(mc.level, scratch);
            if (hardness < 0) return Float.MAX_VALUE;

            float speed = toolStack.isEmpty() ? 1f : toolStack.getDestroySpeed(state);
            boolean slow = state.requiresCorrectToolForDrops() && speed <= 1f;
            return Math.max(1f, hardness * (slow ? 100f : 30f) / Math.max(1f, speed));
        }

        @Override
        public boolean falls(int x, int y, int z) {
            return mc.level.getBlockState(scratch.set(x, y, z)).getBlock() instanceof FallingBlock;
        }
    }

    // ---- rendering ----

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Step to = path != null && idx < path.size() - 1 ? path.get(idx + 1) : null;

        if (renderPath.get() && path != null) renderRoute(event);
        if (renderBreak.get() && path != null) renderBreakBlocks(event);

        if (!tracers.get()) return;
        if (to != null) {
            Vec3 target = mineTarget != null
                ? Vec3.atCenterOf(mineTarget)
                : new Vec3(to.x() + 0.5, to.y() + 0.5, to.z() + 0.5);
            RenderUtilsTHM.renderTracerToVec(event, target, stepTracerColor.get());
        }

        double gy = useY.get() ? goalY + 0.5 : mc.player.getY() + 0.5;
        RenderUtilsTHM.renderTracerToVec(event, new Vec3(goalX + 0.5, gy, goalZ + 0.5), goalTracerColor.get());
    }

    private void renderRoute(Render3DEvent event) {
        int end = Math.min(path.size() - 1, idx + renderSteps.get());
        for (int i = Math.max(0, idx); i < end; i++) {
            Step a = path.get(i);
            Step b = path.get(i + 1);

            boolean digs = false;
            for (int[] c : MinePathPlanner.cellsToClear(a, b)) {
                if (terrain.kindAt(c[0], c[1], c[2]) != MinePathPlanner.AIR) {
                    digs = true;
                    break;
                }
            }

            RenderUtilsTHM.renderLine(event,
                a.x() + 0.5, a.y() + 0.5, a.z() + 0.5,
                b.x() + 0.5, b.y() + 0.5, b.z() + 0.5,
                digs ? mineColor.get() : walkColor.get());
        }
    }

    private void renderBreakBlocks(Render3DEvent event) {
        List<BlockPos> blocks = new ArrayList<>();
        int end = Math.min(path.size() - 1, idx + RENDER_BREAK_STEPS);
        for (int i = Math.max(0, idx); i < end; i++) {
            for (int[] c : MinePathPlanner.cellsToClear(path.get(i), path.get(i + 1))) {
                if (terrain.kindAt(c[0], c[1], c[2]) == MinePathPlanner.SOLID) blocks.add(new BlockPos(c[0], c[1], c[2]));
            }
        }

        if (!blocks.isEmpty()) RenderUtilsTHM.renderBlocks(event, blocks, breakSide.get(), breakLine.get(), ShapeMode.Both);
    }
}
