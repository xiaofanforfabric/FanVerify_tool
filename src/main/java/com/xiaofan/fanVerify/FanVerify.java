package com.xiaofan.fanVerify;

import com.xiaofan.fanVerify.commands.BindCommand;
import com.xiaofan.fanVerify.commands.PassCommand;
import com.xiaofan.fanVerify.commands.UnbindCommand;
import com.xiaofan.fanVerify.database.Database;
import com.xiaofan.fanVerify.listener.PlayerLoginListener;
import com.xiaofan.fanVerify.manager.PlayerStateManager;
import com.xiaofan.fanVerify.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FanVerify extends JavaPlugin {

    private static final Logger log = LoggerFactory.getLogger(FanVerify.class);
    private static FanVerify instance;
    private String devAccessToken;
    private int timeout;
    private Database database;
    private PlayerStateManager stateManager;
    private Location loginPoint;

    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 加载配置
        loadConfig();
        
        // 验证配置
        if (devAccessToken == null || devAccessToken.isEmpty()) {
            log.error("dev_access_token 未配置！请在 config.yml 中设置开发者令牌");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化调度（探测 Paper / Folia 运行环境，双端通用）
        SchedulerUtil.init();
        
        // 初始化数据库
        database = new Database(this);
        database.connect();
        
        // 初始化状态管理器
        stateManager = new PlayerStateManager(this);
        
        // 注册监听器
        getServer().getPluginManager().registerEvents(
            new PlayerLoginListener(this, stateManager), 
            this
        );
        
        log.info("FanVerify plugin has been enabled");
        log.info("Timeout: {}s", timeout);
        
        // 注册命令 - 使用传统 Bukkit API
        this.getCommand("bind").setExecutor(new BindCommand());
        this.getCommand("p").setExecutor(new PassCommand());
        this.getCommand("ubind").setExecutor(new UnbindCommand());
    }
    
    @Override
    public void onDisable() {
        // 关闭数据库连接
        if (database != null) {
            database.disconnect();
        }
    }
    
    private void loadConfig() {
        FileConfiguration config = getConfig();
        devAccessToken = config.getString("dev_access_token", "");
        timeout = config.getInt("timeout", 120);
        
        // 加载登录点
        String worldName = config.getString("login_point.world", "world");
        double x = config.getDouble("login_point.x", 0.0);
        double y = config.getDouble("login_point.y", 64.0);
        double z = config.getDouble("login_point.z", 0.0);
        float yaw = (float) config.getDouble("login_point.yaw", 0.0);
        float pitch = (float) config.getDouble("login_point.pitch", 0.0);
        
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            loginPoint = new Location(world, x, y, z, yaw, pitch);
            log.info("登录点已设置: {} ({}, {}, {})", worldName, x, y, z);
        } else {
            log.error("登录点世界不存在: {}", worldName);
            loginPoint = null;
        }
    }
    
    public static FanVerify getInstance() {
        return instance;
    }
    
    public String getDevAccessToken() {
        return devAccessToken;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public Database getDatabase() {
        return database;
    }
    
    public PlayerStateManager getStateManager() {
        return stateManager;
    }
    
    public Location getLoginPoint() {
        return loginPoint;
    }
/*
  这烂怂模板咋还在,直接注释

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }}

 */
}