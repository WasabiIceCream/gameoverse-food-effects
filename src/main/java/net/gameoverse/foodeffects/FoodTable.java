package net.gameoverse.foodeffects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** The generated table (tools/generate.py -> food_effects.json in the jar): item id -> its final status effects. */
final class FoodTable {
    record Entry(Identifier effect, int amplifier, int seconds, float probability) {}

    private FoodTable() {}

    static Map<Identifier, List<Entry>> load() {
        Map<Identifier, List<Entry>> out = new LinkedHashMap<>();
        try (InputStream in = FoodTable.class.getResourceAsStream("/gameoverse_food_effects/food_effects.json")) {
            if (in == null) throw new IllegalStateException("food_effects.json missing from the jar");
            JsonObject foods = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("foods");
            for (var e : foods.entrySet()) {
                List<Entry> list = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new Entry(Identifier.parse(o.get("effect").getAsString()), o.get("amplifier").getAsInt(),
                            o.get("seconds").getAsInt(), o.get("probability").getAsFloat()));
                }
                out.put(Identifier.parse(e.getKey()), list);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Couldn't read food_effects.json", ex);
        }
        return out;
    }
}
