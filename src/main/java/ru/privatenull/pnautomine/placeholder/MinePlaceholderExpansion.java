package ru.privatenull.pnautomine.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.pnautomine.PnAutoMinePlugin;
import ru.privatenull.pnautomine.mine.Mine;
import ru.privatenull.pnautomine.mine.MineType;
import ru.privatenull.pnautomine.util.ColorUtil;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/**
 * PlaceholderAPI expansion for pnAutoMine.
 *
 * Placeholders:
 *   %pnautomine_<mine_id>_name%           - Display name of the mine
 *   %pnautomine_<mine_id>_type%           - Type ID
 *   %pnautomine_<mine_id>_type_display%   - Type display name (colored)
 *   %pnautomine_<mine_id>_blocks_total%   - Total blocks
 *   %pnautomine_<mine_id>_blocks_remaining% - Remaining blocks
 *   %pnautomine_<mine_id>_blocks_mined%   - Mined blocks
 *   %pnautomine_<mine_id>_percentage%     - Percentage remaining
 *   %pnautomine_<mine_id>_percentage_mined% - Percentage mined
 *   %pnautomine_<mine_id>_reset_time%     - Formatted time until reset
 *   %pnautomine_<mine_id>_reset_seconds%  - Seconds until reset (raw)
 *   %pnautomine_<mine_id>_reset_interval% - Reset interval in seconds
 *   %pnautomine_current_<value>%           - Value for the mine containing the player
 *   %pnautomine_current_exists%            - Whether the player is inside a mine
 *   %pnautomine_current_next_type%         - Type selected on the next reset
 *   %pnautomine_current_player_mined_<id>% - Player blocks mined in this reset cycle
 *   %pnautomine_next_mine_<value>%         - Mine whose reset is scheduled next
 *   %pnautomine_mine_count%                - Number of loaded mines
 */
public final class MinePlaceholderExpansion extends PlaceholderExpansion {

    private final PnAutoMinePlugin plugin;

    public MinePlaceholderExpansion(PnAutoMinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pnautomine";
    }

    @Override
    public @NotNull String getAuthor() {
        return "privatenull";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("mine_count")) {
            return String.valueOf(plugin.getMineManager().getAllMines().size());
        }

        if (params.regionMatches(true, 0, "next_mine_", 0, "next_mine_".length())) {
            Mine nextMine = plugin.getMineManager().getNextMineToReset();
            if (nextMine == null) return "";
            String placeholder = params.substring("next_mine_".length()).toLowerCase(Locale.ROOT);
            return resolvePlaceholder(nextMine, player, placeholder);
        }

        if (params.regionMatches(true, 0, "current_", 0, "current_".length())) {
            String placeholder = params.substring("current_".length()).toLowerCase(Locale.ROOT);
            Mine currentMine = player == null ? null : plugin.getMineManager().getMineAt(player.getLocation());
            if (placeholder.equals("exists")) {
                return String.valueOf(currentMine != null);
            }
            return currentMine == null ? "" : resolvePlaceholder(currentMine, player, placeholder);
        }

        // Mine IDs may contain underscores and one ID may prefix another.
        // Matching the longest ID first keeps placeholders deterministic.
        Mine mine = plugin.getMineManager().getAllMines().stream()
                .filter(candidate -> params.startsWith(candidate.getId() + "_"))
                .max(Comparator.comparingInt(candidate -> candidate.getId().length()))
                .orElse(null);
        if (mine == null) return null;

        String placeholder = params.substring(mine.getId().length() + 1).toLowerCase(Locale.ROOT);
        return resolvePlaceholder(mine, player, placeholder);
    }

    private String resolvePlaceholder(Mine mine, Player player, String placeholder) {
        String exactValue = switch (placeholder) {
            case "id" -> mine.getId();
            case "name" -> mine.getDisplayName() != null ? ColorUtil.colorize(mine.getDisplayName()) : mine.getId();
            case "type" -> mine.getTypeName();
            case "type_display" -> {
                MineType type = plugin.getMineTypes().getType(mine.getTypeName());
                yield type != null ? ColorUtil.colorize(type.getDisplayName()) : mine.getTypeName();
            }
            case "next_type" -> {
                if (plugin.getConfig().getBoolean("random-mine-type.enabled", true)) {
                    yield "random";
                }
                MineType type = plugin.getMineManager().getNextType(mine);
                yield type == null ? "" : type.getId();
            }
            case "next_type_display" -> {
                if (plugin.getConfig().getBoolean("random-mine-type.enabled", true)) {
                    yield ColorUtil.colorize("&fСлучайный тип");
                }
                MineType type = plugin.getMineManager().getNextType(mine);
                yield type == null ? "" : ColorUtil.colorize(type.getDisplayName());
            }
            case "blocks_total" -> String.valueOf(mine.getTotalBlocks());
            case "blocks_remaining" -> String.valueOf(mine.getRemainingBlocks());
            case "blocks_mined" -> String.valueOf(mine.getMinedBlocks());
            case "percentage" -> String.format(Locale.ROOT, "%.1f", mine.getPercentageRemaining());
            case "percentage_mined" -> String.format(Locale.ROOT, "%.1f", mine.getPercentageMined());
            case "reset_time" -> mine.getFormattedTimeUntilReset();
            case "reset_seconds" -> String.valueOf(mine.getSecondsUntilReset());
            case "reset_interval" -> String.valueOf(mine.getResetInterval());
            case "world" -> mine.getWorldName();
            default -> null;
        };
        if (exactValue != null) return exactValue;

        if (placeholder.equals("player_earnings") || placeholder.equals("player_salary")) {
            if (player == null) return "0";
            double earnings = plugin.getMiningStats().calculateEarnings(
                    mine.getPlayerMinedBlocksSnapshot(player.getUniqueId()));
            return plugin.getMiningStats().formatEarnings(earnings);
        }

        if (placeholder.startsWith("player_mined_")) {
            if (player == null) return "0";
            String groupOrMaterial = placeholder.substring("player_mined_".length());
            if (groupOrMaterial.equals("total")) {
                return String.valueOf(mine.getPlayerMinedBlocks(player.getUniqueId()));
            }
            Collection<Material> materials = plugin.getMiningStats()
                    .resolveMaterials(groupOrMaterial);
            return String.valueOf(mine.getPlayerMinedBlocks(player.getUniqueId(), materials));
        }

        String groupOrMaterial = null;
        if (placeholder.startsWith("blocks_mined_")) {
            groupOrMaterial = placeholder.substring("blocks_mined_".length());
        } else if (placeholder.startsWith("mined_")) {
            groupOrMaterial = placeholder.substring("mined_".length());
        }
        if (groupOrMaterial != null) {
            if (groupOrMaterial.equals("total")) return String.valueOf(mine.getMinedBlocks());
            return String.valueOf(mine.getMinedBlocks(
                    plugin.getMiningStats().resolveMaterials(groupOrMaterial)));
        }

        return null;
    }
}
