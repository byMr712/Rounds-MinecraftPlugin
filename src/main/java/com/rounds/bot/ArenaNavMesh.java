package com.rounds.bot;

import com.rounds.blocks.BlockListener;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Precomputed navigation mesh and tactical waypoint graph for the active arena zone.
 * Enables zero-raycast corridor navigation, precomputed cover points, and instant BFS routing
 * for 30+ bots without server lag.
 */
public class ArenaNavMesh {

    public static class NavNode {
        public final int id;
        public final Location loc;
        public final boolean isCover;
        public final int breadth; // Walkable platform breadth in 3x3 radius (1 to 9 blocks)
        public final List<NavNode> neighbors = new ArrayList<>(4);

        public NavNode(int id, Location loc, boolean isCover, int breadth) {
            this.id = id;
            this.loc = loc;
            this.isCover = isCover;
            this.breadth = breadth;
        }

        public NavNode(int id, Location loc, boolean isCover) {
            this(id, loc, isCover, 5);
        }

        public double distanceSquared(Location other) {
            double dx = loc.getX() - other.getX();
            double dy = loc.getY() - other.getY();
            double dz = loc.getZ() - other.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final Location center;
    private final List<NavNode> allNodes = new ArrayList<>();
    private final List<NavNode> coverNodes = new ArrayList<>();

    public ArenaNavMesh(Location center) {
        this.center = center != null ? center.clone() : null;
    }

    public boolean isEmpty() {
        return allNodes.isEmpty();
    }

    public Location getCenter() {
        return center;
    }

    public List<NavNode> getAllNodes() {
        return allNodes;
    }

    public List<NavNode> getCoverNodes() {
        return coverNodes;
    }

    public NavNode getRandomNode() {
        if (allNodes.isEmpty()) return null;
        return allNodes.get(ThreadLocalRandom.current().nextInt(allNodes.size()));
    }

    public NavNode getRandomCoverNode() {
        if (coverNodes.isEmpty()) return getRandomNode();
        return coverNodes.get(ThreadLocalRandom.current().nextInt(coverNodes.size()));
    }

    /**
     * Finds nearest navigable node in the mesh.
     */
    public NavNode getNearestNode(Location loc) {
        if (loc == null || allNodes.isEmpty()) return null;
        NavNode best = null;
        double bestDist = Double.MAX_VALUE;
        for (NavNode node : allNodes) {
            double d = node.distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = node;
            }
        }
        return best;
    }

    /**
     * Finds nearest unvisited node in the mesh, avoiding visited cells and blocked edges,
     * factoring in high-ground vantage points and wide walkable platforms.
     */
    public NavNode findNearestUnvisitedNode(Location from, Set<Long> visitedCells, Set<Long> blockedCells, int level, boolean preferHighGround) {
        if (from == null || allNodes.isEmpty()) return null;
        NavNode best = null;
        double bestScore = -Double.MAX_VALUE;
        for (NavNode node : allNodes) {
            long cell = (((long) (node.loc.getBlockX() >> 2)) << 32) | ((node.loc.getBlockZ() >> 2) & 0xFFFFFFFFL);
            if (visitedCells != null && visitedCells.contains(cell)) continue;
            if (blockedCells != null && blockedCells.contains(cell)) continue;

            double dist = Math.sqrt(node.distanceSquared(from));
            // Base score favors reachable nearby exploration nodes (10-30m range)
            double score = (dist >= 8.0 && dist <= 35.0) ? (40.0 - Math.abs(dist - 18.0)) : (20.0 - dist);

            // Wide platforms get a safety bonus (prefer areas with multiple blocks to walk on)
            score += node.breadth * 3.0;

            // Pro bots (level 7+) and snipers seek high ground (stairs/upper floors/towers)
            if (preferHighGround || level >= 7) {
                double deltaY = node.loc.getY() - from.getY();
                if (deltaY > 0.5) {
                    score += deltaY * 12.0; // Strong attraction to higher floors and platforms
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best;
    }

    public NavNode findNearestUnvisitedNode(Location from, Set<Long> visitedCells, Set<Long> blockedCells) {
        return findNearestUnvisitedNode(from, visitedCells, blockedCells, 5, false);
    }

    /**
     * Finds the best safe cover node near botLoc that has a solid wall or obstacle blocking line of sight from enemyLoc.
     */
    public Location findBestCover(Location botLoc, Location enemyLoc, double maxDist) {
        if (botLoc == null || enemyLoc == null || coverNodes.isEmpty()) return null;
        double maxDistSq = maxDist * maxDist;
        NavNode best = null;
        double bestScore = -Double.MAX_VALUE;

        for (NavNode node : coverNodes) {
            double dBot = node.distanceSquared(botLoc);
            if (dBot > maxDistSq) continue;
            double dEnemy = node.distanceSquared(enemyLoc);
            // Prefer cover points close to bot that maximize distance / angle from enemy
            double score = dEnemy - dBot * 1.5;
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best != null ? best.loc.clone() : null;
    }

    /**
     * Fast BFS corridor path search: returns the immediate next waypoint on the graph towards target.
     */
    public Location findNextWaypoint(Location from, Location to) {
        if (from == null || to == null || allNodes.isEmpty()) return null;
        NavNode start = getNearestNode(from);
        NavNode goal = getNearestNode(to);
        if (start == null || goal == null || start == goal) return null;

        Map<NavNode, NavNode> prev = new HashMap<>();
        Queue<NavNode> queue = new ArrayDeque<>();
        Set<NavNode> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        boolean found = false;
        while (!queue.isEmpty()) {
            NavNode curr = queue.poll();
            if (curr == goal) {
                found = true;
                break;
            }
            for (NavNode neighbor : curr.neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    prev.put(neighbor, curr);
                    queue.add(neighbor);
                }
            }
        }

        if (!found) return goal.loc;

        // Backtrack to find the immediate next node after start
        NavNode step = goal;
        while (prev.containsKey(step) && prev.get(step) != start) {
            step = prev.get(step);
        }
        return step.loc;
    }

    /**
     * Generates navigation grid and connectivity graph for the arena around the given center location.
     */
    public static ArenaNavMesh build(Location center, BlockListener bl, int radius, int step) {
        if (center == null || center.getWorld() == null) return new ArenaNavMesh(center);
        World world = center.getWorld();
        ArenaNavMesh mesh = new ArenaNavMesh(center);

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int nodeId = 0;
        List<NavNode> generated = new ArrayList<>();

        // 1. Grid sample on floor blocks (multi-level scanning for stairs, bridges, and upper decks)
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int bx = cx + dx;
                int bz = cz + dz;

                int lastBy = Integer.MAX_VALUE;
                for (int dy = 14; dy >= -12; dy--) {
                    int by = cy + dy;
                    if (Math.abs(by - lastBy) < 3) continue; // Minimum vertical separation between floors

                    Block floor = world.getBlockAt(bx, by, bz);
                    if (!floor.getType().isSolid()) continue;

                    Block above1 = world.getBlockAt(bx, by + 1, bz);
                    Block above2 = world.getBlockAt(bx, by + 2, bz);
                    if (above1.getType().isSolid() || above2.getType().isSolid()) continue;

                    Location floorLoc = floor.getLocation();
                    if (bl != null && (bl.isJumpBlock(floorLoc) || bl.isUpBlock(floorLoc))) continue;

                    // Evaluate platform breadth (density of solid neighbor blocks in 3x3 footprint)
                    int breadth = 0;
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            Block nbFloor = world.getBlockAt(bx + ox, by, bz + oz);
                            Block nbAbove = world.getBlockAt(bx + ox, by + 1, bz + oz);
                            if (nbFloor.getType().isSolid() && !nbAbove.getType().isSolid()) {
                                if (bl == null || (!bl.isJumpBlock(nbFloor.getLocation()) && !bl.isUpBlock(nbFloor.getLocation()))) {
                                    breadth++;
                                }
                            }
                        }
                    }

                    // Check if adjacent to at least one wall block (qualifies as tactical cover)
                    boolean hasAdjacentWall = world.getBlockAt(bx + 1, by + 1, bz).getType().isSolid()
                            || world.getBlockAt(bx - 1, by + 1, bz).getType().isSolid()
                            || world.getBlockAt(bx, by + 1, bz + 1).getType().isSolid()
                            || world.getBlockAt(bx, by + 1, bz - 1).getType().isSolid();

                    Location nodeLoc = new Location(world, bx + 0.5, by + 1.0, bz + 0.5);
                    NavNode node = new NavNode(nodeId++, nodeLoc, hasAdjacentWall, breadth);
                    generated.add(node);
                    mesh.allNodes.add(node);
                    if (hasAdjacentWall) {
                        mesh.coverNodes.add(node);
                    }
                    lastBy = by;
                }
            }
        }

        // 2. Connect neighboring nodes (distance <= step * 1.5, vertical delta <= 2.65 for stairs/ramps)
        double maxNeighborDistSq = (step * 1.5) * (step * 1.5);
        for (int i = 0; i < generated.size(); i++) {
            NavNode a = generated.get(i);
            for (int j = i + 1; j < generated.size(); j++) {
                NavNode b = generated.get(j);
                double dSq = a.distanceSquared(b.loc);
                if (dSq <= maxNeighborDistSq && Math.abs(a.loc.getY() - b.loc.getY()) <= 2.65) {
                    if (isWalkableConnection(world, a.loc, b.loc, bl)) {
                        a.neighbors.add(b);
                        b.neighbors.add(a);
                    }
                }
            }
        }

        return mesh;
    }

    private static boolean isWalkableConnection(World world, Location a, Location b, BlockListener bl) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.1) return true;
        int steps = (int) Math.ceil(dist / 0.8);
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            double x = a.getX() + dx * t;
            double y = a.getY() + dy * t;
            double z = a.getZ() + dz * t;
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);

            // Feet, body and head must not be solid
            if (world.getBlockAt(bx, by, bz).getType().isSolid() ||
                world.getBlockAt(bx, by + 1, bz).getType().isSolid()) {
                return false;
            }

            // Hazard check: do not connect across jump blocks or up blocks
            if (bl != null) {
                Location pt = new Location(world, bx + 0.5, by, bz + 0.5);
                Location below = new Location(world, bx + 0.5, by - 1, bz + 0.5);
                if (bl.isJumpBlock(pt) || bl.isJumpBlock(below) || bl.isUpBlock(pt) || bl.isUpBlock(below)) {
                    return false;
                }
            }
        }
        return true;
    }
}
