package net.misakplak.stasisPaperFix;


import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class BooberEntityManager {

    private final StasisPaperFix plugin;

    public StasisPaperFix getPlugin() {
        return plugin;
    }

    /*
     * Player UUID -> invisible holder.
     */
    private final Map<UUID, ArmorStand> stands = new HashMap<>();

    /*
     * Player UUID -> fishing hook UUID .
     */
    private final Map<UUID, UUID> hooks = new HashMap<>();

    /*
     * Player UUID -> saved bobber location.
     *
     * This is kept even after the real FishHook disappears.
     */
    private final Map<UUID, Location> locations = new HashMap<>();

    public BooberEntityManager(StasisPaperFix plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player throws a fishing rod.
     */
    public void onCast(Player player, FishHook hook) {

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        UUID playerUUID = player.getUniqueId();

        /*
         * Remove an old holder.
         */
        ArmorStand oldStand = stands.remove(playerUUID);

        if (oldStand != null && !oldStand.isDead()) {
            oldStand.remove();
        }

        /*
         * Remove old saved location.
         *
         */


        locations.remove(playerUUID);

        /*
         * Track the new fishing hook.
         */

        hooks.put(
                playerUUID,
                hook.getUniqueId()
        );

        /*
         * Save immediately so the file knows that this
         * player currently has an active fishing hook.
         */
        saveData();
    }

    /**
     * Called when the player reels the rod in.
     */
    public void onReelIn(UUID playerUUID) {

        hooks.remove(playerUUID);
        locations.remove(playerUUID);

        ArmorStand stand = stands.remove(playerUUID);

        if (stand != null && !stand.isDead()) {
            stand.remove();
        }

        saveData();
    }

    /**
     * Releases an active stasis holder.
     */
    public void reelStasis(Player player) {

        UUID playerUUID = player.getUniqueId();

        Location location = locations.get(playerUUID);

        if (location == null || location.getWorld() == null) {
            return;
        }

        //loads stasis chamber chunk.
        location.getChunk().load();

        // Remove the stored holder reference.
        ArmorStand stand = stands.remove(playerUUID);

        if (stand != null && !stand.isDead()) {
            stand.remove();
        }

        // Also look for the actual entity in the world.
        ArmorStand actualHolder = findHolder(location);

        if (actualHolder != null && !actualHolder.isDead()) {
            actualHolder.remove();

            plugin.getLogger().info(
                    "Removed actual stasis holder for "
                            + player.getName()
            );
        }

        // Clear the stasis state.
        locations.remove(playerUUID);
        hooks.remove(playerUUID);

        saveData();

        plugin.getLogger().info(
                "Reeled in stasis for "
                        + player.getName()
        );
    }

    public boolean hasStasis(Player player) {
        return locations.containsKey(player.getUniqueId());
    }

    /**
     * Checks whether the fishing bobber is sitting on a presure plate.
     */
    private boolean isPressurePlate(Location location) {

        Material current = location.getBlock().getType();

        if (current.name().endsWith("_PRESSURE_PLATE")) {
            return true;
        }

        Material below = location.getBlock()
                .getRelative(org.bukkit.block.BlockFace.DOWN)
                .getType();

        return below.name().endsWith("_PRESSURE_PLATE");
    }


    /**
     * Checks fishing hooks every tick.
     */
    public void tick() {

        Iterator<Map.Entry<UUID, UUID>> iterator =
                hooks.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<UUID, UUID> entry =
                    iterator.next();

            UUID playerUUID = entry.getKey();
            UUID hookUUID = entry.getValue();

            FishHook hook = findHook(hookUUID);

            /*
             * The hook no longer exists.
             *
             * If we already saved its location, keep that location.
             */
            if (hook == null || hook.isDead()) {

                iterator.remove();

                saveData();

                continue;
            }


            /*
             * The original Stasis Fixer uses:
             *
             * hook.getVelocity().lengthSquared() < 0.005
             */
            if (hook.getVelocity().lengthSquared() < 0.005) {

                if (locations.containsKey(playerUUID)) {
                    iterator.remove();
                    continue;
                }

                Location location =
                        hook.getLocation().clone();

                if (!isPressurePlate(location)){
                    continue;
                }

                locations.put(
                        playerUUID,
                        location
                );

                iterator.remove();

                saveData();

                plugin.getLogger().info(
                        "Saved stasis location for "
                                + playerUUID
                                + " at "
                                + formatLoc(location)
                );
            }
        }
    }

    /**
     * Called on any world change. Spawns the holder if the
     * player has a settled bobber location and doesn't already
     * have one - safe to call every time, since both of those
     * checks make it a no- op otherwise.
     */
    public void activateStasis(Player player) {

        UUID playerUUID =
                player.getUniqueId();

        /*
         * Already has a holder.
         */
        ArmorStand existing =
                stands.get(playerUUID);

        if (existing != null &&
                !existing.isDead() &&
                existing.isValid()) {
            return;
        }

        Location location =
                locations.get(playerUUID);

        if (location == null) {
            return;
        }

        /*
         * Create the invisible entity at the ORIGINAL
         * fishing bobber location.
         */
        ArmorStand stand =
                spawnHolder(location);

        if (stand == null) {
            return;
        }

        stands.put(
                playerUUID,
                stand
        );

        saveData();

        plugin.getLogger().info(
                "Activated stasis for "
                        + player.getName()
                        + " at "
                        + formatLoc(location)
        );
    }

    /**
     * Creates the invisible collision entity.
     */
    private ArmorStand spawnHolder(Location location) {

        World world = location.getWorld();

        if (world == null) {
            return null;
        }

        return world.spawn(
                location,
                ArmorStand.class,
                stand -> {

                    stand.setVisible(false);
                    stand.setGravity(false);
                    stand.setMarker(false);

                    stand.setPersistent(true);
                    stand.setInvulnerable(true);
                    stand.setSilent(true);
                    stand.setSmall(false);

                    // Don't push players.
                    stand.setCollidable(false);

                    stand.addScoreboardTag("stasis_holder");
                }
        );
    }

    /**
     * Finds a fishing hook by UUID.
     */
    private FishHook findHook(UUID uuid) {

        for (World world :
                plugin.getServer().getWorlds()) {

            Entity entity =
                    world.getEntity(uuid);

            if (entity instanceof FishHook hook) {
                return hook;
            }
        }

        return null;
    }

    /**
     * Saves everything to holders.yml.
     */
    public void saveData() {

        plugin.getDataFolder().mkdirs();

        File file =
                new File(
                        plugin.getDataFolder(),
                        "holders.yml"
                );

        FileConfiguration config =
                new YamlConfiguration();

        /*
         * Save active holders.
         */
        for (Map.Entry<UUID, ArmorStand> entry :
                stands.entrySet()) {

            ArmorStand stand =
                    entry.getValue();

            if (stand == null ||
                    stand.isDead()) {

                continue;
            }

            String path =
                    "holders."
                            + entry.getKey();

            Location location =
                    stand.getLocation();

            config.set(
                    path + ".standUUID",
                    stand.getUniqueId().toString()
            );

            saveLocation(
                    config,
                    path,
                    location
            );
        }

        /*
         * Save pending stasis locations.
         */
        for (Map.Entry<UUID, Location> entry :
                locations.entrySet()) {

            String path =
                    "locations."
                            + entry.getKey();

            saveLocation(
                    config,
                    path,
                    entry.getValue()
            );
        }

        try {

            config.save(file);

        } catch (IOException e) {

            plugin.getLogger().severe(
                    "Could not save holders.yml: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Loads holders.yml when the server starts.
     */
    public void loadData() {

        File file =
                new File(
                        plugin.getDataFolder(),
                        "holders.yml"
                );

        if (!file.exists()) {
            return;
        }

        FileConfiguration config =
                YamlConfiguration.loadConfiguration(file);

        /*
         * Load saved locations.
         */
        ConfigurationSection locationsSection =
                config.getConfigurationSection(
                        "locations"
                );

        if (locationsSection != null) {

            for (String uuidString :
                    locationsSection.getKeys(false)) {

                UUID uuid;

                try {
                    uuid =
                            UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                Location location =
                        loadLocation(
                                config,
                                "locations." + uuidString
                        );

                if (location != null) {
                    locations.put(
                            uuid,
                            location
                    );
                }
            }
        }

        /*
         * Load existing ArmorStands.
         */
        ConfigurationSection holdersSection =
                config.getConfigurationSection(
                        "holders"
                );

        if (holdersSection == null) {
            return;
        }

        for (String uuidString :
                holdersSection.getKeys(false)) {

            UUID playerUUID;

            try {
                playerUUID =
                        UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }

            String standUUIDString =
                    config.getString(
                            "holders."
                                    + uuidString
                                    + ".standUUID"
                    );

            if (standUUIDString == null) {
                continue;
            }

            UUID standUUID;

            try {
                standUUID =
                        UUID.fromString(
                                standUUIDString
                        );
            } catch (IllegalArgumentException e) {
                continue;
            }

            Location location =
                    loadLocation(
                            config,
                            "holders." + uuidString
                    );

            if (location == null ||
                    location.getWorld() == null) {

                continue;
            }

            location.getWorld().getChunkAt(location).load();

            ArmorStand stand = findHolder(location);

            if (stand != null) {
                stands.put(playerUUID, stand);
            } else {
                ArmorStand newStand = spawnHolder(location);

                if (newStand != null) {
                    stands.put(playerUUID, newStand);
                }
            }
        }
    }

    /**
     * Finds one of our invisible holders near the saved bobber location.
     */
    private ArmorStand findHolder(Location location) {

        World world = location.getWorld();

        if (world == null) {
            return null;
        }

        for (Entity entity : world.getNearbyEntities(
                location,
                1.5,
                1.5,
                1.5
        )) {

            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }

            if (stand.getScoreboardTags().contains("stasis_holder")) {
                return stand;
            }
        }

        return null;
    }

    /**
     * Restores an active stasis holder after the player rejoins.
     *
     *
     *
     * The actual holder is stored in the world, but its chunk may have
     * been unloaded while the player was offline. We therefore load the
     * original bobber chunk and look for our holder again.
     */
    public void restoreStasis(Player player) {

        UUID playerUUID = player.getUniqueId();

        Location location = locations.get(playerUUID);

        if (location == null || location.getWorld() == null) {
            return;
        }

        // Make sure the stasis chamber's chunk is loaded.
        location.getChunk().load();

        ArmorStand stand = stands.get(playerUUID);

        // Our in-memory reference is still valid.
        if (stand != null && !stand.isDead() && stand.isValid()) {
            plugin.getLogger().info(
                    "Restored stasis reference for "
                            + player.getName()
            );
            return;
        }

        // The old entity reference is no longer usable.
        stands.remove(playerUUID);

        // Look for the holder that Minecraft loaded from disk.
        ArmorStand existing = findHolder(location);

        if (existing != null) {

            stands.put(playerUUID, existing);
            saveData();

            plugin.getLogger().info(
                    "Found existing stasis holder for "
                            + player.getName()
                            + " after rejoin."
            );

            return;
        }

        // The old ArmorStand wasn't loaded/saved, so create a new one.
        ArmorStand newStand = spawnHolder(location);

        if (newStand == null) {
            plugin.getLogger().warning(
                    "Could not restore stasis holder for "
                            + player.getName()
            );
            return;
        }

        stands.put(playerUUID, newStand);
        saveData();

        plugin.getLogger().info(
                "Created new stasis holder for "
                        + player.getName()
                        + " after rejoin."
        );
    }

    /**
     * Saves a Location using individual values.
     */
    private void saveLocation(
            FileConfiguration config,
            String path,
            Location location
    ) {

        if (location == null ||
                location.getWorld() == null) {

            return;
        }

        config.set(
                path + ".world",
                location.getWorld()
                        .getUID()
                        .toString()
        );

        config.set(
                path + ".x",
                location.getX()
        );

        config.set(
                path + ".y",
                location.getY()
        );

        config.set(
                path + ".z",
                location.getZ()
        );

        config.set(
                path + ".yaw",
                location.getYaw()
        );

        config.set(
                path + ".pitch",
                location.getPitch()
        );
    }

    /**
     * Loads a Location from holders.yml.
     */
    private Location loadLocation(
            FileConfiguration config,
            String path
    ) {

        String worldString =
                config.getString(
                        path + ".world"
                );

        if (worldString == null) {
            return null;
        }

        UUID worldUUID;

        try {
            worldUUID =
                    UUID.fromString(worldString);
        } catch (IllegalArgumentException e) {
            return null;
        }

        World world =
                plugin.getServer()
                        .getWorld(worldUUID);

        if (world == null) {
            return null;
        }

        double x =
                config.getDouble(
                        path + ".x"
                );

        double y =
                config.getDouble(
                        path + ".y"
                );

        double z =
                config.getDouble(
                        path + ".z"
                );

        float yaw =
                (float) config.getDouble(
                        path + ".yaw"
                );

        float pitch =
                (float) config.getDouble(
                        path + ".pitch"
                );

        return new Location(
                world,
                x,
                y,
                z,
                yaw,
                pitch
        );
    }

    private String formatLoc(Location location) {

        return location.getWorld().getName()
                + " "
                + location.getX()
                + ", "
                + location.getY()
                + ", "
                + location.getZ();
    }


    /*
    *PLEASE WORK THIS TOOK ME AGES TO FIGGURE OUT BRO...
    * note *there is AI used at some point because i needed to get this done.
     */


}