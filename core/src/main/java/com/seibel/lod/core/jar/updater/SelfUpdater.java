package com.seibel.lod.core.jar.updater;

import com.seibel.lod.coreapi.ModInfo;
import com.seibel.lod.core.config.Config;
import com.seibel.lod.core.dependencyInjection.SingletonInjector;
import com.seibel.lod.core.jar.JarUtils;
import com.seibel.lod.core.jar.installer.ModrinthGetter;
import com.seibel.lod.core.jar.installer.WebDownloader;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.core.wrapperInterfaces.IVersionConstants;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Used to update the mod automatically
 *
 * @author coolGi
 */
public class SelfUpdater {
    private static final Logger LOGGER = DhLoggerBuilder.getLogger(SelfUpdater.class.getSimpleName());

    /** As we cannot delete(or replace) the jar while the mod is running, we just have this to delete it once the game closes */
    public static boolean deleteOldOnClose = false;

    /**
     * Should be called on the game starting.
     * (After the config has been initialised)
     * @return Whether it should open the update ui
     */
    public static boolean onStart() {
        // Some init stuff
        // We use sha1 to check the version as our versioning system is different to the one on modrinth
        if (!ModrinthGetter.init()) return false;
        String jarSha = "";
        try { jarSha = JarUtils.getFileChecksum(MessageDigest.getInstance("SHA"), JarUtils.jarFile);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String mcVersion = SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion();

        // Check the sha's of both our stuff
        if (jarSha.equals(ModrinthGetter.getLatestShaForVersion(mcVersion)))
            return false;


        LOGGER.info("New version ("+ModrinthGetter.getLatestNameForVersion(mcVersion)+") of "+ ModInfo.READABLE_NAME+" is available");
        if (!Config.Client.AutoUpdater.promptForUpdate.get()) {
            // Auto-update mod
            updateMod(mcVersion);
            return false;
        } // else
        return true;
    }

    /**
     * Should be called when the game is closed.
     * This is ued to delete the previous file if it is required at the end.
     */
    public static void onClose() {
        if (deleteOldOnClose) {
            try {
                Files.delete(JarUtils.jarFile.toPath());
            } catch (Exception e) {
                LOGGER.warn("Failed to delete previous " + ModInfo.READABLE_NAME + " file, please delete it manually at [" + JarUtils.jarFile + "]");
                e.printStackTrace();
            }
        }
    }

    public static boolean updateMod() {
        return updateMod(
                SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion()
        );
    }
    public static boolean updateMod(String minecraftVersion) {
        try {
            LOGGER.info("Attempting to auto update " + ModInfo.READABLE_NAME);
            WebDownloader.downloadAsFile(ModrinthGetter.getLatestDownloadForVersion(minecraftVersion), JarUtils.jarFile.getParentFile().toPath().resolve(ModInfo.NAME + "-" + ModrinthGetter.getLatestNameForVersion(minecraftVersion) + ".jar").toFile());
            deleteOldOnClose = true;
            LOGGER.info(ModInfo.READABLE_NAME + " successfully updated. It will apply on game's relaunch");
            return true;
        } catch (Exception e) {
            LOGGER.info("Failed to update "+ModInfo.READABLE_NAME+" to version "+ModrinthGetter.getLatestNameForVersion(minecraftVersion));
            e.printStackTrace();
            return false;
        }
    }
}
