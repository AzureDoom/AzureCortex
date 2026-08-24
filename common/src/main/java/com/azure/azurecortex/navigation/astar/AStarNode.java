package com.azure.azurecortex.navigation.astar;

import net.minecraft.core.BlockPos;

import java.util.LinkedList;
import java.util.List;

/**
 * An immutable search node used internally by the open-set priority queue of the A*-style searches in this package.
 *
 * @param pos    the block position this node represents
 * @param g      accumulated movement cost from the start to this node
 * @param f      total estimated cost ({@code g} + heuristic to goal)
 * @param parent the node this was reached from, or {@code null} for the start
 */
public record AStarNode(
    BlockPos pos,
    double g,
    double f,
    AStarNode parent
) {

    /**
     * Reconstructs the path by walking parent pointers from {@code node} back to the start.
     *
     * @param node the terminal node of the found path
     * @return an ordered list of positions from start (inclusive) to this node (inclusive)
     */
    public static List<BlockPos> reconstruct(AStarNode node) {
        LinkedList<BlockPos> result = new LinkedList<>();

        var current = node;
        while (current != null) {
            result.addFirst(current.pos());
            current = current.parent();
        }

        return result;
    }
}
