package ru.privatenull.pnautomine.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import ru.privatenull.pnautomine.PnAutoMinePlugin;
import ru.privatenull.pnautomine.mine.Mine;
import ru.privatenull.pnautomine.mine.MineType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Bukkit sidebar scoreboard. Does not require TAB/PlaceholderAPI.
 */
public final class MineScoreboardService {
    private final PnAutoMinePlugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public MineScoreboardService(PnAutoMinePlugin plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        if (!isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        if (!isEnabled()) {
            clear(player);
            return;
        }

        Mine mine = plugin.getMineManager().getMineAt(player.getLocation());
        if (mine == null) {
            clear(player);
            return;
        }

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("pnautomine");
        if (objective == null) {
            objective = board.registerNewObjective("pnautomine", "dummy", color(plugin.getConfig().getString(
                    "scoreboard.title", "&b&lАВТОШАХТА")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        objective.setDisplayName(color(plugin.getConfig().getString("scoreboard.title", "&b&lАВТОШАХТА")));

        // Recreate the sidebar every tick to guarantee that stale lines never remain.
        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        List<String> configured = plugin.getConfig().getStringList("scoreboard.lines");
        if (configured.isEmpty()) {
            configured = List.of(
                    "&7Шахта: &f{mine_name}",
                    "&7Тип: {mine_type_display}",
                    "&7Осталось: &f{blocks_remaining}/{blocks_total}",
                    "&7Сброс: &e{reset_time}"
            );
        }

        int score = configured.size();
        int index = 0;
        for (String raw : configured) {
            String line = color(replace(raw, mine, player));
            if (line.isEmpty()) line = " ";
            // A scoreboard entry must be unique. Invisible color suffixes make equal lines safe.
            line = makeUnique(line, index++);
            objective.getScore(line).setScore(score--);
            if (score < 0) break;
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    public void clear(Player player) {
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board != null && player.getScoreboard() == board) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void remove(Player player) {
        clear(player);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        boards.clear();
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("scoreboard.enabled", true) && plugin.getConfig().getString("scoreboard.provider", "internal").equalsIgnoreCase("internal");
    }

    private String replace(String text, Mine mine, Player player) {
        MineType next = plugin.getMineManager().getNextType(mine);
        return text
                .replace("{mine_id}", mine.getId())
                .replace("{mine_name}", plain(mine.getDisplayName()))
                .replace("{mine_type}", mine.getTypeName())
                .replace("{mine_type_display}", plain(mine.getDisplayName()))
                .replace("{next_type}", next == null ? "" : next.getId())
                .replace("{next_type_display}", plugin.getConfig().getBoolean("random-mine-type.enabled", true) ? "Случайно" : (next == null ? "" : plain(next.getDisplayName())))
                .replace("{blocks_remaining}", String.valueOf(mine.getRemainingBlocks()))
                .replace("{blocks_total}", String.valueOf(mine.getTotalBlocks()))
                .replace("{blocks_mined}", String.valueOf(mine.getMinedBlocks()))
                .replace("{blocks_percentage}", format(mine.getPercentageRemaining()))
                .replace("{percentage_mined}", format(mine.getPercentageMined()))
                .replace("{reset_time}", mine.getFormattedTimeUntilReset())
                .replace("{reset_time_seconds}", String.valueOf(mine.getSecondsUntilReset()))
                .replace("{player}", player.getName());
    }

    private String makeUnique(String line, int index) {
        return line + ChatColor.COLOR_CHAR + Integer.toHexString(index & 15);
    }

    private String plain(String value) {
        if (value == null) return "";
        return ChatColor.stripColor(color(value));
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String color(String value) {
        if (value == null) return "";
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
