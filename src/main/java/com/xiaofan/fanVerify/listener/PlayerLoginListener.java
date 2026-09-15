package com.xiaofan.fanVerify.listener;

import com.xiaofan.fanVerify.FanVerify;
import com.xiaofan.fanVerify.manager.PlayerStateManager;
import com.xiaofan.fanVerify.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlayerLoginListener implements Listener {
    
    private final FanVerify plugin;
    private final PlayerStateManager stateManager;


    public PlayerLoginListener(FanVerify plugin, PlayerStateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uid = plugin.getDatabase().getFanverifyUid(player.getUniqueId());
        
        // 传送到登录点：Folia 下不能在 join 事件里同步 teleportAsync（会触发
        // "Player is already removed from player chunk loader" 崩溃），
        // 调度到下一 tick 再传送，让玩家完成初始加载。
        Location loginPoint = plugin.getLoginPoint();
        if (loginPoint != null) {
            SchedulerUtil.runLater(player, () -> player.teleportAsync(loginPoint), 1L);
        }
        
        if (uid == null) {
            // 新用户
            showNewPlayerMessage(player);
        } else {
            // 老用户
            showOldPlayerMessage(player, uid);
        }
        
        // 冻结玩家
        stateManager.freezePlayer(player);
        
        // 启动超时踢出任务
        stateManager.startKickTimer(player);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // 只有本会话验证成功的玩家才保存位置（防止验证失败/被踢/超时把登录点写进数据库）
        if (stateManager.isVerified(player)) {
            plugin.getDatabase().updatePlayer(
                player.getName(),
                player.getUniqueId(),
                plugin.getDatabase().getFanverifyUid(player.getUniqueId()),
                0, // pass_level 保持不变，这里传0表示不更新
                player.getAddress().getAddress().getHostAddress(),
                System.currentTimeMillis(),
                player.getWorld().getName(),
                player.getLocation()
            );
        }
        
        // 清理玩家会话状态
        stateManager.clearSession(player);
    }
    
    // 阻止未验证玩家移动（允许下落，禁止水平移动和上升）
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (stateManager.isFrozen(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (to == null) return;
            
            // 只检查水平移动和上升
            double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) + 
                Math.pow(to.getZ() - from.getZ(), 2)
            );
            
            // 如果有水平移动或上升，取消
            if (horizontalDistance > 0 || to.getY() > from.getY()) {
                // 保持Y坐标，允许下落
                Location newTo = from.clone();
                newTo.setY(Math.min(to.getY(), from.getY()));
                event.setTo(newTo);
            }
        }
    }
    
    // 阻止未验证玩家打开背包
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (stateManager.isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }
    
    // 阻止未验证玩家聊天
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (stateManager.isFrozen(player)) {
            event.setCancelled(true);
            player.sendMessage("§c请先完成 FanVerify 验证");
        }
    }
    
    // 阻止未验证玩家执行命令（除了验证相关命令）
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (stateManager.isFrozen(player)) {
            String cmd = event.getMessage().toLowerCase().split(" ")[0];
            if (!cmd.equals("/bind") && !cmd.equals("/p")) {
                event.setCancelled(true);
                player.sendMessage("§c请先完成 FanVerify 验证");
            }
        }
    }
    
    // 阻止未验证玩家受伤
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (stateManager.isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }
    
    // 阻止未验证玩家破坏方块
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (stateManager.isFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    // 阻止未验证玩家放置方块
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (stateManager.isFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    private void showNewPlayerMessage(Player player) {
        int timeout = plugin.getTimeout();
        player.sendMessage("§a§l欢迎来到服务器！");
        player.sendMessage("§e本服务器采用 FanVerify 验证");
        player.sendMessage("§e您需要绑定 FanVerify 账户才能进入服务器");
        player.sendMessage("§b请打开微信小程序 §l(博誉鉴权) §r§b查看验证码或扫描手中二维码");
        player.sendMessage("§6使用命令: §e/bind <UID> <验证码> §6进行绑定");
        player.sendMessage("§c请在 §l" + timeout + "秒 §r§c内完成验证，否则您将被踢出服务器");
    }
    
    private void showOldPlayerMessage(Player player, String uid) {
        int timeout = plugin.getTimeout();
        player.sendMessage("§a§l欢迎回来！！！");
        player.sendMessage("§e您绑定的 FanVerify 帐号 UID 为: §l" + uid);
        player.sendMessage("§b请打开微信小程序 §l(博誉鉴权) §r§b查看验证码或扫描手中二维码");
        player.sendMessage("§6使用命令: §e/p <验证码> §6进行验证");
        player.sendMessage("§c请在 §l" + timeout + "秒 §r§c内完成验证，否则您将被踢出服务器");
    }
}
