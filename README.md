# Gameoverse Food Effects

Fabric mod for 26.1.2, both sides, MIT. Every food gives a small effect from its ingredients, and cooking it into
dishes and meals makes the effect last longer (after Matcha Flavoured's ingredient effects). Design and reasoning:
`docs/food-effects-design.md` in the server project. Player-facing: the guide's Food Effects page.

## How it works

- `tools/generate.py` builds `src/main/resources/gameoverse_food_effects/food_effects.json`: for every food, its
  ingredient families (from `tools/families.json`, matched against item and tag names), traced through the recipes
  the server loads, its preparation tier (raw / prepared / dish / meal) and the final status effect list. The food's
  own mod effects come first, except the ones that only act on the hunger bar (Hearty Meals keeps it full):
  Nourishment, Comfort, Vigor, Hunger. `tools/review.tsv` lists every food with its tier, families, effects and the
  recipe used. Per-item fixes go in `tools/overrides.json`.
- The mod writes those effects into each food's `consumable` component (`DefaultItemComponentEvents.MODIFY`, in a
  phase after the default one so Farmer's Delight's vanilla-soup effects are already there and replaced). Every way
  of eating applies them, and Kaleidoscope Cookery's dish quality scales them.
- A marker consume effect (`track_food_effects`) after the status effects records which beneficial effects the player
  got from food and until when (player attachment). Well Fed: 3 / 5 / 7 of them active at once give level I / II / III
  (+10% healing received and +1 armor per level).
- New effects: Steadfast (+0.2 knockback resistance), Fresh (+10% healing received), Keen (+5% crit chance), Focused
  (+5% spell power), Mana Flow (+1.5 mana regeneration), Well Fed. Their attributes (Apothic Attributes, Spell Power,
  Mana Attributes) are looked up by id when applied, so a missing mod only disables that part.
- Client: a tooltip line per effect with its duration, skipped when the food's own tooltip already lists it.

Icons: `tools/icons/draw.py` (original pixel art).

## Regenerating (after adding or updating a food mod)

1. Take this mod's jar out of the local server's `mods/` (the generator refuses a dump taken with it installed, since
   its effects would then count as the food mods' own).
2. Put `gameoverse-recipe-audit` in `mods/`, start the server, run `/gameoverse_food_audit` and
   `/gameoverse_recipe_dump`, stop, remove that jar.
3. `python3 tools/generate.py`, read the new rows in `tools/review.tsv`, fix oddities in `families.json` /
   `overrides.json`, rebuild (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk sh ./gradlew build`), redeploy, update the
   guide page if a family changed.
