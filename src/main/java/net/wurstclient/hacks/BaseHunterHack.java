/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

@DontSaveState
@SearchTags({"auto base finder", "base hunter", "base search"})
public final class BaseHunterHack extends Hack implements UpdateListener
{
	private static final int SCAN_RADIUS = 64;
	private static final int STEP = SCAN_RADIUS * 3 / 2;
	
	private final SliderSetting radius = new SliderSetting("Area radius",
		"description.wurst.setting.basehunter.radius", 96, 96, 256, 32,
		ValueDisplay.INTEGER.withSuffix(" blocks"));
	
	private final CheckboxSetting fly = new CheckboxSetting("Fly",
		"description.wurst.setting.basehunter.fly", false);
	
	private final ArrayList<BlockPos> route = new ArrayList<>();
	private PathFinder pathFinder;
	private PathProcessor processor;
	private int routeIndex;
	private int ticksProcessing;
	private int unreachableWaypoints;
	private boolean startedBaseFinder;
	private boolean startedFlight;
	
	public BaseHunterHack()
	{
		super("BaseHunter");
		setCategory(Category.MOVEMENT);
		addSetting(radius);
		addSetting(fly);
	}
	
	@Override
	public String getRenderName()
	{
		if(route.isEmpty())
			return getName();
		
		return getName() + " [" + Math.min(routeIndex + 1, route.size()) + "/"
			+ route.size() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.level == null)
		{
			ChatUtils.error("BaseHunter can only be enabled in a world.");
			setEnabled(false);
			return;
		}
		
		BaseFinderHack baseFinder = WURST.getHax().baseFinderHack;
		startedBaseFinder = !baseFinder.isEnabled();
		if(startedBaseFinder)
			baseFinder.setEnabled(true);
		
		FlightHack flightHack = WURST.getHax().flightHack;
		startedFlight = fly.isChecked() && !flightHack.isEnabled()
			&& !MC.player.getAbilities().flying;
		if(startedFlight)
			flightHack.setEnabled(true);
		
		if(fly.isChecked() && !flightHack.isEnabled()
			&& !MC.player.getAbilities().flying)
		{
			ChatUtils
				.error("BaseHunter couldn't enable Flight; it may be blocked.");
			setEnabled(false);
			return;
		}
		
		route.clear();
		routeIndex = 0;
		unreachableWaypoints = 0;
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
		createRoute();
		EVENTS.add(UpdateListener.class, this);
		
		ChatUtils.message("BaseHunter started. It scans the loaded area while"
			+ " patrolling; unloaded chunks are not searched until reached.");
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(MC.player != null)
			PathProcessor.releaseControls();
		
		route.clear();
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
		
		if(startedBaseFinder)
			WURST.getHax().baseFinderHack.setEnabled(false);
		if(startedFlight)
			WURST.getHax().flightHack.setEnabled(false);
		
		startedBaseFinder = false;
		startedFlight = false;
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			setEnabled(false);
			return;
		}
		
		if(routeIndex >= route.size())
		{
			if(unreachableWaypoints == 0)
				ChatUtils.message("BaseHunter finished its patrol.");
			else
				ChatUtils.warning(
					"BaseHunter finished its patrol; " + unreachableWaypoints
						+ " waypoints were unreachable in the loaded area.");
			
			setEnabled(false);
			return;
		}
		
		if(pathFinder == null)
			pathFinder = new PathFinder(route.get(routeIndex));
		
		if(!pathFinder.isDone() && !pathFinder.isFailed())
		{
			PathProcessor.lockControls();
			pathFinder.think();
			
			if(!pathFinder.isDone() && pathFinder.isFailed())
				skipUnreachableWaypoint();
			
			return;
		}
		
		if(pathFinder.isFailed())
		{
			skipUnreachableWaypoint();
			return;
		}
		
		if(processor == null)
		{
			pathFinder.formatPath();
			processor = pathFinder.getProcessor();
			PathProcessor.releaseControls();
		}
		
		if(!pathFinder.isPathStillValid(processor.getIndex())
			|| ticksProcessing >= 10 && processor.getTicksOffPath() > 5)
		{
			pathFinder = new PathFinder(pathFinder.getGoal());
			processor = null;
			ticksProcessing = 0;
			return;
		}
		
		processor.process();
		ticksProcessing++;
		
		if(processor.isDone())
		{
			routeIndex++;
			pathFinder = null;
			processor = null;
			ticksProcessing = 0;
		}
	}
	
	private void createRoute()
	{
		int r = radius.getValueI();
		int centerX = MC.player.blockPosition().getX();
		int centerZ = MC.player.blockPosition().getZ();
		int y = fly.isChecked() ? MC.player.blockPosition().getY() : MC.level
			.getHeight(Heightmap.Types.MOTION_BLOCKING, centerX, centerZ);
		
		ArrayList<Integer> offsets = new ArrayList<>();
		for(int offset = -r; offset < r; offset += STEP)
			offsets.add(offset);
		offsets.add(r);
		
		for(int row = 0; row < offsets.size(); row++)
		{
			int z = offsets.get(row);
			for(int col = 0; col < offsets.size(); col++)
			{
				int xIndex = row % 2 == 0 ? col : offsets.size() - col - 1;
				int x = offsets.get(xIndex);
				int goalX = centerX + x;
				int goalZ = centerZ + z;
				int goalY = y;
				if(!fly.isChecked()
					&& MC.level.hasChunk(goalX >> 4, goalZ >> 4))
					goalY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
						goalX, goalZ);
				
				route.add(new BlockPos(goalX, goalY, goalZ));
			}
		}
	}
	
	private void skipUnreachableWaypoint()
	{
		unreachableWaypoints++;
		routeIndex++;
		pathFinder = null;
		processor = null;
		ticksProcessing = 0;
		if(MC.player != null)
			PathProcessor.releaseControls();
	}
}
