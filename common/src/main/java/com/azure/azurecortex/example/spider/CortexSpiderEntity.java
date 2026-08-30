package com.azure.azurecortex.example.spider;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import com.azure.azurecortex.api.blackboard.CommonBlackboardKeys;
import com.azure.azurecortex.api.navigation.MovementCapability;
import com.azure.azurecortex.example.VanillaTargetPredicates;
import com.azure.azurecortex.goap.EmergencyDetector;
import com.azure.azurecortex.goap.GoalExecutor;
import com.azure.azurecortex.navigation.crawl.CrawlCapability;
import com.azure.azurecortex.navigation.crawl.CrawlController;
import com.azure.azurecortex.navigation.crawl.CrawlState;
import com.azure.azurecortex.runtime.CortexRuntime;
import com.azure.azurecortex.sensing.TargetSensor;

/**
 * An example {@code Spider} subclass wired up to AzureCortex, demonstrating the wall/ceiling-crawling movement model
 * ({@link CrawlCapability}, {@link CrawlController}) alongside the same GOAP goal selection and sensing layer shown by
 * {@code com.azure.azurecortex.example.zombie.CortexZombieEntity} and
 * {@code com.azure.azurecortex.example.skeleton.CortexSkeletonEntity}.
 * <p>
 * See {@link CortexSpiderGoal} for the goal types (identical in shape to the skeleton's — wall-crawling is a
 * navigation-layer capability, not a decision-layer one) and {@link CortexSpiderTree} for the behavior tree, in
 * particular {@code HUNT_TARGET}'s use of {@code CrawlToDestinationAction} instead of the ordinary
 * {@code MoveToDestinationAction}.
 * <p>
 * Extends vanilla {@code Spider} purely for its bounding box, attributes, and — critically — its client-side
 * {@code SpiderRenderer}/model/texture, which the client mod entrypoints reuse as-is (see the wiki installation notes);
 * {@link #registerGoals()} strips out every one of vanilla {@code Spider}'s own AI goals in favor of the AzureCortex
 * runtime below, exactly as the zombie/skeleton examples do to their respective vanilla base classes. Vanilla
 * {@code Spider}'s own {@code onClimbable()} override (which always reports {@code true}) is left as-is — it only
 * affects a few minor vanilla physics checks (e.g. slow sliding on contact with a wall) and doesn't conflict with this
 * entity's own {@link #travel(Vec3)} override, which fully replaces movement application while
 * {@link CrawlController#isWallCrawling} is {@code true} regardless of what {@code onClimbable()} reports.
 */
public class CortexSpiderEntity extends Spider implements CrawlCapability, MovementCapability {

    private static final EntityDataAccessor<Boolean> IS_CRAWLING = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.BOOLEAN
    );

    private static final EntityDataAccessor<Float> CRAWL_FWD_X = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    private static final EntityDataAccessor<Float> CRAWL_FWD_Y = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    private static final EntityDataAccessor<Float> CRAWL_FWD_Z = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    private static final EntityDataAccessor<Float> CRAWL_UP_X = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    private static final EntityDataAccessor<Float> CRAWL_UP_Y = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    private static final EntityDataAccessor<Float> CRAWL_UP_Z = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    private static final EntityDataAccessor<Float> CRAWL_DIST = SynchedEntityData.defineId(
        CortexSpiderEntity.class,
        EntityDataSerializers.FLOAT
    );

    /** Delegate holding the actual synced-data-backed {@link CrawlCapability} state. */
    private final CrawlState crawlState = new CrawlState(
        this,
        IS_CRAWLING,
        CRAWL_FWD_X,
        CRAWL_FWD_Y,
        CRAWL_FWD_Z,
        CRAWL_UP_X,
        CRAWL_UP_Y,
        CRAWL_UP_Z,
        CRAWL_DIST
    );

    private final CortexRuntime<CortexSpiderEntity, CortexSpiderGoal> runtime;

    private final CortexSpiderGoalPlanner goalPlanner = new CortexSpiderGoalPlanner();

    public CortexSpiderEntity(EntityType<? extends Spider> entityType, Level level) {
        super(entityType, level);

        var validity = VanillaTargetPredicates.onlyPlayersAtNight()
            .or(VanillaTargetPredicates.onlyGolemsAtNight());

        var targetSensor = new TargetSensor<CortexSpiderEntity>(
            TargetSensor.nearestMatching(20.0D, validity),
            10,
            TargetSensor.lineOfSight()
        );

        this.runtime = new CortexRuntime<>(this, targetSensor, CortexSpiderTree.create());

        this.runtime.addPeriodicHook("spider_sync_chase_destination", 5, (agent, blackboard) -> {
            var goalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
            var target = blackboard.get(CommonBlackboardKeys.TARGET);
            if (goalType == CortexSpiderGoal.HUNT_TARGET && target != null) {
                blackboard.set(CommonBlackboardKeys.DESTINATION, target.blockPosition());
            }
        });
    }

    /** Borrows vanilla {@link Spider}'s tuned attribute values — purely for convenience, no inheritance implied. */
    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_CRAWLING, false);
        builder.define(CRAWL_FWD_X, 0f);
        builder.define(CRAWL_FWD_Y, 0f);
        builder.define(CRAWL_FWD_Z, 1f);
        builder.define(CRAWL_UP_X, 0f);
        builder.define(CRAWL_UP_Y, 1f);
        builder.define(CRAWL_UP_Z, 0f);
        builder.define(CRAWL_DIST, 0f);
    }

    @Override
    public boolean isWallCrawling() {
        return crawlState.isWallCrawling();
    }

    @Override
    public void setWallCrawling(boolean crawling) {
        crawlState.setWallCrawling(crawling);
    }

    @Override
    public int getWallCrawlGraceTicks() {
        return crawlState.getWallCrawlGraceTicks();
    }

    @Override
    public void setWallCrawlGraceTicks(int ticks) {
        crawlState.setWallCrawlGraceTicks(ticks);
    }

    @Override
    public Vec3 getCrawlForward() {
        return crawlState.getCrawlForward();
    }

    @Override
    public Vec3 getOldCrawlForward() {
        return crawlState.getOldCrawlForward();
    }

    @Override
    public Vec3 getCrawlUp() {
        return crawlState.getCrawlUp();
    }

    @Override
    public Vec3 getOldCrawlUp() {
        return crawlState.getOldCrawlUp();
    }

    @Override
    public double getCrawlDistFromBlock() {
        return crawlState.getCrawlDistFromBlock();
    }

    @Override
    public double getOldCrawlDistFromBlock() {
        return crawlState.getOldCrawlDistFromBlock();
    }

    @Override
    public void setCrawlOrientation(Vec3 forward, Vec3 up, double distFromBlock) {
        crawlState.setCrawlOrientation(forward, up, distFromBlock);
    }

    @Override
    public boolean isPassableSolid(Level level, BlockPos pos, BlockState state) {
        return state.is(Blocks.COBWEB);
    }

    @Override
    public boolean isHazardFluid(Level level, BlockPos pos, FluidState fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /**
     * Routes movement application through {@link CrawlController} while actively wall-crawling, instead of vanilla's
     * ordinary gravity-driven {@code travel()} — the same pattern vanilla itself uses for flying mobs
     * ({@code FlyingMob#travel}), applied here to crawling instead of flight. {@code CrawlToDestinationAction} (see
     * {@link CortexSpiderTree}) is what actually sets {@link #getDeltaMovement()} each tick via
     * {@code NavigationQueries#computeWallCrawlVelocity} and toggles {@link #isWallCrawling()} on and off per waypoint;
     * this override is what makes that velocity actually move the entity along a wall or ceiling instead of being
     * immediately overwritten by vanilla's own ground-movement/gravity handling.
     */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (CrawlController.isWallCrawling(this)) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.6D));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick();

        crawlState.tick();
        CrawlController.updateWallCrawlingPhysics(this);

        if (!this.level().isClientSide() && this.isAlive() && !this.isNoAi()) {
            tickGoalPlanner();
            runtime.tick();
            CrawlController.updateCrawlOrientation(this, getDeltaMovement());
        }
    }

    private void tickGoalPlanner() {
        var blackboard = runtime.getBlackboard();
        var cooldowns = runtime.getCooldowns();
        var currentTick = (int) this.level().getGameTime();

        var activeGoalType = blackboard.get(CommonBlackboardKeys.ACTIVE_GOAL_TYPE);
        var isPassive = activeGoalType == null
            || activeGoalType == CortexSpiderGoal.NONE
            || activeGoalType == CortexSpiderGoal.WANDER;

        var reactiveReplan = isPassive && blackboard.has(CommonBlackboardKeys.TARGET);

        var preplanUrgency = EmergencyDetector.detectPreplanUrgency(
            this,
            EmergencyDetector.<CortexSpiderEntity>defaultProbes()
        );

        if (!reactiveReplan && preplanUrgency == null && cooldowns.isOnCooldown(CommonBlackboardKeys.GOAL_REPLAN))
            return;

        if (!reactiveReplan && !GoalExecutor.shouldReplan(blackboard, currentTick, preplanUrgency, this))
            return;

        cooldowns.set(CommonBlackboardKeys.GOAL_REPLAN, 20);

        var newGoal = goalPlanner.chooseGoal(this, blackboard, cooldowns);
        GoalExecutor.apply(this, blackboard, newGoal);
    }
}
