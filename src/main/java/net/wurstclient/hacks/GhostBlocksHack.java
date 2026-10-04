/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.hack.Hack;

@SearchTags({"ghost block", "fake block", "client-side block"})
public final class GhostBlocksHack extends Hack implements RightClickListener
{
	private final Map<BlockPos, GhostBlock> ghostBlocks = new HashMap<>();
	private ClientLevel ghostLevel;
	
	public GhostBlocksHack()
	{
		super("GhostBlocks");
		setCategory(Category.BLOCKS);
	}
	
	@Override
	protected void onEnable()
	{
		ghostBlocks.clear();
		ghostLevel = MC.level;
		EVENTS.add(RightClickListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(RightClickListener.class, this);
		
		if(ghostLevel == MC.level)
			ghostBlocks.forEach((pos, ghost) -> {
				if(ghostLevel.getBlockState(pos).equals(ghost.placedState()))
					ghostLevel.setBlock(pos, ghost.originalState(), 3);
			});
		
		ghostBlocks.clear();
		ghostLevel = null;
	}
	
	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(MC.level != ghostLevel)
		{
			ghostBlocks.clear();
			ghostLevel = MC.level;
		}
		
		if(!(MC.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK)
			return;
		
		ItemStack stack = MC.player.getMainHandItem();
		if(!(stack.getItem() instanceof BlockItem blockItem))
			return;
		
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
		BlockState originalState = ghostLevel.getBlockState(pos);
		if(!originalState.canBeReplaced())
			return;
		
		BlockState ghostState = blockItem.getBlock().defaultBlockState();
		GhostBlock previousGhost = ghostBlocks.get(pos);
		if(previousGhost == null)
			ghostBlocks.put(pos, new GhostBlock(originalState, ghostState));
		else
			ghostBlocks.put(pos,
				new GhostBlock(previousGhost.originalState(), ghostState));
		
		ghostLevel.setBlock(pos, ghostState, 3);
		event.cancel();
	}
	
	private record GhostBlock(BlockState originalState, BlockState placedState)
	{}
}
