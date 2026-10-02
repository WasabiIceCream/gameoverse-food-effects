package net.gameoverse.foodeffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Well Fed: 3 / 5 / 7 distinct food effects running at once give level I / II / III. Its duration is the time until
 * the count drops below that level's threshold, so the icon's timer is honest; a lower level takes over then (the
 * once-a-second refresh picks it up).
 */
final class WellFed {
    private static final int[] THRESHOLDS = {3, 5, 7};

    private WellFed() {}

    static void update(ServerPlayer player) {
        Map<Identifier, Long> tracked = player.getAttachedOrElse(FoodEffects.FOOD_EFFECTS_ACTIVE, Map.of());
        long now = player.level().getGameTime();
        List<Long> until = new ArrayList<>();
        for (var e : tracked.entrySet()) {
            if (e.getValue() <= now) continue;
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(e.getKey()).orElse(null);
            if (holder != null && player.hasEffect(holder)) until.add(e.getValue());
        }
        until.sort(Comparator.reverseOrder());
        int level = 0;
        while (level < THRESHOLDS.length && until.size() >= THRESHOLDS[level]) level++;
        MobEffectInstance current = player.getEffect(FoodEffects.WELL_FED);
        if (level == 0) {
            if (current != null) player.removeEffect(FoodEffects.WELL_FED);
            return;
        }
        int duration = (int) (until.get(THRESHOLDS[level - 1] - 1) - now);
        if (current != null && current.getAmplifier() == level - 1 && Math.abs(current.getDuration() - duration) <= 40) return;
        if (current != null) player.removeEffect(FoodEffects.WELL_FED);
        player.addEffect(new MobEffectInstance(FoodEffects.WELL_FED, duration, level - 1, false, false, true));
    }
}
