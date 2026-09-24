package ru.privatenull.pnautomine.hologram;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.pnautomine.PnAutoMinePlugin;
import ru.privatenull.pnautomine.mine.Mine;
import ru.privatenull.pnautomine.mine.MineType;
import ru.privatenull.pnautomine.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class HologramService {

    private static final String HOLOGRAM_PREFIX = "pnautomine_";

    private final PnAutoMinePlugin plugin;
    private final HologramProviderResolver providerResolver;
    private final Set<String> activeHolograms = new HashSet<>();

    private HologramProvider provider;
    private boolean missingProviderWarningLogged;

    public HologramService(PnAutoMinePlugin plugin) {
        this.plugin = plugin;
        this.providerResolver = new HologramProviderResolver(plugin);
        reloadProvider();
    }

    public void shutdown() {
        clearAll();
        provider = null;
    }

    public void clearAll() {
        HologramProvider currentProvider = provider;
        for (String name : new ArrayList<>(activeHolograms)) {
            removeExternal(name, currentProvider);
        }
        activeHolograms.clear();
    }

    public void updateMineHologram(Mine mine) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) return;
        reloadProvider();

        HologramProvider currentProvider = provider;
        if (currentProvider == null) {
            logMissingProviderOnce();
            return;
        }

        Location location = getHologramLocation(mine);
        if (location == null) return;

        List<String> lines = buildHologramLines(mine);
        String name = HOLOGRAM_PREFIX + mine.getId();

        try {
            if (!currentProvider.isAvailable()) {
                throw new IllegalStateException("провайдер выключен");
            }

            HologramSpec spec = new HologramSpec(name, location, lines);
            currentProvider.create(spec);
            activeHolograms.add(name);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Hologram provider failed while creating " + name, t);
            provider = null;
            logMissingProviderOnce();
        }
    }

    public void removeMineHologram(Mine mine) {
        String name = HOLOGRAM_PREFIX + mine.getId();
        reloadProvider();
        removeExternal(name, provider);
        activeHolograms.remove(name);
    }

    private Location getHologramLocation(Mine mine) {
        // Use custom hologram location if set
        if (mine.getHologramLocation() != null) {
            return mine.getHologramLocation();
        }

        // Otherwise use center-top of mine with config offset
        Location centerTop = mine.getCenterTop();
        if (centerTop == null) return null;

        double offsetX = plugin.getConfig().getDouble("hologram.offset.x", 0.0);
        double offsetY = plugin.getConfig().getDouble("hologram.offset.y", 2.0);
        double offsetZ = plugin.getConfig().getDouble("hologram.offset.z", 0.0);

        return centerTop.clone().add(offsetX, offsetY, offsetZ);
    }

    private List<String> buildHologramLines(Mine mine) {
        List<String> templateLines = plugin.getConfig().getStringList("hologram.lines");
        if (templateLines.isEmpty()) {
            templateLines = List.of(
                    "&b&l{mine_name}",
                    "&7Тип: {mine_type_display}",
                    "&7Блоков: &f{blocks_remaining}&7/&f{blocks_total} &8(&a{blocks_percentage}%&8)",
                    "&7Сброс через: &e{reset_time}"
            );
        }

        MineType type = plugin.getMineTypes().getType(mine.getTypeName());
        String typeDisplay = type != null ? type.getDisplayName() : mine.getTypeName();

        List<String> result = new ArrayList<>(templateLines.size());
        for (String line : templateLines) {
            line = line.replace("{mine_name}", mine.getDisplayName() != null ? mine.getDisplayName() : mine.getId());
            line = line.replace("{mine_id}", mine.getId());
            line = line.replace("{mine_type}", mine.getTypeName());
            line = line.replace("{mine_type_display}", typeDisplay);
            line = line.replace("{blocks_remaining}", String.valueOf(mine.getRemainingBlocks()));
            line = line.replace("{blocks_total}", String.valueOf(mine.getTotalBlocks()));
            line = line.replace("{blocks_percentage}", String.format("%.1f", mine.getPercentageRemaining()));
            line = line.replace("{reset_time}", mine.getFormattedTimeUntilReset());
            line = line.replace("{reset_time_seconds}", String.valueOf(mine.getSecondsUntilReset()));
            result.add(ColorUtil.colorize(line));
        }

        return result;
    }

    private void removeExternal(String name, HologramProvider currentProvider) {
        if (name == null || name.isBlank()) return;

        if (currentProvider != null) {
            try {
                currentProvider.remove(name);
            } catch (Throwable failure) {
                plugin.getLogger().log(Level.FINE, "Holograms: failed to remove " + name, failure);
            }
        }

        // Also try removing from both providers in case of provider switch
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            HologramProvider fancy = providerResolver.fancy();
            if (fancy != null && fancy != currentProvider) {
                try {
                    fancy.remove(name);
                } catch (Throwable ignored) {
                }
            }
        }

        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            HologramProvider decent = providerResolver.decent();
            if (decent != null && decent != currentProvider) {
                try {
                    decent.remove(name);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void reloadProvider() {
        if (provider != null) {
            try {
                if (provider.isAvailable()) return;
            } catch (Throwable failure) {
                plugin.getLogger().log(Level.FINE, "check provider availability", failure);
                provider = null;
            }
        }

        provider = providerResolver.select();
        if (provider != null) {
            missingProviderWarningLogged = false;
        }
    }

    private void logMissingProviderOnce() {
        if (missingProviderWarningLogged) return;
        missingProviderWarningLogged = true;
        plugin.getLogger().warning("Голограммы: FancyHolograms или DecentHolograms не найдены. Голограммы шахт отключены.");
    }
}
