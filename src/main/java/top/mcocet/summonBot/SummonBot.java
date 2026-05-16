package top.mcocet.summonBot;

import org.bukkit.plugin.java.JavaPlugin;
import top.mcocet.summonBot.bot.BotManager;
import top.mcocet.summonBot.commands.SummonCommand;
import top.mcocet.summonBot.config.ConfigManager;
import top.mcocet.summonBot.database.DatabaseManager;

public final class SummonBot extends JavaPlugin {

    private static SummonBot instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private BotManager botManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置管理器
        configManager = new ConfigManager(this);
        getLogger().info("配置管理器已初始化");
        
        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);
        getLogger().info("数据库管理器已初始化");
        
        // 初始化Bot管理器
        botManager = new BotManager(this);
        getLogger().info("Bot管理器已初始化");
        
        // 从数据库恢复所有bot（服务器重启后）
        botManager.restoreBotsFromDatabase();
        
        // 启动区块强制加载任务
        if (getConfigManager().isChunkLoadEnabled()) {
            botManager.getChunkLoadManager().startChunkLoadTask();
        }
        
        // 注册命令
        registerCommands();
        
        // 注册事件监听器
        registerEvents();
        
        getLogger().info("SummonBot 插件已启用!");
    }

    @Override
    public void onDisable() {
        // 清理所有bot（会保存位置到数据库）
        if (botManager != null) {
            getLogger().info("正在清理所有bot...");
            botManager.cleanupAllBots();
            getLogger().info("Bot清理完成");
        }
        
        // 关闭数据库连接（必须在最后）
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("SummonBot 插件已禁用!");
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        getCommand("sbot").setExecutor(new SummonCommand(this));
        getLogger().info("命令已注册");
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new top.mcocet.summonBot.listener.BotListener(this), this);
        getServer().getPluginManager().registerEvents(new top.mcocet.summonBot.listener.BotDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new top.mcocet.summonBot.listener.BotTargetListener(this), this);
        getLogger().info("事件监听器已注册");
    }
    
    /**
     * 获取插件实例
     */
    public static SummonBot getInstance() {
        return instance;
    }
    
    /**
     * 获取配置管理器
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * 获取数据库管理器
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * 获取Bot管理器
     */
    public BotManager getBotManager() {
        return botManager;
    }
}
