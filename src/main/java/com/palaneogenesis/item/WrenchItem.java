package com.palaneogenesis.item;

import com.palaneogenesis.biotech.BiotechSettings;
import com.palaneogenesis.biotech.WrenchUses;
import com.palaneogenesis.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Llave Inglesa: Shift + clic derecho sobre una mesa de crafteo normal la convierte, de forma
 * permanente, en una Estación Bio-Tech. Tiene 2 usos en total (ver biotech.WrenchUses) y después se
 * rompe y desaparece. Los usos van en un tag propio y no en la durabilidad de vanilla para que no se
 * pueda reparar ni encantar. En creativo no gasta usos.
 *
 * Sin Shift el clic derecho sobre la mesa abre la mesa normal (vanilla corre primero el bloque).
 */
public class WrenchItem extends Item {

	public WrenchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();

		if (player == null || !player.isShiftKeyDown() || !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		level.setBlock(pos, ModBlocks.BIOTECH_STATION.get().defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
		spendUse(context, player);
		return InteractionResult.CONSUME;
	}

	private static void spendUse(UseOnContext context, Player player) {
		if (player.getAbilities().instabuild) {
			return;
		}
		ItemStack stack = context.getItemInHand();
		int left = WrenchUses.afterUse(remaining(stack));
		if (WrenchUses.broken(left)) {
			stack.shrink(1);
			player.broadcastBreakEvent(context.getHand());
		} else {
			stack.getOrCreateTag().putInt(WrenchUses.TAG, left);
		}
	}

	/** Usos que le quedan a esta llave. */
	public static int remaining(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		boolean has = tag != null && tag.contains(WrenchUses.TAG, Tag.TAG_INT);
		return WrenchUses.remaining(has, has ? tag.getInt(WrenchUses.TAG) : 0);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return WrenchUses.showBar(remaining(stack));
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return WrenchUses.barWidth(remaining(stack));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xE08A2E;
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.palaneogenesis.wrench.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.palaneogenesis.wrench.uses", remaining(stack), BiotechSettings.WRENCH_USES)
			.withStyle(ChatFormatting.GOLD));
	}
}
