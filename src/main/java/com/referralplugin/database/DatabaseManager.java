package com.referralplugin.database;

import com.referralplugin.ReferralPlugin;
import com.referralplugin.models.ReferralData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final ReferralPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(ReferralPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            HikariConfig config = new HikariConfig();
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String db = plugin.getConfig().getString("database.name", "referrals");
            String user = plugin.getConfig().getString("database.username", "root");
            String pass = plugin.getConfig().getString("database.password", "password");

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            config.setUsername(user);
            config.setPassword(pass);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool.maximum-pool-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("database.pool.minimum-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("database.pool.connection-timeout", 30000));
            config.setIdleTimeout(plugin.getConfig().getLong("database.pool.idle-timeout", 600000));
            config.setMaxLifetime(plugin.getConfig().getLong("database.pool.max-lifetime", 1800000));
            config.setPoolName("ReferralPlugin-Pool");

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Connected to MySQL database successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MySQL: " + e.getMessage(), e);
            return false;
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void createTables() {
        String referralsTable = """
            CREATE TABLE IF NOT EXISTS referral_players (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                referral_code VARCHAR(64) NOT NULL UNIQUE,
                referred_by_uuid VARCHAR(36) DEFAULT NULL,
                total_referrals INT NOT NULL DEFAULT 0,
                join_timestamp BIGINT NOT NULL,
                referral_code_used_at BIGINT DEFAULT NULL,
                INDEX idx_referral_code (referral_code),
                INDEX idx_referred_by (referred_by_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """;

        String sessionsTable = """
            CREATE TABLE IF NOT EXISTS referral_sessions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                referred_uuid VARCHAR(36) NOT NULL,
                referrer_uuid VARCHAR(36) NOT NULL,
                accumulated_seconds BIGINT NOT NULL DEFAULT 0,
                required_seconds BIGINT NOT NULL,
                completed BOOLEAN NOT NULL DEFAULT FALSE,
                completed_at BIGINT DEFAULT NULL,
                reward_issued BOOLEAN NOT NULL DEFAULT FALSE,
                created_at BIGINT NOT NULL,
                UNIQUE KEY unique_referral (referred_uuid),
                INDEX idx_referrer (referrer_uuid),
                INDEX idx_completed (completed)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(referralsTable);
            stmt.execute(sessionsTable);
            plugin.getLogger().info("Database tables verified/created.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create tables: " + e.getMessage(), e);
        }
    }

    public void savePlayer(UUID uuid, String username, String code) {
        String sql = """
            INSERT INTO referral_players (uuid, username, referral_code, join_timestamp)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE username = VALUES(username)
        """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setString(3, code);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving player: " + e.getMessage(), e);
        }
    }

    public ReferralData getPlayer(UUID uuid) {
        String sql = "SELECT * FROM referral_players WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapReferralData(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting player: " + e.getMessage(), e);
        }
        return null;
    }

    public ReferralData getPlayerByCode(String code) {
        boolean ci = plugin.getConfig().getBoolean("settings.case-insensitive-codes", true);
        String sql = ci
                ? "SELECT * FROM referral_players WHERE UPPER(referral_code) = UPPER(?)"
                : "SELECT * FROM referral_players WHERE referral_code = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapReferralData(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting player by code: " + e.getMessage(), e);
        }
        return null;
    }

    public ReferralData getPlayerByName(String username) {
        String sql = "SELECT * FROM referral_players WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapReferralData(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting player by name: " + e.getMessage(), e);
        }
        return null;
    }

    public void setReferredBy(UUID uuid, UUID referrerUUID) {
        String sql = "UPDATE referral_players SET referred_by_uuid = ?, referral_code_used_at = ? WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referrerUUID.toString());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting referred_by: " + e.getMessage(), e);
        }
    }

    public void incrementReferralCount(UUID uuid) {
        String sql = "UPDATE referral_players SET total_referrals = total_referrals + 1 WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error incrementing referral count: " + e.getMessage(), e);
        }
    }

    public void resetReferralCode(UUID uuid, String newCode) {
        String sql = "UPDATE referral_players SET referral_code = ? WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newCode);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error resetting referral code: " + e.getMessage(), e);
        }
    }

    public void createSession(UUID referredUUID, UUID referrerUUID, long requiredSeconds) {
        String sql = """
            INSERT INTO referral_sessions (referred_uuid, referrer_uuid, required_seconds, created_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE referrer_uuid = VALUES(referrer_uuid)
        """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referredUUID.toString());
            ps.setString(2, referrerUUID.toString());
            ps.setLong(3, requiredSeconds);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating session: " + e.getMessage(), e);
        }
    }

    public long getAccumulatedSeconds(UUID referredUUID) {
        String sql = "SELECT accumulated_seconds FROM referral_sessions WHERE referred_uuid = ? AND completed = FALSE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referredUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("accumulated_seconds");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting accumulated seconds: " + e.getMessage(), e);
        }
        return -1;
    }

    public void addAccumulatedSeconds(UUID referredUUID, long seconds) {
        String sql = "UPDATE referral_sessions SET accumulated_seconds = accumulated_seconds + ? WHERE referred_uuid = ? AND completed = FALSE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seconds);
            ps.setString(2, referredUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error adding accumulated seconds: " + e.getMessage(), e);
        }
    }

    public void completeSession(UUID referredUUID) {
        String sql = "UPDATE referral_sessions SET completed = TRUE, completed_at = ? WHERE referred_uuid = ? AND completed = FALSE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, referredUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error completing session: " + e.getMessage(), e);
        }
    }

    public void markRewardIssued(UUID referredUUID) {
        String sql = "UPDATE referral_sessions SET reward_issued = TRUE WHERE referred_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referredUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error marking reward issued: " + e.getMessage(), e);
        }
    }

    public boolean hasCompletedSession(UUID referredUUID) {
        String sql = "SELECT id FROM referral_sessions WHERE referred_uuid = ? AND completed = TRUE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referredUUID.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error checking completed session: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean hasPendingSession(UUID referredUUID) {
        String sql = "SELECT id FROM referral_sessions WHERE referred_uuid = ? AND completed = FALSE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referredUUID.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error checking pending session: " + e.getMessage(), e);
        }
        return false;
    }

    public int getPendingReferralCount(UUID referrerUUID) {
        String sql = "SELECT COUNT(*) FROM referral_sessions WHERE referrer_uuid = ? AND completed = FALSE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referrerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting pending count: " + e.getMessage(), e);
        }
        return 0;
    }

    public List<UUID> getPendingReferredPlayers(UUID referrerUUID) {
        List<UUID> list = new ArrayList<>();
        String sql = "SELECT referred_uuid FROM referral_sessions WHERE referrer_uuid = ? AND completed = FALSE";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, referrerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(UUID.fromString(rs.getString("referred_uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting pending referred players: " + e.getMessage(), e);
        }
        return list;
    }

    private ReferralData mapReferralData(ResultSet rs) throws SQLException {
        ReferralData data = new ReferralData();
        data.setUuid(UUID.fromString(rs.getString("uuid")));
        data.setUsername(rs.getString("username"));
        data.setReferralCode(rs.getString("referral_code"));
        String referredBy = rs.getString("referred_by_uuid");
        data.setReferredByUUID(referredBy != null ? UUID.fromString(referredBy) : null);
        data.setTotalReferrals(rs.getInt("total_referrals"));
        data.setJoinTimestamp(rs.getLong("join_timestamp"));
        data.setReferralCodeUsedAt(rs.getLong("referral_code_used_at"));
        return data;
    }
}
