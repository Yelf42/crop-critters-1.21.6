package yelf42.cropcritters.entity;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;
import yelf42.cropcritters.CropCritters;
import yelf42.cropcritters.items.ModItems;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class AbstractCropCritterEntity extends TameableEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final TrackedData<Boolean> TRUSTING = DataTracker.registerData(AbstractCropCritterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);;
    private static final Predicate<Entity> NOTICEABLE_PLAYER_FILTER = (entity) -> !entity.isSneaky() && EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(entity);
    private static final Predicate<Entity> FARM_ANIMALS_FILTER = (entity -> entity.getType().isIn(CropCritters.SCARE_CRITTERS));

    private static final Predicate<BlockState> TARGET_BLOCK_FILTER = (blockState -> blockState.isOf(Blocks.COARSE_DIRT));
    private static final int TARGET_OFFSET = 1; // For targeting the top of a solid block
    private static final Block TARGET_BLOCK_CHANGED = Blocks.DIRT;
    private static final Item HEALING_ITEM = Items.WHEAT;

    @Nullable
    BlockPos targetPos;
    TargetWorkGoal targetWorkGoal;

    int ticksUntilCanWork = 20 * 10;


    public AbstractCropCritterEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.setPathfindingPenalty(PathNodeType.DANGER_OTHER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_OTHER, 0.0F);
    }

    public void setTrusting(boolean trusting) {
        this.dataTracker.set(TRUSTING, trusting);
    }
    public boolean isTrusting() {
        return (Boolean)this.dataTracker.get(TRUSTING);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putBoolean("Trusting", this.isTrusting());
        view.putInt("TicksUntilCanWork", this.ticksUntilCanWork);
        view.putNullable("target_pos", BlockPos.CODEC, this.targetPos);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.setTrusting(view.getBoolean("Trusting", false));
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(DefaultAnimations.genericWalkIdleController());
    }

    protected void initGoals() {
        net.minecraft.entity.ai.goal.TemptGoal temptGoal = new TemptGoal(this, 0.6, (stack) -> stack.isOf(ModItems.LOST_SOUL), true);
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, temptGoal);
        this.goalSelector.add(3, new FleeEntityGoal<>(this, AnimalEntity.class, 10.0F, 1.6, 1.4, FARM_ANIMALS_FILTER::test));
        this.goalSelector.add(3, new FleeEntityGoal<>(this, PlayerEntity.class, 10.0F, 1.6, 1.4, (entity) -> NOTICEABLE_PLAYER_FILTER.test(entity) && !this.isTrusting()));
        this.targetWorkGoal = new TargetWorkGoal();
        this.goalSelector.add(4, this.targetWorkGoal);
        this.goalSelector.add(11, new WanderAroundGoal(this, 0.8));
        this.goalSelector.add(16, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(16, new LookAroundGoal(this));
    }

    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TRUSTING, false);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 18)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.ATTACK_DAMAGE, 1)
                .add(EntityAttributes.FOLLOW_RANGE, 20)
                .add(EntityAttributes.TEMPT_RANGE, 10);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        MobNavigation mobNavigation = new MobNavigation(this, world);
        mobNavigation.setCanSwim(true);
        return mobNavigation;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Override
    protected void mobTick(ServerWorld world) {
        for(PrioritizedGoal prioritizedGoal : this.goalSelector.getGoals()) {
            if (prioritizedGoal.isRunning()) {
                CropCritters.LOGGER.info(prioritizedGoal.getGoal().getClass().toString());
            }
        }
        //CropCritters.LOGGER.info(String.valueOf(this.navigation.isIdle()));
        super.mobTick(world);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (this.isInvulnerableTo(world, source)) {
            return false;
        } else {
            this.targetWorkGoal.cancel();
            return super.damage(world, source, amount);
        }
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.ticksUntilCanWork > 0) {
            --this.ticksUntilCanWork;
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (!this.getWorld().isClient()) {
            if (itemStack.isOf(ModItems.LOST_SOUL) && !this.isTrusting()) {
                this.eat(player, hand, itemStack);
                this.tryTame();
                this.setPersistent();
            } else if (itemStack.isOf(HEALING_ITEM) && (this.getHealth() < this.getMaxHealth())) {
                this.eat(player, hand, itemStack);
                this.heal(1.f);
                this.getWorld().sendEntityStatus(this, (byte)7);
            }
            return ActionResult.SUCCESS;
        }

        ActionResult actionResult = super.interactMob(player, hand);
        if (actionResult.isAccepted()) {
            this.setPersistent();
        }

        return actionResult;
    }

    private void tryTame() {
        if (this.random.nextInt(3) == 0) {
            this.setTrusting(true);
            this.getWorld().sendEntityStatus(this, (byte)7);
        } else {
            this.getWorld().sendEntityStatus(this, (byte)6);
        }

    }

    // Override for longer/shorter delays between jobs
    private void resetTicksUntilCanWork() {
        this.ticksUntilCanWork = MathHelper.nextInt(this.random, 100, 200);
    }

    void clearTargetPos() {
        this.targetPos = null;
        resetTicksUntilCanWork();
    }

    public static boolean isAttractive(@NotNull BlockState state) {
        return TARGET_BLOCK_FILTER.test(state);
    }

    // Override for more complex objectives
    public void completeTargetGoal() {
        if (this.targetPos == null) return;
        this.playSound(SoundEvents.ITEM_HOE_TILL, 1.0F, 1.0F);
        this.getWorld().setBlockState(this.targetPos, AbstractCropCritterEntity.TARGET_BLOCK_CHANGED.getDefaultState(), Block.NOTIFY_ALL_AND_REDRAW);
        ((ServerWorld)this.getWorld()).spawnParticles(ParticleTypes.DUST_PLUME, this.targetPos.getX() + 0.5, this.targetPos.getY() + 1.0, this.targetPos.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0.0);
    }


    class TargetWorkGoal extends Goal {
        private Long2LongOpenHashMap unreachableTargetsPosCache = new Long2LongOpenHashMap();
        private boolean running;
        private int ticks;
        private Vec3d nextTarget;

        TargetWorkGoal() {
            this.setControls(EnumSet.of(Control.MOVE));
        }

        public void start() {
            this.running = true;
            this.ticks = 0;
        }

        public void stop() {
            this.running = false;
            AbstractCropCritterEntity.this.navigation.stop();
            AbstractCropCritterEntity.this.resetTicksUntilCanWork();
        }

        @Override
        public boolean canStart() {
            if (AbstractCropCritterEntity.this.ticksUntilCanWork > 0) return false;
            if (!AbstractCropCritterEntity.this.isTrusting()) return false;
            Optional<BlockPos> optional = this.getTargetBlock();
            if (optional.isPresent()) {
                AbstractCropCritterEntity.this.targetPos = (BlockPos)optional.get();
                return true;
            } else {
                AbstractCropCritterEntity.this.ticksUntilCanWork = 80;
                return false;
            }
        }

        @Override
        public boolean shouldContinue() {
            return this.running && (AbstractCropCritterEntity.this.targetPos != null);
        }

        public void tick() {
            if (AbstractCropCritterEntity.this.targetPos != null) {
                ++this.ticks;
                if (this.ticks > 600 || !(AbstractCropCritterEntity.isAttractive(AbstractCropCritterEntity.this.getWorld().getBlockState(AbstractCropCritterEntity.this.targetPos)))) {
                    AbstractCropCritterEntity.this.clearTargetPos();
                } else {
                    Vec3d vec3d = Vec3d.ofBottomCenter(AbstractCropCritterEntity.this.targetPos).add((double)0.0F, (double)TARGET_OFFSET, (double)0.0F);
                    if (vec3d.squaredDistanceTo(AbstractCropCritterEntity.this.getPos()) > (double)1.0F) {
                        this.nextTarget = vec3d;
                        this.moveToNextTarget();
                    } else {
                        if (this.nextTarget == null) {
                            this.nextTarget = vec3d;
                        }

                        boolean bl = AbstractCropCritterEntity.this.getPos().distanceTo(this.nextTarget) <= 0.5;
                        if (!bl && this.ticks > 600) {
                            AbstractCropCritterEntity.this.clearTargetPos();
                        } else if (bl) {
                            // At target pos
                            AbstractCropCritterEntity.this.completeTargetGoal();
                            AbstractCropCritterEntity.this.clearTargetPos();
                        } else {
                            AbstractCropCritterEntity.this.getMoveControl().moveTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), (double)1.2F);
                        }
                    }
                }
            }
        }

        private void moveToNextTarget() {
            AbstractCropCritterEntity.this.navigation.startMovingAlong(AbstractCropCritterEntity.this.navigation.findPathTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), 0), (double)1.2F);
        }

        void cancel() {
            this.running = false;
        }

        private Optional<BlockPos> getTargetBlock() {
            Iterable<BlockPos> iterable = BlockPos.iterateOutwards(AbstractCropCritterEntity.this.getBlockPos(), 6, 3, 6);
            Long2LongOpenHashMap long2LongOpenHashMap = new Long2LongOpenHashMap();

            for(BlockPos blockPos : iterable) {
                long l = this.unreachableTargetsPosCache.getOrDefault(blockPos.asLong(), Long.MIN_VALUE);
                if (AbstractCropCritterEntity.this.getWorld().getTime() < l) {
                    long2LongOpenHashMap.put(blockPos.asLong(), l);
                } else if (AbstractCropCritterEntity.isAttractive(AbstractCropCritterEntity.this.getWorld().getBlockState(blockPos)) && AbstractCropCritterEntity.this.getWorld().getBlockState(blockPos.up()).isAir()) {
                    Path path = AbstractCropCritterEntity.this.navigation.findPathTo(blockPos, 0);
                    if (path != null && path.reachesTarget()) {
                        return Optional.of(blockPos);
                    }

                    long2LongOpenHashMap.put(blockPos.asLong(), AbstractCropCritterEntity.this.getWorld().getTime() + 600L);
                }
            }

            this.unreachableTargetsPosCache = long2LongOpenHashMap;
            return Optional.empty();
        }
    }

    static class TemptGoal extends net.minecraft.entity.ai.goal.TemptGoal {
        @Nullable
        private PlayerEntity player;
        private final AbstractCropCritterEntity critter;

        public TemptGoal(AbstractCropCritterEntity critter, double speed, Predicate<ItemStack> foodPredicate, boolean canBeScared) {
            super(critter, speed, foodPredicate, canBeScared);
            this.critter = critter;
        }

        public void tick() {
            super.tick();
            if (this.player == null && this.mob.getRandom().nextInt(this.getTickCount(600)) == 0) {
                this.player = this.closestPlayer;
            } else if (this.mob.getRandom().nextInt(this.getTickCount(500)) == 0) {
                this.player = null;
            }

        }

        protected boolean canBeScared() {
            return (this.player == null || !this.player.equals(this.closestPlayer)) && super.canBeScared();
        }

        public boolean canStart() {
            return super.canStart() && !this.critter.isTrusting();
        }
    }
}
