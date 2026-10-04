package io.github.sharewarehud;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import io.github.sharewarehud.SharewareConfig.ExperienceStyle;

/**
 * The 3D Shareware v1.34 status bar, laid out like a 90s FPS:
 *
 * <pre>
 * +--------+--------+-------+------+-----------+
 * |  100%  |   45%  | (you) | beef | [1][2][3] |
 * |        |        |       |  12  | [4][5][6] |
 * | HEALTH | ARMOR  |       | [OH] | [7][8][9] |
 * +--------+--------+-------+------+-----------+
 * </pre>
 *
 * Everything is laid out in "panel space" and scaled by {@code hudScale} around the bottom of
 * the screen. The portrait is the exception: the entity renderer ignores the pose stack, so its
 * box is converted to real screen coordinates by hand.
 */
public final class SharewarePanel implements HudElement {
	// ---- layout (panel pixels) ----
	private static final int BORDER = 2;
	private static final int GAP = 2;
	private static final int SLOT = 20;
	private static final int STAT_W = 54;
	private static final int FACE_W = 46;
	private static final int FOOD_W = 28;
	private static final int ARMS_W = SLOT * 3 + 2;
	private static final int SECTION_H = SLOT * 3 + 2;

	public static final int HEIGHT = SECTION_H + BORDER * 2;
	public static final int WIDTH = BORDER * 2 + STAT_W * 2 + FACE_W + FOOD_W + ARMS_W + GAP * 4;

	// ---- colours (RGB; alpha comes from the opacity settings) ----
	private static final int PANEL = 0x5B5B5B;
	private static final int PANEL_LIGHT = 0x8C8C8C;
	private static final int PANEL_DARK = 0x2C2C2C;
	private static final int WELL = 0x3A3A3A;
	private static final int WELL_DARK = 0x1E1E1E;
	private static final int WELL_LIGHT = 0x767676;
	private static final int SLOT_BG = 0x8B8B8B;
	private static final int SLOT_DARK = 0x373737;
	private static final int SLOT_LIGHT = 0xFFFFFF;
	private static final int DIGITS = 0xD42A1C;
	private static final int DIGITS_ABSORB = 0xF2C12E;
	private static final int LABEL = 0xC6C6C6;
	private static final int XP_GREEN = 0x80FF20;
	private static final int XP_BAR_BG = 0x000000;

	// ---- vanilla GUI sprites ----
	private static final Identifier HOTBAR_SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
	private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier ARMOR_FULL = Identifier.withDefaultNamespace("hud/armor_full");
	private static final Identifier AIR = Identifier.withDefaultNamespace("hud/air");

	// Created lazily: ItemStacks can't be built during mod init, before item components are bound.
	private static ItemStack bone;
	private static ItemStack beef;

	private final SharewareConfig config;

	// per-frame opacity, 0..255
	private int fgAlpha;
	private int bgAlpha;

	// head-turning state for the portrait
	private float lookCurrent;
	private float lookTarget;
	private long nextLookChange;
	private long lastFrame;

	public SharewarePanel(SharewareConfig config) {
		this.config = config;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) return;

		Font font = mc.font;
		boolean survival = mc.gameMode.canHurtPlayer();
		float scale = config.hudScale;
		fgAlpha = Math.round(config.hudOpacity * 255.0F);
		bgAlpha = Math.round(config.backgroundOpacity * 255.0F);

		// panel space: the screen as seen after scaling
		int x = Math.round((graphics.guiWidth() / scale - WIDTH) / 2.0F);
		int y = Math.round(graphics.guiHeight() / scale - HEIGHT);

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);

		// outer frame
		bevel(graphics, x, y, WIDTH, HEIGHT, PANEL, PANEL_LIGHT, PANEL_DARK);

		int sx = x + BORDER;
		int sy = y + BORDER;

		// HEALTH
		well(graphics, sx, sy, STAT_W, SECTION_H);
		if (survival) {
			float absorb = player.getAbsorptionAmount();
			int pct = Mth.ceil((player.getHealth() + absorb) / Math.max(1.0F, player.getMaxHealth()) * 100.0F);
			bigNumber(graphics, font, pct + "%", sx, sy, STAT_W, absorb > 0 ? DIGITS_ABSORB : DIGITS);
		}
		labelWithIcon(graphics, font, "HEALTH", sx, sy, STAT_W, HEART_CONTAINER, HEART_FULL);
		sx += STAT_W + GAP;

		// ARMOR
		well(graphics, sx, sy, STAT_W, SECTION_H);
		if (survival) {
			bigNumber(graphics, font, player.getArmorValue() * 5 + "%", sx, sy, STAT_W, DIGITS);
		}
		labelWithIcon(graphics, font, "ARMOR", sx, sy, STAT_W, null, ARMOR_FULL);
		sx += STAT_W + GAP;

		// PORTRAIT
		well(graphics, sx, sy, FACE_W, SECTION_H);
		if (fgAlpha > 0) {
			portrait(graphics, player, scale, sx + 1, sy + 1, sx + FACE_W - 1, sy + SECTION_H - 1);
		}
		sx += FACE_W + GAP;

		// FOOD (beef on a bone), XP, offhand
		well(graphics, sx, sy, FOOD_W, SECTION_H);
		food(graphics, player, survival, sx + (FOOD_W - 16) / 2, sy + 5);
		if (survival && config.experienceStyle == ExperienceStyle.PANEL) {
			experience(graphics, font, player, sx, sy + 23, FOOD_W);
		}
		int offX = sx + (FOOD_W - SLOT) / 2;
		int offY = sy + SECTION_H - SLOT - 1;
		slot(graphics, offX, offY);
		drawItem(graphics, font, player.getOffhandItem(), offX + 2, offY + 2);
		sx += FOOD_W + GAP;

		// ARMS: the 3x3 hotbar, slot 1 top-left .. slot 9 bottom-right
		well(graphics, sx, sy, ARMS_W, SECTION_H);
		int gx = sx + 1;
		int gy = sy + 1;
		for (int i = 0; i < 9; i++) {
			slot(graphics, gx + (i % 3) * SLOT, gy + (i / 3) * SLOT);
		}
		int selected = player.getInventory().getSelectedSlot();
		if (selected >= 0 && selected < 9) {
			sprite(graphics, HOTBAR_SELECTION, gx + (selected % 3) * SLOT - 2, gy + (selected / 3) * SLOT - 2, 24, 23);
		}
		for (int i = 0; i < 9; i++) {
			drawItem(graphics, font, player.getInventory().getItem(i),
					gx + (i % 3) * SLOT + 2, gy + (i / 3) * SLOT + 2);
		}

		// air bubbles sit just above the hotbar block
		if (survival) {
			air(graphics, player, x + WIDTH - BORDER, y - 10);
		}

		graphics.pose().popMatrix();
	}

	// ------------------------------------------------------------------ sections

	private void bigNumber(GuiGraphicsExtractor graphics, Font font, String text, int sx, int sy, int w, int rgb) {
		float scale = 2.0F;
		int textW = font.width(text);
		// shrink very long values (e.g. modded 1000%+) so they still fit the box
		if (textW * scale > w - 4) scale = (w - 4) / (float) textW;

		graphics.pose().pushMatrix();
		graphics.pose().translate(sx + (w - textW * scale) / 2.0F, sy + 14.0F);
		graphics.pose().scale(scale, scale);
		text(graphics, font, text, 0, 0, rgb, true);
		graphics.pose().popMatrix();
	}

	private void labelWithIcon(GuiGraphicsExtractor graphics, Font font, String label, int sx, int sy, int w,
			Identifier background, Identifier icon) {
		int textW = font.width(label);
		int total = 9 + 2 + textW;
		int ix = sx + (w - total) / 2;
		int iy = sy + SECTION_H - 16;

		if (background != null) sprite(graphics, background, ix, iy, 9, 9);
		sprite(graphics, icon, ix, iy, 9, 9);
		text(graphics, font, label, ix + 11, iy + 1, LABEL, true);
	}

	private void portrait(GuiGraphicsExtractor graphics, LocalPlayer player, float scale, int x1, int y1, int x2, int y2) {
		long now = Util.getMillis();
		float dt = lastFrame == 0 ? 0 : Math.min(0.25F, (now - lastFrame) / 1000.0F);
		lastFrame = now;

		if (config.portraitLooksAround) {
			if (now >= nextLookChange) {
				ThreadLocalRandom rng = ThreadLocalRandom.current();
				// mostly look ahead, sometimes glance to one side, like the shareware face
				lookTarget = rng.nextFloat() < 0.45F ? 0.0F : (rng.nextBoolean() ? -1.0F : 1.0F);
				nextLookChange = now + 900 + rng.nextInt(1800);
			}
			lookCurrent += (lookTarget - lookCurrent) * (1.0F - (float) Math.exp(-dt * 10.0F));
		} else {
			lookCurrent = 0.0F;
		}

		// The entity renderer works in real screen coordinates and ignores the pose stack.
		int sx1 = Math.round(x1 * scale);
		int sy1 = Math.round(y1 * scale);
		int sx2 = Math.round(x2 * scale);
		int sy2 = Math.round(y2 * scale);
		int modelScale = Math.max(1, Math.round(config.portraitScale * scale));

		float cx = (sx1 + sx2) / 2.0F;
		float cy = (sy1 + sy2) / 2.0F;
		// the renderer turns the model to "follow" this point; feed it a fake mouse position
		float mouseX = cx + lookCurrent * 45.0F * scale;
		float mouseY = cy - 6.0F * scale;

		InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, sx1, sy1, sx2, sy2,
				modelScale, config.portraitYOffset, mouseX, mouseY, player);
	}

	private static void food(GuiGraphicsExtractor graphics, LocalPlayer player, boolean survival, int ix, int iy) {
		if (bone == null) {
			bone = new ItemStack(Items.BONE);
			beef = new ItemStack(Items.BEEF);
		}

		graphics.item(bone, ix, iy);
		if (!survival) return; // the original only showed the bone in Creative

		int food = Mth.clamp(player.getFoodData().getFoodLevel(), 0, 20);
		if (food <= 0) return;

		// the beef "drains" from the top as you get hungry
		int visible = Mth.ceil(16 * food / 20.0F);
		graphics.enableScissor(ix, iy + 16 - visible, ix + 16, iy + 16);
		graphics.item(beef, ix, iy);
		graphics.disableScissor();
	}

	/** Compact XP: vanilla-style green level number over a thin progress bar. */
	private void experience(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int sx, int top, int w) {
		int level = player.experienceLevel;
		if (level > 0) {
			String text = String.valueOf(level);
			int tx = sx + (w - font.width(text)) / 2;
			// black outline like the vanilla level number
			text(graphics, font, text, tx + 1, top, 0x000000, false);
			text(graphics, font, text, tx - 1, top, 0x000000, false);
			text(graphics, font, text, tx, top + 1, 0x000000, false);
			text(graphics, font, text, tx, top - 1, 0x000000, false);
			text(graphics, font, text, tx, top, XP_GREEN, false);
		}

		int barW = w - 6;
		int bx = sx + 3;
		int by = top + 10;
		int filled = Math.round(barW * Math.clamp(player.experienceProgress, 0.0F, 1.0F));
		graphics.fill(bx, by, bx + barW, by + 3, fg(XP_BAR_BG));
		if (filled > 0) graphics.fill(bx, by + 1, bx + filled, by + 2, fg(XP_GREEN));
	}

	private void air(GuiGraphicsExtractor graphics, LocalPlayer player, int right, int top) {
		int max = player.getMaxAirSupply();
		int supply = Math.clamp(player.getAirSupply(), 0, max);
		if (!player.isEyeInFluid(FluidTags.WATER) && supply >= max) return;

		int bubbles = Mth.ceil(supply * 10.0 / Math.max(1, max));
		for (int i = 0; i < bubbles; i++) {
			sprite(graphics, AIR, right - 9 - i * 8, top, 9, 9);
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Items can't be faded by the GUI renderer, so they stay opaque unless the HUD is fully hidden. */
	private void drawItem(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int ix, int iy) {
		if (stack.isEmpty() || fgAlpha == 0) return;
		graphics.item(stack, ix, iy);
		if (config.showItemDecorations) graphics.itemDecorations(font, stack, ix, iy);
	}

	private void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int rgb, boolean shadow) {
		if (fgAlpha == 0) return;
		graphics.text(font, text, x, y, fg(rgb), shadow);
	}

	private void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int w, int h) {
		if (fgAlpha == 0) return;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, fg(0xFFFFFF));
	}

	private int fg(int rgb) {
		return (fgAlpha << 24) | (rgb & 0xFFFFFF);
	}

	private int bg(int rgb) {
		return (bgAlpha << 24) | (rgb & 0xFFFFFF);
	}

	/** Raised panel: fill plus a light top/left edge and a dark bottom/right edge. */
	private void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int light, int dark) {
		if (bgAlpha == 0) return;
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg(fill));
		g.fill(x, y, x + w, y + 1, bg(light));
		g.fill(x, y + 1, x + 1, y + h, bg(light));
		g.fill(x + 1, y + h - 1, x + w, y + h, bg(dark));
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg(dark));
	}

	/** Recessed section the stats sit in. */
	private void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		bevel(g, x, y, w, h, WELL, WELL_DARK, WELL_LIGHT);
	}

	/** Inventory-style 18x18 slot centred in a 20x20 cell. */
	private void slot(GuiGraphicsExtractor g, int x, int y) {
		bevel(g, x + 1, y + 1, 18, 18, SLOT_BG, SLOT_DARK, SLOT_LIGHT);
	}
}
