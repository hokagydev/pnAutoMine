package ru.privatenull.pnautomine.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.privatenull.pnautomine.PnAutoMinePlugin;
import ru.privatenull.pnautomine.config.MessagesConfig;
import ru.privatenull.pnautomine.mine.Mine;
import ru.privatenull.pnautomine.mine.MineManager;
import ru.privatenull.pnautomine.mine.MineRegion;
import ru.privatenull.pnautomine.mine.MineType;
import ru.privatenull.pnautomine.worldedit.WorldEditAdapter;
import ru.privatenull.pnautomine.util.ColorUtil;

/**
 * Main command handler for /pnautomine (aliases: /mine, /mines, /am).
 */
public final class MineCommand implements CommandExecutor {

    private final PnAutoMinePlugin plugin;

    public MineCommand(PnAutoMinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "reset" -> handleReset(sender, args);
            case "resetall" -> handleResetAll(sender);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "setspawn", "settp" -> handleSetSpawn(sender, args);
            case "sethologram", "setholo" -> handleSetHologram(sender, args);
            case "reload" -> handleReload(sender);
            case "types" -> handleTypes(sender);
            default -> sender.sendMessage(msg.get("unknown-command"));
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessagesConfig msg = plugin.getMessages();
        sender.sendMessage(msg.get("help-header"));
        sender.sendMessage(msg.get("help-create"));
        sender.sendMessage(msg.get("help-delete"));
        sender.sendMessage(msg.get("help-reset"));
        sender.sendMessage(msg.get("help-resetall"));
        sender.sendMessage(msg.get("help-list"));
        sender.sendMessage(msg.get("help-info"));
        sender.sendMessage(msg.get("help-setspawn"));
        sender.sendMessage(msg.get("help-sethologram"));
        sender.sendMessage(msg.get("help-reload"));
        sender.sendMessage(msg.get("help-types"));
        sender.sendMessage(msg.get("help-footer"));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("player-only"));
            return;
        }

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ColorUtil.colorize("&cИспользование: /mine create <id> <type>"));
            return;
        }

        String id = args[1].toLowerCase();
        String typeName = args[2].toLowerCase();

        if (plugin.getMineManager().hasMine(id)) {
            sender.sendMessage(msg.get("create-already-exists", "id", id));
            return;
        }

        if (!plugin.getMineTypes().hasType(typeName)) {
            sender.sendMessage(msg.get("create-invalid-type", "type", typeName));
            return;
        }

        // Get WorldEdit selection
        MineRegion region = WorldEditAdapter.getSelection(player);
        if (region == null) {
            sender.sendMessage(msg.get("create-no-selection"));
            return;
        }

        Mine mine = plugin.getMineManager().createMine(id, typeName, region, player.getWorld().getName());
        if (mine == null) {
            sender.sendMessage(msg.get("create-already-exists", "id", id));
            return;
        }

        MineType type = plugin.getMineTypes().getType(typeName);
        String typeDisplay = type != null ? type.getDisplayName() : typeName;
        sender.sendMessage(msg.get("create-success", "id", id, "type", ColorUtil.colorize(typeDisplay)));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cИспользование: /mine delete <id>"));
            return;
        }

        String id = args[1].toLowerCase();
        if (plugin.getMineManager().deleteMine(id)) {
            sender.sendMessage(msg.get("delete-success", "id", id));
        } else {
            sender.sendMessage(msg.get("delete-not-found", "id", id));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cИспользование: /mine reset <id>"));
            return;
        }

        String id = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(id);
        if (mine == null) {
            sender.sendMessage(msg.get("reset-not-found", "id", id));
            return;
        }

        plugin.getMineManager().resetMine(mine);
        sender.sendMessage(msg.get("reset-success", "id", id));
    }

    private void handleResetAll(CommandSender sender) {
        MessagesConfig msg = plugin.getMessages();

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        plugin.getMineManager().resetAllMines();
        sender.sendMessage(msg.get("resetall-success"));
    }

    private void handleList(CommandSender sender) {
        MessagesConfig msg = plugin.getMessages();
        MineManager manager = plugin.getMineManager();

        if (manager.getAllMines().isEmpty()) {
            sender.sendMessage(msg.get("list-empty"));
            return;
        }

        sender.sendMessage(msg.get("list-header"));
        for (Mine mine : manager.getAllMines()) {
            MineType type = plugin.getMineTypes().getType(mine.getTypeName());
            String typeDisplay = type != null ? type.getDisplayName() : mine.getTypeName();
            sender.sendMessage(msg.get("list-entry",
                    "id", mine.getId(),
                    "type", ColorUtil.colorize(typeDisplay),
                    "remaining", String.valueOf(mine.getRemainingBlocks()),
                    "total", String.valueOf(mine.getTotalBlocks())));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cИспользование: /mine info <id>"));
            return;
        }

        String id = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(id);
        if (mine == null) {
            sender.sendMessage(msg.get("reset-not-found", "id", id));
            return;
        }

        MineType type = plugin.getMineTypes().getType(mine.getTypeName());
        String typeDisplay = type != null ? type.getDisplayName() : mine.getTypeName();
        String spawnStr = mine.getSpawnLocation() != null
                ? String.format("%.1f, %.1f, %.1f",
                mine.getSpawnLocation().getX(),
                mine.getSpawnLocation().getY(),
                mine.getSpawnLocation().getZ())
                : "Не установлен";

        sender.sendMessage(msg.get("info-header", "id", mine.getId()));
        sender.sendMessage(msg.get("info-type", "type", ColorUtil.colorize(typeDisplay)));
        sender.sendMessage(msg.get("info-world", "world", mine.getWorldName()));
        sender.sendMessage(msg.get("info-blocks",
                "remaining", String.valueOf(mine.getRemainingBlocks()),
                "total", String.valueOf(mine.getTotalBlocks())));
        sender.sendMessage(msg.get("info-reset-interval", "interval", String.valueOf(mine.getResetInterval())));
        sender.sendMessage(msg.get("info-next-reset", "time", mine.getFormattedTimeUntilReset()));
        sender.sendMessage(msg.get("info-spawn", "spawn", spawnStr));
        sender.sendMessage(msg.get("info-footer"));
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("player-only"));
            return;
        }

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cИспользование: /mine setspawn <id>"));
            return;
        }

        String id = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(id);
        if (mine == null) {
            sender.sendMessage(msg.get("setspawn-not-found", "id", id));
            return;
        }

        mine.setSpawnLocation(player.getLocation());
        plugin.getMineManager().saveMine(mine);
        sender.sendMessage(msg.get("setspawn-success", "id", id));
    }

    private void handleSetHologram(CommandSender sender, String[] args) {
        MessagesConfig msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("player-only"));
            return;
        }

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cИспользование: /mine sethologram <id>"));
            return;
        }

        String id = args[1].toLowerCase();
        Mine mine = plugin.getMineManager().getMine(id);
        if (mine == null) {
            sender.sendMessage(msg.get("sethologram-not-found", "id", id));
            return;
        }

        mine.setHologramLocation(player.getLocation());
        plugin.getMineManager().saveMine(mine);
        plugin.getHolograms().updateMineHologram(mine);
        sender.sendMessage(msg.get("sethologram-success", "id", id));
    }

    private void handleReload(CommandSender sender) {
        MessagesConfig msg = plugin.getMessages();

        if (!sender.hasPermission("pnautomine.admin")) {
            sender.sendMessage(msg.get("no-permission"));
            return;
        }

        plugin.reloadPlugin();
        sender.sendMessage(plugin.getMessages().get("reload-success"));
    }

    private void handleTypes(CommandSender sender) {
        MessagesConfig msg = plugin.getMessages();
        sender.sendMessage(msg.get("types-header"));

        for (MineType type : plugin.getMineTypes().getAllTypes()) {
            sender.sendMessage(msg.get("types-entry",
                    "display_name", ColorUtil.colorize(type.getDisplayName()),
                    "id", type.getId()));
        }
    }
}
