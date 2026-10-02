package net.gameoverse.foodeffects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;

/**
 * Appended after a food's status effects: records which of them the eater now has and until when, so Well Fed counts
 * effects that came from food (a Strength potion doesn't count). Reads the effects back off the player, so Kaleidoscope
 * Cookery's quality scaling and any longer effect already running are respected.
 */
public record TrackFoodEffects(List<Identifier> effects) implements ConsumeEffect {
    public static final MapCodec<TrackFoodEffects> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.listOf().fieldOf("effects").forGetter(TrackFoodEffects::effects)
    ).apply(i, TrackFoodEffects::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackFoodEffects> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), TrackFoodEffects::effects, TrackFoodEffects::new);
    public static final Type<TrackFoodEffects> TYPE = new Type<>(CODEC, STREAM_CODEC);

    @Override
    public Type<TrackFoodEffects> getType() {
        return TYPE;
    }

    @Override
    public boolean apply(Level level, ItemStack stack, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        long now = level.getGameTime();
        Map<Identifier, Long> active = new HashMap<>(player.getAttachedOrElse(FoodEffects.FOOD_EFFECTS_ACTIVE, Map.of()));
        active.values().removeIf(until -> until <= now);
        for (Identifier id : effects) {
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
            MobEffectInstance inst = holder == null ? null : player.getEffect(holder);
            if (inst != null && !inst.isInfiniteDuration()) active.merge(id, now + inst.getDuration(), Math::max);
        }
        player.setAttached(FoodEffects.FOOD_EFFECTS_ACTIVE, Map.copyOf(active));
        WellFed.update(player);
        return true;
    }
}
