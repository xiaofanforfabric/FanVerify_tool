package com.xiaofan.fanVerify.utils;

import com.xiaofan.fanVerify.FanVerify;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * 双端通用调度封装（Paper / Folia）。
 *
 * 说明：Paper 1.20.1+ 与 Folia 都提供了统一的区域调度 API
 * (AsyncScheduler / GlobalRegionScheduler / RegionScheduler)。
 * 使用这些通用 API 即可双端通吃，无需两套截然不同的代码路径。
 *
 * runOnRegion / runLater / runRepeating 会绑定到指定玩家所在的区域线程，
 * 这是 Folia 下操作玩家背包/踢出/同步回主线程所必需的。
 */
public final class SchedulerUtil {

    private static boolean folia = false;

    private SchedulerUtil() {
    }

    /** 启动时调用，运行时探测是否为 Folia */
    public static void init() {
        try {
            // Folia 才有 RegionScheduler 这个包；Paper 1.20.1 也提供，但为区分运行时行为保留判断位
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            // 进一步确认是 Folia（Folia 才有 RegionizedServer 线程分区模型）
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
    }

    /** 当前是否运行在 Folia */
    public static boolean isFolia() {
        return folia;
    }

    private static FanVerify plugin() {
        return FanVerify.getInstance();
    }

    /**
     * 异步执行（HTTP / IO 等），两端通用。
     */
    public static void runAsync(Runnable r) {
        Bukkit.getAsyncScheduler().runNow(plugin(), task -> r.run());
    }

    /**
     * 在玩家所在区域线程同步执行（背包、踢出、同步回主线程等）。
     */
    public static void runOnRegion(Player p, Runnable r) {
        if (p != null && p.isOnline()) {
            Bukkit.getRegionScheduler().run(plugin(), p.getLocation(), task -> r.run());
        } else {
            runGlobal(r);
        }
    }

    /**
     * 全局同步执行（控制台或非玩家绑定操作）。
     */
    public static void runGlobal(Runnable r) {
        Bukkit.getGlobalRegionScheduler().run(plugin(), task -> r.run());
    }

    /**
     * 延迟执行（绑定玩家区域线程）。
     * @param delayTicks 延迟的 tick 数（20 tick = 1s）
     */
    public static CancellableTask runLater(Player p, Runnable r, long delayTicks) {
        if (p != null && p.isOnline()) {
            ScheduledTask t = Bukkit.getRegionScheduler()
                    .runDelayed(plugin(), p.getLocation(), task -> r.run(), delayTicks);
            return t::cancel;
        }
        ScheduledTask t = Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin(), task -> r.run(), delayTicks);
        return t::cancel;
    }

    /**
     * 循环执行（绑定玩家区域线程）。
     * @param delayTicks 初次延迟 tick
     * @param periodTicks 循环间隔 tick
     */
    public static CancellableTask runRepeating(Player p, Runnable r, long delayTicks, long periodTicks) {
        if (p != null && p.isOnline()) {
            ScheduledTask t = Bukkit.getRegionScheduler()
                    .runAtFixedRate(plugin(), p.getLocation(), task -> r.run(), delayTicks, periodTicks);
            return t::cancel;
        }
        ScheduledTask t = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin(), task -> r.run(), delayTicks, periodTicks);
        return t::cancel;
    }

    /** 可取消任务句柄，统一 Paper(Folia) 两种调度器的取消方式 */
    public interface CancellableTask {
        void cancel();
    }
}
