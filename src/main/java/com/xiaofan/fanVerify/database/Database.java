package com.xiaofan.fanVerify.database;

import com.xiaofan.fanVerify.FanVerify;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class Database {
    
    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private final FanVerify plugin;
    private Connection connection;

    
    public Database(FanVerify plugin) {
        this.plugin = plugin;
    }



    public void connect() {

        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            String url = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + "/userdata.db";
            connection = DriverManager.getConnection(url);
            log.info("数据库连接成功");
            
            // 自我愈合：创建或修复表
            healTable();
            
        } catch (SQLException e) {
            log.error("数据库连接失败", e);
        }
    }
    
    private void healTable() {
        try {
            // 检查表是否存在
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet tables = meta.getTables(null, null, "userdata", null);
            
            if (!tables.next()) {
                // 表不存在，创建新表
                createTable();
                log.info("userdata 表已创建");
            } else {
                // 表存在，检查并修复列
                repairTable();
                log.info("userdata 表已检查并修复");
            }
            
        } catch (SQLException e) {
            log.error("表自我愈合失败", e);
        }
    }
    
    private void createTable() throws SQLException {
        String sql = """
            CREATE TABLE userdata (
                playername TEXT NOT NULL,
                uuid TEXT PRIMARY KEY NOT NULL,
                fanverify_uid TEXT,
                pass_level INTEGER DEFAULT 0,
                ip TEXT,
                last_login_time INTEGER,
                last_login_world TEXT,
                last_login_pos TEXT
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void repairTable() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet columns = meta.getColumns(null, null, "userdata", null);
        
        // 记录现有列
        boolean hasPlayername = false;
        boolean hasUuid = false;
        boolean hasFanverifyUid = false;
        boolean hasPassLevel = false;
        boolean hasIp = false;
        boolean hasLastLoginTime = false;
        boolean hasLastLoginWorld = false;
        boolean hasLastLoginPos = false;
        
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            switch (columnName.toLowerCase()) {
                case "playername" -> hasPlayername = true;
                case "uuid" -> hasUuid = true;
                case "fanverify_uid" -> hasFanverifyUid = true;
                case "pass_level" -> hasPassLevel = true;
                case "ip" -> hasIp = true;
                case "last_login_time" -> hasLastLoginTime = true;
                case "last_login_world" -> hasLastLoginWorld = true;
                case "last_login_pos" -> hasLastLoginPos = true;
            }
        }
        
        // 添加缺失的列
        try (Statement stmt = connection.createStatement()) {
            if (!hasPlayername) stmt.execute("ALTER TABLE userdata ADD COLUMN playername TEXT NOT NULL DEFAULT ''");
            if (!hasUuid) stmt.execute("ALTER TABLE userdata ADD COLUMN uuid TEXT PRIMARY KEY NOT NULL DEFAULT ''");
            if (!hasFanverifyUid) stmt.execute("ALTER TABLE userdata ADD COLUMN fanverify_uid TEXT");
            if (!hasPassLevel) stmt.execute("ALTER TABLE userdata ADD COLUMN pass_level INTEGER DEFAULT 0");
            if (!hasIp) stmt.execute("ALTER TABLE userdata ADD COLUMN ip TEXT");
            if (!hasLastLoginTime) stmt.execute("ALTER TABLE userdata ADD COLUMN last_login_time INTEGER");
            if (!hasLastLoginWorld) stmt.execute("ALTER TABLE userdata ADD COLUMN last_login_world TEXT");
            if (!hasLastLoginPos) stmt.execute("ALTER TABLE userdata ADD COLUMN last_login_pos TEXT");
        }
    }
    
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            log.error("数据库关闭失败", e);
        }
    }
    
    public void updatePlayer(String playerName, UUID uuid, String fanverifyUid, int passLevel, 
                            String ip, long loginTime, String world, Location pos) {
        String sql = """
            INSERT INTO userdata (playername, uuid, fanverify_uid, pass_level, ip, last_login_time, last_login_world, last_login_pos)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                playername = excluded.playername,
                fanverify_uid = excluded.fanverify_uid,
                pass_level = excluded.pass_level,
                ip = excluded.ip,
                last_login_time = excluded.last_login_time,
                last_login_world = COALESCE(excluded.last_login_world, userdata.last_login_world),
                last_login_pos = COALESCE(excluded.last_login_pos, userdata.last_login_pos)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, uuid.toString());
            pstmt.setString(3, fanverifyUid);
            pstmt.setInt(4, passLevel);
            pstmt.setString(5, ip);
            pstmt.setLong(6, loginTime);
            pstmt.setString(7, world);
            pstmt.setString(8, formatLocation(pos));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("更新玩家数据失败", e);
        }
    }
    
    public String getFanverifyUid(UUID uuid) {
        String sql = "SELECT fanverify_uid FROM userdata WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("fanverify_uid");
            }
        } catch (SQLException e) {
            log.error("查询玩家 UID 失败", e);
        }
        return null;
    }
    
    /**
     * 根据玩家名查询其 UUID
     * @param playerName 玩家名
     * @return UUID 或 null（无记录）
     */
    public UUID getUuidByName(String playerName) {
        String sql = "SELECT uuid FROM userdata WHERE playername = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            log.error("查询玩家 UUID 失败", e);
        }
        return null;
    }
    
    /**
     * 解绑玩家，清除绑定 UID 和等级
     * @param uuid 玩家 UUID
     */
    public void unbind(UUID uuid) {
        String sql = "UPDATE userdata SET fanverify_uid = NULL, pass_level = 0 WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("解绑玩家失败", e);
        }
    }
    
    public Location getLastLocation(UUID uuid) {
        String sql = "SELECT last_login_world, last_login_pos FROM userdata WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String worldName = rs.getString("last_login_world");
                String pos = rs.getString("last_login_pos");
                
                if (worldName != null && pos != null) {
                    String[] coords = pos.split(",");
                    if (coords.length == 3) {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            return new Location(
                                world,
                                Double.parseDouble(coords[0]),
                                Double.parseDouble(coords[1]),
                                Double.parseDouble(coords[2])
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("查询玩家上次位置失败", e);
        }
        return null;
    }
    
    private String formatLocation(Location loc) {
        if (loc == null) return null;
        return String.format("%.2f,%.2f,%.2f", loc.getX(), loc.getY(), loc.getZ());
    }
    
    public Connection getConnection() {
        return connection;
    }
}
