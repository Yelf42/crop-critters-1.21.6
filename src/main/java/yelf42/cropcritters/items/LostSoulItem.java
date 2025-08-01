package yelf42.cropcritters.items;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import yelf42.cropcritters.entity.ModEntities;

public class LostSoulItem extends Item {

    public LostSoulItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) return ActionResult.PASS;
        ServerWorld world = (ServerWorld) context.getWorld();
        BlockPos blockPos = context.getBlockPos();
        BlockState state = world.getBlockState(blockPos);
        ItemStack itemStack = context.getStack();
        if (!spawnCritter(world, blockPos, state)) return ActionResult.PASS;
        world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        PlayerEntity playerEntity = context.getPlayer();
        world.playSound(null, blockPos, SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.BLOCKS, 1F, 1F);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0F);
        if (playerEntity instanceof ServerPlayerEntity serverPlayerEntity) {
            Criteria.ITEM_USED_ON_BLOCK.trigger(serverPlayerEntity, blockPos, itemStack);
        }
        itemStack.decrement(1);
        return ActionResult.SUCCESS;
    }

    private boolean spawnCritter(ServerWorld world, BlockPos blockPos, BlockState state) {
        if (state.isOf(Blocks.PUMPKIN)) {
            ModEntities.PUMPKIN_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
        } else if (state.isOf(Blocks.MELON)) {
            ModEntities.MELON_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
        } else if (state.isOf(Blocks.COCOA) && state.get(CocoaBlock.AGE, 0) >= 2) {
            ModEntities.COCOA_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
        } else if (state.getBlock() instanceof PlantBlock) {
            if (state.isOf(Blocks.PITCHER_PLANT) || (state.isOf(Blocks.PITCHER_CROP) && state.get(PitcherCropBlock.AGE, 0) >= 4)) {
                if (state.get(PitcherCropBlock.HALF, DoubleBlockHalf.LOWER) == DoubleBlockHalf.UPPER) blockPos = blockPos.down();
                ModEntities.PITCHER_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
            } else if (state.isOf(Blocks.TORCHFLOWER)) {
                ModEntities.TORCHFLOWER_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
            } else if (state.isOf(Blocks.NETHER_WART)) {
                for (int i = 0; i <= world.random.nextInt(3); i++) {
                    ModEntities.NETHER_WART_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
                }
            } else if (state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMature(state)) {
                if (state.isOf(Blocks.WHEAT)) {
                    ModEntities.WHEAT_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
                } else if (state.isOf(Blocks.CARROTS)) {
                    ModEntities.CARROT_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
                } else if (state.isOf(Blocks.POTATOES)) {
                    if (world.random.nextInt(100) + 1 < world.getDifficulty().getId() * 2) {
                        ModEntities.POISONOUS_POTATO_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
                    } else {
                        ModEntities.POTATO_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
                    }
                } else if (state.isOf(Blocks.BEETROOTS)) {
                    ModEntities.BEETROOT_CRITTER.spawn(world, blockPos, SpawnReason.NATURAL);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }
}
