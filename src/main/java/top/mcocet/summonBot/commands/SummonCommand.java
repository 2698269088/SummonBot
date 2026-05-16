package top.mcocet.summonBot.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import top.mcocet.summonBot.SummonBot;
import top.mcocet.summonBot.database.DatabaseManager;

import java.util.List;

public class SummonCommand implements CommandExecutor {
    
    private final SummonBot plugin;
    
    public SummonCommand(SummonBot plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "sum":
                handleSum(sender, args);
                break;
            case "uns":
                handleUns(sender, args);
                break;
            case "list":
                handleList(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    /**
     * 处理召唤bot命令 /sbot sum [Bot名字]
     */
    private void handleSum(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家才能召唤bot!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /sbot sum <Bot名字>");
            return;
        }
        
        Player player = (Player) sender;
        String botName = args[1];
        
        // 检查是否为管理员
        boolean isAdmin = player.hasPermission("summonbot.admin");
        
        // 检查bot名字是否已存在（在活跃bot中）
        if (plugin.getBotManager().hasBot(botName)) {
            player.sendMessage(ChatColor.RED + "Bot名字 '" + botName + "' 已被使用!");
            return;
        }
        
        // 如果不是管理员，检查bot数量限制
        if (!isAdmin) {
            // 获取配置中的最大bot数量
            int maxBots = plugin.getConfigManager().getMaxBotsPerPlayer();
            
            // 获取玩家当前的bot数量（从BotManager中查询活跃bot）
            int currentBots = plugin.getBotManager().getPlayerBotNames(player.getUniqueId()).size();
            
            // 检查是否达到上限
            if (currentBots >= maxBots) {
                player.sendMessage(ChatColor.RED + "你已达到最大bot数量限制 (" + maxBots + ")!");
                return;
            }
        } else {
            // 管理员无限制
            player.sendMessage(ChatColor.GOLD + "管理员模式: 无bot数量限制");
        }
        
        // 注册玩家信息
        plugin.getDatabaseManager().registerPlayer(player);
        
        // 添加bot记录到数据库
        boolean dbSuccess = plugin.getDatabaseManager().addBot(
            player.getUniqueId(),
            player.getName(),
            botName,
            player.getLocation()
        );
        
        if (!dbSuccess) {
            player.sendMessage(ChatColor.RED + "创建bot记录失败!");
            return;
        }
        
        // 召唤bot实体
        boolean summonSuccess = plugin.getBotManager().summonBot(
            player.getUniqueId(),
            player.getName(),
            botName,
            player.getLocation()
        );
        
        if (summonSuccess) {
            player.sendMessage(ChatColor.GREEN + "成功召唤bot: " + botName);
            if (!isAdmin) {
                int maxBots = plugin.getConfigManager().getMaxBotsPerPlayer();
                int currentBots = plugin.getBotManager().getPlayerBotNames(player.getUniqueId()).size();
                player.sendMessage(ChatColor.GRAY + "当前bot数量: " + currentBots + "/" + maxBots);
            } else {
                int totalBots = plugin.getBotManager().getActiveBotCount();
                player.sendMessage(ChatColor.GRAY + "服务器总bot数量: " + totalBots);
            }
        } else {
            player.sendMessage(ChatColor.RED + "召唤bot实体失败，但数据库记录已创建");
            // 可以选择回滚数据库操作
        }
    }
    
    /**
     * 处理移除bot命令 /sbot uns [Bot名字]
     */
    private void handleUns(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家才能移除此命令!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /sbot uns <Bot名字>");
            return;
        }
        
        Player player = (Player) sender;
        String botName = args[1];
        
        // 检查是否为管理员
        boolean isAdmin = sender.hasPermission("summonbot.admin");
        
        DatabaseManager.BotInfo botInfo = null;
        
        if (isAdmin) {
            // 管理员可以移除任何bot
            botInfo = plugin.getDatabaseManager().getBotByName(botName);
            if (botInfo == null) {
                sender.sendMessage(ChatColor.RED + "未找到名为 '" + botName + "' 的bot!");
                return;
            }
        } else {
            // 普通玩家只能移除自己的bot
            List<DatabaseManager.BotInfo> playerBots = plugin.getDatabaseManager().getPlayerBots(player.getUniqueId());
            boolean found = false;
            for (DatabaseManager.BotInfo bot : playerBots) {
                if (bot.getBotName().equalsIgnoreCase(botName)) {
                    botInfo = bot;
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                player.sendMessage(ChatColor.RED + "你没有一个名为 '" + botName + "' 的bot!");
                return;
            }
        }
        
        // 移除bot实体和记录
        boolean success = plugin.getBotManager().removeBot(botName);
        
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "成功移除bot: " + botName);
            if (isAdmin && botInfo != null && !botInfo.getOwnerUuid().equals(player.getUniqueId())) {
                sender.sendMessage(ChatColor.GRAY + "Bot主人: " + botInfo.getOwnerName());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "移除bot失败!");
        }
    }
    
    /**
     * 处理查看bot列表命令 /sbot list
     */
    private void handleList(CommandSender sender, String[] args) {
        // 检查权限（需要管理员权限）
        if (!sender.hasPermission("summonbot.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限查看bot列表!");
            return;
        }
        
        // 如果有指定玩家名字，查看特定玩家的bot
        if (args.length >= 2) {
            String playerName = args[1];
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
            
            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                sender.sendMessage(ChatColor.RED + "未找到玩家: " + playerName);
                return;
            }
            
            List<DatabaseManager.BotInfo> bots = plugin.getDatabaseManager().getPlayerBots(targetPlayer.getUniqueId());
            
            sender.sendMessage(ChatColor.GREEN + "=== 玩家 " + targetPlayer.getName() + " 的Bot列表 ===");
            
            if (bots.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "该玩家没有召唤任何bot");
            } else {
                for (DatabaseManager.BotInfo bot : bots) {
                    sender.sendMessage(ChatColor.AQUA + "- " + bot.getBotName() + 
                                     ChatColor.GRAY + " (位置: " + bot.getWorld() + " " +
                                     String.format("%.1f, %.1f, %.1f", bot.getX(), bot.getY(), bot.getZ()) + ")");
                }
                sender.sendMessage(ChatColor.GRAY + "总计: " + bots.size() + " 个bot");
            }
        } else {
            // 查看所有bot
            List<DatabaseManager.BotInfo> allBots = plugin.getDatabaseManager().getAllBots();
            
            sender.sendMessage(ChatColor.GREEN + "=== 所有Bot列表 ===");
            
            if (allBots.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "当前没有bot");
            } else {
                // 按主人分组显示
                java.util.Map<String, java.util.List<DatabaseManager.BotInfo>> botsByOwner = new java.util.HashMap<>();
                for (DatabaseManager.BotInfo bot : allBots) {
                    String ownerName = bot.getOwnerName();
                    botsByOwner.computeIfAbsent(ownerName, k -> new java.util.ArrayList<>()).add(bot);
                }
                
                for (java.util.Map.Entry<String, java.util.List<DatabaseManager.BotInfo>> entry : botsByOwner.entrySet()) {
                    sender.sendMessage(ChatColor.YELLOW + "玩家: " + entry.getKey() + " (" + entry.getValue().size() + " 个bot)");
                    for (DatabaseManager.BotInfo bot : entry.getValue()) {
                        sender.sendMessage(ChatColor.AQUA + "  - " + bot.getBotName() + 
                                         ChatColor.GRAY + " (" + bot.getWorld() + " " +
                                         String.format("%.1f, %.1f, %.1f", bot.getX(), bot.getY(), bot.getZ()) + ")");
                    }
                }
                sender.sendMessage(ChatColor.GRAY + "总计: " + allBots.size() + " 个bot");
            }
        }
    }
    
    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== SummonBot 帮助 ===");
        sender.sendMessage(ChatColor.YELLOW + "/sbot sum <Bot名字> - 召唤一个bot");
        sender.sendMessage(ChatColor.YELLOW + "/sbot uns <Bot名字> - 移除你的bot");
        if (sender.hasPermission("summonbot.admin")) {
            sender.sendMessage(ChatColor.GOLD + "--- 管理员命令 ---");
            sender.sendMessage(ChatColor.YELLOW + "/sbot list [玩家] - 查看bot列表");
            sender.sendMessage(ChatColor.YELLOW + "/sbot uns <Bot名字> - 移除任意bot");
            sender.sendMessage(ChatColor.GOLD + "管理员特权: 无bot数量限制");
        }
    }
}
