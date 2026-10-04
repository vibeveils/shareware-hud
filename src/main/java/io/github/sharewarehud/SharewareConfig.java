package io.github.sharewarehud;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Plain JSON config stored at {@code config/sharewarehud.json}.
 * Edit the file and restart the game to apply changes.
 */
public final class SharewareConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("sharewarehud.json");

	/** Size of the player model inside the portrait box (pixels per block). */
	public int portraitScale = 42;
	/** Shifts the model inside the portrait box. Higher = model sits lower, showing more of the head. */
	public float portraitYOffset = 0.55F;
	/** The portrait's head glances left/right at random, like the original. */
	public boolean portraitLooksAround = true;
	/** The original removed the XP bar. Set to true to keep it (moved above the panel). */
	public boolean showExperience = false;
	/** Draw item count / durability overlays on the 3x3 hotbar. */
	public boolean showItemDecorations = true;

	public static SharewareConfig load() {
		SharewareConfig config = new SharewareConfig();

		try {
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					SharewareConfig read = GSON.fromJson(reader, SharewareConfig.class);
					if (read != null) config = read;
				}
			}

			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(config, writer);
			}
		} catch (Exception e) {
			SharewareHud.LOGGER.warn("Could not read/write {}, using defaults", FILE, e);
		}

		return config;
	}
}
