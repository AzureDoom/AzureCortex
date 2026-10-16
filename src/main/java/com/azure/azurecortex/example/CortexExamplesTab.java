package com.azure.azurecortex.example;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/**
 * Client-only creative tab listing the example entities' spawn eggs, so they're easy to find instead of being appended
 * to the end of the vanilla Miscellaneous tab. Created from {@code ClientProxy#preInit}.
 */
@SideOnly(Side.CLIENT)
public final class CortexExamplesTab {

    public static final CreativeTabs TAB = new CreativeTabs("azurecortex.examples") {

        @Override
        @Nonnull
        public ItemStack getTabIconItem() {
            return eggFor(ExampleRegistry.entries().get(0));
        }

        @Override
        public void displayAllRelevantItems(@Nonnull NonNullList<ItemStack> items) {
            super.displayAllRelevantItems(items);
            for (EntityEntry entry : ExampleRegistry.entries()) {
                items.add(eggFor(entry));
            }
        }
    };

    private CortexExamplesTab() {}

    private static ItemStack eggFor(EntityEntry entry) {
        ItemStack egg = new ItemStack(Items.SPAWN_EGG);
        ItemMonsterPlacer.applyEntityIdToItemStack(egg, entry.getRegistryName());
        return egg;
    }

    /** Forces the tab's static initializer to run. */
    public static void init() {}
}
