package top.mcocet.summonBot.database;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import top.mcocet.summonBot.SummonBot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    
    private final SummonBot plugin;
    private Connection connection;
    private final String dbPath;
    
    public DatabaseManager(SummonBot plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + "/bots.db";
        initializeDatabase();
    }
    
    /**
     * 初始化数据库连接和表结构
     */
    private void initializeDatabase() {
        try {
            // 加载SQLite驱动
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            // 启用外键支持
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            
            // 检查是否需要迁移数据库（移除is_active字段）
            migrateDatabase();
            
            // 创建bots表
            String createTableSQL = "CREATE TABLE IF NOT EXISTS bots (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT NOT NULL," +
                    "owner_name TEXT NOT NULL," +
                    "bot_name TEXT NOT NULL UNIQUE," +
                    "world TEXT NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL DEFAULT 0," +
                    "pitch FLOAT NOT NULL DEFAULT 0," +
                    "spawn_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (owner_uuid) REFERENCES players(uuid)" +
                    ")";
            
            connection.createStatement().execute(createTableSQL);
            
            // 创建players表（用于追踪玩家）
            String createPlayersTableSQL = "CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            
            connection.createStatement().execute(createPlayersTableSQL);
            
            plugin.getLogger().info("数据库初始化成功");
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("无法找到SQLite驱动: " + e.getMessage());
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 迁移数据库（移除is_active字段）
     */
    private void migrateDatabase() {
        try {
            // 检查旧表是否存在is_active字段
            ResultSet rs = connection.createStatement().executeQuery("PRAGMA table_info(bots)");
            boolean hasIsActive = false;
            
            while (rs.next()) {
                if ("is_active".equals(rs.getString("name"))) {
                    hasIsActive = true;
                    break;
                }
            }
            rs.close();
            
            if (hasIsActive) {
                plugin.getLogger().info("检测到旧版数据库，正在迁移...");
                
                // 创建新表
                connection.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS bots_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT NOT NULL," +
                    "owner_name TEXT NOT NULL," +
                    "bot_name TEXT NOT NULL UNIQUE," +
                    "world TEXT NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL DEFAULT 0," +
                    "pitch FLOAT NOT NULL DEFAULT 0," +
                    "spawn_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (owner_uuid) REFERENCES players(uuid)" +
                    ")"
                );
                
                // 复制数据（排除is_active字段）
                connection.createStatement().execute(
                    "INSERT OR IGNORE INTO bots_new (id, owner_uuid, owner_name, bot_name, world, x, y, z, yaw, pitch, spawn_time, last_active) " +
                    "SELECT id, owner_uuid, owner_name, bot_name, world, x, y, z, yaw, pitch, spawn_time, last_active FROM bots"
                );
                
                // 删除旧表
                connection.createStatement().execute("DROP TABLE IF EXISTS bots");
                
                // 重命名新表
                connection.createStatement().execute("ALTER TABLE bots_new RENAME TO bots");
                
                plugin.getLogger().info("数据库迁移完成");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("数据库迁移失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取数据库连接失败: " + e.getMessage());
        }
        return connection;
    }
    
    /**
     * 注册或更新玩家信息
     */
    public void registerPlayer(OfflinePlayer player) {
        String sql = "INSERT OR REPLACE INTO players (uuid, name, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, player.getName());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("注册玩家失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加bot记录
     */
    public boolean addBot(UUID ownerUuid, String ownerName, String botName, Location location) {
        String sql = "INSERT OR REPLACE INTO bots (owner_uuid, owner_name, bot_name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, ownerName);
            stmt.setString(3, botName);
            stmt.setString(4, location.getWorld().getName());
            stmt.setDouble(5, location.getX());
            stmt.setDouble(6, location.getY());
            stmt.setDouble(7, location.getZ());
            stmt.setFloat(8, location.getYaw());
            stmt.setFloat(9, location.getPitch());
            
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("添加bot记录失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除bot记录
     */
    public boolean removeBot(String botName) {
        String sql = "DELETE FROM bots WHERE bot_name = ?";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, botName);
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("删除bot记录失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取玩家当前bot数量
     */
    public int getPlayerBotCount(UUID playerUuid) {
        String sql = "SELECT COUNT(*) as count FROM bots WHERE owner_uuid = ?";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询玩家bot数量失败: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * 获取玩家的所有bot
     */
    public List<BotInfo> getPlayerBots(UUID playerUuid) {
        List<BotInfo> bots = new ArrayList<>();
        String sql = "SELECT * FROM bots WHERE owner_uuid = ?";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                BotInfo botInfo = new BotInfo(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("bot_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch"),
                    rs.getTimestamp("spawn_time"),
                    rs.getTimestamp("last_active")
                );
                bots.add(botInfo);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询玩家bots失败: " + e.getMessage());
        }
        
        return bots;
    }
    
    /**
     * 更新bot的最后活动时间
     */
    public void updateBotLastActive(String botName) {
        String sql = "UPDATE bots SET last_active = CURRENT_TIMESTAMP WHERE bot_name = ?";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, botName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("更新bot活动时间失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据bot名字获取bot信息
     */
    public BotInfo getBotByName(String botName) {
        String sql = "SELECT * FROM bots WHERE bot_name = ?";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, botName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new BotInfo(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("bot_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch"),
                    rs.getTimestamp("spawn_time"),
                    rs.getTimestamp("last_active")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询bot信息失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 获取所有bot
     */
    public List<BotInfo> getAllBots() {
        List<BotInfo> bots = new ArrayList<>();
        String sql = "SELECT * FROM bots";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                BotInfo botInfo = new BotInfo(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getString("bot_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch"),
                    rs.getTimestamp("spawn_time"),
                    rs.getTimestamp("last_active")
                );
                bots.add(botInfo);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询所有bots失败: " + e.getMessage());
        }
        
        return bots;
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
        }
    }
    
    /**
     * Bot信息数据类
     */
    public static class BotInfo {
        private final int id;
        private final UUID ownerUuid;
        private final String ownerName;
        private final String botName;
        private final String world;
        private final double x, y, z;
        private final float yaw, pitch;
        private final Timestamp spawnTime;
        private final Timestamp lastActive;
        
        public BotInfo(int id, UUID ownerUuid, String ownerName, String botName, 
                      String world, double x, double y, double z, float yaw, float pitch,
                      Timestamp spawnTime, Timestamp lastActive) {
            this.id = id;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.botName = botName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.spawnTime = spawnTime;
            this.lastActive = lastActive;
        }
        
        // Getters
        public int getId() { return id; }
        public UUID getOwnerUuid() { return ownerUuid; }
        public String getOwnerName() { return ownerName; }
        public String getBotName() { return botName; }
        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public Timestamp getSpawnTime() { return spawnTime; }
        public Timestamp getLastActive() { return lastActive; }
        
        public Location getLocation(org.bukkit.World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
}
