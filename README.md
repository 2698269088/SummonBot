# SummonBot - Minecraft 挂机Bot插件

## 功能特性

- ✅ 配置管理：通过config.yml管理插件配置
- ✅ SQLite数据库：存储bot主人、名字、位置等信息
- ✅ 数量限制：可设置每个玩家最多召唤的bot数量
- ✅ Folia支持：完全兼容Folia服务端
- ✅ 命令系统：提供完整的bot管理命令
- ✅ Bot实体：召唤无AI的村民作为挂机bot
- ✅ 防交易保护：阻止玩家与bot进行交易
- ✅ 权限控制：区分普通玩家和管理员权限

## 安装方法

1. 将编译好的JAR文件放入Folia服务端的`plugins`目录
2. 启动服务器生成配置文件
3. 编辑`plugins/SummonBot/config.yml`自定义配置
4. 重启服务器

## 配置说明

### config.yml
```yaml
# 每个玩家最多可以召唤的bot数量
max-bots-per-player: 3

# 数据库设置
database:
  # 自动保存间隔（秒）
  auto-save-interval: 300

# Bot设置
bot:
  # bot空闲超时时间（秒），0表示不超时
  idle-timeout: 600
  
  # 区块加载设置
  chunk-load:
    # 是否启用bot周围区块强制加载
    enabled: true
    # 加载半径（以bot为中心的区块半径，1-10）
    radius: 3
```

## Bot特性

### 村民Bot属性
- **无AI行为**：不会移动、不会寻找路径
- **无法交易**：右键点击不会打开交易界面
- **标准血量**：20点血量（10颗心），可以受到伤害
- **非无敌状态**：可以被攻击和杀死
- **允许发声**：会发出村民的声音
- **免疫敌对生物**：敌对生物不会主动攻击bot
- **永久存活**：bot没有存活时间限制，除非被移除或死亡
- **区块加载**：强制加载bot周围指定半径的区块，即使玩家不在也能保持加载
- **永久存在**：不会因为距离远而消失
- **名称显示**：显示为 `[Bot]<名字>` 格式（例如：`[Bot]testbot`）
- **职业设置**：无业村民，避免意外行为

## 使用方法

### 命令
- `/sbot sum <Bot名字>` - 召唤一个bot
- `/sbot uns <Bot名字>` - 移除你的bot
- `/sbot list [玩家名字]` - 查看bot列表（管理员）

### 权限
- `summonbot.use` - 允许使用基本命令（默认所有玩家都有）
- `summonbot.admin` - 管理员权限，可以查看所有bot、移除任意bot、无bot数量限制（默认OP拥有）

## 数据库结构

### bots表
- `id`: bot的唯一ID
- `owner_uuid`: 主人UUID
- `owner_name`: 主人名字
- `bot_name`: bot名字
- `world`: 所在世界
- `x, y, z`: 位置坐标
- `yaw, pitch`: 朝向
- `spawn_time`: 召唤时间
- `last_active`: 最后活动时间
- `is_active`: 是否活跃

### players表
- `uuid`: 玩家UUID
- `name`: 玩家名字
- `first_seen`: 首次出现时间
- `last_seen`: 最后出现时间

## 开发API

### 获取管理器实例
```java
SummonBot plugin = SummonBot.getInstance();
ConfigManager configManager = plugin.getConfigManager();
DatabaseManager databaseManager = plugin.getDatabaseManager();
```

### 配置管理
```java
// 获取最大bot数量
int maxBots = configManager.getMaxBotsPerPlayer();

// 设置最大bot数量
configManager.setMaxBotsPerPlayer(5);
```

### 数据库操作
```java
// 获取玩家bot数量
int count = databaseManager.getPlayerBotCount(playerUuid);

// 添加bot记录
databaseManager.addBot(ownerUuid, ownerName, botName, location);

// 获取玩家的所有bot
List<BotInfo> bots = databaseManager.getPlayerBots(playerUuid);
```

## 注意事项

1. 本插件专为Folia服务端设计
2. 确保服务器安装了SQLite驱动（已内置）
3. 定期备份`plugins/SummonBot/bots.db`数据库文件
4. 根据服务器性能调整配置参数

## 许可证

本项目采用MIT许可证。
