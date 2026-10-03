/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

@SearchTags({"elytra bounce", "bounce elytra"})
public final class ElytraBounceHack extends Hack implements UpdateListener
{
	public ElytraBounceHack()
	{
		super("ElytraBounce");
		setCategory(Category.MOVEMENT);
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
		if(!MC.player.canGlide() || !MC.player.onGround()
			|| MC.player.isShiftKeyDown()
			|| MC.player.zza == 0 && MC.player.xxa == 0)
			return;
		
		MC.player.setSprinting(true);
		MC.player.jumpFromGround();
		MC.player.connection.send(new ServerboundPlayerCommandPacket(MC.player,
			ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
	}
}
