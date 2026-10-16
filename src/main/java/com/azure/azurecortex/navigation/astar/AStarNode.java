package com.azure.azurecortex.navigation.astar;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedList;
import java.util.List;

/**
 * An immutable search node used internally by the open-set priority queue of the A*-style searches in this package.
 */
public final class AStarNode {

    private final BlockPos pos;

    private final double g;

    private final double f;

    private final AStarNode parent;

    /**
     * @param pos    the block position this node represents
     * @param g      accumulated movement cost from the start to this node
     * @param f      total estimated cost ({@code g} + heuristic to goal)
     * @param parent the node this was reached from, or {@code null} for the start
     */
    public AStarNode(BlockPos pos, double g, double f, AStarNode parent) {
        this.pos = pos;
        this.g = g;
        this.f = f;
        this.parent = parent;
    }

    public BlockPos pos() {
        return pos;
    }

    public double g() {
        return g;
    }

    public double f() {
        return f;
    }

    public AStarNode parent() {
        return parent;
    }

    /**
     * Reconstructs the path by walking parent pointers from {@code node} back to the start.
     *
     * @param node the terminal node of the found path
     * @return an ordered list of positions from start (inclusive) to this node (inclusive)
     */
    public static List<BlockPos> reconstruct(AStarNode node) {
        LinkedList<BlockPos> result = new LinkedList<>();

        AStarNode current = node;
        while (current != null) {
            result.addFirst(current.pos());
            current = current.parent();
        }

        return result;
    }

    @Override
    public String toString() {
        return "AStarNode[pos=" + pos + ", g=" + g + ", f=" + f + "]";
    }
}
