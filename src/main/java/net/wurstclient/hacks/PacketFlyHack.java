/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@DontSaveState
@SearchTags({"packet fly", "packetflight", "packet flight"})
public final class PacketFlyHack extends Hack implements UpdateListener
{
	private boolean previousNoPhysics;
	
	private final SliderSetting speed =
		new SliderSetting("Speed",
			"Movement per tick. Servers still validate movement packets, even"
				+ " without an anti-cheat.",
			1, 0.1, 3, 0.1, ValueDisplay.DECIMAL);
	
	public PacketFlyHack()
	{
		super("PacketFly");
		setCategory(Category.MOVEMENT);
		addSetting(speed);
	}
	
	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.player.connection == null)
		{
			setEnabled(false);
			return;
		}
		
		previousNoPhysics = MC.player.noPhysics;
		MC.player.noPhysics = true;
		MC.player.fallDistance = 0;
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		if(MC.player != null)
		{
			MC.player.noPhysics = previousNoPhysics;
			MC.player.setDeltaMovement(Vec3.ZERO);
		}
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.connection == null)
		{
			setEnabled(false);
			return;
		}
		
		double forward = (MC.options.keyUp.isDown() ? 1 : 0)
			- (MC.options.keyDown.isDown() ? 1 : 0);
		double strafe = (MC.options.keyLeft.isDown() ? 1 : 0)
			- (MC.options.keyRight.isDown() ? 1 : 0);
		double vertical = (MC.options.keyJump.isDown() ? 1 : 0)
			- (MC.options.keyShift.isDown() ? 1 : 0);
		
		double length = Math.sqrt(forward * forward + strafe * strafe);
		if(length > 1)
		{
			forward /= length;
			strafe /= length;
		}
		
		float yaw = MC.player.getYRot() * Mth.DEG_TO_RAD;
		double dx = (strafe * Math.cos(yaw) - forward * Math.sin(yaw))
			* speed.getValue();
		double dz = (forward * Math.cos(yaw) + strafe * Math.sin(yaw))
			* speed.getValue();
		double dy = vertical * speed.getValue();
		
		MC.player.setPos(MC.player.getX() + dx, MC.player.getY() + dy,
			MC.player.getZ() + dz);
		MC.player.setDeltaMovement(Vec3.ZERO);
		MC.player.fallDistance = 0;
		MC.player.connection
			.send(new PosRot(MC.player.position(), MC.player.getYRot(),
				MC.player.getXRot(), false, MC.player.horizontalCollision));
	}
}
