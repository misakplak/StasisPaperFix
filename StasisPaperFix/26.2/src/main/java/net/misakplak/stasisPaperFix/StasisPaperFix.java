package net.misakplak.stasisPaperFix;

import org.bukkit.plugin.java.JavaPlugin;

public final class StasisPaperFix extends JavaPlugin {

    private BooberEntityManager manager;


    @Override
    public void onEnable() {

        manager = new BooberEntityManager(this);

        // Load saved stasis locations / holders
        manager.loadData();

        getServer().getPluginManager().registerEvents(
                new StasisListener(this),
                this
        );

        // Check fishing hooks every tick
        getServer().getScheduler().runTaskTimer(
                this,
                manager::tick,
                1L,
                1L
        );

        getLogger().info("Stasis plugin enabled.");
    }

    @Override
    public void onDisable() {
        manager.saveData();
    }

    public BooberEntityManager getBobberManager() {
        return manager;
    }

    /*
     *PLEASE WORK THIS TOOK ME AGES TO FIGGURE OUT BRO...
     * note *there is AI used at some point because i needed to get this done.
     */
}