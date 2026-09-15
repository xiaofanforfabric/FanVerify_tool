package com.xiaofan.fanVerify.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.xiaofan.fanVerify.FanVerify;
import com.xiaofan.fanVerify.api.FanVerifyAPI;
import com.xiaofan.fanVerify.utils.QRCodeMapRenderer;
import com.xiaofan.fanVerify.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerStateManager extends PacketListenerAbstract {

    private final FanVerify plugin;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> verifiedPlayers = new HashSet<>();
    private final Map<UUID, SchedulerUtil.CancellableTask> kickTasks = new HashMap<>();
    private final Map<UUID, String> playerOTPs = new HashMap<>();
    private final Map<UUID, ItemStack> qrCodeMaps = new HashMap<>();
    private final Map<UUID, ItemStack> backupSlotItems = new HashMap<>();
    private final Map<UUID, SchedulerUtil.CancellableTask> pollTasks = new HashMap<>();


    public PlayerStateManager(FanVerify plugin) {
        this.plugin = plugin;
        // 注册 PacketEvents 监听器
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }



    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            Player player = (Player) event.getPlayer();
            if (player != null && isFrozen(player)) {
                WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
                // 清空所有物品（第一个槽位的地图通过真实物品实现）
                wrapper.getItems().clear();
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            Player player = (Player) event.getPlayer();
            if (player != null && isFrozen(player)) {
                WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
                // 如果不是热键栏第一格，清空
                if (wrapper.getSlot() != 36) { // 36 = 热键栏第一格
                    wrapper.setItem(com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY);
                }
            }
        }
    }

    public void freezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        frozenPlayers.add(uuid);

        // 异步申请 OTP 并生成二维码
        SchedulerUtil.runAsync(() -> {
            String otp = FanVerifyAPI.requestOTP(plugin.getDevAccessToken());
            if (otp != null) {
                playerOTPs.put(uuid, otp);

                BufferedImage qrCode = FanVerifyAPI.generateQRCode(plugin.getDevAccessToken(), otp);
                if (qrCode != null) {
                    // 同步回玩家区域线程：给地图 + 开始轮询
                    SchedulerUtil.runOnRegion(player, () -> {
                        giveQRCodeMap(player, qrCode);
                        startOTPPolling(player, otp);
                    });
                }
            }
        });
    }

    private void startOTPPolling(Player player, String otp) {
        UUID uuid = player.getUniqueId();

        SchedulerUtil.CancellableTask task = SchedulerUtil.runRepeating(player, () -> {
            if (!player.isOnline() || !isFrozen(player)) {
                cancelTask(uuid);
                return;
            }

            // 异步轮询
            SchedulerUtil.runAsync(() -> {
                FanVerifyAPI.OTPPollResponse response = FanVerifyAPI.pollOTP(
                        plugin.getDevAccessToken(), otp);

                // 同步回玩家区域线程处理结果
                SchedulerUtil.runOnRegion(player, () -> {
                    if (response.status == FanVerifyAPI.OTPStatus.SUCCESS) {
                        handleOTPSuccess(player, response);
                        cancelTask(uuid);
                    } else if (response.status == FanVerifyAPI.OTPStatus.RATE_LIMIT) {
                        player.sendMessage("§c请求过快，请稍后...");
                    }
                    // WAITING 和 ERROR 继续轮询
                });
            });
        }, 100L, 100L); // 5秒轮询一次 (100 ticks = 5s)

        pollTasks.put(uuid, task);
    }

    private void handleOTPSuccess(Player player, FanVerifyAPI.OTPPollResponse response) {
        // 读取当前已绑定的 UID
        String boundUid = plugin.getDatabase().getFanverifyUid(player.getUniqueId());

        // 已绑定用户：必须校验扫码账号 == 绑定账号，防止别的账号冒充登录
        if (boundUid != null) {
            if (!String.valueOf(response.uid).equals(boundUid)) {
                // 账号不匹配，立刻踢出（在区域线程，允许踢出）
                cancelTasksAndKick(player);
                player.kickPlayer("§c验证失败：扫码账号与绑定账号不匹配！\n§e绑定 UID: " + boundUid + "，扫码 UID: " + response.uid + "\n§7请使用绑定的账号扫码，或联系管理员解绑");
                return;
            }
        }

        // 更新数据库：已绑定用户保持原 UID，新用户绑定扫码账号
        plugin.getDatabase().updatePlayer(
                player.getName(),
                player.getUniqueId(),
                boundUid != null ? boundUid : String.valueOf(response.uid),
                Integer.parseInt(response.level),
                player.getAddress().getAddress().getHostAddress(),
                System.currentTimeMillis(),
                null,
                null
        );

        // 解除冻结（会取消轮询/踢出任务并清理地图）
        unfreezePlayer(player);

        // 传送到上次下线位置（Folia 必须异步传送）
        Location lastLocation = plugin.getDatabase().getLastLocation(player.getUniqueId());
        if (lastLocation != null) {
            player.teleportAsync(lastLocation);
        }

        player.sendMessage("§a§l扫码验证成功！");
        player.sendMessage("§e您的 UID: §l" + (boundUid != null ? boundUid : response.uid));
        player.sendMessage("§e等级: §l" + response.level);
        player.sendMessage("§a欢迎来到服务器！");
    }

    /**
     * 取消该玩家的所有任务并清理地图状态（验证失败被踢时用）
     */
    private void cancelTasksAndKick(Player player) {
        UUID uuid = player.getUniqueId();

        cancelTask(uuid);
        // 取消超时踢出任务
        SchedulerUtil.CancellableTask kickTask = kickTasks.remove(uuid);
        if (kickTask != null) kickTask.cancel();

        // 清理 OTP 和地图
        playerOTPs.remove(uuid);
        qrCodeMaps.remove(uuid);
        frozenPlayers.remove(uuid);
        // 未验证成功，确保不会误判为已验证
        verifiedPlayers.remove(uuid);

        // 恢复第一格原物品（验证失败被踢，不能让玩家丢物品）
        restoreFirstSlot(player);
    }

    /**
     * 取消并移除指定玩家的轮询任务
     */
    private void cancelTask(UUID uuid) {
        SchedulerUtil.CancellableTask pollTask = pollTasks.remove(uuid);
        if (pollTask != null) pollTask.cancel();
    }

    private void giveQRCodeMap(Player player, BufferedImage qrCode) {
        // 备份第一格原物品（用于后续恢复）
        ItemStack original = player.getInventory().getItem(0);
        backupSlotItems.put(player.getUniqueId(), original);

        // 创建地图物品
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();

        // 创建地图视图
        MapView mapView = Bukkit.createMap(player.getWorld());

        // 清除默认渲染器
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }

        // 添加二维码渲染器
        mapView.addRenderer(new QRCodeMapRenderer(qrCode));

        meta.setMapView(mapView);
        map.setItemMeta(meta);

        // 保存地图引用
        qrCodeMaps.put(player.getUniqueId(), map);

        // 直接给玩家第一个槽位（覆盖，但已备份原物品）
        player.getInventory().setItem(0, map);
        player.sendMessage("§a已生成 OTP 二维码地图，请使用微信扫描");
    }

    public void unfreezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        frozenPlayers.remove(uuid);
        // 标记为已验证（本会话成功登录）
        verifiedPlayers.add(uuid);

        // 取消超时踢出任务
        SchedulerUtil.CancellableTask task = kickTasks.remove(uuid);
        if (task != null) task.cancel();

        // 取消轮询任务
        cancelTask(uuid);

        // 清理 OTP 和地图
        playerOTPs.remove(uuid);
        qrCodeMaps.remove(uuid);

        // 恢复第一格原物品
        restoreFirstSlot(player);

        // 刷新玩家背包显示
        player.updateInventory();
    }

    /**
     * 恢复第一格被地图占用的原物品，并清空备份
     * 注意：仅当存在备份时才恢复，避免备份已被消费后再次清空物品栏
     */
    private void restoreFirstSlot(Player player) {
        UUID uuid = player.getUniqueId();
        if (backupSlotItems.containsKey(uuid)) {
            ItemStack original = backupSlotItems.remove(uuid);
            player.getInventory().setItem(0, original);
        }
    }

    public String getPlayerOTP(UUID uuid) {
        return playerOTPs.get(uuid);
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    /**
     * 玩家本会话是否已成功验证
     */
    public boolean isVerified(Player player) {
        return verifiedPlayers.contains(player.getUniqueId());
    }

    /**
     * 清理玩家会话状态（退出时调用）
     */
    public void clearSession(Player player) {
        UUID uuid = player.getUniqueId();
        frozenPlayers.remove(uuid);
        verifiedPlayers.remove(uuid);
        playerOTPs.remove(uuid);
        qrCodeMaps.remove(uuid);

        cancelTask(uuid);
        SchedulerUtil.CancellableTask kickTask = kickTasks.remove(uuid);
        if (kickTask != null) kickTask.cancel();

        // 恢复第一格原物品（防止退出时地图残留）
        restoreFirstSlot(player);
    }

    public void startKickTimer(Player player) {
        UUID uuid = player.getUniqueId();
        int timeout = plugin.getTimeout();

        SchedulerUtil.CancellableTask task = SchedulerUtil.runLater(player, () -> {
            if (player.isOnline() && isFrozen(player)) {
                player.kickPlayer("§c验证超时！\n§e请在 " + timeout + " 秒内完成 FanVerify 验证");
            }
            kickTasks.remove(uuid);
        }, timeout * 20L); // 20 ticks = 1 second

        kickTasks.put(uuid, task);
    }

    public void showCachedLoginMessage(Player player) {
        player.sendMessage("§a§l欢迎回来！！！");
        player.sendMessage("§e您的信息已通过缓存登录！");
    }
}
