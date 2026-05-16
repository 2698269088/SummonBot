package top.mcocet.summonBot.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import top.mcocet.summonBot.SummonBot;

public class BotListener implements Listener {
    
    private final SummonBot plugin;
    private boolean botsRestored = false; // 标记是否已经恢复过bot
    
    public BotListener(SummonBot plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 监听世界加载事件，在世界完全加载后恢复bot
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // 只恢复一次
        if (botsRestored) {
            return;
        }
        
        // 检查数据库中是否有bot需要恢复
        if (plugin.getDatabaseManager().getAllBots().isEmpty()) {
            return;
        }
        
        // 延迟2秒执行，确保世界完全准备好
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, (task) -> {
            if (!botsRestored) {
                botsRestored = true;
                plugin.getLogger().info("世界已加载，开始恢复bot...");
                plugin.getBotManager().restoreBotsFromDatabase();
            }
        }, 40L); // 2秒 = 40 ticks
    }
    
    /**
     * 处理玩家与实体交互事件
     * 阻止玩家与bot进行交易
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) {
            return;
        }
        
        Villager villager = (Villager) event.getRightClicked();
        
        // 检查是否是SummonBot的bot
        if (!villager.hasMetadata("SummonBot")) {
            return;
        }
        
        // 取消事件，阻止交易界面打开
        event.setCancelled(true);
        
        // 获取bot名字
        String botName = plugin.getBotManager().getBotNameByEntity(villager);
        if (botName != null) {
            Player player = event.getPlayer();
            player.sendMessage("§e这是一个挂机bot: " + botName);
        }
    }
}
