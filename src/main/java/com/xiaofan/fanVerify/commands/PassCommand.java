package com.xiaofan.fanVerify.commands;

import com.xiaofan.fanVerify.FanVerify;
import com.xiaofan.fanVerify.api.FanVerifyAPI;
import com.xiaofan.fanVerify.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PassCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§c用法: /p <pass_code>");
            return true;
        }

        Player player = (Player) sender;
        String passCode = args[0];
        
        // 获取已绑定的 UID
        String uid = FanVerify.getInstance().getDatabase().getFanverifyUid(player.getUniqueId());
        
        if (uid == null) {
            player.sendMessage("§c您还未绑定 FanVerify 账户！");
            player.sendMessage("§e请使用: /bind <uid> <pass_code> 进行绑定");
            return true;
        }
        
        player.sendMessage("§e正在验证...");
        
        // 异步调用 API
        SchedulerUtil.runAsync(() -> {
            FanVerifyAPI.UserVerifyResponse response = FanVerifyAPI.verifyUser(
                FanVerify.getInstance().getDevAccessToken(),
                uid,
                passCode
            );
            
            // 同步回玩家区域线程处理结果
            SchedulerUtil.runOnRegion(player, () -> {
                if (response.success) {
                    // 验证成功，更新数据（不覆盖上次下线位置）
                    FanVerify.getInstance().getDatabase().updatePlayer(
                        player.getName(),
                        player.getUniqueId(),
                        String.valueOf(response.uid),
                        Integer.parseInt(response.level),
                        player.getAddress().getAddress().getHostAddress(),
                        System.currentTimeMillis(),
                        null,
                        null
                    );
                    
                    // 解除冻结
                    FanVerify.getInstance().getStateManager().unfreezePlayer(player);
                    
                    // 传送到上次下线位置（异步）
                    Location lastLocation = FanVerify.getInstance().getDatabase().getLastLocation(player.getUniqueId());
                    if (lastLocation != null) {
                        player.teleportAsync(lastLocation);
                    }
                    
                    player.sendMessage("§a§l验证成功！");
                    player.sendMessage("§e等级: §l" + response.level);
                    player.sendMessage("§a欢迎回来！");
                } else {
                    player.sendMessage("§c验证失败: " + response.error);
                    player.sendMessage("§e请检查验证码是否正确");
                }
            });
        });
        
        return true;
    }
}
