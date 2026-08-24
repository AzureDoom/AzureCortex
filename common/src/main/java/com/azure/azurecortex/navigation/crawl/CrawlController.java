package com.azure.azurecortex.navigation.crawl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.azure.azurecortex.api.navigation.NavigationHandler;
import com.azure.azurecortex.navigation.movement.NavigationQueries;
import com.azure.azurecortex.navigation.traversal.CollisionQueries;

/**
 * Drives wall-crawling physics, orientation, and approach-decision logic for mobs that implement
 * {@link CrawlCapability}.
 * <p>
 * Call {@link #updateWallCrawlingPhysics} and {@link #updateCrawlOrientation} each tick from the mob's tick method to
 * keep gravity suppression and surface alignment current. Implements {@link NavigationHandler} so it can be used
 * anywhere the generic navigation API is expected; ground-only mobs should use
 * {@code com.azure.azurecortex.navigation.movement.MovementController} instead.
 */
@SuppressWarnings("unused")
public final class CrawlController implements NavigationHandler {

    /** Shared stateless instance. */
    public static final CrawlController INSTANCE = new CrawlController();

    @Override
    public Vec3 computeMovement(Mob mob, Vec3 desiredMovement) {
        return com.azure.azurecortex.navigation.movement.MovementController.INSTANCE.computeMovement(
            mob,
            desiredMovement
        );
    }

    @Override
    public void tick(Mob mob, Vec3 movement) {
        updateWallCrawlingPhysics(mob);
        updateCrawlOrientation(mob, movement);
    }

    /**
     * Returns {@code true} if {@code mob} implements {@link CrawlCapability} and is in a state where wall-crawling is
     * permitted (not in water, not a vehicle).
     */
    public static boolean canWallCrawl(Mob mob) {
        return mob instanceof CrawlCapability && !mob.isInWater() && !mob.isVehicle();
    }

    /**
     * Sets the wall-crawling flag on {@code mob} if it implements {@link CrawlCapability}. Does nothing otherwise.
     */
    public static void setWallCrawling(Mob mob, boolean crawling) {
        if (mob instanceof CrawlCapability wallCrawler) {
            wallCrawler.setWallCrawling(crawling);
        }
    }

    /**
     * Returns true if the mob was wall-crawling recently — either it is currently crawling, or it still has grace ticks
     * remaining from a recent crawl. Use this in actions that take over from a crawl-driven approach action so they can
     * inherit the crawling state even after the previous action cleared the flag.
     */
    public static boolean wasRecentlyWallCrawling(Mob mob) {
        if (!(mob instanceof CrawlCapability wallCrawler))
            return false;
        return wallCrawler.isWallCrawling() || wallCrawler.getWallCrawlGraceTicks() > 0;
    }

    /**
     * Returns {@code true} if {@code mob} is currently in an active wall-crawling state.
     */
    public static boolean isWallCrawling(Mob mob) {
        return mob instanceof CrawlCapability wallCrawler && wallCrawler.isWallCrawling();
    }

    /**
     * Updates gravity suppression and fall-distance zeroing each tick for wall-crawling mobs.
     * <p>
     * Gravity is suppressed while the mob is actively crawling or has grace ticks remaining and is adjacent to a
     * surface. Grace ticks are decremented here.
     */
    public static void updateWallCrawlingPhysics(Mob mob) {
        if (!(mob instanceof CrawlCapability wallCrawler)) {
            mob.setNoGravity(false);
            return;
        }

        if (wallCrawler.isWallCrawling()) {
            wallCrawler.setWallCrawlGraceTicks(3);
        } else if (wallCrawler.getWallCrawlGraceTicks() > 0) {
            wallCrawler.setWallCrawlGraceTicks(wallCrawler.getWallCrawlGraceTicks() - 1);
        }

        var isCrawling = wallCrawler.isWallCrawling();
        var touchingSurface = isAdjacentToSideWall(mob);

        var inTunnel = CrawlTraversalEvaluator.tunnelCanStandAt(
            mob.level(),
            mob,
            BlockPos.containing(mob.getX(), mob.getBoundingBox().minY, mob.getZ())
        );

        var active = isCrawling && (touchingSurface || inTunnel);

        mob.setNoGravity(active);

        if (active) {
            mob.fallDistance = 0.0F;
        }
    }

    /**
     * Recomputes and stores the mob's crawl orientation (forward and up vectors) based on its current movement and the
     * nearest surface normal.
     * <p>
     * Should be called after applying movement each tick so that the renderer can smoothly interpolate the mob's
     * rotation.
     */
    public static void updateCrawlOrientation(Mob mob, Vec3 movement) {
        if (!(mob instanceof CrawlCapability wallCrawler)) {
            return;
        }

        var up = findSurfaceNormal(mob);

        var forward = new Vec3(movement.x, movement.y, movement.z);

        if (forward.lengthSqr() < 0.0001D) {
            forward = wallCrawler.getCrawlForward();
        }

        forward = forward.subtract(up.scale(forward.dot(up)));

        if (forward.lengthSqr() < 0.0001D) {
            forward = new Vec3(0.0D, 1.0D, 0.0D).subtract(up.scale(up.y));
        }

        if (forward.lengthSqr() < 0.0001D) {
            forward = wallCrawler.getCrawlForward();
        }

        wallCrawler.setCrawlOrientation(
            forward.normalize(),
            up.normalize(),
            distanceToSurface(mob, up.scale(-1.0D))
        );
    }

    private static Vec3 findSurfaceNormal(Mob mob) {
        var level = mob.level();
        var box = mob.getBoundingBox();

        Vec3 bestSurfaceUp = null;
        var bestDistance = Double.MAX_VALUE;

        Vec3 currentUp = null;
        double hysteresisBonus = 0.0D;
        if (mob instanceof CrawlCapability wc) {
            var up = wc.getCrawlUp();
            if (up != null && up.lengthSqr() > 0.0001D) {
                currentUp = up;
                hysteresisBonus = 0.8D;
            }
        }

        var horizontalDirections = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

        var detectionProbe = 0.25D;

        for (var direction : horizontalDirections) {
            var intoSurface = Vec3.atLowerCornerOf(direction.getNormal());
            var movedBox = box.move(intoSurface.scale(detectionProbe));

            if (!level.noCollision(mob, movedBox)) {
                var distance = distanceToSurface(mob, intoSurface);
                var candidateUp = intoSurface.scale(-1.0D);

                var effectiveDistance = distance;
                if (currentUp != null && candidateUp.dot(currentUp) < 0.5D) {
                    effectiveDistance += hysteresisBonus;
                }

                if (effectiveDistance < bestDistance) {
                    bestDistance = effectiveDistance;
                    bestSurfaceUp = candidateUp;
                }
            }
        }

        if (bestSurfaceUp != null) {
            return bestSurfaceUp;
        }

        if (mob instanceof CrawlCapability wc) {
            var lastUp = wc.getCrawlUp();

            if (lastUp != null && lastUp.lengthSqr() > 0.0001D && Math.abs(lastUp.y) < 0.5D) {
                return lastUp;
            }
        }

        return new Vec3(0.0D, 1.0D, 0.0D);
    }

    private static boolean isAdjacentToSideWall(Mob mob) {
        var level = mob.level();
        var box = mob.getBoundingBox();

        var probe = (mob.getBbWidth() / 2.0D) + 0.5D;

        if (!level.noCollision(mob, box.move(probe, 0, 0)))
            return true;
        if (!level.noCollision(mob, box.move(-probe, 0, 0)))
            return true;
        if (!level.noCollision(mob, box.move(0, 0, probe)))
            return true;
        if (!level.noCollision(mob, box.move(0, 0, -probe)))
            return true;

        var standingBox = box.move(0.0D, 1.0D, 0.0D);
        if (!level.noCollision(mob, standingBox.move(probe, 0, 0)))
            return true;
        if (!level.noCollision(mob, standingBox.move(-probe, 0, 0)))
            return true;
        if (!level.noCollision(mob, standingBox.move(0, 0, probe)))
            return true;
        return !level.noCollision(mob, standingBox.move(0, 0, -probe));
    }

    private static double distanceToSurface(Mob mob, Vec3 normal) {
        var level = mob.level();
        var box = mob.getBoundingBox();

        for (var distance = 0.0D; distance <= 1.5D; distance += 0.05D) {
            var movedBox = box.move(normal.scale(distance));

            if (!level.noCollision(mob, movedBox)) {
                return distance;
            }
        }

        return mob.getBbHeight() / 2.0D;
    }

    /**
     * Returns {@code true} if the mob should use wall-crawl movement to close in on {@code target} specifically for
     * combat purposes.
     * <p>
     * Activates when the vertical gap is three or more blocks and either the horizontal distance is within eight blocks
     * or a wall-crawl is otherwise required.
     */
    public static boolean shouldUseWallCrawlingToTarget(Mob mob, LivingEntity target) {
        if (!canWallCrawl(mob) || target == null || !target.isAlive()) {
            return false;
        }

        if (needsWallCrawl(mob, target.position())) {
            return true;
        }

        var yDiff = target.blockPosition().getY() - mob.blockPosition().getY();
        var absYDiff = Math.abs(yDiff);

        if (absYDiff >= 1 && absYDiff <= 2) {
            var horizontalDistSqr = mob.position()
                .multiply(1.0D, 0.0D, 1.0D)
                .distanceToSqr(target.position().multiply(1.0D, 0.0D, 1.0D));

            if (horizontalDistSqr <= 8.0D * 8.0D) {
                return isWallBlockedBetween(mob, target);
            }
        }

        if (absYDiff > 2) {
            var horizontalDistSqr = mob.position()
                .multiply(1.0D, 0.0D, 1.0D)
                .distanceToSqr(target.position().multiply(1.0D, 0.0D, 1.0D));

            if (horizontalDistSqr <= 8.0D * 8.0D) {
                if (yDiff < 0) {
                    if (CollisionQueries.isClimbable(mob.level(), mob.blockPosition(), true) || isWallCrawling(mob)) {
                        return true;
                    }
                    var level = mob.level();
                    var origin = mob.blockPosition();
                    for (var dir : Direction.Plane.HORIZONTAL) {
                        var adj = origin.relative(dir);
                        if (CollisionQueries.isSafeClimbNode(level, adj, mob)) {
                            return true;
                        }
                    }
                    return false;
                }
                return true;
            }
        }

        if (absYDiff == 0) {
            return isWallBlockedBetween(mob, target);
        }

        return CollisionQueries.isClimbable(mob.level(), target.blockPosition(), false);
    }

    /**
     * Returns {@code true} if reaching {@code wanted} requires wall-crawl movement given the mob's current position and
     * the surrounding terrain.
     */
    public static boolean needsWallCrawl(Mob mob, Vec3 wanted) {
        return NavigationQueries.needsWallCrawl(mob, wanted);
    }

    /**
     * Returns {@code true} if there is a solid block between {@code mob} and {@code target} in the horizontal direction
     * — i.e. the mob cannot reach the target by walking and needs to climb over or around a wall.
     */
    private static boolean isWallBlockedBetween(Mob mob, LivingEntity target) {
        var level = mob.level();
        var from = mob.position();
        var to = target.position();

        var horizontal = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        var dist = horizontal.length();

        if (dist < 0.5D) {
            return false;
        }

        var dir = horizontal.normalize();
        var feetY = mob.getBoundingBox().minY;

        for (var d = 0.5D; d <= Math.min(dist, 4.0D); d += 0.5D) {
            var sample = from.add(dir.scale(d));
            var feet = BlockPos.containing(sample.x, feetY, sample.z);
            var head = feet.above();

            var feetState = level.getBlockState(feet);
            var headState = level.getBlockState(head);

            var feetShape = feetState.getCollisionShape(level, feet);
            var headShape = headState.getCollisionShape(level, head);

            if (feetShape.isEmpty() || headShape.isEmpty()) {
                continue;
            }

            var feetMaxY = feetShape.max(Direction.Axis.Y);
            var headMaxY = headShape.max(Direction.Axis.Y);

            if (feetMaxY <= 0.5D || headMaxY <= 0.5D) {
                continue;
            }

            var feetMaxX = feetShape.max(Direction.Axis.X);
            var feetMinX = feetShape.min(Direction.Axis.X);
            var feetWidth = feetMaxX - feetMinX;

            if (feetWidth < 0.9D) {
                continue;
            }

            return true;
        }

        return false;
    }
}
