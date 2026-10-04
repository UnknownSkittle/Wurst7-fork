/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.datafixers.util.Either;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.TrackedWaypointChunkAccessor;
import net.wurstclient.mixin.TrackedWaypointPositionAccessor;
import net.wurstclient.util.ChatUtils;

@DontSaveState
@SearchTags({"locator bar", "player coordinates", "player tracker"})
public final class PlayerLocatorHack extends Hack implements UpdateListener
{
	private static final int REPORT_DISTANCE_SQUARED = 16 * 16;
	
	private final Map<UUID, ReportedLocation> reportedLocations =
		new HashMap<>();
	
	public PlayerLocatorHack()
	{
		super("PlayerLocator");
		setCategory(Category.RENDER);
	}
	
	@Override
	protected void onEnable()
	{
		reportedLocations.clear();
		
		if(MC.player == null || MC.player.connection == null)
		{
			ChatUtils.error("PlayerLocator can only be enabled in a world.");
			setEnabled(false);
			return;
		}
		
		EVENTS.add(UpdateListener.class, this);
		ChatUtils.message("PlayerLocator reports player positions provided by"
			+ " the server through the locator bar. Arrow-only markers do not"
			+ " contain coordinates.");
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		reportedLocations.clear();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.connection == null)
		{
			setEnabled(false);
			return;
		}
		
		Map<UUID, LocatorResult> currentLocations = new HashMap<>();
		MC.player.connection.getWaypointManager().forEachWaypoint(MC.player,
			waypoint -> addWaypoint(currentLocations, waypoint));
		
		Set<UUID> seen = new HashSet<>();
		for(Map.Entry<UUID, LocatorResult> entry : currentLocations.entrySet())
		{
			UUID playerId = entry.getKey();
			LocatorResult result = entry.getValue();
			seen.add(playerId);
			
			ReportedLocation previous = reportedLocations.get(playerId);
			if(previous == null || previous.isExact() != result.isExact()
				|| hasMoved(previous.location(), result.position()))
			{
				PlayerInfo playerInfo =
					MC.player.connection.getPlayerInfo(playerId);
				if(playerInfo == null)
					continue;
				
				ChatUtils.message(playerInfo.getProfile().name() + ": "
					+ result.description());
				reportedLocations.put(playerId,
					new ReportedLocation(result.position(), result.isExact()));
			}
		}
		
		reportedLocations.keySet().retainAll(seen);
	}
	
	private void addWaypoint(Map<UUID, LocatorResult> locations,
		TrackedWaypoint waypoint)
	{
		Either<UUID, String> id = waypoint.id();
		UUID playerId = id.left().orElse(null);
		if(playerId == null || playerId.equals(MC.player.getUUID())
			|| MC.player.connection.getPlayerInfo(playerId) == null)
			return;
		
		LocatorResult result;
		if(waypoint instanceof TrackedWaypointPositionAccessor exact)
		{
			BlockPos pos = BlockPos.containing(exact.wurst$getPosition().getX(),
				exact.wurst$getPosition().getY(),
				exact.wurst$getPosition().getZ());
			result = new LocatorResult(pos,
				pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), true);
		}else if(waypoint instanceof TrackedWaypointChunkAccessor chunkMarker)
		{
			ChunkPos chunk = chunkMarker.wurst$getChunkPos();
			int x = chunk.getMinBlockX() + 8;
			int z = chunk.getMinBlockZ() + 8;
			BlockPos pos = new BlockPos(x, 0, z);
			result = new LocatorResult(pos, "approximately chunk " + chunk.x()
				+ ", ?Y, " + chunk.z() + " (center " + x + ", " + z + ")",
				false);
		}else
			return;
		
		LocatorResult previous = locations.get(playerId);
		if(previous == null || result.isExact() && !previous.isExact())
			locations.put(playerId, result);
	}
	
	private boolean hasMoved(BlockPos previous, BlockPos current)
	{
		long dx = previous.getX() - current.getX();
		long dy = previous.getY() - current.getY();
		long dz = previous.getZ() - current.getZ();
		return dx * dx + dy * dy + dz * dz >= REPORT_DISTANCE_SQUARED;
	}
	
	private record LocatorResult(BlockPos position, String description,
		boolean isExact)
	{}
	
	private record ReportedLocation(BlockPos location, boolean isExact)
	{}
}
