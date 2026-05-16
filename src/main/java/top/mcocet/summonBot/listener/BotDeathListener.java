package top.mcocet.summonBot.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import top.mcocet.summonBot.SummonBot;

public class BotDeathListener implements Listener {
    
    private final SummonBot plugin;
    
    public BotDeathListener(SummonBot plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 处理实体死亡事件
     * 当bot死亡时清理相关数据
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        String botName = plugin.getBotManager().getBotNameByEntity(event.getEntity());
        
        if (botName != null) {
            // bot死亡，从管理器中移除（如果存在）
            boolean removed = plugin.getBotManager().removeBot(botName);
            
            // 如果bot不在活跃列表中（可能已重启），直接删除数据库记录
            if (!removed) {
                plugin.getDatabaseManager().removeBot(botName);
            }
            
            // 记录日志
            plugin.getLogger().info("Bot " + botName + " 已死亡并清理");
        }
    }
}
