package net.gameoverse.foodeffects;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FoodEffects implements ModInitializer {
    public static final String MOD_ID = "gameoverse_food_effects";
    static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final AttachmentType<Map<Identifier, Long>> FOOD_EFFECTS_ACTIVE = AttachmentRegistry.create(
            id("food_effects_active"), b -> b.persistent(Codec.unboundedMap(Identifier.CODEC, Codec.LONG)));

    public static Holder<MobEffect> WELL_FED;
    /** Item id -> the status effects it ends up with; read by the client tooltip too. */
    public static final Map<Identifier, List<MobEffectInstance>> APPLIED = new LinkedHashMap<>();

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id(name), effect);
    }

    @Override
    public void onInitialize() {
        register("steadfast", new LazyAttributeEffect(MobEffectCategory.BENEFICIAL, 0x8B6A3E)
                .with("minecraft:knockback_resistance", "steadfast", 0.2, Operation.ADD_VALUE));
        register("fresh", new LazyAttributeEffect(MobEffectCategory.BENEFICIAL, 0x6CC24A)
                .with("apothic_attributes:healing_received", "fresh", 0.10, Operation.ADD_VALUE));
        register("keen", new LazyAttributeEffect(MobEffectCategory.BENEFICIAL, 0xC2304A)
                .with("apothic_attributes:crit_chance", "keen", 0.05, Operation.ADD_VALUE));
        register("focused", new LazyAttributeEffect(MobEffectCategory.BENEFICIAL, 0x7A5CD6)
                .with("spell_power:generic", "focused", 0.05, Operation.ADD_MULTIPLIED_BASE));
        register("mana_flow", new LazyAttributeEffect(MobEffectCategory.BENEFICIAL, 0x3A8DDE)
                .with("manaattributes:mana_regeneration", "mana_flow", 1.5, Operation.ADD_VALUE));
        WELL_FED = register("well_fed", new LazyAttributeEffect(MobEffectCategory.BENEFICIAL, 0xE8A33C)
                .with("apothic_attributes:healing_received", "well_fed", 0.10, Operation.ADD_VALUE)
                .with("minecraft:armor", "well_fed_armor", 1.0, Operation.ADD_VALUE));
        Registry.register(BuiltInRegistries.CONSUME_EFFECT_TYPE, id("track_food_effects"), TrackFoodEffects.TYPE);

        Map<Identifier, List<FoodTable.Entry>> table = FoodTable.load();
        // After Farmer's Delight, which adds its vanilla-soup effects in the default phase: the table already includes them.
        Identifier late = id("after_mods");
        DefaultItemComponentEvents.MODIFY.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        DefaultItemComponentEvents.MODIFY.register(late, context -> {
            int applied = 0;
            Set<Identifier> missing = new java.util.TreeSet<>();
            for (var e : table.entrySet()) {
                Item item = BuiltInRegistries.ITEM.getOptional(e.getKey()).orElse(null);
                if (item == null) continue;
                Map<Float, List<MobEffectInstance>> byChance = new LinkedHashMap<>();
                List<Identifier> tracked = new ArrayList<>();
                List<MobEffectInstance> all = new ArrayList<>();
                for (FoodTable.Entry en : e.getValue()) {
                    Holder<MobEffect> h = BuiltInRegistries.MOB_EFFECT.get(en.effect()).orElse(null);
                    if (h == null) {
                        missing.add(en.effect());
                        continue;
                    }
                    MobEffectInstance inst = new MobEffectInstance(h, en.seconds() * 20, en.amplifier());
                    byChance.computeIfAbsent(en.probability(), k -> new ArrayList<>()).add(inst);
                    all.add(inst);
                    if (h.value().getCategory() == MobEffectCategory.BENEFICIAL && !tracked.contains(en.effect())) tracked.add(en.effect());
                }
                APPLIED.put(e.getKey(), List.copyOf(all));
                context.modify(item, builder -> {
                    Consumable old = builder.getOrCreate(DataComponents.CONSUMABLE, () -> Consumables.DEFAULT_FOOD);
                    List<ConsumeEffect> effects = new ArrayList<>();
                    for (ConsumeEffect ce : old.onConsumeEffects()) {
                        if (!(ce instanceof ApplyStatusEffectsConsumeEffect) && !(ce instanceof TrackFoodEffects)) effects.add(ce);
                    }
                    byChance.forEach((p, list) -> effects.add(new ApplyStatusEffectsConsumeEffect(list, p)));
                    if (!tracked.isEmpty()) effects.add(new TrackFoodEffects(List.copyOf(tracked)));
                    builder.set(DataComponents.CONSUMABLE, new Consumable(old.consumeSeconds(), old.animation(), old.sound(),
                            old.hasConsumeParticles(), effects));
                });
                applied++;
            }
            LOG.info("Food effects set on {} foods", applied);
            if (!missing.isEmpty()) LOG.warn("Effects not registered here, skipped: {}", missing);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasAttached(FOOD_EFFECTS_ACTIVE)) WellFed.update(player);
            }
        });
    }
}
