package top.mcocet.summonBot.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import top.mcocet.summonBot.SummonBot;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    
    private final SummonBot plugin;
    private FileConfiguration config;
    private File configFile;
    
    public ConfigManager(SummonBot plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // 设置默认值
        addDefault("max-bots-per-player", 3, "每个玩家最多可以召唤的bot数量");
        addDefault("database.auto-save-interval", 300, "数据库自动保存间隔（秒）");
        addDefault("bot.idle-timeout", 600, "bot空闲超时时间（秒），0表示不超时");
        addDefault("bot.chunk-load.enabled", true, "是否启用bot周围区块强制加载");
        addDefault("bot.chunk-load.radius", 3, "加载半径（以bot为中心的区块半径，1-10）");
        
        saveConfig();
    }
    
    /**
     * 添加默认配置项
     */
    private void addDefault(String path, Object value, String comment) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }
    
    /**
     * 保存配置文件
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存配置文件: " + e.getMessage());
        }
    }
    
    /**
     * 重新加载配置文件
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadConfig();
    }
    
    /**
     * 获取每个玩家最多可以召唤的bot数量
     * @return 最大bot数量
     */
    public int getMaxBotsPerPlayer() {
        return config.getInt("max-bots-per-player", 3);
    }
    
    /**
     * 设置每个玩家最多可以召唤的bot数量
     * @param maxBots 最大bot数量
     */
    public void setMaxBotsPerPlayer(int maxBots) {
        config.set("max-bots-per-player", maxBots);
        saveConfig();
    }
    
    /**
     * 获取数据库自动保存间隔
     * @return 自动保存间隔（秒）
     */
    public int getAutoSaveInterval() {
        return config.getInt("database.auto-save-interval", 300);
    }
    
    /**
     * 获取bot空闲超时时间
     * @return 超时时间（秒），0表示不超时
     */
    public int getIdleTimeout() {
        return config.getInt("bot.idle-timeout", 600);
    }
    
    /**
     * 是否启用bot周围区块强制加载
     * @return 是否启用
     */
    public boolean isChunkLoadEnabled() {
        return config.getBoolean("bot.chunk-load.enabled", true);
    }
    
    /**
     * 获取bot周围区块加载半径
     * @return 加载半径（1-10）
     */
    public int getChunkLoadRadius() {
        int radius = config.getInt("bot.chunk-load.radius", 3);
        // 限制半径范围在1-10之间
        return Math.max(1, Math.min(10, radius));
    }
    
    /**
     * 获取配置文件对象
     * @return FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
