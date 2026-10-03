/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.InteractSwingSetting.InteractSwing;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"scaffold walk", "BridgeWalk", "bridge walk", "AutoBridge",
	"auto bridge", "tower"})
public final class ScaffoldWalkHack extends Hack implements UpdateListener
{
	private final SliderSetting blocksPerTick =
		new SliderSetting("Blocks per tick",
			"description.wurst.setting.scaffoldwalk.blocks_per_tick", 3, 1, 9,
			1, ValueDisplay.INTEGER);
	
	public ScaffoldWalkHack()
	{
		super("ScaffoldWalk");
		setCategory(Category.BLOCKS);
		addSetting(blocksPerTick);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		BlockPos belowPlayer =
			BlockPos.containing(MC.player.position()).below();
		
		// check if block is already placed
		if(!BlockUtils.getState(belowPlayer).canBeReplaced())
			return;
		
		// search blocks in hotbar
		int newSlot = -1;
		for(int i = 0; i < 9; i++)
		{
			// filter out non-block items
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;
			
			// filter out non-solid blocks
			Block block = Block.byItem(stack.getItem());
			BlockState state = block.defaultBlockState();
			if(!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,
				BlockPos.ZERO))
				continue;
			
			// filter out blocks that would fall
			if(block instanceof FallingBlock && FallingBlock
				.isFree(BlockUtils.getState(belowPlayer.below())))
				continue;
			
			newSlot = i;
			break;
		}
		
		// check if any blocks were found
		if(newSlot == -1)
			return;
		
		// set slot
		int oldSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(newSlot);
		
		scaffoldTo(belowPlayer, blocksPerTick.getValueI());
		
		// reset slot
		MC.player.getInventory().setSelectedSlot(oldSlot);
	}
	
	private void scaffoldTo(BlockPos belowPlayer, int maxBlocks)
	{
		Direction forward = MC.player.getDirection();
		Direction[] sides = {forward, forward.getClockWise(),
			forward.getCounterClockWise(), forward.getOpposite()};
		
		int placed = placeBlock(belowPlayer) ? 1 : 0;
		
		for(Direction side : sides)
		{
			if(placed >= maxBlocks)
				return;
			
			if(placeBlock(belowPlayer.relative(side)))
				placed++;
		}
		
		for(Direction side : sides)
			for(Direction side2 : sides)
			{
				if(placed >= maxBlocks)
					return;
				
				if(side2 == side || side2 == side.getOpposite())
					continue;
				
				BlockPos diagonal = belowPlayer.relative(side).relative(side2);
				if(placeBlock(diagonal))
					placed++;
			}
	}
	
	private boolean placeBlock(BlockPos pos)
	{
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;
		
		Vec3 eyesPos = RotationUtils.getEyesPos();
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			Direction side2 = side.getOpposite();
			
			// check if side is visible (facing away from player)
			if(eyesPos.distanceToSqr(Vec3.atCenterOf(pos)) >= eyesPos
				.distanceToSqr(Vec3.atCenterOf(neighbor)))
				continue;
			
			// check if neighbor can be right clicked
			if(!BlockUtils.canBeClicked(neighbor))
				continue;
			
			Vec3 hitVec = Vec3.atCenterOf(neighbor)
				.add(Vec3.atLowerCornerOf(side2.getUnitVec3i()).scale(0.5));
			
			// check if hitVec is within range (4.25 blocks)
			if(eyesPos.distanceToSqr(hitVec) > 18.0625)
				continue;
			
			// place block
			RotationUtils.getNeededRotations(hitVec).sendPlayerLookPacket();
			IMC.getInteractionManager().rightClickBlock(neighbor, side2,
				hitVec);
			InteractSwing.CLIENT.swing(InteractionHand.MAIN_HAND);
			MC.rightClickDelay = 4;
			
			return true;
		}
		
		return false;
	}
}
