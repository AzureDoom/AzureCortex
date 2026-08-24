package com.azure.azurecortex.navigation.crawl;

import net.minecraft.world.phys.Vec3;

/**
 * Mixin-style interface implemented by mobs capable of crawling along walls, ceilings, and floors.
 * <p>
 * Physics and orientation are managed by {@link CrawlController}; this interface is the data contract a mod injects
 * into its own entity class (typically via a mixin onto its entity base class, mirroring how the historical
 * {@code WallCrawlingMob} interface was applied in Ovomorphosis). Prefix any implementing accessor/mutator fields
 * however suits your mod's mixin conventions — the method names here are the actual contract.
 */
@SuppressWarnings("unused")
public interface CrawlCapability {

    /**
     * Returns {@code true} if the mob is currently in an active wall-crawling state.
     */
    boolean isWallCrawling();

    /**
     * Sets whether the mob is actively crawling on a surface.
     *
     * @param crawling {@code true} to enable crawling, {@code false} to disable
     */
    void setWallCrawling(boolean crawling);

    /**
     * Returns the number of grace ticks remaining after crawling stopped, during which gravity is still suppressed.
     */
    int getWallCrawlGraceTicks();

    /**
     * Sets the remaining grace tick count.
     *
     * @param ticks the new grace tick value
     */
    void setWallCrawlGraceTicks(int ticks);

    /**
     * Returns the mob's current crawl-forward direction (the direction it is moving along the surface).
     */
    Vec3 getCrawlForward();

    /**
     * Returns the crawl-forward direction from the previous tick, used for smooth interpolation.
     */
    Vec3 getOldCrawlForward();

    /**
     * Returns the surface normal the mob is currently clinging to (the "up" direction relative to the surface).
     */
    Vec3 getCrawlUp();

    /**
     * Returns the surface normal from the previous tick, used for smooth interpolation.
     */
    Vec3 getOldCrawlUp();

    /**
     * Returns the mob's current distance from the surface it is clinging to, in blocks.
     */
    double getCrawlDistFromBlock();

    /**
     * Returns the distance from the surface recorded on the previous tick, in blocks.
     */
    double getOldCrawlDistFromBlock();

    /**
     * Updates all three orientation values atomically.
     *
     * @param forward       the new forward direction along the surface
     * @param up            the new surface normal
     * @param distFromBlock the new distance from the surface in blocks
     */
    void setCrawlOrientation(Vec3 forward, Vec3 up, double distFromBlock);
}
