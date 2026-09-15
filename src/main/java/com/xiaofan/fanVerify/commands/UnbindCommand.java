package com.xiaofan.fanVerify.commands;

import com.xiaofan.fanVerify.FanVerify;
import com.xiaofan.fanVerify.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class UnbindCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 仅 OP 和管理员可用
        if (!sender.isOp() && !sender.hasPermission("fanverify.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§c用法: /ubind <playername> [-y]");
            return true;
        }

        String targetName = args[0];
        boolean confirmed = args.length >= 2 && args[1].equalsIgnoreCase("-y");

        // 查找目标玩家 UUID：优先在线玩家，其次查数据库
        UUID targetUuid = null;
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
        } else {
            // 尝试离线玩家（数据库里有记录才能解绑）
            targetUuid = FanVerify.getInstance().getDatabase().getUuidByName(targetName);
        }

        if (targetUuid == null) {
            sender.sendMessage("§c找不到玩家或该玩家未在数据库登记: " + targetName);
            return true;
        }

        // 查询当前绑定的 UID
        String boundUid = FanVerify.getInstance().getDatabase().getFanverifyUid(targetUuid);
        if (boundUid == null) {
            sender.sendMessage("§c玩家 " + targetName + " 未绑定 FanVerify 帐号");
            return true;
        }

        // 未确认，提示确认
        if (!confirmed) {
            sender.sendMessage("§e确定要解绑玩家: §l" + targetName + "§r§e 的 FanVerify 帐号吗？");
            sender.sendMessage("§e绑定 UID: §l" + boundUid);
            sender.sendMessage("§7输入 §l/ubind " + targetName + " -y §r§7 确认解绑");
            return true;
        }

        // 确认执行
        FanVerify.getInstance().getDatabase().unbind(targetUuid);
        sender.sendMessage("§a已解绑玩家: §l" + targetName);
        sender.sendMessage("§e原绑定 UID: §l" + boundUid);

        // 如果目标在线且处于冻结状态，清理其会话（必须在目标所在区域线程执行，Folia 下安全）
        if (onlineTarget != null) {
            SchedulerUtil.runOnRegion(onlineTarget, () ->
                    FanVerify.getInstance().getStateManager().clearSession(onlineTarget));
        }
        return true;
    }
}
