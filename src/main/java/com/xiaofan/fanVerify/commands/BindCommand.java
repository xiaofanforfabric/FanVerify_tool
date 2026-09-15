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

public class BindCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§c用法: /bind <uid> <pass_code>");
            return true;
        }

        Player player = (Player) sender;
        
        // 检查是否已经绑定
        String existingUid = FanVerify.getInstance().getDatabase().getFanverifyUid(player.getUniqueId());
        if (existingUid != null) {
            player.sendMessage("§c您已经绑定了 FanVerify 账户！");
            player.sendMessage("§e您的 UID: §l" + existingUid);
            player.sendMessage("§7如需更换绑定，请联系管理员");
            return true;
        }
        
        String uid = args[0];
        String passCode = args[1];
        
        player.sendMessage("§e正在验证...");
        
        // 异步调用 API
        SchedulerUtil.runAsync(() -> {
            FanVerifyAPI.UserVerifyResponse response = FanVerifyAPI.verifyUser(
                FanVerify.getInstance().getDevAccessToken(),
                uid,
                passCode
            );
            
            // 回到主线程处理结果
            SchedulerUtil.runOnRegion(player, () -> {
                if (response.success) {
                    // 验证成功，保存绑定（不覆盖上次下线位置）
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
                    
                    player.sendMessage("§a§l绑定成功！");
                    player.sendMessage("§e您的 UID: §l" + response.uid);
                    player.sendMessage("§e等级: §l" + response.level);
                    player.sendMessage("§a欢迎来到服务器！");
                } else {
                    player.sendMessage("§c验证失败: " + response.error);
                    player.sendMessage("§e请检查 UID 和验证码是否正确");
                }
            });
        });
        
        return true;
    }
}
