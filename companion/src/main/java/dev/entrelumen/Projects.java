package dev.entrelumen;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Immutable campaign definitions, replaced only after a complete validated resource reload. */
public final class Projects {
  /**
   * One campaign project. {@code reward} is the single item granted once after a successful delivery;
   * {@code extraRewards} are further stacks granted with it (24 September 2026: the Act I closure also
   * hands out the first two calibration frames, which have no crafting recipe).
   */
  public record Project(
      int act, Map<String, Integer> items, Set<String> prerequisites, String reward,
      Map<String, Integer> extraRewards) {
    public Project(int act, Map<String, Integer> items, Set<String> prerequisites, String reward) {
      this(act, items, prerequisites, reward, Map.of());
    }
  }

  static final Set<String> STABLE_IDS =
      Set.of(
          "atlas_awakened",
          "travellers_table",
          "lens_assembled",
          "field_survey",
          "first_signal",
          "precision_bench",
          "crystal_grid",
          "living_workshop",
          "travelling_pantry",
          "lost_workshop",
          "signal_exchange",
          "nursery_protocol",
          "distributed_power",
          "measured_logistics",
          "workshop_hands",
          "exchange_route",
          "spectral_archive",
          "horizon_survey",
          "pollinator_treaty",
          "sealed_memory",
          "heliodor_heart",
          "atlas_voices",
          "resilient_backbone",
          "renewal_engine",
          "settlement_supply",
          "world_network",
          "engineering_module",
          "arcane_module",
          "nature_module",
          "exploration_module",
          "logistics_module",
          "habitation_module");
  private static volatile Map<String, Project> active = Map.of();

  public static Map<String, Project> all() {
    return active;
  }

  static void publish(Map<String, Project> prepared) {
    active = prepared;
  }

  static Map<String, Project> parse(JsonElement root, Predicate<ResourceLocation> itemExists) {
    if (!root.isJsonObject()) throw invalid("root", "expected an object of project definitions");
    Map<String, Project> result = new LinkedHashMap<>();
    root.getAsJsonObject()
        .entrySet()
        .forEach(
            entry -> {
              String id = entry.getKey();
              validateProjectId(id, id);
              if (CampaignMilestones.OBSERVATIONS.contains(id))
                throw invalid(id, "observation IDs cannot be deliverable projects");
              if (CampaignMilestones.RESERVED_IDS.contains(id)
                  || CampaignMilestones.LAST_HORIZON.equals(id))
                throw invalid(id, "derived or terminal milestone IDs cannot be deliverable projects");
              if (!entry.getValue().isJsonObject()) throw invalid(id, "expected a project object");
              JsonObject obj = entry.getValue().getAsJsonObject();
              for (String field : obj.keySet())
                if (!Set.of("act", "items", "requires", "reward", "extraRewards").contains(field))
                  throw invalid(id, "unknown field " + field);
              int act = positiveInteger(obj.get("act"), id + ".act");
              if (act > Campaigns.FINAL_ACT) throw invalid(id + ".act", "expected 1.." + Campaigns.FINAL_ACT);
              if (!obj.has("items")
                  || !obj.get("items").isJsonObject()
                  || obj.getAsJsonObject("items").isEmpty())
                throw invalid(id + ".items", "expected a nonempty item/count object");
              Map<String, Integer> items = new LinkedHashMap<>();
              obj.getAsJsonObject("items")
                  .entrySet()
                  .forEach(
                      cost -> {
                        String item = validateItem(cost.getKey(), id + ".items", itemExists);
                        if (items.put(item, positiveInteger(cost.getValue(), id + ".items." + item))
                            != null)
                          throw invalid(id + ".items", "duplicate normalized item " + item);
                      });
              Set<String> prerequisites = new LinkedHashSet<>();
              if (obj.has("requires")) {
                if (!obj.get("requires").isJsonArray())
                  throw invalid(id + ".requires", "expected an array of project IDs");
                for (JsonElement requirement : obj.getAsJsonArray("requires")) {
                  String prerequisite = string(requirement, id + ".requires");
                  validateProjectId(prerequisite, id + ".requires");
                  if (!prerequisites.add(prerequisite))
                    throw invalid(id + ".requires", "duplicate prerequisite " + prerequisite);
                }
              }
              String reward =
                  obj.has("reward")
                      ? validateItem(
                          string(obj.get("reward"), id + ".reward"), id + ".reward", itemExists)
                      : "";
              Map<String, Integer> extraRewards = new LinkedHashMap<>();
              if (obj.has("extraRewards")) {
                if (!obj.get("extraRewards").isJsonObject()
                    || obj.getAsJsonObject("extraRewards").isEmpty())
                  throw invalid(id + ".extraRewards", "expected a nonempty item/count object");
                obj.getAsJsonObject("extraRewards")
                    .entrySet()
                    .forEach(
                        extra -> {
                          String item = validateItem(extra.getKey(), id + ".extraRewards", itemExists);
                          int count = positiveInteger(extra.getValue(), id + ".extraRewards." + item);
                          if (count > 64)
                            throw invalid(id + ".extraRewards." + item, "expected at most one stack (64)");
                          if (item.equals(reward) || extraRewards.put(item, count) != null)
                            throw invalid(id + ".extraRewards", "duplicate reward item " + item);
                        });
              }
              result.put(
                  id,
                  new Project(
                      act,
                      Collections.unmodifiableMap(items),
                      Collections.unmodifiableSet(prerequisites),
                      reward,
                      Collections.unmodifiableMap(extraRewards)));
            });
    Set<String> missing = new TreeSet<>(STABLE_IDS);
    missing.removeAll(result.keySet());
    if (!missing.isEmpty()) throw invalid("root", "missing stable project IDs " + missing);
    result.forEach(
        (id, project) ->
            project
                .prerequisites()
                .forEach(
                    prerequisite -> {
                      if (CampaignMilestones.OBSERVATIONS.contains(prerequisite)) return;
                      Project previous = result.get(prerequisite);
                      if (previous == null)
                        throw invalid(id + ".requires", "unknown prerequisite " + prerequisite);
                      if (previous.act() > project.act())
                        throw invalid(
                            id + ".requires",
                            "prerequisite belongs to a later act: " + prerequisite);
                    }));
    Set<String> visited = new HashSet<>();
    LinkedHashSet<String> path = new LinkedHashSet<>();
    for (String id : result.keySet()) visit(id, result, visited, path);
    return Collections.unmodifiableMap(result);
  }

  private static void visit(
      String id, Map<String, Project> projects, Set<String> visited, LinkedHashSet<String> path) {
    if (visited.contains(id)) return;
    if (!path.add(id))
      throw invalid(id, "prerequisite cycle: " + String.join(" -> ", path) + " -> " + id);
    for (String prerequisite : projects.get(id).prerequisites())
      if (!CampaignMilestones.OBSERVATIONS.contains(prerequisite)) visit(prerequisite, projects, visited, path);
    path.remove(id);
    visited.add(id);
  }

  private static void validateProjectId(String id, String path) {
    // AtlasNetwork encodes project and prerequisite IDs with writeUtf(..., 128).
    if (id.length() > 128)
      throw invalid(path, "project IDs must be at most 128 characters");
    if (!id.matches("[a-z0-9_]+"))
      throw invalid(path, "project IDs must contain lowercase letters, digits or underscores");
  }

  private static int positiveInteger(JsonElement element, String path) {
    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
      throw invalid(path, "expected a positive integer");
    try {
      int value = element.getAsBigDecimal().intValueExact();
      if (value <= 0) throw invalid(path, "expected a positive integer");
      return value;
    } catch (ArithmeticException | NumberFormatException e) {
      throw invalid(path, "expected a positive integer within 32-bit range");
    }
  }

  private static String string(JsonElement element, String path) {
    if (element == null
        || !element.isJsonPrimitive()
        || !element.getAsJsonPrimitive().isString()
        || element.getAsString().isBlank()) throw invalid(path, "expected a nonempty string");
    return element.getAsString();
  }

  private static String validateItem(
      String value, String path, Predicate<ResourceLocation> exists) {
    ResourceLocation id = ResourceLocation.tryParse(value);
    if (id == null || id.equals(ResourceLocation.withDefaultNamespace("air")) || !exists.test(id))
      throw invalid(path, "unknown or unusable item ID " + value);
    return id.toString();
  }

  private static IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException("Entrelumen campaign " + path + ": " + message);
  }

  public static Set<String> forAct(int act) {
    Set<String> result = new HashSet<>();
    active.forEach(
        (id, p) -> {
          if (p.act() == act) result.add(id);
        });
    return result;
  }
}
