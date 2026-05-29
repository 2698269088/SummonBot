package top.mcocet.summonBot.bot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.metadata.FixedMetadataValue;
import top.mcocet.summonBot.SummonBot;
import top.mcocet.summonBot.database.DatabaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BotManager {
    
    private final SummonBot plugin;
    private final Map<String, Villager> activeBots; // bot名字 -> Villager实体
    private final ChunkLoadManager chunkLoadManager; // 区块加载管理器
    
    public BotManager(SummonBot plugin) {
        this.plugin = plugin;
        this.activeBots = new HashMap<>();
        this.chunkLoadManager = new ChunkLoadManager(plugin);
    }
    
    /**
     * 召唤一个bot
     * @param ownerUuid 主人UUID
     * @param ownerName 主人名字
     * @param botName bot名字
     * @param location 召唤位置
     * @return 是否成功
     */
    public boolean summonBot(UUID ownerUuid, String ownerName, String botName, Location location) {
        try {
            // 检查bot名字是否已存在（在活跃bot中）
            if (activeBots.containsKey(botName)) {
                return false;
            }
            
            // 在世界中生成村民
            World world = location.getWorld();
            if (world == null) {
                return false;
            }
            
            Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
            
            // 配置bot属性
            configureBot(villager, botName, ownerUuid);
            
            // 添加到活跃bot列表
            activeBots.put(botName, villager);
            
            // 注册bot到区块加载管理器（会自动加载周围区块）
            chunkLoadManager.registerBot(botName, villager);
            
            // 更新数据库中的最后活动时间
            plugin.getDatabaseManager().updateBotLastActive(botName);
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("召唤bot失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 配置bot的属性
     */
    private void configureBot(Villager villager, String botName, UUID ownerUuid) {
        // 设置自定义名称（格式：[Bot]testbot）
        villager.setCustomName("§e[Bot]" + botName);
        villager.setCustomNameVisible(true);
        
        // 禁用AI
        villager.setAI(false);
        
        // 不禁用静音（允许发出声音）
        // villager.setSilent(true);
        
        // 禁止拾取物品
        villager.setCanPickupItems(false);
        
        // 设置村民职业为无业（避免交易界面）
        villager.setProfession(Villager.Profession.NONE);
        
        // 移除所有AI相关的行为
        villager.setRemoveWhenFarAway(false); // 不会因为距离远而消失
        
        // 添加元数据标记
        villager.setMetadata("SummonBot", new FixedMetadataValue(plugin, "true"));
        villager.setMetadata("BotOwner", new FixedMetadataValue(plugin, ownerUuid.toString()));
        villager.setMetadata("BotName", new FixedMetadataValue(plugin, botName));
        
        // 设置血量为标准的20（10颗心）
        villager.setMaxHealth(20.0);
        villager.setHealth(20.0);
        
        // 设置为非无敌状态（可以受到伤害）
        villager.setInvulnerable(false);
        
        // 设置年龄为成人（避免变成小村民）
        villager.setAge(0);

        // 由于已经调用了 setAI(false)，AI目标已经被禁用
        // villager.clearGoals();
    }
    
    /**
     * 移除bot
     * @param botName bot名字
     * @return 是否成功
     */
    public boolean removeBot(String botName) {
        Villager villager = activeBots.remove(botName);
        
        if (villager != null) {
            // 从区块加载管理器中移除（会自动卸载周围区块）
            chunkLoadManager.unregisterBot(botName);
            
            // 如果bot还活着，先移除实体
            if (!villager.isDead()) {
                villager.remove();
            }
            // 删除数据库记录
            plugin.getDatabaseManager().removeBot(botName);
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取bot实体
     * @param botName bot名字
     * @return Villager实体，如果不存在返回null
     */
    public Villager getBot(String botName) {
        return activeBots.get(botName);
    }
    
    /**
     * 检查bot是否存在
     * @param botName bot名字
     * @return 是否存在
     */
    public boolean hasBot(String botName) {
        return activeBots.containsKey(botName);
    }
    
    /**
     * 获取玩家的所有bot名字
     * @param ownerUuid 主人UUID
     * @return bot名字列表
     */
    public java.util.List<String> getPlayerBotNames(UUID ownerUuid) {
        java.util.List<String> botNames = new java.util.ArrayList<>();
        
        for (Map.Entry<String, Villager> entry : activeBots.entrySet()) {
            Villager villager = entry.getValue();
            String owner = villager.getMetadata("BotOwner").isEmpty() ? 
                          null : villager.getMetadata("BotOwner").get(0).asString();
            
            if (owner != null && owner.equals(ownerUuid.toString())) {
                botNames.add(entry.getKey());
            }
        }
        
        return botNames;
    }
    
    /**
     * 从数据库恢复所有bot（服务器重启后调用）
     */
    public void restoreBotsFromDatabase() {
        java.util.List<top.mcocet.summonBot.database.DatabaseManager.BotInfo> allBots = 
            plugin.getDatabaseManager().getAllBots();
        
        if (allBots.isEmpty()) {
            plugin.getLogger().info("数据库中没有需要恢复的Bot");
            return;
        }
        
        // 先收集所有需要清理的旧bot实体（在恢复完成后再清理）
        List<Villager> oldBotsToRemove = collectOldBotEntities();
        
        // 先恢复所有bot，恢复完成后再清理旧实体
        scheduleRestoreBots(allBots, oldBotsToRemove);
    }
    
    /**
     * 收集世界中所有带有SummonBot元数据的旧实体
     */
    private List<Villager> collectOldBotEntities() {
        List<Villager> oldBots = new ArrayList<>();
        
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager && entity.hasMetadata("SummonBot")) {
                    oldBots.add((Villager) entity);
                }
            }
        }
        
        return oldBots;
    }
    
    /**
     * 调度恢复所有bot，恢复完成后清理旧实体
     */
    private void scheduleRestoreBots(java.util.List<top.mcocet.summonBot.database.DatabaseManager.BotInfo> allBots,
                                      List<Villager> oldBotsToRemove) {
        // 使用原子计数器跟踪异步恢复结果
        AtomicInteger restoredCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        AtomicInteger scheduledCount = new AtomicInteger(0);
        
        for (top.mcocet.summonBot.database.DatabaseManager.BotInfo botInfo : allBots) {
            try {
                // 检查世界是否存在
                org.bukkit.World world = plugin.getServer().getWorld(botInfo.getWorld());
                if (world == null) {
                    plugin.getLogger().warning("Bot '" + botInfo.getBotName() + "' 的世界 '" + 
                        botInfo.getWorld() + "' 不存在，跳过恢复");
                    failedCount.incrementAndGet();
                    continue;
                }
                
                // 检查该名字是否已经被占用（跳过旧实体，因为旧实体稍后会被清理）
                if (activeBots.containsKey(botInfo.getBotName())) {
                    plugin.getLogger().warning("Bot '" + botInfo.getBotName() + "' 的名字已被占用，跳过恢复");
                    failedCount.incrementAndGet();
                    continue;
                }
                
                // 创建位置
                Location location = botInfo.getLocation(world);
                
                // 使用RegionScheduler在正确的区域线程执行实体生成（Folia兼容性）
                scheduledCount.incrementAndGet();
                plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
                    try {
                        // 生成村民实体
                        Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
                        
                        // 配置bot属性
                        configureBot(villager, botInfo.getBotName(), botInfo.getOwnerUuid());
                        
                        // 添加到活跃bot列表
                        activeBots.put(botInfo.getBotName(), villager);
                        
                        // 注册到区块加载管理器
                        chunkLoadManager.registerBot(botInfo.getBotName(), villager);
                        
                        restoredCount.incrementAndGet();
                        plugin.getLogger().info("恢复Bot: " + botInfo.getBotName() + " (所有者: " + 
                            botInfo.getOwnerName() + ", 位置: " + location.getBlockX() + ", " + 
                            location.getBlockY() + ", " + location.getBlockZ() + ")");
                    } catch (Exception e) {
                        plugin.getLogger().severe("恢复Bot '" + botInfo.getBotName() + "' 失败: " + e.getMessage());
                        e.printStackTrace();
                        failedCount.incrementAndGet();
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("恢复Bot '" + botInfo.getBotName() + "' 失败: " + e.getMessage());
                e.printStackTrace();
                failedCount.incrementAndGet();
            }
        }
        
        plugin.getLogger().info("Bot恢复调度完成: 计划恢复 " + scheduledCount.get() + " 个Bot");
        
        // 延迟清理旧实体，确保新bot已经恢复完成
        if (!oldBotsToRemove.isEmpty()) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, (task) -> {
                int cleanedCount = 0;
                for (Villager oldVillager : oldBotsToRemove) {
                    Location loc = oldVillager.getLocation();
                    plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                        if (!oldVillager.isDead()) {
                            String oldBotName = oldVillager.getMetadata("BotName").isEmpty() ? 
                                               "unknown" : oldVillager.getMetadata("BotName").get(0).asString();
                            oldVillager.remove();
                            plugin.getLogger().info("清理残留旧Bot实体: " + oldBotName);
                        }
                    });
                }
                plugin.getLogger().info("已调度清理 " + oldBotsToRemove.size() + " 个残留旧Bot实体");
            }, 10L); // 延迟0.5秒，确保恢复任务已执行
        }
    }
    
    /**
     * 清理所有bot（插件卸载时调用）
     */
    public void cleanupAllBots() {
        for (Map.Entry<String, Villager> entry : activeBots.entrySet()) {
            String botName = entry.getKey();
            Villager villager = entry.getValue();
            
            if (villager != null && !villager.isDead()) {
                try {
                    // 更新数据库中bot的位置信息
                    plugin.getDatabaseManager().addBot(
                        UUID.fromString(villager.getMetadata("BotOwner").get(0).asString()),
                        "", // ownerName不需要更新
                        botName,
                        villager.getLocation()
                    );
                } catch (Exception e) {
                    plugin.getLogger().warning("保存bot '" + botName + "' 位置时出错: " + e.getMessage());
                }
            }
        }
        
        // 注意：不在这里移除实体，因为：
        // 1. 如果是插件重载，实体应该保留
        // 2. 如果是服务器关闭，服务器会自动清理所有实体
        // 3. 在服务器关闭时移除实体会导致空指针异常
        
        activeBots.clear();
        chunkLoadManager.cleanupAllLoadedChunks();
    }
    
    /**
     * 根据实体获取bot名字
     * @param entity 实体
     * @return bot名字，如果不是bot返回null
     */
    public String getBotNameByEntity(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof Villager)) {
            return null;
        }
        
        if (!entity.hasMetadata("SummonBot")) {
            return null;
        }
        
        return entity.getMetadata("BotName").isEmpty() ? 
               null : entity.getMetadata("BotName").get(0).asString();
    }
    
    /**
     * 获取活跃bot数量
     */
    public int getActiveBotCount() {
        return activeBots.size();
    }
    
    /**
     * 获取区块加载管理器
     */
    public ChunkLoadManager getChunkLoadManager() {
        return chunkLoadManager;
    }
}
