/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AttackSwingSetting;
import net.wurstclient.settings.AttackSwingSetting.AttackSwing;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.ItemUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;
import net.minecraft.tags.ItemTags;

@DontSaveState
@SearchTags({"spear kill", "spear lock", "spear dash"})
public final class SpearKillHack extends Hack implements UpdateListener
{
	private static final double ATTACK_DISTANCE = 2.8;
	
	private final SliderSetting targetRange = new SliderSetting("Target range",
		"Maximum distance to acquire a player target.", 32, 4, 64, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));
	
	private final SliderSetting dashSpeed = new SliderSetting("Dash speed",
		"Maximum movement per tick while dashing. Servers may correct or reject"
			+ " large position changes.",
		4, 0.5, 8, 0.5, ValueDisplay.DECIMAL.withSuffix(" blocks/tick"));
	
	private final AttackSwingSetting attackSwing = new AttackSwingSetting(
		AttackSwingSetting.genericCombatDescription(this), AttackSwing.CLIENT);
	
	private Player target;
	private int previousSlot;
	private boolean previousNoPhysics;
	private boolean previousSprinting;
	
	public SpearKillHack()
	{
		super("SpearKill");
		setCategory(Category.COMBAT);
		addSetting(targetRange);
		addSetting(dashSpeed);
		addSetting(attackSwing);
	}
	
	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.level == null
			|| MC.player.connection == null)
		{
			ChatUtils.error("SpearKill can only be enabled in a world.");
			setEnabled(false);
			return;
		}
		
		int spearSlot = findSpear();
		if(spearSlot < 0)
		{
			ChatUtils.error("SpearKill requires a spear in your hotbar.");
			setEnabled(false);
			return;
		}
		
		WURST.getHax().aimAssistHack.setEnabled(false);
		WURST.getHax().clickAuraHack.setEnabled(false);
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().killauraLegitHack.setEnabled(false);
		WURST.getHax().multiAuraHack.setEnabled(false);
		WURST.getHax().tpAuraHack.setEnabled(false);
		WURST.getHax().triggerBotHack.setEnabled(false);
		
		previousSlot = MC.player.getInventory().getSelectedSlot();
		previousNoPhysics = MC.player.noPhysics;
		previousSprinting = MC.player.isSprinting();
		MC.player.getInventory().setSelectedSlot(spearSlot);
		MC.player.noPhysics = true;
		MC.player.setSprinting(true);
		MC.player.connection.send(new ServerboundPlayerCommandPacket(MC.player,
			Action.START_SPRINTING));
		target = null;
		
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
			MC.player.getInventory().setSelectedSlot(previousSlot);
			MC.player.setSprinting(previousSprinting);
			if(!previousSprinting && MC.player.connection != null)
				MC.player.connection.send(new ServerboundPlayerCommandPacket(
					MC.player, Action.STOP_SPRINTING));
		}
		
		target = null;
	}
	
	@Override
	public String getRenderName()
	{
		if(target == null)
			return getName();
		
		return getName() + " [" + target.getName().getString() + "]";
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null
			|| MC.player.connection == null)
		{
			setEnabled(false);
			return;
		}
		
		if(!MC.player.getMainHandItem().is(ItemTags.SPEARS))
		{
			int spearSlot = findSpear();
			if(spearSlot < 0)
			{
				ChatUtils.error("SpearKill stopped because no spear is in"
					+ " your hotbar.");
				setEnabled(false);
				return;
			}
			
			MC.player.getInventory().setSelectedSlot(spearSlot);
		}
		
		if(target == null || !EntityUtils.IS_ATTACKABLE.test(target)
			|| MC.level.getEntity(target.getUUID()) != target
			|| MC.player.distanceToSqr(target) > targetRange.getValueSq() * 1.5)
			target = findTarget();
		
		if(target == null)
			return;
		
		Vec3 targetPosition = target.getBoundingBox().getCenter();
		double distance = Math.sqrt(EntityUtils.distanceToHitboxSq(target));
		if(distance <= ATTACK_DISTANCE)
		{
			RotationUtils.getNeededRotations(targetPosition)
				.sendPlayerLookPacket(false, MC.player.horizontalCollision);
			if(MC.player.getAttackStrengthScale(0) >= 1)
			{
				MC.gameMode.attack(MC.player, target);
				attackSwing.swing();
			}
			
			return;
		}
		
		Vec3 direction =
			targetPosition.subtract(MC.player.position()).normalize();
		double step =
			Math.min(dashSpeed.getValue(), distance - ATTACK_DISTANCE + 0.2);
		Vec3 nextPosition = MC.player.position().add(direction.scale(step));
		
		Rotation rotation = RotationUtils.getNeededRotations(targetPosition);
		MC.player.setPos(nextPosition);
		MC.player.setDeltaMovement(Vec3.ZERO);
		MC.player.fallDistance = 0;
		
		ClientPacketListener connection = MC.player.connection;
		connection.send(new PosRot(nextPosition, rotation.yaw(),
			rotation.pitch(), false, MC.player.horizontalCollision));
	}
	
	private int findSpear()
	{
		int bestSlot = -1;
		double bestDamage = Double.NEGATIVE_INFINITY;
		
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!stack.is(ItemTags.SPEARS))
				continue;
			
			double damage = ItemUtils
				.getAttribute(stack.getItem(), Attributes.ATTACK_DAMAGE)
				.orElse(0);
			if(damage > bestDamage)
			{
				bestDamage = damage;
				bestSlot = i;
			}
		}
		
		return bestSlot;
	}
	
	private Player findTarget()
	{
		double rangeSq = targetRange.getValueSq();
		return EntityUtils.getAliveEntities(Player.class)
			.filter(EntityUtils.IS_ATTACKABLE)
			.filter(player -> EntityUtils.distanceToHitboxSq(player) <= rangeSq)
			.min(Comparator.comparingDouble(EntityUtils::distanceToHitboxSq))
			.orElse(null);
	}
}
