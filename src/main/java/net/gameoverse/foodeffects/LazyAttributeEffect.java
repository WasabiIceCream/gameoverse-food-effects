package net.gameoverse.foodeffects;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A status effect whose attribute modifiers name their attribute by id and resolve it when applied, so the effect can
 * be registered before the mod that owns the attribute (Apothic Attributes, Spell Power, Mana Attributes) and does
 * nothing if that mod is absent. Amount scales with amplifier + 1, like vanilla's own attribute effects.
 */
public class LazyAttributeEffect extends MobEffect {
    private record Template(Identifier attribute, Identifier modifierId, double amount, AttributeModifier.Operation op) {}

    private final List<Template> templates = new ArrayList<>();

    public LazyAttributeEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public LazyAttributeEffect with(String attribute, String modifierPath, double amount, AttributeModifier.Operation op) {
        templates.add(new Template(Identifier.parse(attribute), FoodEffects.id(modifierPath), amount, op));
        return this;
    }

    private static Holder<Attribute> resolve(Identifier id) {
        return BuiltInRegistries.ATTRIBUTE.get(id).orElse(null);
    }

    @Override
    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        super.createModifiers(amplifier, consumer);
        for (Template t : templates) {
            Holder<Attribute> attr = resolve(t.attribute);
            if (attr != null) consumer.accept(attr, new AttributeModifier(t.modifierId, t.amount * (amplifier + 1), t.op));
        }
    }

    @Override
    public void addAttributeModifiers(AttributeMap map, int amplifier) {
        super.addAttributeModifiers(map, amplifier);
        for (Template t : templates) {
            Holder<Attribute> attr = resolve(t.attribute);
            AttributeInstance inst = attr == null ? null : map.getInstance(attr);
            if (inst != null) {
                inst.removeModifier(t.modifierId);
                inst.addOrUpdateTransientModifier(new AttributeModifier(t.modifierId, t.amount * (amplifier + 1), t.op));
            }
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap map) {
        super.removeAttributeModifiers(map);
        for (Template t : templates) {
            Holder<Attribute> attr = resolve(t.attribute);
            AttributeInstance inst = attr == null ? null : map.getInstance(attr);
            if (inst != null) inst.removeModifier(t.modifierId);
        }
    }
}
