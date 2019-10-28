package dev.brighten.anticheat;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.config.system.Configuration;
import cc.funkemunky.api.config.system.ConfigurationProvider;
import cc.funkemunky.api.config.system.YamlConfiguration;
import cc.funkemunky.api.profiling.ToggleableProfiler;
import cc.funkemunky.api.utils.Color;
import cc.funkemunky.api.utils.MiscUtils;
import cc.funkemunky.api.utils.RunUtils;
import cc.funkemunky.api.utils.TickTimer;
import dev.brighten.anticheat.check.api.Check;
import dev.brighten.anticheat.data.DataManager;
import dev.brighten.anticheat.logs.LoggerManager;
import dev.brighten.anticheat.processing.EntityProcessor;
import dev.brighten.anticheat.processing.PacketProcessor;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Kauri extends JavaPlugin {

    public static Kauri INSTANCE;

    public PacketProcessor packetProcessor;
    public DataManager dataManager;
    public LoggerManager loggerManager;

    //Lag Information
    public double tps;
    public TickTimer lastTickLag;

    public ExecutorService executor;
    public ToggleableProfiler profiler;

    public boolean enabled = false;
    public TickTimer lastEnabled;

    //Config Stuff
    public Configuration kauriConfig;

    public void onEnable() {
        MiscUtils.printToConsole(Color.Red + "Starting Kauri " + getDescription().getVersion() + "...");
        INSTANCE = this;

        load(); //Everything in one method so we can use it in other places like when reloading.
    }

    public void onDisable() {
        unload();
    }

    public void unload() {
        enabled = false;
        executor.execute(() -> loggerManager.logsDatabase.saveDatabase());
        //Clearing all fields in ObjectData to prevent work from GC.
        MiscUtils.printToConsole("&7Shutting down threadPool and saving config...");
        saveConfig(); //Saving config.
        MiscUtils.printToConsole("&7Unregistering Atlas and Bukkit listeners...");
        HandlerList.unregisterAll(this); //Unregistering Bukkit listeners.
        Atlas.getInstance().getEventManager().unregisterAll(this); //Unregistering Atlas listeners.
        MiscUtils.printToConsole("&7Unregistering commands...");
        Atlas.getInstance().getCommandManager().unregisterCommand("kauri"); //Unregister all commands starting with the arg "Kauri".
        MiscUtils.printToConsole("&7Shutting down all Bukkit tasks...");
        Bukkit.getScheduler().cancelTasks(this); //Cancelling all Bukkit tasks for this plugin.

        Kauri.INSTANCE.dataManager.dataMap.keySet().forEach(key -> Kauri.INSTANCE.dataManager.dataMap.remove(key));
        MiscUtils.printToConsole("&7Unloading DataManager...");
        //Clearing the dataManager.
        Kauri.INSTANCE.dataManager.dataMap.clear();
        Kauri.INSTANCE.dataManager.dataMap = null;
        Kauri.INSTANCE.dataManager = null;

        MiscUtils.printToConsole("&7Clearing checks and cached entity information...");
        EntityProcessor.vehicles.clear(); //Clearing all registered vehicles.
        //Clearing the checks.
        Check.checkClasses.clear();
        Check.checkSettings.clear();
        profiler.enabled = false;
        profiler = null;
        packetProcessor = null;
        executor.shutdown(); //Shutting down threads.
    }

    public void load() {
        MiscUtils.printToConsole(Color.Gray + "Starting thread pool...");
        executor = Executors.newFixedThreadPool(3);

        MiscUtils.printToConsole(Color.Gray + "Loading config...");
        kauriConfig = YamlConfiguration.saveDefaultConfig(this, "config.yml");

        MiscUtils.printToConsole(Color.Gray + "Running scanner...");
        Atlas.getInstance().initializeScanner(getClass(),
                this,
                true,
                true,
                kauriConfig);
        MiscUtils.printToConsole(Color.Gray + "Registering processors...");
        packetProcessor = new PacketProcessor();
        dataManager = new DataManager();
        loggerManager = new LoggerManager(true);
        EntityProcessor.start();

        MiscUtils.printToConsole(Color.Gray + "Registering checks...");
        Check.registerChecks();

        MiscUtils.printToConsole(Color.Gray + "Running tps task...");
        runTpsTask();
        profiler = new ToggleableProfiler();
        profiler.enabled = true;

        if(Bukkit.getOnlinePlayers().size() > 0) {
            RunUtils.taskLater(() -> {
                MiscUtils.printToConsole(Color.Gray + "Detected players! Creating data objects...");
                Bukkit.getOnlinePlayers().forEach(dataManager::createData);
            }, this, 6L);
        }
        lastEnabled = new TickTimer(20);
        enabled = true;
        lastEnabled.reset();
    }

    public void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(kauriConfig, new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        try {
            File file;
            if(!(file = new File(getDataFolder(), "config.yml")).exists()) {
                YamlConfiguration.saveDefaultConfig(this, "config.yml");
            }
            ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runTpsTask() {
        lastTickLag = new TickTimer(6);
        new BukkitRunnable() {
            private int ticks;
            private long lastTimeStamp, lastTick;
            public void run() {
                ticks++;
                long currentTime = System.currentTimeMillis();

                if(currentTime - lastTick > 120) {
                    lastTickLag.reset();
                }
                if(ticks >= 10) {
                    ticks = 0;
                    tps = 500D / (currentTime - lastTimeStamp) * 20;
                    lastTimeStamp = currentTime;
                }
                lastTick = currentTime;
            }
        }.runTaskTimer(this, 1L, 1L);
    }
}
