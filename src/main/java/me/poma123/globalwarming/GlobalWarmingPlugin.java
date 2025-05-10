package me.poma123.globalwarming;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemConsumptionHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.updater.GitHubBuildsUpdater;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

import me.poma123.globalwarming.api.TemperatureType;
import me.poma123.globalwarming.commands.GlobalWarmingCommand;
import me.poma123.globalwarming.items.CinnabariteResource;
import me.poma123.globalwarming.items.machines.AirCompressor;
import me.poma123.globalwarming.items.machines.TemperatureMeter;
import me.poma123.globalwarming.listeners.PollutionListener;
import me.poma123.globalwarming.listeners.WorldListener;
import me.poma123.globalwarming.tasks.BurnTask;
import me.poma123.globalwarming.tasks.FireTask;
import me.poma123.globalwarming.tasks.MeltTask;
import me.poma123.globalwarming.tasks.SlownessTask;

public class GlobalWarmingPlugin extends JavaPlugin implements SlimefunAddon {

    private static GlobalWarmingPlugin instance;
    private static Registry registry = new Registry();
    private final TemperatureManager temperatureManager = new TemperatureManager();
    private final GlobalWarmingCommand command = new GlobalWarmingCommand(this);
    private final Config cfg = new Config(this);
    private Config messages;

    @Override
    public void onEnable() {
        instance = this;

        if (cfg.getBoolean("options.auto-update") && getDescription().getVersion().startsWith("DEV - ")) {
            new GitHubBuildsUpdater(this, getFile(), "poma123/GlobalWarming/master").start();
        }

        new Metrics(this, 9132);

        final File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/messages.yml"), messagesFile.toPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create default messages.yml file", e);
            }
        }
        messages = new Config(this, "messages.yml");

        File biomeMapDirectory = new File(getDataFolder(), "biome-maps");
        if (!biomeMapDirectory.exists()) {
            biomeMapDirectory.mkdirs();
        }

        // Create biome map files
        final File pre118BiomeMap = new File(biomeMapDirectory, "pre-1.18.json");
        if (!pre118BiomeMap.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/biome-maps/pre-1.18.json"), pre118BiomeMap.toPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create default biome-maps/pre-1.18.json file", e);
            }
        }
        
        final File post118BiomeMap = new File(biomeMapDirectory, "post-1.18.json");
        if (!post118BiomeMap.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/biome-maps/post-1.18.json"), post118BiomeMap.toPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create default biome-maps/post-1.18.json file", e);
            }
        }
        
        registerItems();
        registerResearches();
        registry.load(cfg, messages);
        scheduleTasks();

        command.register();
        Bukkit.getPluginManager().registerEvents(new PollutionListener(), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(), this);
    }

    private void registerItems() {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(this, "global_warming"), CustomItemStack.create(Items.THERMOMETER.item(), "&2Global Warming"));

        new TemperatureMeter(itemGroup, Items.THERMOMETER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.NICKEL_INGOT.item(), new ItemStack(Material.GLASS), SlimefunItems.NICKEL_INGOT.item(),
                SlimefunItems.NICKEL_INGOT.item(), Items.MERCURY.item(), SlimefunItems.NICKEL_INGOT.item(),
                SlimefunItems.NICKEL_INGOT.item(), new ItemStack(Material.GLASS), SlimefunItems.NICKEL_INGOT.item()
        }) {
            @Override
            public void tick(Block b) {
                Location loc = b.getLocation();
                updateHologram(b, GlobalWarmingPlugin.getTemperatureManager().getTemperatureString(loc, TemperatureType.valueOf(BlockStorage.getLocationInfo(loc, "type"))));
            }
        }.register(this);

        new TemperatureMeter(itemGroup, Items.AIR_QUALITY_METER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.BILLON_INGOT.item(), SlimefunItems.BILLON_INGOT.item(), SlimefunItems.BILLON_INGOT.item(),
                SlimefunItems.SOLDER_INGOT.item(), Items.THERMOMETER.item(), SlimefunItems.SOLDER_INGOT.item(),
                SlimefunItems.SOLDER_INGOT.item(), SlimefunItems.MAGNET.item(), SlimefunItems.SOLDER_INGOT.item()
        }) {
            @Override
            public void tick(Block b) {
                Location loc = b.getLocation();
                updateHologram(b, "&7Climate change: " + GlobalWarmingPlugin.getTemperatureManager().getAirQualityString(loc.getWorld(), TemperatureType.valueOf(BlockStorage.getLocationInfo(loc, "type"))));
            }
        }.register(this);

        new AirCompressor(itemGroup, Items.AIR_COMPRESSOR, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.SOLDER_INGOT.item(), Items.FILTER.item(), SlimefunItems.SOLDER_INGOT.item(),
                SlimefunItems.ALUMINUM_BRASS_INGOT.item(), SlimefunItems.ELECTRIC_MOTOR.item(), SlimefunItems.ALUMINUM_BRASS_INGOT.item(),
                SlimefunItems.SOLDER_INGOT.item(), SlimefunItems.BATTERY.item(), SlimefunItems.SOLDER_INGOT.item()
        }) {
            @Override
            public int getEnergyConsumption() {
                return 16;
            }

            @Override
            public int getCapacity() {
                return 512;
            }

            @Override
            public int getSpeed() {
                return 1;
            }
        }.register(this);

        new SlimefunItem(itemGroup, Items.EMPTY_CANISTER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                null, SlimefunItems.SOLDER_INGOT.item(), null,
                SlimefunItems.SOLDER_INGOT.item(), new ItemStack(Material.GLASS_BOTTLE), SlimefunItems.SOLDER_INGOT.item(),
                SlimefunItems.SOLDER_INGOT.item(), SlimefunItems.SOLDER_INGOT.item(), SlimefunItems.SOLDER_INGOT.item()
        }).register(this);

        new SimpleSlimefunItem<ItemConsumptionHandler>(itemGroup, Items.CO2_CANISTER, AirCompressor.RECIPE_TYPE, new ItemStack[] {
                null, null, null,
                null, Items.EMPTY_CANISTER.item(), null,
                null, null, null
        }) {
            @Override
            public ItemConsumptionHandler getItemHandler() {
                return (e, p, item) -> e.setCancelled(true);
            }
        }.register(this);

        new SlimefunItem(itemGroup, Items.CINNABARITE, RecipeType.GEO_MINER, new ItemStack[]{}).register(this);
        new CinnabariteResource().register();

        new SlimefunItem(itemGroup, Items.MERCURY, RecipeType.SMELTERY, new ItemStack[]{
                Items.CINNABARITE.item(), null, null,
                null, null, null,
                null, null, null
        }).register(this);

        new SlimefunItem(itemGroup, Items.FILTER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[]{
                null, new ItemStack(Material.GLASS), null,
                new ItemStack(Material.GLASS), SlimefunItems.GOLD_PAN.item(), new ItemStack(Material.GLASS),
                null, new ItemStack(Material.GLASS), null
        }).register(this);
    }

    private void registerResearches() {
        registerResearch("thermometer", 69696969, "Thermometer", 10, Items.THERMOMETER.item());
        registerResearch("air_quality_meter", 69696970, "Air Quality Meter", 30, Items.AIR_QUALITY_METER.item());
        registerResearch("air_compressor", 69696971, "Air Compressor", 40, Items.AIR_COMPRESSOR.item());
        registerResearch("canisters", 69696972, "Pollution storing", 6, Items.EMPTY_CANISTER.item(), Items.CO2_CANISTER.item());
        registerResearch("filter", 69696973, "Filter", 8, Items.FILTER.item());
        registerResearch("mercury", 69696973, "Mercury", 12, Items.CINNABARITE.item(), Items.MERCURY.item());
    }

    private void scheduleTasks() {
        if (cfg.getBoolean("mechanics.FOREST_FIRES.enabled")) {
            new FireTask(cfg.getOrSetDefault("mechanics.FOREST_FIRES.min-temperature-in-celsius", 40.0),
                    cfg.getOrSetDefault("mechanics.FOREST_FIRES.chance", 0.3),
                    cfg.getOrSetDefault("mechanics.FOREST_FIRES.fire-per-second", 10)
            ).scheduleRepeating(0, 20);
        }

        if (cfg.getBoolean("mechanics.ICE_MELTING.enabled")) {
            new MeltTask(cfg.getOrSetDefault("mechanics.ICE_MELTING.min-temperature-in-celsius", 2.0),
                    cfg.getOrSetDefault("mechanics.ICE_MELTING.chance", 0.5),
                    cfg.getOrSetDefault("mechanics.ICE_MELTING.melt-per-second", 10)
            ).scheduleRepeating(0, 20);
        }

        if (cfg.getBoolean("mechanics.SLOWNESS.enabled")) {
            new SlownessTask(cfg.getOrSetDefault("mechanics.SLOWNESS.chance", 0.8)).scheduleRepeating(0, 200);
        }

        if (cfg.getBoolean("mechanics.BURN.enabled")) {
            new BurnTask(cfg.getOrSetDefault("mechanics.BURN.chance", 0.8)).scheduleRepeating(0, 200);
        }

        temperatureManager.runCalculationTask(0, 100);
    }

    private void registerResearch(String key, int id, String name, int defaultCost, ItemStack... items) {
        Research research = new Research(new NamespacedKey(this, key), id, name, defaultCost);

        for (ItemStack item : items) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);

            if (sfItem != null) {
                research.addItems(sfItem);
            }
        }

        research.register();
    }

    public static Registry getRegistry() {
        return registry;
    }

    public static TemperatureManager getTemperatureManager() {
        return instance.temperatureManager;
    }

    public static GlobalWarmingPlugin getInstance() {
        return instance;
    }

    public static GlobalWarmingCommand getCommand() {
        return instance.command;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/poma123/GlobalWarming/issues";
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    public static Config getCfg() {
        return instance.cfg;
    }

    public static Config getMessagesConfig() {
        return instance.messages;
    }

}
