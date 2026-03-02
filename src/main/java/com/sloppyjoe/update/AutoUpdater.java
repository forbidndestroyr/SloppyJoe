package com.sloppyjoe.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sloppyjoe.SloppyJoeMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;

public class AutoUpdater {

    private static final String GITHUB_REPO    = "forbidndestroyr/SloppyJoe";
    private static final String STAGING_SUBDIR = "updates";

    private static volatile boolean updatePending  = false;
    private static volatile String  pendingVersion = null;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called first in SloppyJoeMod.onInitialize().
     * Deletes any *.jar.disabled files left over from a previous update install.
     */
    public static void cleanupDisabledJars() {
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar.disabled")) {
            for (Path p : stream) {
                try {
                    Files.deleteIfExists(p);
                    SloppyJoeMod.LOGGER.info("[AutoUpdater] Deleted stale disabled jar: {}", p.getFileName());
                } catch (IOException e) {
                    SloppyJoeMod.LOGGER.warn("[AutoUpdater] Could not delete {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            SloppyJoeMod.LOGGER.warn("[AutoUpdater] Could not scan mods dir: {}", e.getMessage());
        }
    }

    /**
     * Called first in SloppyJoeModClient.onInitializeClient().
     * Starts the background update check and registers the JOIN notification listener.
     */
    public static void initClient() {
        Thread t = new Thread(() -> {
            try {
                checkAndDownload();
            } catch (Exception e) {
                SloppyJoeMod.LOGGER.warn("[AutoUpdater] Unexpected error: {}", e.getMessage());
            }
        }, "sloppyjoe-updater");
        t.setDaemon(true);
        t.start();

        // Notify the player once they join a world, if an update was staged.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> {
                    if (updatePending && client.player != null) {
                        client.player.sendMessage(
                                Text.literal("\u00a7a[SloppyJoe]\u00a7f Update v" + pendingVersion
                                        + " downloaded. Restart Minecraft to apply!"),
                                false);
                    }
                }));
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private static void checkAndDownload() {
        String currentVersion = FabricLoader.getInstance()
                .getModContainer(SloppyJoeMod.MOD_ID)
                .map(mc -> mc.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");

        JsonObject release = fetchLatestRelease(GITHUB_REPO);
        if (release == null) return;

        String latestTag = release.get("tag_name").getAsString();
        if (!isNewerVersion(latestTag, currentVersion)) {
            SloppyJoeMod.LOGGER.info("[AutoUpdater] Already up to date ({})", currentVersion);
            return;
        }

        // Find JAR asset
        JsonArray assets = release.getAsJsonArray("assets");
        JsonObject targetAsset = null;
        for (JsonElement el : assets) {
            JsonObject asset = el.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.startsWith("sloppyjoe-") && name.endsWith(".jar")) {
                targetAsset = asset;
                break;
            }
        }
        if (targetAsset == null) {
            SloppyJoeMod.LOGGER.warn("[AutoUpdater] No JAR asset found in release {}", latestTag);
            return;
        }

        String downloadUrl = targetAsset.get("browser_download_url").getAsString();
        String assetName   = targetAsset.get("name").getAsString();
        long   assetSize   = targetAsset.get("size").getAsLong();

        // Staging: config/sloppyjoe/updates/<assetName>
        Path stagingDir = FabricLoader.getInstance().getConfigDir().resolve("sloppyjoe").resolve(STAGING_SUBDIR);
        try {
            Files.createDirectories(stagingDir);
        } catch (IOException e) {
            SloppyJoeMod.LOGGER.warn("[AutoUpdater] Could not create staging dir: {}", e.getMessage());
            return;
        }

        Path stagedJar = stagingDir.resolve(assetName);
        if (!downloadFile(downloadUrl, stagedJar, assetSize)) return;

        Path modsDir       = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path currentJarPath = getCurrentJarPath();
        String versionLabel = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;

        // Shutdown hook — runs when the JVM exits cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // 1. Copy staged JAR into mods/
                Path dest = modsDir.resolve(assetName);
                Files.copy(stagedJar, dest, StandardCopyOption.REPLACE_EXISTING);
                SloppyJoeMod.LOGGER.info("[AutoUpdater] Installed new JAR: {}", dest.getFileName());

                // 2. Disable the currently-loaded JAR (only when running from a real JAR)
                if (currentJarPath != null) {
                    Path disabled = Path.of(currentJarPath.toString() + ".disabled");
                    Files.move(currentJarPath, disabled, StandardCopyOption.REPLACE_EXISTING);
                    SloppyJoeMod.LOGGER.info("[AutoUpdater] Disabled old JAR: {}", disabled.getFileName());
                }

                // 3. Clean up staging file
                Files.deleteIfExists(stagedJar);
            } catch (Exception e) {
                SloppyJoeMod.LOGGER.warn("[AutoUpdater] Shutdown hook failed: {}", e.getMessage());
            }
        }, "sloppyjoe-update-installer"));

        updatePending  = true;
        pendingVersion = versionLabel;
        SloppyJoeMod.LOGGER.info("[AutoUpdater] Update v{} staged — will install on next exit.", versionLabel);
    }

    /** GET https://api.github.com/repos/{repo}/releases/latest and parse JSON. */
    private static JsonObject fetchLatestRelease(String repo) {
        String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            int status = conn.getResponseCode();
            if (status == 429) {
                SloppyJoeMod.LOGGER.warn("[AutoUpdater] GitHub API rate limit hit — skipping.");
                return null;
            }
            if (status != 200) {
                SloppyJoeMod.LOGGER.warn("[AutoUpdater] GitHub API returned HTTP {} — skipping.", status);
                return null;
            }
            try (Reader r = new InputStreamReader(conn.getInputStream())) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IOException e) {
            SloppyJoeMod.LOGGER.warn("[AutoUpdater] Network error checking for updates: {}", e.getMessage());
            return null;
        }
    }

    /** Returns true if latestTag is a higher semver than current. */
    private static boolean isNewerVersion(String latestTag, String current) {
        int[] latest = parseSemver(latestTag);
        int[] cur    = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (latest[i] > cur[i]) return true;
            if (latest[i] < cur[i]) return false;
        }
        return false;
    }

    private static int[] parseSemver(String tag) {
        String clean = tag.startsWith("v") ? tag.substring(1) : tag;
        String[] parts = clean.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /**
     * Returns the Path of the currently-loaded mod JAR, or null when running
     * from a directory (dev environment) or if the path cannot be determined.
     */
    private static Path getCurrentJarPath() {
        return FabricLoader.getInstance()
                .getModContainer(SloppyJoeMod.MOD_ID)
                .map(mc -> {
                    try {
                        for (Path p : mc.getOrigin().getPaths()) {
                            if (p.toString().endsWith(".jar")) return p;
                        }
                    } catch (Exception ignored) {}
                    return null;
                })
                .orElse(null);
    }

    /**
     * Downloads {@code url} to {@code dest}, verifying the file size afterwards.
     * Deletes {@code dest} and returns false on any error or size mismatch.
     */
    private static boolean downloadFile(String url, Path dest, long expectedSize) {
        SloppyJoeMod.LOGGER.info("[AutoUpdater] Downloading {} ...", dest.getFileName());
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            if (conn.getResponseCode() != 200) {
                SloppyJoeMod.LOGGER.warn("[AutoUpdater] Download failed: HTTP {}", conn.getResponseCode());
                return false;
            }
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            long actualSize = Files.size(dest);
            if (expectedSize > 0 && actualSize != expectedSize) {
                SloppyJoeMod.LOGGER.warn("[AutoUpdater] Size mismatch: expected {} bytes, got {}. Deleting staged file.",
                        expectedSize, actualSize);
                Files.deleteIfExists(dest);
                return false;
            }
            SloppyJoeMod.LOGGER.info("[AutoUpdater] Download complete ({} bytes).", actualSize);
            return true;
        } catch (IOException e) {
            SloppyJoeMod.LOGGER.warn("[AutoUpdater] Download error: {}", e.getMessage());
            try { Files.deleteIfExists(dest); } catch (IOException ignored) {}
            return false;
        }
    }
}
