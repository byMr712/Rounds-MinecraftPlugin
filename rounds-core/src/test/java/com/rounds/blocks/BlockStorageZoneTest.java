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

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center, List.of(center));

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

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center, List.of(center));

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

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center, List.of(center));

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

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center, List.of(center));

        assertEquals("block.map-border-reason.not-four", result.warningKey);
        assertEquals(3, result.warningNumber);
        assertEquals(-25, result.bounds.minX);
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

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center, List.of(center));

        assertEquals("block.map-border-reason.quadrants", result.warningKey);
        assertEquals(-25, result.bounds.minX);
    }

    @Test
    void tooSmallFallsBackWithWarning() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        addCorners(storage, world, -2, 2, -2, 2);

        BlockStorage.ZoneResolution result = storage.resolveZoneBounds(center, List.of(center));

        assertEquals("block.map-border-reason.too-small", result.warningKey);
        assertEquals(BlockStorage.MIN_ZONE_SIZE, result.warningNumber);
    }

    @Test
    void boundsContainWorksOnEdgesAndRejectsOtherWorld() {
        World world = world();
        BlockStorage storage = new BlockStorage(tempDir.toFile());
        BlockStorage.MapBlock center = center(world, 0, 0);
        addCorners(storage, world, -10, 10, -10, 10);

        BlockStorage.ZoneBounds bounds = storage.resolveZoneBounds(center, List.of(center)).bounds;

        assertTrue(bounds.contains(loc(world, -10, -10)));
        assertTrue(bounds.contains(loc(world, 10, 10)));
        assertFalse(bounds.contains(loc(world, 11, 0)));
        assertFalse(bounds.contains(loc(world(), 0, 0)));
    }
}
