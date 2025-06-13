/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.core.storage;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

import static java.sql.Statement.RETURN_GENERATED_KEYS;

public abstract class SQLStorage implements AuthStorage {

    protected static final String PREMIUM_TABLE = "premium";
    protected static final String CREATE_TABLE_STMT = "CREATE TABLE IF NOT EXISTS `" + PREMIUM_TABLE + "` ("
            + "`UserID` INTEGER PRIMARY KEY AUTO_INCREMENT, "
            + "`UUID` CHAR(36), "
            + "`Name` VARCHAR(16) NOT NULL, "
            + "`Premium` BOOLEAN NOT NULL, "
            + "`LastIp` VARCHAR(255) NOT NULL, "
            + "`LastLogin` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            //the premium shouldn't steal the cracked account by changing the name
            + "UNIQUE (`Name`) "
            + ')';

    protected static final String ADD_FLOODGATE_COLUMN_STMT = "ALTER TABLE `" + PREMIUM_TABLE
            + "` ADD COLUMN `Floodgate` INTEGER(3)";

    protected static final String LOAD_BY_NAME = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `Name`=? LIMIT 1";
    protected static final String LOAD_BY_UUID = "SELECT * FROM `" + PREMIUM_TABLE
            + "` WHERE `UUID`=? LIMIT 1";
    protected static final String DELETE_BY_NAME = "DELETE FROM " + PREMIUM_TABLE
            + " WHERE `Name` = ?";
    protected static final String INSERT_PROFILE = "INSERT INTO `" + PREMIUM_TABLE
            + "` (`UUID`, `Name`, `Premium`, `Floodgate`, `LastIp`) " + "VALUES (?, ?, ?, ?, ?) ";
    // limit not necessary here, because it's unique
    protected static final String UPDATE_PROFILE = "UPDATE `" + PREMIUM_TABLE
            + "` SET `UUID`=?, `Name`=?, `Premium`=?, `Floodgate`=?, `LastIp`=?, "
            + "`LastLogin`=CURRENT_TIMESTAMP WHERE `UserID`=?";

    protected final Logger log;
    protected final HikariDataSource dataSource;

    public SQLStorage(Logger log, String poolName, ThreadFactory threadFactory, HikariConfig config) {
        this.log = log;
        config.setPoolName(poolName);
        if (threadFactory != null) {
            config.setThreadFactory(threadFactory);
        }

        this.dataSource = new HikariDataSource(config);
    }

    public void createTables() throws SQLException {
        // choose surrogate PK(ID), because UUID can be null for offline players
        // if UUID is always Premium UUID we would have to update offline player entries on insert
        // name cannot be PK, because it can be changed for premium players
        //todo: add unique uuid index usage
        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate(getCreateTableStmt());

            // add Floodgate column
            DatabaseMetaData md = con.getMetaData();
            if (isColumnMissing(md, "Floodgate")) {
                stmt.executeUpdate(ADD_FLOODGATE_COLUMN_STMT);
            }

        }
    }

    private boolean isColumnMissing(DatabaseMetaData metaData, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(null, null, PREMIUM_TABLE, columnName)) {
            return !rs.next();
        }
    }

    @Override
    public StoredProfile loadProfile(String name) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement loadStmt = con.prepareStatement(LOAD_BY_NAME)
        ) {
            loadStmt.setString(1, name);

            try (ResultSet resultSet = loadStmt.executeQuery()) {
                return parseResult(resultSet).orElseGet(() -> new StoredProfile(null, name, false,
                        FloodgateState.FALSE, ""));
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to query profile: {}", name, sqlEx);
        }

        return null;
    }

    @Override
    public StoredProfile loadProfile(UUID uuid) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement loadStmt = con.prepareStatement(LOAD_BY_UUID)) {
            loadStmt.setString(1, UUIDAdapter.toMojangId(uuid));

            try (ResultSet resultSet = loadStmt.executeQuery()) {
                return parseResult(resultSet).orElse(null);
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to query profile: {}", uuid, sqlEx);
        }

        return null;
    }

    @Override
    public int deleteProfile(String name) {
        try (Connection con = dataSource.getConnection();
            PreparedStatement deleteStmt = con.prepareStatement(DELETE_BY_NAME)) {
            deleteStmt.setString(1, name);

            int rowsDeleted = deleteStmt.executeUpdate();
            if (rowsDeleted > 0) {
                log.info("Deleted {}'s profile data", name);
            } else {
                log.info("No profile data found for {}", name);
            }
            return rowsDeleted;
        } catch (SQLException sqlEx) {
            log.error("Failed to query profile: {}", name, sqlEx);
            return 0;
        }
    }

    private Optional<StoredProfile> parseResult(ResultSet resultSet) throws SQLException {
        if (resultSet.next()) {
            long userId = resultSet.getInt("UserID");

            UUID uuid = Optional.ofNullable(resultSet.getString("UUID")).map(UUIDAdapter::parseId).orElse(null);

            String name = resultSet.getString("Name");
            boolean premium = resultSet.getBoolean("Premium");
            int floodgateNum = resultSet.getInt("Floodgate");
            FloodgateState floodgate;

            // if the player wasn't migrated to the new database format
            if (resultSet.wasNull()) {
                floodgate = FloodgateState.NOT_MIGRATED;
            } else {
                floodgate = FloodgateState.fromInt(floodgateNum);
            }

            String lastIp = resultSet.getString("LastIp");
            Instant lastLogin = resultSet.getTimestamp("LastLogin").toInstant();
            return Optional.of(new StoredProfile(userId, uuid, name, premium, floodgate, lastIp, lastLogin));
        }

        return Optional.empty();
    }

    @Override
    public void save(StoredProfile playerProfile) {
        try (Connection con = dataSource.getConnection()) {
            String uuid = playerProfile.getOptId().map(UUIDAdapter::toMojangId).orElse(null);

            playerProfile.getSaveLock().lock();
            try {
                if (playerProfile.isExistingPlayer()) {
                    try (PreparedStatement saveStmt = con.prepareStatement(UPDATE_PROFILE)) {
                        saveStmt.setString(1, uuid);
                        saveStmt.setString(2, playerProfile.getName());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setInt(4, playerProfile.getFloodgate().getValue());
                        saveStmt.setString(5, playerProfile.getLastIp());

                        saveStmt.setLong(6, playerProfile.getRowId());
                        saveStmt.execute();
                    }
                } else {
                    try (PreparedStatement saveStmt = con.prepareStatement(INSERT_PROFILE, RETURN_GENERATED_KEYS)) {
                        saveStmt.setString(1, uuid);

                        saveStmt.setString(2, playerProfile.getName());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setBoolean(3, playerProfile.isOnlinemodePreferred());
                        saveStmt.setInt(4, playerProfile.getFloodgate().getValue());
                        saveStmt.setString(5, playerProfile.getLastIp());

                        saveStmt.execute();
                        try (ResultSet generatedKeys = saveStmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                playerProfile.setRowId(generatedKeys.getInt(1));
                            }
                        }
                    }
                }
            } finally {
                playerProfile.getSaveLock().unlock();
            }
        } catch (SQLException ex) {
            log.error("Failed to save playerProfile {}", playerProfile, ex);
        }
    }

    /**
     * SQLite has a slightly different syntax, so this will be overridden by SQLiteStorage
     * @return An SQL Statement to create the `premium` table
     */
    protected String getCreateTableStmt() {
        return CREATE_TABLE_STMT;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
