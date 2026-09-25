package com.azure.azurecortex.navigation.crawl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
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
 * <h3>Squeezing through gaps narrower than the mob's real hitbox</h3> A mob's real
 * {@link net.minecraft.world.entity.EntityDimensions} should <strong>not</strong> be shrunk to let it fit through a
 * tight tunnel or vertical shaft — doing so desyncs the hitbox players actually fight/shoot from the one the pathfinder
 * validated the route against, and flips it on every crawl-state transition. Instead, override the mob's own
 * {@code travel(Vec3)} like so:
 *
 * <pre>{@code
 *
 * @Override
 * public void travel(@NotNull Vec3 movement) {
 *     if (CrawlController.shouldUseSlimMovement(this) && CrawlController.applySlimMovement(this)) {
 *         return;
 *     }
 *     super.travel(movement);
 * }
 * }</pre>
 *
 * {@link #shouldUseSlimMovement} and {@link #applySlimMovement} together validate and apply a movement step against the
 * same slim, crawl-sized box {@link CrawlTraversalEvaluator} already validates path nodes with — bypassing vanilla's
 * real-bounding-box collision resolution only for that step, rather than changing the mob's actual dimensions.
 * {@link #applySlimMovement} reads {@link Mob#getDeltaMovement()} for the step, not the {@code movement} parameter
 * {@code travel(Vec3)} itself receives (that parameter is the AI's raw steering input — forward/strafe/ jump — and
 * stays zero for a mob like this one whose actions drive velocity directly via {@link Mob#setDeltaMovement} rather than
 * vanilla's {@code MoveControl}). Passing that parameter through instead would make every slim-movement step a no-op:
 * {@code current + zero} never goes anywhere, so the mob "solves" its path (pathfinding debug particles trace the whole
 * route) but never actually walks it.
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
     * <p>
     * Immediately calls {@link Mob#refreshDimensions()} whenever this actually flips the flag. Mods commonly override
     * getDefaultDimensions to report a squeezed, wall-hugging profile while {@link CrawlCapability#isWallCrawling()} is
     * {@code true} (a wider/taller standing mob flattening against a surface), but {@code Entity#dimensions} is a
     * cached value that only recomputes when something calls {@code refreshDimensions()} — it does not notice on its
     * own that the flag it depends on just changed. A mob that lazily refreshes dimensions on some unrelated periodic
     * tick (e.g. alongside a slow growth-scale update) could otherwise keep colliding with its full standing-size
     * bounding box for several ticks after {@link CrawlCapability#isWallCrawling()} already reports {@code true} — long
     * enough to snag on a block that protrudes from an otherwise flat cliff face, since the pathfinder validated that
     * node against the smaller crawl-profile footprint the mob was supposed to have at that instant, not the stale
     * larger one it was actually still colliding with. Forcing the refresh right at the transition keeps the real
     * collision box and the profile pathfinding assumed in sync.
     */
    public static void setWallCrawling(Mob mob, boolean crawling) {
        if (mob instanceof CrawlCapability wallCrawler) {
            var changed = wallCrawler.isWallCrawling() != crawling;
            wallCrawler.setWallCrawling(crawling);
            if (changed) {
                mob.refreshDimensions();
            }
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
     * Returns {@code true} if {@code mob} is currently standing in a spot the pathfinder itself only agreed to route
     * through as a tight squeeze — a confined tunnel node ({@link CrawlTraversalEvaluator#tunnelCanStandAt}) or a
     * vertical shaft node ({@link CrawlTraversalEvaluator#verticalShaftCanCrawlAt}) — as opposed to ordinary open-air
     * wall/ceiling crawling.
     */
    public static boolean isInTightSqueeze(Mob mob) {
        var level = mob.level();
        var feet = BlockPos.containing(mob.getX(), mob.getBoundingBox().minY, mob.getZ());
        return CrawlTraversalEvaluator.tunnelCanStandAt(level, mob, feet)
            || CrawlTraversalEvaluator.verticalShaftCanCrawlAt(level, mob, feet);
    }

    /**
     * Returns {@code true} if {@code mob}'s movement this tick should be driven by {@link #applySlimMovement} instead
     * of vanilla's own bounding-box collision resolution — i.e. it's actively wall-crawling at all, whether that's a
     * tight tunnel/shaft squeeze or ordinary open-wall/ceiling climbing. Every crawling state needs this, not just a
     * tight squeeze: a mob's real, full-size hitbox doesn't slide cleanly along a wall or ceiling under vanilla's
     * upright-body collision assumptions either way.
     * <p>
     * Call this from the mob's {@code travel(Vec3)} override; see the class docs for the pattern.
     */
    public static boolean shouldUseSlimMovement(Mob mob) {
        return isWallCrawling(mob);
    }

    /**
     * Moves {@code mob} by its current {@link Mob#getDeltaMovement()} using the same slim, crawl-sized box the
     * pathfinder already validated the mob's route with ({@link CrawlTraversalEvaluator#canOccupySlim}), setting
     * position directly rather than going through vanilla's real-bounding-box collision resolution.
     * <p>
     * Deliberately reads {@link Mob#getDeltaMovement()} rather than taking a movement vector as a parameter: actions
     * like {@code CrawlToDestinationAction} drive a crawling mob by calling {@link Mob#setDeltaMovement} directly every
     * tick (see {@link NavigationQueries#computeWallCrawlVelocity}), not by steering vanilla's {@code MoveControl} — so
     * the vector {@code travel(Vec3)} itself receives is a separate, usually-zero AI input signal, not the mob's actual
     * intended step. Reading {@code getDeltaMovement()} here is what makes this consistent with what vanilla's own
     * {@code travel()} would have used internally.
     * <p>
     * Resolves the three axes independently — Y first, then X, then Z — rather than testing the full 3D displacement as
     * one atomic box check. A single all-or-nothing test rejects the whole step the instant any one axis is even
     * slightly obstructed (a tiny horizontal correction toward a shaft's centerline while the vertical component is
     * completely clear, say), which made climbing flaky: it only ever advanced on ticks where every axis happened to be
     * clear simultaneously, and otherwise just held position — indistinguishable from not climbing at all. Resolving
     * per axis is what vanilla's own collision does for exactly this reason, and lets partial progress (e.g. purely
     * vertical, with the horizontal component blocked) go through instead of being discarded along with it.
     * <p>
     * This exists so a mob's real {@link net.minecraft.world.entity.EntityDimensions} never has to shrink to fit
     * through a tight gap — the hitbox players see and fight stays full-size at all times, and only this specific
     * movement step is validated against a smaller, purely internal box. Call only when {@link #shouldUseSlimMovement}
     * is {@code true}; see the class docs' {@code travel(Vec3)} pattern.
     *
     * @return {@code true} if the step was handled (position/velocity were set directly and the caller should
     *         {@code return} from its {@code travel(Vec3)} override without calling {@code super.travel(...)}), or
     *         {@code false} if the mob's current position doesn't actually satisfy the slim box even before any axis is
     *         applied (shouldn't normally happen given the {@link #shouldUseSlimMovement} guard, but a stale/ edge-case
     *         call is possible) — in which case the caller should fall back to {@code super.travel(movement)} rather
     *         than silently freezing the mob mid-squeeze.
     */
    public static boolean applySlimMovement(Mob mob) {
        var level = mob.level();
        var movement = mob.getDeltaMovement();
        var current = mob.position();

        var full = current.add(movement);
        if (CrawlTraversalEvaluator.canOccupySlim(level, mob, full)) {
            mob.setPos(full.x, full.y, full.z);
            return true;
        }

        if (!CrawlTraversalEvaluator.canOccupySlim(level, mob, current)) {
            // Not even standing somewhere valid right now — bail out to vanilla rather than resolve axes from a
            // position the slim box itself doesn't agree with.
            return false;
        }

        var resultX = current.x;
        var resultY = current.y;
        var resultZ = current.z;
        var movedAny = false;

        if (Math.abs(movement.y) > 1.0E-6D) {
            var afterY = new Vec3(resultX, resultY + movement.y, resultZ);
            if (CrawlTraversalEvaluator.canOccupySlim(level, mob, afterY)) {
                resultY = afterY.y;
                movedAny = true;
            }
        }

        if (Math.abs(movement.x) > 1.0E-6D) {
            var afterX = new Vec3(resultX + movement.x, resultY, resultZ);
            if (CrawlTraversalEvaluator.canOccupySlim(level, mob, afterX)) {
                resultX = afterX.x;
                movedAny = true;
            }
        }

        if (Math.abs(movement.z) > 1.0E-6D) {
            var afterZ = new Vec3(resultX, resultY, resultZ + movement.z);
            if (CrawlTraversalEvaluator.canOccupySlim(level, mob, afterZ)) {
                resultZ = afterZ.z;
                movedAny = true;
            }
        }

        if (movedAny) {
            mob.setPos(resultX, resultY, resultZ);
            return true;
        }

        // Every axis blocked; still validly at the current spot (checked above) — hold position rather than let
        // vanilla shove the full-size box around, which would immediately eject the mob from a gap its real hitbox
        // was never meant to enter.
        mob.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /**
     * Updates gravity suppression and fall-distance zeroing each tick for wall-crawling mobs.
     * <p>
     * Gravity is suppressed while the mob is actively crawling or has grace ticks remaining and is adjacent to a
     * surface. Grace-tick decay itself is NOT handled here — that's {@link CrawlState#tick()}'s job, called once per
     * tick from the owning entity's own tick method, and {@link CrawlCapability#setWallCrawling} already refreshes the
     * counter to its peak every time a movement action calls it with {@code true} (which happens every tick during an
     * active climb). This method used to also reset/decrement the same counter independently — at a different peak
     * value (3, vs. 4 elsewhere) — so the two decayed it in lockstep whenever crawling stopped, exhausting the grace
     * window in about half the intended time. That silently starved every consumer of {@link #wasRecentlyWallCrawling}
     * (hitbox-dimension timing, action handoffs like {@code BreakToTargetAction}'s maintain-crawl logic) of the
     * tolerance window they were built to rely on.
     */
    public static void updateWallCrawlingPhysics(Mob mob) {
        if (!(mob instanceof CrawlCapability wallCrawler)) {
            mob.setNoGravity(false);
            return;
        }

        var isCrawling = wallCrawler.isWallCrawling();
        var touchingSurface = isAdjacentToClingSurface(mob);

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
        var box = effectiveBoundingBox(mob);

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

        var probedDirections = new Direction[] {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.UP
        };

        var detectionProbe = 0.25D;

        for (var direction : probedDirections) {
            var intoSurface = Vec3.atLowerCornerOf(direction.getNormal());
            var movedBox = box.move(intoSurface.scale(detectionProbe));

            if (!level.noBlockCollision(mob, movedBox)) {
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

    /**
     * The box wall-adherence detection ({@link #isAdjacentToClingSurface}, {@link #findSurfaceNormal},
     * {@link #distanceToSurface}) — and any other crawl-aware collision check outside this class — should probe
     * against: the same slim, crawl-sized box movement itself is validated against (see
     * {@link CrawlTraversalEvaluator#slimCrawlBox}) whenever {@code mob} was recently wall-crawling, or its real
     * bounding box otherwise.
     * <p>
     * Public because this isn't just an internal concern of this class: any action code that makes its own collision
     * decisions during a crawl (deciding whether a step-up direction is clear, whether a corner escape route is open,
     * etc.) needs the same substitution wherever it currently reads {@code mob.getBoundingBox()} for a shape-sensitive
     * check. These probes — and any caller's — used to get the slim box for free because the mob's real
     * {@link net.minecraft.world.entity.EntityDimensions} were shrunk while crawling; now that dimensions never change,
     * every such caller has to ask for the slim geometry explicitly instead of reading {@code mob.getBoundingBox()}
     * directly — otherwise "is there room to move here" gets checked against the mob's full standing size while it's
     * flattened against a surface only the slim profile fits against, which reports false blockage (or, depending on
     * geometry, false clearance) far more often than intended. A vertical "is there headroom to step up" probe is the
     * case this bites hardest: raising the real, tall box even one block up almost always finds a ceiling in a shaft
     * sized only for the slim profile, so a caller using the real box there will conclude every climb step is blocked.
     */
    public static AABB effectiveBoundingBox(Mob mob) {
        if (wasRecentlyWallCrawling(mob)) {
            return CrawlTraversalEvaluator.slimCrawlBox(mob, mob.position());
        }
        return mob.getBoundingBox();
    }

    /**
     * Returns {@code true} if the mob is currently touching a surface it can cling to — a side wall (checked at the
     * mob's current height and one block up, so it still grips a wall it's partway up) or a ceiling directly overhead.
     * <p>
     * The ceiling check matters just as much as the side-wall ones: a mob crawling across a flat ceiling away from any
     * side wall has nothing horizontal to grip, and without this check {@link #updateWallCrawlingPhysics} would find
     * {@code touchingSurface == false} and re-enable gravity, yanking the mob off the ceiling mid-crawl.
     */
    private static boolean isAdjacentToClingSurface(Mob mob) {
        var level = mob.level();
        var box = effectiveBoundingBox(mob);

        var probe = ((box.maxX - box.minX) / 2.0D) + 0.5D;

        if (!level.noBlockCollision(mob, box.move(probe, 0, 0)))
            return true;
        if (!level.noBlockCollision(mob, box.move(-probe, 0, 0)))
            return true;
        if (!level.noBlockCollision(mob, box.move(0, 0, probe)))
            return true;
        if (!level.noBlockCollision(mob, box.move(0, 0, -probe)))
            return true;

        var ceilingProbe = 0.1D;
        if (!level.noBlockCollision(mob, box.move(0.0D, ceilingProbe, 0.0D)))
            return true;

        var standingBox = box.move(0.0D, 1.0D, 0.0D);
        if (!level.noBlockCollision(mob, standingBox.move(probe, 0, 0)))
            return true;
        if (!level.noBlockCollision(mob, standingBox.move(-probe, 0, 0)))
            return true;
        if (!level.noBlockCollision(mob, standingBox.move(0, 0, probe)))
            return true;
        return !level.noBlockCollision(mob, standingBox.move(0, 0, -probe));
    }

    private static double distanceToSurface(Mob mob, Vec3 normal) {
        var level = mob.level();
        var box = effectiveBoundingBox(mob);

        for (var distance = 0.0D; distance <= 1.5D; distance += 0.05D) {
            var movedBox = box.move(normal.scale(distance));

            if (!level.noBlockCollision(mob, movedBox)) {
                return distance;
            }
        }

        return (box.maxY - box.minY) / 2.0D;
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
