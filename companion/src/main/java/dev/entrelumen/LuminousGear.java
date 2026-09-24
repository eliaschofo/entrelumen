package dev.entrelumen;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Item classes of the luminous content. Every luminous piece shares three behaviours through the
 * static helpers: damage stops one point before breaking, a piece on its last point is dimmed (no
 * attribute modifiers, fist mining speed, no drops that need a tool) and names carry the luminous
 * colour. Light repair and the set bonus run per player in {@link Luminous}.
 *
 * <p>No enchantment glint: the shine is the controller's animated sprite, and the purple vanilla
 * glint would hide the six discipline colours and read as "enchanted".
 */
public final class LuminousGear {
  private LuminousGear() {}

  /** Marker for every luminous armour piece and tool. */
  public interface Piece {}

  public static boolean dimmed(ItemStack stack) {
    return stack.getItem() instanceof Piece && stack.isDamageableItem()
        && LuminousRules.dimmed(stack.getDamageValue(), stack.getMaxDamage());
  }

  static int clamp(ItemStack stack, int amount) {
    return LuminousRules.allowedDamage(stack.getDamageValue(), stack.getMaxDamage(), amount);
  }

  static Component luminousName(Component name) {
    return name.copy().withColor(LuminousRules.LUMINOUS_COLOR);
  }

  static void gearTooltip(ItemStack stack, List<Component> tooltip, String... extra) {
    if (dimmed(stack))
      tooltip.add(Component.translatable("entrelumen.luminous.dimmed").withStyle(ChatFormatting.RED));
    for (String line : extra)
      tooltip.add(Component.translatable(line).withColor(LuminousRules.LUMINOUS_COLOR));
    tooltip.add(Component.translatable("entrelumen.luminous.light_repair").withStyle(ChatFormatting.GRAY));
    tooltip.add(Component.translatable("entrelumen.luminous.never_breaks").withStyle(ChatFormatting.GRAY));
  }

  /** A Luminosity: condensed light of one discipline, named in its colour. */
  public static final class Luminosity extends Item {
    public final LuminousRules.Discipline discipline;

    public Luminosity(LuminousRules.Discipline discipline, Properties properties) {
      super(properties);
      this.discipline = discipline;
    }

    @Override
    public Component getName(ItemStack stack) {
      return super.getName(stack).copy().withColor(discipline.color);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("entrelumen.luminosity." + discipline.id + ".tooltip")
          .withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("entrelumen.luminosity.source").withStyle(ChatFormatting.DARK_GRAY));
    }
  }

  /** The Luminous Ingot: cream-white gold, the material of every luminous piece. */
  public static final class Ingot extends Item {
    public Ingot(Properties properties) {
      super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("entrelumen.luminous_ingot.tooltip").withStyle(ChatFormatting.GRAY));
    }
  }

  public static final class Armor extends ArmorItem implements Piece {
    public Armor(Holder<ArmorMaterial> material, Type type, Properties properties) {
      super(material, type, properties);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
      return dimmed(stack) ? ItemAttributeModifiers.EMPTY : super.getDefaultAttributeModifiers(stack);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
      return clamp(stack, amount);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      gearTooltip(stack, tooltip, "entrelumen.luminous.set_bonus");
    }
  }

  public static final class Sword extends SwordItem implements Piece {
    private final ItemAttributeModifiers attributes;

    public Sword(Properties properties) {
      super(Luminous.TIER, properties);
      attributes = SwordItem.createAttributes(Luminous.TIER, LuminousRules.SWORD_DAMAGE, LuminousRules.SWORD_SPEED);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
      return dimmed(stack) ? ItemAttributeModifiers.EMPTY : attributes;
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
      var weapon = source.getWeaponItem();
      if (weapon != null && dimmed(weapon)) return 0f;
      return target instanceof LivingEntity living && living.getType().is(EntityTypeTags.UNDEAD)
          ? damage * LuminousRules.SWORD_UNDEAD_BONUS : 0f;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
      if (!dimmed(stack))
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, LuminousRules.SWORD_REVEAL_TICKS, 0, false, false),
            attacker);
      return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
      return clamp(stack, amount);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
      return dimmed(stack) ? 1f : super.getDestroySpeed(stack, state);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      gearTooltip(stack, tooltip, "entrelumen.luminous.sword");
    }
  }

  public static final class Pickaxe extends PickaxeItem implements Piece {
    private final ItemAttributeModifiers attributes;

    public Pickaxe(Properties properties) {
      super(Luminous.TIER, properties);
      attributes = DiggerItem.createAttributes(Luminous.TIER, LuminousRules.PICKAXE_DAMAGE, LuminousRules.PICKAXE_SPEED);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
      return dimmed(stack) ? ItemAttributeModifiers.EMPTY : attributes;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
      return dimmed(stack) ? 1f : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
      return !dimmed(stack) && super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
      return clamp(stack, amount);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      gearTooltip(stack, tooltip);
    }
  }

  public static final class Axe extends AxeItem implements Piece {
    private final ItemAttributeModifiers attributes;

    public Axe(Properties properties) {
      super(Luminous.TIER, properties);
      attributes = DiggerItem.createAttributes(Luminous.TIER, LuminousRules.AXE_DAMAGE, LuminousRules.AXE_SPEED);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
      return dimmed(stack) ? ItemAttributeModifiers.EMPTY : attributes;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
      return dimmed(stack) ? 1f : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
      return !dimmed(stack) && super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
      return clamp(stack, amount);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      gearTooltip(stack, tooltip);
    }
  }

  public static final class Shovel extends ShovelItem implements Piece {
    private final ItemAttributeModifiers attributes;

    public Shovel(Properties properties) {
      super(Luminous.TIER, properties);
      attributes = DiggerItem.createAttributes(Luminous.TIER, LuminousRules.SHOVEL_DAMAGE, LuminousRules.SHOVEL_SPEED);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
      return dimmed(stack) ? ItemAttributeModifiers.EMPTY : attributes;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
      return dimmed(stack) ? 1f : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
      return !dimmed(stack) && super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
      return clamp(stack, amount);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      gearTooltip(stack, tooltip);
    }
  }

  public static final class Hoe extends HoeItem implements Piece {
    private final ItemAttributeModifiers attributes;

    public Hoe(Properties properties) {
      super(Luminous.TIER, properties);
      attributes = DiggerItem.createAttributes(Luminous.TIER, LuminousRules.HOE_DAMAGE, LuminousRules.HOE_SPEED);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
      return dimmed(stack) ? ItemAttributeModifiers.EMPTY : attributes;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
      return dimmed(stack) ? 1f : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
      return !dimmed(stack) && super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
      return clamp(stack, amount);
    }

    @Override
    public Component getName(ItemStack stack) {
      return luminousName(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      gearTooltip(stack, tooltip);
    }
  }
}
