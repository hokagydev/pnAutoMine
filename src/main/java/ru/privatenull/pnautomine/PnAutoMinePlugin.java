package ru.privatenull.pnautomine;

import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnautomine.commands.MineCommand;
import ru.privatenull.pnautomine.commands.MineTabCompleter;
import ru.privatenull.pnautomine.config.MessagesConfig;
import ru.privatenull.pnautomine.config.MineTypesConfig;
import ru.privatenull.pnautomine.config.MiningStatsConfig;
import ru.privatenull.pnautomine.hologram.HologramService;
import ru.privatenull.pnautomine.listeners.BlockBreakListener;
import ru.privatenull.pnautomine.mine.MineManager;
import ru.privatenull.pnautomine.placeholder.MinePlaceholderExpansion;
import ru.privatenull.pnautomine.scoreboard.MineScoreboardService;
import ru.privatenull.pnlibrary.banner.PluginBanner;
import ru.privatenull.pnlibrary.lifecycle.PluginRuntime;
import ru.privatenull.pnlibrary.update.PluginUpdateService;

import java.io.File;
import java.time.Duration;

public final class PnAutoMinePlugin extends JavaPlugin {

    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";
    private static final String UPDATE_PERMISSION = "pnautomine.admin";
    private static final long UPDATE_CHECK_PERIOD_HOURS = 12L;

    private MessagesConfig messages;
    private MineTypesConfig mineTypes;
    private MiningStatsConfig miningStats;
    private MineManager mineManager;
    private HologramService holograms;
    private PluginRuntime runtime;
    private MineScoreboardService scoreboard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "tab-scoreboard-example.yml").isFile()) {
            saveResource("tab-scoreboard-example.yml", false);
        }
        if (!new File(getDataFolder(), "tab-mining-scoreboard-example.yml").isFile()) {
            saveResource("tab-mining-scoreboard-example.yml", false);
        }

        messages = new MessagesConfig(this);
        mineTypes = new MineTypesConfig(this);
        miningStats = new MiningStatsConfig(this);
        holograms = new HologramService(this);

        mineManager = new MineManager(this);
        mineManager.loadMines();

        scoreboard = new MineScoreboardService(this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (scoreboard != null) scoreboard.updateAll();
        }, 1L, 20L);

        runtime = PluginRuntime.start(createPluginIdentity());

        var cmd = getCommand("pnautomine");
        if (cmd == null) {
            throw new IllegalStateException("Команда pnautomine отсутствует в plugin.yml");
        }
        var executor = new MineCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(new MineTabCompleter(this));

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MinePlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI подключён.");
        }

    }

    @Override
    public void onDisable() {
        if (mineManager != null) {
            mineManager.shutdown();
        }
        if (holograms != null) {
            holograms.shutdown();
        }
        if (scoreboard != null) {
            scoreboard.shutdown();
            scoreboard = null;
        }
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.load();
        mineTypes.reload();
        miningStats.reload();

        if (mineManager != null) {
            mineManager.shutdown();
        }
        mineManager = new MineManager(this);
        mineManager.loadMines();

        if (holograms != null) {
            holograms.shutdown();
        }
        holograms = new HologramService(this);
        mineManager.syncHolograms();

        if (runtime != null) {
            runtime.reload();
        }
    }

    public MessagesConfig getMessages() {
        return messages;
    }

    public MineTypesConfig getMineTypes() {
        return mineTypes;
    }

    public MiningStatsConfig getMiningStats() {
        return miningStats;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public HologramService getHolograms() {
        return holograms;
    }

    public PluginUpdateService getUpdateChecker() {
        return runtime != null && runtime.hasUpdates() ? runtime.updates() : null;
    }

    private PluginBanner.Identity createPluginIdentity() {
        return new PluginBanner.Identity(this, "PnFolder")
                .github("Dy6HiLa", "pnAutoMine")
                .bStats(32828)
                .notifyAdministrators(true)
                .notificationPermission(UPDATE_PERMISSION)
                .notifyOnlineAdministrators(true)
                .notifyAdministratorsOnJoin(true)
                .supportUrl(SUPPORT_DISCORD)
                .updateCheckInterval(Duration.ofHours(UPDATE_CHECK_PERIOD_HOURS));
    }
}
