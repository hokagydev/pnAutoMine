package ru.privatenull.pnautomine.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pnautomine.PnAutoMinePlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class MineManager {

    private final PnAutoMinePlugin plugin;
    private final Map<String, Mine> mines = new ConcurrentHashMap<>();
    private final File minesFolder;
    private BukkitTask tickTask;

    public MineManager(PnAutoMinePlugin plugin) {
        this.plugin = plugin;
        this.minesFolder = new File(plugin.getDataFolder(), "mines");
        if (!minesFolder.exists()) {
            minesFolder.mkdirs();
        }
    }

    public void loadMines() {
        mines.clear();
        File[] files = minesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                Mine mine = loadMine(file);
                if (mine != null) {
                    mines.put(mine.getId(), mine);
                    plugin.getLogger().info("Загружена шахта: " + mine.getId());
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Ошибка загрузки шахты из " + file.getName(), ex);
            }
        }

        startTickTask();
        plugin.getLogger().info("Загружено шахт: " + mines.size());
    }

    private Mine loadMine(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        if (id == null || id.isBlank()) return null;

        Mine mine = new Mine(id);
        mine.setTypeName(config.getString("type", "normal"));
        mine.setDisplayName(config.getString("display-name", id));
        mine.setWorldName(config.getString("world", "world"));

        // Load defaults from config, then override from mine file
        int defaultInterval = plugin.getConfig().getInt("defaults.reset-interval", 300);
        mine.setResetInterval(config.getInt("reset-interval", defaultInterval));
        mine.setBroadcastReset(config.getBoolean("broadcast-reset",
                plugin.getConfig().getBoolean("defaults.broadcast-reset", true)));
        mine.setTeleportOnReset(config.getBoolean("teleport-on-reset",
                plugin.getConfig().getBoolean("defaults.teleport-on-reset", true)));
        mine.setResetSound(config.getString("reset-sound",
                plugin.getConfig().getString("defaults.reset-sound", "ENTITY_EXPERIENCE_ORB_PICKUP")));

        // Load region
        ConfigurationSection regionSection = config.getConfigurationSection("region");
        if (regionSection != null) {
            mine.setRegion(deserializeRegion(regionSection));
        }

        // Load spawn location
        ConfigurationSection spawnSection = config.getConfigurationSection("spawn-location");
        if (spawnSection != null) {
            mine.setSpawnLocation(deserializeLocation(spawnSection, mine.getWorldName()));
        }

        // Load hologram location
        ConfigurationSection holoSection = config.getConfigurationSection("hologram-location");
        if (holoSection != null) {
            mine.setHologramLocation(deserializeLocation(holoSection, mine.getWorldName()));
        }

        mine.setLastResetTime(System.currentTimeMillis());
        return mine;
    }

    public void saveMine(Mine mine) {
        File file = new File(minesFolder, mine.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("id", mine.getId());
        config.set("type", mine.getTypeName());
        config.set("display-name", mine.getDisplayName());
        config.set("world", mine.getWorldName());
        config.set("reset-interval", mine.getResetInterval());
        config.set("broadcast-reset", mine.isBroadcastReset());
        config.set("teleport-on-reset", mine.isTeleportOnReset());
        config.set("reset-sound", mine.getResetSound());

        // Save region
        if (mine.getRegion() != null) {
            serializeRegion(config.createSection("region"), mine.getRegion());
        }

        // Save spawn location
        if (mine.getSpawnLocation() != null) {
            serializeLocation(config.createSection("spawn-location"), mine.getSpawnLocation());
        }

        // Save hologram location
        if (mine.getHologramLocation() != null) {
            serializeLocation(config.createSection("hologram-location"), mine.getHologramLocation());
        }

        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения шахты " + mine.getId(), ex);
        }
    }

    public Mine createMine(String id, String typeName, MineRegion region, String worldName) {
        if (mines.containsKey(id)) return null;

        MineType type = plugin.getMineTypes().getType(typeName);
        if (type == null) return null;

        Mine mine = new Mine(id);
        mine.setTypeName(typeName);
        mine.setDisplayName(type.getDisplayName());
        mine.setWorldName(worldName);
        mine.setRegion(region);

        // Set defaults from config
        mine.setResetInterval(plugin.getConfig().getInt("defaults.reset-interval", 300));
        mine.setBroadcastReset(plugin.getConfig().getBoolean("defaults.broadcast-reset", true));
        mine.setTeleportOnReset(plugin.getConfig().getBoolean("defaults.teleport-on-reset", true));
        mine.setResetSound(plugin.getConfig().getString("defaults.reset-sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));

        mines.put(id, mine);
        saveMine(mine);
        resetMine(mine);
        syncHolograms();
        return mine;
    }

    public boolean deleteMine(String id) {
        Mine mine = mines.remove(id);
        if (mine == null) return false;

        plugin.getHolograms().removeMineHologram(mine);

        File file = new File(minesFolder, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
        return true;
    }

    public void resetMine(Mine mine) {
        MineType type = resolveResetType(mine);
        if (type == null) {
            plugin.getLogger().warning("Тип шахты не найден: " + mine.getTypeName());
            return;
        }

        World world = mine.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Мир не найден для шахты " + mine.getId() + ": " + mine.getWorldName());
            return;
        }

        MineRegion region = mine.getRegion();
        if (region == null) return;

        // Teleport players out before reset
        if (mine.isTeleportOnReset()) {
            teleportPlayersOut(mine);
        }

        // Fill region with random blocks based on type weights
        for (int[] pos : region.getBlocks()) {
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            block.setType(type.randomBlock(), false);
        }

        mine.resetMiningStats();
        mine.setLastResetTime(System.currentTimeMillis());

        // Broadcast reset message
        if (mine.isBroadcastReset()) {
            String message = plugin.getMessages().get("reset-broadcast",
                    "mine_name", ColorUtil.colorize(mine.getDisplayName()));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }

        // Play sound
        playResetSound(mine);

        // Update hologram
        plugin.getHolograms().updateMineHologram(mine);
    }

    public void resetAllMines() {
        for (Mine mine : mines.values()) {
            resetMine(mine);
        }
    }

    private void teleportPlayersOut(Mine mine) {
        MineRegion region = mine.getRegion();
        if (region == null) return;

        World world = mine.getWorld();
        if (world == null) return;

        Location spawn = mine.getSpawnLocation();
        if (spawn == null) {
            // If no spawn set, teleport to center-top of mine
            spawn = region.centerTop(world);
        }

        String message = plugin.getMessages().get("teleport-on-reset",
                "mine_name", ColorUtil.colorize(mine.getDisplayName()));

        for (Player player : world.getPlayers()) {
            if (region.containsPlayer(player.getLocation())) {
                player.teleport(spawn);
                player.sendMessage(message);
            }
        }
    }

    private void playResetSound(Mine mine) {
        String soundName = mine.getResetSound();
        if (soundName == null || soundName.isEmpty()) return;

        try {
            NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase());
            Sound sound = Registry.SOUNDS.get(key);
            if (sound == null) return;

            World world = mine.getWorld();
            if (world == null) return;

            for (Player player : world.getPlayers()) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid sound name, skip
        }
    }

    private void startTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Mine mine : mines.values()) {
                if (mine.getResetInterval() > 0 && now >= mine.getNextResetTime()) {
                    resetMine(mine);
                }
            }
            // Update holograms every 20 ticks (1 second)
            syncHolograms();
        }, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void syncHolograms() {
        for (Mine mine : mines.values()) {
            plugin.getHolograms().updateMineHologram(mine);
        }
    }

    private MineType resolveResetType(Mine mine) {
        MineType currentType = plugin.getMineTypes().getType(mine.getTypeName());
        if (currentType == null) {
            return null;
        }

        // Weighted random mode is independent on every reset. The same type may
        // be selected several times in a row; probabilities are not a progression.
        if (plugin.getConfig().getBoolean("random-mine-type.enabled", true)) {
            MineType randomType = chooseRandomType();
            if (randomType != null) {
                mine.setTypeName(randomType.getId());
                mine.setDisplayName(randomType.getDisplayName());
                saveMine(mine);
                return randomType;
            }
        }

        MineType nextType = getNextType(mine);
        if (nextType == null) return currentType;

        mine.setTypeName(nextType.getId());
        mine.setDisplayName(nextType.getDisplayName());
        saveMine(mine);
        return nextType;
    }

    /**
     * Selects a mine type by configured weight. Weights do not have to add up
     * to 100: they are normalized automatically. Example: 70/20/10 means
     * approximately 70%, 20%, 10%.
     */
    private MineType chooseRandomType() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("random-mine-type.types");
        if (section == null) {
            return null;
        }

        List<MineType> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;

        for (String id : section.getKeys(false)) {
            MineType type = plugin.getMineTypes().getType(id);
            double weight = section.getDouble(id, 0.0);
            if (type != null && weight > 0.0) {
                candidates.add(type);
                weights.add(weight);
                total += weight;
            }
        }

        if (candidates.isEmpty() || total <= 0.0) {
            return null;
        }

        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /** Returns the type that will be selected on the next reset without changing the mine. */
    public MineType getNextType(Mine mine) {
        if (!plugin.getConfig().getBoolean("mine-type-progression.enabled", true)) return null;

        MineType currentType = plugin.getMineTypes().getType(mine.getTypeName());
        if (currentType == null) return null;

        List<String> typeIds = new ArrayList<>(plugin.getMineTypes().getTypeIds());
        if (typeIds.size() < 2) return null;

        int currentIndex = typeIds.indexOf(mine.getTypeName());
        if (currentIndex < 0) return null;

        int nextIndex = currentIndex + 1;
        if (nextIndex >= typeIds.size()) {
            if (!plugin.getConfig().getBoolean("mine-type-progression.loop", true)) return null;
            nextIndex = 0;
        }

        String nextTypeId = typeIds.get(nextIndex);
        return nextTypeId.equals(mine.getTypeName()) ? null : plugin.getMineTypes().getType(nextTypeId);
    }

    /** Returns the enabled mine whose scheduled reset is closest. */
    public Mine getNextMineToReset() {
        return mines.values().stream()
                .filter(mine -> mine.getResetInterval() > 0)
                .min(Comparator.comparingLong(Mine::getNextResetTime))
                .orElse(null);
    }

    public Mine getMine(String id) {
        return mines.get(id);
    }

    public Collection<Mine> getAllMines() {
        return Collections.unmodifiableCollection(mines.values());
    }

    public Set<String> getMineIds() {
        return Collections.unmodifiableSet(mines.keySet());
    }

    public boolean hasMine(String id) {
        return mines.containsKey(id);
    }

    /**
     * Find which mine contains the given location.
     */
    public Mine getMineAt(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (Mine mine : mines.values()) {
            MineRegion region = mine.getRegion();
            if (region != null && mine.getWorldName().equals(location.getWorld().getName())
                    && region.contains(x, y, z)) {
                return mine;
            }
        }
        return null;
    }

    // --- Serialization helpers ---

    private void serializeRegion(ConfigurationSection section, MineRegion region) {
        section.set("minX", region.getMinX());
        section.set("minY", region.getMinY());
        section.set("minZ", region.getMinZ());
        section.set("maxX", region.getMaxX());
        section.set("maxY", region.getMaxY());
        section.set("maxZ", region.getMaxZ());

        List<String> blockStrings = new ArrayList<>();
        for (int[] block : region.getBlocks()) {
            blockStrings.add(block[0] + "," + block[1] + "," + block[2]);
        }
        section.set("blocks", blockStrings);
    }

    private MineRegion deserializeRegion(ConfigurationSection section) {
        List<String> blockStrings = section.getStringList("blocks");
        if (!blockStrings.isEmpty()) {
            List<int[]> blocks = new ArrayList<>();
            for (String s : blockStrings) {
                String[] parts = s.split(",");
                if (parts.length == 3) {
                    blocks.add(new int[]{
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                    });
                }
            }
            return MineRegion.fromBlocks(blocks);
        }

        // Fallback: cuboid from bounds
        return MineRegion.cuboid(
                section.getInt("minX"), section.getInt("minY"), section.getInt("minZ"),
                section.getInt("maxX"), section.getInt("maxY"), section.getInt("maxZ")
        );
    }

    private void serializeLocation(ConfigurationSection section, Location loc) {
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", (double) loc.getYaw());
        section.set("pitch", (double) loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection section, String worldName) {
        World world = Bukkit.getWorld(worldName);
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0),
                (float) section.getDouble("pitch", 0)
        );
    }
}
