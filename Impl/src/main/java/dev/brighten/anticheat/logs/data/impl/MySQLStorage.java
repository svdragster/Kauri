package dev.brighten.anticheat.logs.data.impl;

import cc.funkemunky.api.utils.MiscUtils;
import cc.funkemunky.api.utils.RunUtils;
import dev.brighten.anticheat.Kauri;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.logs.data.DataStorage;
import dev.brighten.anticheat.logs.data.sql.MySQL;
import dev.brighten.anticheat.logs.data.sql.Query;
import dev.brighten.anticheat.logs.objects.Log;
import dev.brighten.anticheat.logs.objects.Punishment;
import lombok.val;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class MySQLStorage implements DataStorage {

    private List<Log> logs = new CopyOnWriteArrayList<>();
    private List<Punishment> punishments = new CopyOnWriteArrayList<>();
    private BukkitTask task;

    public MySQLStorage() {
        MySQL.init();
        Query.prepare("CREATE TABLE IF NOT EXISTS `VIOLATIONS` (" +
                "`UUID` VARCHAR(64) NOT NULL," +
                "`TIME` LONG NOT NULL," +
                "`VL` FLOAT NOT NULL," +
                "`CHECK` VARCHAR(32) NOT NULL," +
                "`PING` SMALLINT NOT NULL," +
                "`TPS` DOUBLE NOT NULL," +
                "`INFO` LONGTEXT NOT NULL)").execute();
        Query.prepare("CREATE TABLE IF NOT EXISTS `PUNISHMENTS` (" +
                "`UUID` VARCHAR(64) NOT NULL," +
                "`TIME` LONG NOT NULL," +
                "`CHECK` VARCHAR(32) NOT NULL)").execute();
        Query.prepare("CREATE TABLE IF NOT EXISTS `NAMECACHE` (" +
                "`UUID` VARCHAR(64) NOT NULL," +
                "`NAME` VARCHAR(16) NOT NULL," +
                "`TIMESTAMP` LONG NOT NULL)").execute();
        Kauri.INSTANCE.loggingThread.execute(() -> {
            MiscUtils.printToConsole("&7Creating iv_uuid index for SQL...");
            Query.prepare("CREATE INDEX `iv_uuid` ON `VIOLATIONS` (UUID)").execute();
            MiscUtils.printToConsole("&aCreated iv_uuid!");
            MiscUtils.printToConsole("&7Creating ip_uuid index for SQL...");
            Query.prepare("CREATE INDEX `ip_uuid` ON `PUNISHMENTS` (UUID)").execute();
            MiscUtils.printToConsole("&aCreated ip_uuid!");
            MiscUtils.printToConsole("&a7 Creating iv_time index for SQL...");
            Query.prepare("CREATE INDEX `iv_time` ON `VIOLATIONS` (`TIME`)").execute();
            MiscUtils.printToConsole("&aCreated!");
        });

        task = RunUtils.taskTimerAsync(() -> {
            if(logs.size() > 0) {
                for (Log log : logs) {
                    try {
                        Query.prepare("INSERT INTO `VIOLATIONS`" +
                                " (`UUID`, `TIME`, `VL`, `CHECK`, `PING`, `TPS`, `INFO`) VALUES (?,?,?,?,?,?,?)")
                                .append(log.uuid.toString()).append(log.timeStamp).append(log.vl)
                                .append(log.checkName).append((int)log.ping).append(log.tps)
                                .append(log.info)
                                .execute();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    logs.remove(log);
                }
            }
            if(punishments.size() > 0) {
                for(Punishment punishment : punishments) {
                    try {
                        Query.prepare("INSERT INTO `PUNISHMENTS` (`UUID`,`TIME`,`CHECK`) VALUES (?,?,?)")
                                .append(punishment.uuid.toString())
                                .append(punishment.timeStamp).append(punishment.checkName)
                                .execute();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    punishments.remove(punishment);
                }
            }
        }, Kauri.INSTANCE, 120L, 40L);
    }

    @Override
    public void shutdown() {
        task.cancel();
        task = null;
        logs.clear();
        punishments.clear();
        MySQL.shutdown();
    }

    @Override
    public List<Log> getLogs(UUID uuid, Check check, int arrayMin, int arrayMax, long timeFrom, long timeTo) {
        List<Log> logs = new ArrayList<>();

        if(uuid != null) {
            Query.prepare("SELECT `TIME`, `VL`, `CHECK`, `PING`, `TPS`, `INFO` " +
                    "FROM `VIOLATIONS` WHERE `UUID` = ?"+ (check != null ? " AND WHERE `CHECK` = " + check.name : "")
                    + " AND `TIME` BETWEEN ? AND ? ORDER BY `TIME` DESC LIMIT ?,?")
                    .append(uuid.toString()).append(timeFrom).append(timeTo).append(arrayMin).append(arrayMax)
                    .execute(rs ->
                            logs.add(new Log(uuid,
                                    rs.getString("CHECK"), rs.getString("INFO"),
                                    rs.getFloat("VL"), (long)rs.getInt("PING"),
                                    rs.getLong("TIME"), rs.getDouble("TPS"))));
        } else {
            Query.prepare("SELECT `UUID`, `TIME`, `VL`, `CHECK`, `PING`, `TPS`, `INFO` " +
                    "FROM `VIOLATIONS`" + (check != null ? " WHERE `CHECK` = " + check.name + " AND" : " WHERE")
                    + " `TIME` BETWEEN ? AND ? ORDER BY `TIME` DESC LIMIT ?,?")
                    .append(timeFrom).append(timeTo).append(arrayMin).append(arrayMax)
                    .execute(rs -> {
                        logs.add(new Log(UUID.fromString(rs.getString("UUID")),
                                rs.getString("CHECK"), rs.getString("INFO"),
                                rs.getFloat("VL"), (long)rs.getInt("PING"),
                                rs.getLong("TIME"), rs.getDouble("TPS")));
                    });
        }

        return logs;
    }

    @Override
    public List<Punishment> getPunishments(UUID uuid, int arrayMin, int arrayMax, long timeFrom, long timeTo) {
        List<Punishment> punishments = new ArrayList<>();

        if(uuid != null) {
            Query.prepare("SELECT `TIME`, `CHECK` FROM `PUNISHMENTS` " +
                    "WHERE `UUID` = ? AND TIME BETWEEN ? AND ? ORDER BY `TIME` DESC LIMIT ?,?")
                    .append(uuid.toString()).append(timeFrom).append(timeTo).append(arrayMin).append(arrayMax)
                    .execute(rs -> punishments
                            .add(new Punishment(uuid, rs.getString("CHECK"), rs.getLong("TIME"))));
        } else {
            Query.prepare("SELECT `UUID`, `TIME`, `CHECK` FROM `PUNISHMENTS` " +
                    "WHERE TIME BETWEEN ? AND ? ORDER BY `TIME` DESC LIMIT ?,?")
                    .append(timeFrom).append(timeTo).append(arrayMin).append(arrayMax)
                    .execute(rs -> punishments
                            .add(new Punishment(UUID.fromString(rs.getString("UUID")),
                                    rs.getString("CHECK"), rs.getLong("TIME"))));
        }

        return punishments;
    }

    @Override
    public List<Log> getHighestVL(UUID uuid, Check check, int limit, long timeFrom, long timeTo) {
        List<Log> logs = getLogs(uuid, check, 0, limit, timeFrom, timeTo);

        Map<String, Log> logsMax = new HashMap<>();

        logs.forEach(log -> {
            if(logsMax.containsKey(log.checkName)) {
                Log toCheck = logsMax.get(log.checkName);

                if(toCheck.vl < log.vl) {
                    logsMax.put(log.checkName, log);
                }
            } else logsMax.put(log.checkName, log);
        });
        return new ArrayList<>(logsMax.values());
    }

    @Override
    public void addLog(Log log) {
        logs.add(log);
    }

    @Override
    public void removeAll(UUID uuid) {
        Query.prepare("DELETE FROM `VIOLATIONS` WHERE UUID = ?").append(uuid.toString())
                .execute();
        Query.prepare("DELETE FROM `PUNISHMENTS` WHERE UUID = ?").append(uuid.toString())
                .execute();
    }

    @Override
    public void addPunishment(Punishment punishment) {
        punishments.add(punishment);
    }

    @Override
    public void cacheAPICall(UUID uuid, String name) {
        Kauri.INSTANCE.loggingThread.execute(() -> {
            Query.prepare("DELETE FROM `NAMECACHE` WHERE `UUID` = ?").append(uuid.toString()).execute();
            Query.prepare("INSERT INTO `NAMECACHE` (`UUID`, `NAME`, `TIMESTAMP`) VALUES (?, ?, ?)")
                    .append(uuid.toString()).append(name).append(System.currentTimeMillis()).execute();
        });
    }

    @Override
    public UUID getUUIDFromName(String name) {
        try {
            val rs = Query.prepare("SELECT `UUID`, `TIMESTAMP` FROM `NAMECACHE` WHERE `NAME` = ?")
                    .append(name).executeQuery();

            String uuidString = rs.getString("UUID");

            if(uuidString != null) {
                UUID uuid = UUID.fromString(rs.getString("UUID"));

                if(System.currentTimeMillis() - rs.getLong("TIMESTAMP") > TimeUnit.DAYS.toMillis(1)) {
                    Kauri.INSTANCE.loggingThread.execute(() -> {
                        Query.prepare("DELETE FROM `NAMECACHE` WHERE `UUID` = ?").append(uuidString).execute();
                        MiscUtils.printToConsole("Deleted " + uuidString + " from name cache (age > 1 day).");
                    });
                }
                return uuid;
            }
        } catch (SQLException e) {
            RunUtils.task(e::printStackTrace);
        } catch(Exception e) {
            e.printStackTrace();
            //Empty catch
        }
        return null;
    }

    @Override
    public String getNameFromUUID(UUID uuid) {
        try {
            val rs = Query.prepare("SELECT `NAME` `TIMESTAMP` FROM `NAMECACHE` WHERE `UUID` = ?")
                    .append(uuid.toString()).executeQuery();

            String name = rs.getString("NAME");

            if(name != null) {
                if(System.currentTimeMillis() - rs.getLong("TIMESTAMP") > TimeUnit.DAYS.toMillis(1)) {
                    Kauri.INSTANCE.loggingThread.execute(() -> {
                        Query.prepare("DELETE FROM `NAMECACHE` WHERE `NAME` = ?").append(name).execute();
                        MiscUtils.printToConsole("Deleted " + name + " from name cache (age > 1 day).");
                    });
                }
                return name;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}