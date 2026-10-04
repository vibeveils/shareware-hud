package io.github.sharewarehud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.sharewarehud.SharewareConfig.ExperienceStyle;

/**
 * Settings screen opened from Mod Menu. Built only from vanilla buttons, so it needs no config library.
 * Changes apply immediately and are saved when the screen closes.
 */
public final class SharewareConfigScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int COL_W = 260;
	private static final int STEP_W = 20;

	private final Screen parent;
	private final SharewareConfig config;
	private final List<Label> labels = new ArrayList<>();

	public SharewareConfigScreen(Screen parent) {
		super(Component.literal("Shareware HUD"));
		this.parent = parent;
		this.config = SharewareHud.config;
	}

	@Override
	protected void init() {
		labels.clear();
		int left = this.width / 2 - COL_W / 2;
		int y = 32;

		stepper(left, y, () -> "HUD size: " + pct(config.hudScale),
				() -> config.hudScale = step(config.hudScale, -0.05F, 0.25F, 1.0F),
				() -> config.hudScale = step(config.hudScale, 0.05F, 0.25F, 1.0F));
		y += ROW_H;

		stepper(left, y, () -> "HUD opacity: " + pct(config.hudOpacity),
				() -> config.hudOpacity = step(config.hudOpacity, -0.1F, 0.0F, 1.0F),
				() -> config.hudOpacity = step(config.hudOpacity, 0.1F, 0.0F, 1.0F));
		y += ROW_H;

		stepper(left, y, () -> "Background opacity: " + pct(config.backgroundOpacity),
				() -> config.backgroundOpacity = step(config.backgroundOpacity, -0.1F, 0.0F, 1.0F),
				() -> config.backgroundOpacity = step(config.backgroundOpacity, 0.1F, 0.0F, 1.0F));
		y += ROW_H;

		toggle(left, y, () -> "Experience: " + switch (config.experienceStyle) {
			case PANEL -> "In panel";
			case VANILLA -> "Vanilla bar";
			case HIDDEN -> "Hidden (original)";
		}, () -> {
			ExperienceStyle[] all = ExperienceStyle.values();
			config.experienceStyle = all[(config.experienceStyle.ordinal() + 1) % all.length];
		});
		y += ROW_H;

		stepper(left, y, () -> "Portrait zoom: " + config.portraitScale,
				() -> config.portraitScale = Math.max(10, config.portraitScale - 2),
				() -> config.portraitScale = Math.min(120, config.portraitScale + 2));
		y += ROW_H;

		stepper(left, y, () -> String.format("Portrait framing: %.2f", config.portraitYOffset),
				() -> config.portraitYOffset = step(config.portraitYOffset, -0.05F, -2.0F, 2.0F),
				() -> config.portraitYOffset = step(config.portraitYOffset, 0.05F, -2.0F, 2.0F));
		y += ROW_H;

		toggle(left, y, () -> "Portrait looks around: " + onOff(config.portraitLooksAround),
				() -> config.portraitLooksAround = !config.portraitLooksAround);
		y += ROW_H;

		toggle(left, y, () -> "Item counts & durability: " + onOff(config.showItemDecorations),
				() -> config.showItemDecorations = !config.showItemDecorations);
		y += ROW_H + 8;

		int half = (COL_W - 4) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), _ -> {
			config.copyFrom(new SharewareConfig());
			this.rebuildWidgets();
		}).pos(left, y).size(half, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), _ -> this.onClose())
				.pos(left + half + 4, y).size(half, 20).build());
	}

	/** Row with [-] value [+]. The value text is drawn in {@link #extractRenderState}. */
	private void stepper(int left, int y, Supplier<String> text, Runnable down, Runnable up) {
		this.addRenderableWidget(Button.builder(Component.literal("-"), _ -> down.run())
				.pos(left, y).size(STEP_W, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"), _ -> up.run())
				.pos(left + COL_W - STEP_W, y).size(STEP_W, 20).build());
		labels.add(new Label(this.width / 2, y + 6, text));
	}

	/** Full-width button whose text shows the current value; clicking changes it. */
	private void toggle(int left, int y, Supplier<String> text, Runnable action) {
		Button button = Button.builder(Component.literal(text.get()), b -> {
			action.run();
			b.setMessage(Component.literal(text.get()));
		}).pos(left, y).size(COL_W, 20).build();
		this.addRenderableWidget(button);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, "Shareware HUD", this.width / 2, 14, 0xFFFFFFFF);
		for (Label label : labels) {
			graphics.centeredText(this.font, label.text.get(), label.x, label.y, 0xFFFFFFFF);
		}
	}

	@Override
	public void onClose() {
		config.sanitize();
		config.save();
		this.minecraft.gui.setScreen(parent);
	}

	private static float step(float value, float delta, float min, float max) {
		float v = Math.round((value + delta) * 100.0F) / 100.0F;
		return Math.max(min, Math.min(max, v));
	}

	private static String pct(float v) {
		return Math.round(v * 100.0F) + "%";
	}

	private static String onOff(boolean b) {
		return b ? "ON" : "OFF";
	}

	private record Label(int x, int y, Supplier<String> text) { }
}
