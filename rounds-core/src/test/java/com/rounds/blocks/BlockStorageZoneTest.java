package com.rounds.blocks;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockStorageZoneTest {

    @TempDir
    Path tempDir;

    private World world() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        return world;
    }

    private Location loc(World world, int x, int z) {
        return new Location(world, x, 64, z);
    }

    private BlockStorage.MapBlock center(World world, int x, int z) {
        return new BlockStorage.MapBlock(loc(world, x, z), BlockStorage.DEFAULT_MAP_SIZE);
    }

    private void addCorners(BlockStorage storage, World world, int minX, int maxX, int minZ, int maxZ) {
        storage.addBorderBlock(loc(world, minX, minZ));
        storage.addBorderBlock(loc(world, maxX, minZ));
        storage.addBorderBlock(loc(world, minX, maxZ));
        storage.addBorderBlock(loc(world, maxX, maxZ));
    }

    @Test
    void noBorderBlocksFallsBackSilently() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center);

        assertNull(result.warningKey);
        assertEquals(-25, result.bounds.minX);
        assertEquals(25, result.bounds.maxX);
        assertEquals(51, result.bounds.width());
    }

    @Test
    void fourSymmetricCornersOverrideBounds() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        addCorners(storage, world, -100, 100, -100, 100);

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center);

        assertNull(result.warningKey);
        assertEquals(-100, result.bounds.minX);
        assertEquals(100, result.bounds.maxX);
        assertEquals(-100, result.bounds.minZ);
        assertEquals(100, result.bounds.maxZ);
        assertEquals(201, result.bounds.width());
    }

    @Test
    void asymmetricSideIsAllowed() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        addCorners(storage, world, -100, 101, -100, 100);

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center);

        assertNull(result.warningKey);
        assertEquals(-100, result.bounds.minX);
        assertEquals(101, result.bounds.maxX);
    }

    @Test
    void threeCornersFallBackWithWarning() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        storage.addBorderBlock(loc(world, -100, -100));
        storage.addBorderBlock(loc(world, 100, -100));
        storage.addBorderBlock(loc(world, -100, 100));

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center);

        assertEquals("block.map-border-reason.not-four", result.warningKey);
        assertEquals(3, result.warningNumber);
        assertEquals(-25, result.bounds.minX);
        assertEquals(4, result.debugLines.size());
        assertTrue(result.debugLines.get(0).startsWith("BORDER1 coord="));
        assertTrue(result.debugLines.get(1).startsWith("BORDER2 coord="));
        assertTrue(result.debugLines.get(2).startsWith("BORDER3 >>MISSING<<"));
        assertTrue(result.debugLines.get(3).startsWith("BORDER4 coord="));
    }

    @Test
    void duplicateQuadrantFallsBackWithWarning() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        storage.addBorderBlock(loc(world, -100, -100));
        storage.addBorderBlock(loc(world, -100, -100)); // same corner twice
        storage.addBorderBlock(loc(world, 100, -100));
        storage.addBorderBlock(loc(world, 100, 100));

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center);

        assertEquals("block.map-border-reason.quadrants", result.warningKey);
        assertEquals(-25, result.bounds.minX);
    }

    @Test
    void tooSmallFallsBackWithWarning() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        addCorners(storage, world, -2, 2, -2, 2);

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center);

        assertEquals("block.map-border-reason.too-small", result.warningKey);
        assertEquals(BlockStorage.MIN_ZONE_SIZE, result.warningNumber);
    }

    @Test
    void boundsContainWorksOnEdgesAndRejectsOtherWorld() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        addCorners(storage, world, -10, 10, -10, 10);

        BlockStorage.ZoneBounds bounds = storage.resolveZoneBounds(center).bounds;

        assertTrue(bounds.contains(loc(world, -10, -10)));
        assertTrue(bounds.contains(loc(world, 10, 10)));
        assertFalse(bounds.contains(loc(world, 11, 0)));
        assertFalse(bounds.contains(loc(world(), 0, 0)));
    }

    /**
     * The original ifrit arena has seven tightly packed zones whose 28 border corners are closer
     * to neighboring centers in several cases. The resolver must still pick each map's own
     * rectangle instead of losing corners to the "nearest center" rule.
     */
    @Test
    void packedOriginalMapCornersResolveToTheirOwnZones() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());

        int[][] centers = {
                {-1, 41}, {51, 26}, {2, 87}, {-56, 50}, {6, -63}, {70, -63}, {-38, -14}
        };
        int[][] borders = {
                {-19, 25}, {-19, 57}, {14, 57}, {14, 25},
                {26, 1}, {26, 51}, {76, 51}, {76, 1},
                {-19, 63}, {24, 63}, {24, 111}, {-19, 111},
                {-31, 75}, {-31, 25}, {-81, 25}, {-81, 75},
                {31, -38}, {31, -88}, {-19, -88}, {-19, -38},
                {95, -38}, {95, -88}, {45, -88}, {45, -38},
                {-22, 2}, {-22, -30}, {-55, -30}, {-55, 2}
        };
        int[][] expected = {
                {-19, 14, 25, 57},
                {26, 76, 1, 51},
                {-19, 24, 63, 111},
                {-81, -31, 25, 75},
                {-19, 31, -88, -38},
                {45, 95, -88, -38},
                {-55, -22, -30, 2}
        };

        for (int[] b : borders) storage.addBorderBlock(loc(world, b[0], b[1]));

        for (int i = 0; i < centers.length; i++) {
            BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center(world, centers[i][0], centers[i][1]));
            assertNull(result.warningKey, "map " + i + " should not fall back");
            assertEquals(expected[i][0], result.bounds.minX, "map " + i + " minX");
            assertEquals(expected[i][1], result.bounds.maxX, "map " + i + " maxX");
            assertEquals(expected[i][2], result.bounds.minZ, "map " + i + " minZ");
            assertEquals(expected[i][3], result.bounds.maxZ, "map " + i + " maxZ");
            assertEquals(4, result.debugLines.size(), "map " + i + " should log exactly four border lines");
            assertTrue(result.debugLines.stream().noneMatch(l -> l.contains("MISSING")),
                    "map " + i + " should not report missing corners");
            if (i == 0) {
                assertEquals("BORDER1 coord=(-19,64,25) distant=24.1", result.debugLines.get(0));
                assertEquals("BORDER2 coord=(14,64,25) distant=21.9", result.debugLines.get(1));
                assertEquals("BORDER3 coord=(14,64,57) distant=21.9", result.debugLines.get(2));
                assertEquals("BORDER4 coord=(-19,64,57) distant=24.1", result.debugLines.get(3));
            }
        }
    }
}

