package top.mcocet.summonBot.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import top.mcocet.summonBot.SummonBot;

public class BotTargetListener implements Listener {
    
    private final SummonBot plugin;
    
    public BotTargetListener(SummonBot plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 处理实体目标事件
     * 阻止敌对生物攻击bot
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();
        
        // 检查目标是否是bot
        if (target == null || !target.hasMetadata("SummonBot")) {
            return;
        }
        
        // 取消事件，阻止敌对生物攻击bot
        event.setCancelled(true);
    }
}
