package top.mcocet.summonBot.bot;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import top.mcocet.summonBot.SummonBot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChunkLoadManager {
    
    private final SummonBot plugin;
    private final Map<String, Villager> botChunks; // bot名字 -> bot实体映射
    private ScheduledExecutorService scheduler; // 定时任务执行器
    
    public ChunkLoadManager(SummonBot plugin) {
        this.plugin = plugin;
        this.botChunks = new ConcurrentHashMap<>();
    }
    
    /**
     * 启动定时任务，持续加载bot周围的区块
     */
    public void startChunkLoadTask() {
        if (!plugin.getConfigManager().isChunkLoadEnabled()) {
            return;
        }
        
        // 创建单线程调度器
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SummonBot-ChunkLoader");
            thread.setDaemon(true);
            return thread;
        });
        
        // 每3秒执行一次，确保bot周围区块持续加载
        // 注意：间隔不能太长，否则区块可能在两次检查之间被卸载
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int loadedCount = 0;
                for (Villager bot : botChunks.values()) {
                    if (bot != null && !bot.isDead()) {
                        // 使用 RegionScheduler 在正确的区域线程执行区块加载
                        org.bukkit.Location loc = bot.getLocation();
                        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                            forceLoadChunksAroundBot(bot);
                        });
                        loadedCount++;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("区块加载任务执行出错: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 3, TimeUnit.SECONDS);
        
        plugin.getLogger().info("区块强制加载任务已启动");
    }
    
    /**
     * 停止定时任务
     */
    public void stopChunkLoadTask() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
            plugin.getLogger().info("区块强制加载任务已停止");
        }
    }
    
    /**
     * 注册bot到区块加载管理器
     * @param botName bot名字
     * @param bot bot实体
     */
    public void registerBot(String botName, Villager bot) {
        botChunks.put(botName, bot);
        
        // 立即强制加载周围区块（使用RegionScheduler确保Folia兼容性）
        org.bukkit.Location loc = bot.getLocation();
        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
            forceLoadChunksAroundBot(bot);
            plugin.getLogger().info("Bot '" + botName + "' 的区块已强制加载 (位置: " + 
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
        });
    }
    
    /**
     * 从区块加载管理器中移除bot
     * @param botName bot名字
     */
    public void unregisterBot(String botName) {
        Villager bot = botChunks.remove(botName);
        if (bot != null) {
            // 使用 RegionScheduler 确保在正确的区域线程执行（Folia兼容性）
            org.bukkit.Location loc = bot.getLocation();
            plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                unloadChunksAroundBot(bot);
            });
        }
    }
    
    /**
     * 强制加载bot周围的区块（不考虑是否有玩家）
     * @param bot bot实体
     */
    private void forceLoadChunksAroundBot(Villager bot) {
        int radius = plugin.getConfigManager().getChunkLoadRadius();
        Location location = bot.getLocation();
        World world = location.getWorld();
        
        if (world == null) {
            return;
        }
        
        int botChunkX = location.getBlockX() >> 4;
        int botChunkZ = location.getBlockZ() >> 4;
        
        // 加载以bot为中心的半径范围内的所有区块
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = botChunkX + x;
                int chunkZ = botChunkZ + z;
                
                try {
                    // 获取区块
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    
                    // 1. 确保区块被加载
                    if (!chunk.isLoaded()) {
                        chunk.load();
                    }
                    
                    // 2. 添加插件区块票据，保持实体tick
                    // 这是关键！没有这个，即使区块加载，实体也不会tick
                    chunk.addPluginChunkTicket(plugin);
                } catch (Exception e) {
                    // 忽略加载错误（可能是服务器关闭时）
                    plugin.getLogger().warning("加载区块失败 (" + chunkX + ", " + chunkZ + "): " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 卸载bot周围的区块（当bot被移除时调用）
     * @param bot bot实体
     */
    public void unloadChunksAroundBot(Villager bot) {
        if (!plugin.getConfigManager().isChunkLoadEnabled()) {
            return;
        }
        
        int radius = plugin.getConfigManager().getChunkLoadRadius();
        Location location = bot.getLocation();
        World world = location.getWorld();
        
        if (world == null) {
            return;
        }
        
        // 检查服务器是否正在关闭，如果是则跳过卸载操作
        if (!plugin.isEnabled()) {
            return;
        }
        
        int botChunkX = location.getBlockX() >> 4;
        int botChunkZ = location.getBlockZ() >> 4;
        
        // 卸载以bot为中心的半径范围内的所有区块
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = botChunkX + x;
                int chunkZ = botChunkZ + z;
                
                try {
                    // 获取区块
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    
                    // 移除插件区块票据
                    // 当所有票据都被移除后，服务器会自动卸载区块
                    chunk.removePluginChunkTicket(plugin);
                } catch (Exception e) {
                    // 捕获可能的异常（如服务器关闭时的空指针异常）
                    plugin.getLogger().warning("处理区块时出错 (" + chunkX + ", " + chunkZ + "): " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 清理所有加载的区块记录
     */
    public void cleanupAllLoadedChunks() {
        // 停止定时任务
        stopChunkLoadTask();
        
        // 清空bot映射
        botChunks.clear();
    }
}
