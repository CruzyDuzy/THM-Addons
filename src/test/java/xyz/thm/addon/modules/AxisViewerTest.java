/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AxisViewerTest {
    @Test
    void highwayRadiiCoverXaeroPlusAndKeepExistingThmDiamonds() {
        assertArrayEquals(new int[] {
            200, 500, 750, 1000, 1500, 2000, 2500, 3131, 3500, 5000, 6250, 7500,
            10000, 12500, 15000, 20000, 25000, 50000, 55000, 62500, 75000, 100000,
            125000, 250000, 500000, 750000, 1000000, 1250000, 1568852, 1875000,
            2500000, 3750000, 30000000
        }, AxisViewer.RING_ROADS.toIntArray());
        assertArrayEquals(new int[] {
            1000, 2000, 2500, 5000, 10000, 25000, 50000, 125000, 250000, 500000, 3750000
        }, AxisViewer.DIAMONDS.toIntArray());
    }

    @Test
    void worldSpanningAxesAreClippedToTheCameraWindowInEitherDirection() {
        double[] output = new double[4];
        assertTrue(clip(-30_000_000, 0.5, 30_000_000, 0.5, output));
        assertArrayEquals(new double[] {-128, 0.5, 128, 0.5}, output, 1e-6);
        assertTrue(clip(0.5, 30_000_000, 0.5, -30_000_000, output));
        assertArrayEquals(new double[] {0.5, 128, 0.5, -128}, output, 1e-6);
    }

    @Test
    void bothDiagonalsRetainTheirTrueCenterAlignment() {
        double[] output = new double[4];
        assertTrue(clip(-30_000_000 + 0.5, -30_000_000 + 0.5,
            30_000_000 + 0.5, 30_000_000 + 0.5, output));
        assertArrayEquals(new double[] {-128, -128, 128, 128}, output, 1e-6);
        assertTrue(clip(-30_000_000, 30_000_001, 30_000_000, -29_999_999, output));
        assertArrayEquals(new double[] {-127, 128, 128, -127}, output, 1e-6);
    }

    @Test
    void invisibleRoadsAreRejectedEvenWhenTheirBoundingBoxOverlaps() {
        double[] output = new double[4];
        assertFalse(clip(-50_000, 5000.5, 50_000, 5000.5, output));
        assertFalse(clip(5000.5, -50_000, 5000.5, 50_000, output));
        assertFalse(AxisViewer.clipLine(-100, 130, 130, -100, -10, -10, 10, 10, output));
    }

    @Test
    void gridAndExtensionEndpointsAreNeverExtendedByClipping() {
        double[] output = new double[4];
        assertTrue(AxisViewer.clipLine(50_000.5, 50_000.5, 125_000.5, 50_000.5,
            124_872, 49_872, 125_128, 50_128, output));
        assertArrayEquals(new double[] {124_872, 50_000.5, 125_000.5, 50_000.5}, output, 1e-6);
        assertFalse(AxisViewer.clipLine(5000.5, -50_000 + 0.5, 5000.5, 50_000.5,
            4872, 51_000, 5128, 51_256, output));
    }

    @Test
    void clippingFollowsDistantCameraPositionsAndHandlesBorderScaleRoads() {
        double[] output = new double[4];
        assertTrue(AxisViewer.clipLine(30_000_000.5, -30_000_000 + 0.5,
            30_000_000.5, 30_000_000.5, 29_999_872, -128, 30_000_128, 128, output));
        assertArrayEquals(new double[] {30_000_000.5, -128, 30_000_000.5, 128}, output, 1e-6);
        assertTrue(AxisViewer.clipLine(-125_000 + 0.5, -50_000 + 0.5,
            -50_000 + 0.5, -50_000 + 0.5, -125_128, -50_128, -124_872, -49_872, output));
        assertArrayEquals(new double[] {-124_999.5, -49_999.5, -124_872, -49_999.5}, output, 1e-6);
    }

    @Test
    void shortRoadsInsideTheWindowKeepTheirEndpointsAndBoundaryLinesRemainVisible() {
        double[] output = new double[4];
        assertTrue(clip(-10, 20, 30, -40, output));
        assertArrayEquals(new double[] {-10, 20, 30, -40}, output);
        assertTrue(clip(-1000, 128, 1000, 128, output));
        assertArrayEquals(new double[] {-128, 128, 128, 128}, output, 1e-6);
    }

    private static boolean clip(double x1, double z1, double x2, double z2, double[] output) {
        return AxisViewer.clipLine(x1, z1, x2, z2, -128, -128, 128, 128, output);
    }
}
