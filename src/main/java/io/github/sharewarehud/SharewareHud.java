package io.github.sharewarehud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PlayerRideableJumping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class SharewareHud implements ClientModInitializer {
	public static final String MOD_ID = "sharewarehud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Height of the vanilla hotbar the rest of the vanilla HUD is positioned against. */
	private static final int VANILLA_HOTBAR_HEIGHT = 22;

	private static final HudElement NOTHING = (graphics, deltaTracker) -> { };

	public static SharewareConfig config;

	@Override
	public void onInitializeClient() {
		config = SharewareConfig.load();
		SharewarePanel panel = new SharewarePanel(config);

		// The panel takes the hotbar's place. The hotbar element is only extracted when the
		// player has a hotbar (not in spectator) and the HUD is visible, which is exactly when
		// the panel should show. It includes the stats, so it also shows in Creative mode
		// with empty stat boxes, just like the original.
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, _ -> panel);

		// Vanilla stat bars are folded into the panel.
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, _ -> NOTHING);
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, _ -> NOTHING);
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, _ -> NOTHING);
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, _ -> NOTHING);

		// Things that normally sit just above the hotbar get pushed above the taller panel.
		int lift = -(SharewarePanel.HEIGHT - VANILLA_HOTBAR_HEIGHT);
		shift(VanillaHudElements.HELD_ITEM_TOOLTIP, lift);
		shift(VanillaHudElements.OVERLAY_MESSAGE, lift);
		shift(VanillaHudElements.MOUNT_HEALTH, lift);

		// Experience bar / locator bar / jump bar.
		// The original hid experience and drew the horse jump bar at the top of the screen.
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, original -> (graphics, deltaTracker) -> {
			if (isRidingJumpable()) {
				// vanilla draws the bar at guiHeight - 29; move it to the top edge
				translated(graphics, deltaTracker, original, -(graphics.guiHeight() - 29) + 2);
			} else if (config.showExperience) {
				translated(graphics, deltaTracker, original, lift);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, original -> (graphics, deltaTracker) -> {
			if (config.showExperience && !isRidingJumpable()) {
				translated(graphics, deltaTracker, original, lift);
			}
		});

		LOGGER.info("MineCraft 3D: Memory Block Edition HUD loaded");
	}

	private static boolean isRidingJumpable() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getVehicle() instanceof PlayerRideableJumping;
	}

	private static void shift(Identifier id, int dy) {
		HudElementRegistry.replaceElement(id, original -> (graphics, deltaTracker) -> translated(graphics, deltaTracker, original, dy));
	}

	private static void translated(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, HudElement element, int dy) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0F, (float) dy);
		element.extractRenderState(graphics, deltaTracker);
		graphics.pose().popMatrix();
	}
}
