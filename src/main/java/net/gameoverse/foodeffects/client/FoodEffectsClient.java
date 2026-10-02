package net.gameoverse.foodeffects.client;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.gameoverse.foodeffects.FoodEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * One tooltip line per effect a food gives, "Strength (0:20)" in the effect's colour, for every food the table
 * covers. Foods whose own mod already lists its effects (Farmer's Delight and its addons) keep that list: a line is
 * only added when no existing line already names the effect.
 */
public final class FoodEffectsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!FoodEffects.APPLIED.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return;
            Consumable consumable = stack.get(DataComponents.CONSUMABLE);
            if (consumable == null) return;
            int at = Math.min(1, lines.size());
            for (var ce : consumable.onConsumeEffects()) {
                if (!(ce instanceof ApplyStatusEffectsConsumeEffect apply)) continue;
                for (MobEffectInstance inst : apply.effects()) {
                    String name = Component.translatable(inst.getDescriptionId()).getString();
                    if (alreadyListed(lines, name)) continue;
                    MutableComponent line = Component.translatable(inst.getDescriptionId());
                    if (inst.getAmplifier() > 0) {
                        line = Component.translatable("potion.withAmplifier", line,
                                Component.translatable("potion.potency." + inst.getAmplifier()));
                    }
                    line = Component.translatable("potion.withDuration", line,
                            MobEffectUtil.formatDuration(inst, 1.0F, context.tickRate()));
                    if (apply.probability() < 1F) {
                        line = Component.translatable("tooltip.gameoverse_food_effects.chance", line,
                                Math.round(apply.probability() * 100));
                    }
                    lines.add(at++, line.withStyle(inst.getEffect().value().getCategory().getTooltipFormatting()));
                }
            }
        });
    }

    private static boolean alreadyListed(List<Component> lines, String name) {
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).getString().contains(name)) return true;
        }
        return false;
    }
}
