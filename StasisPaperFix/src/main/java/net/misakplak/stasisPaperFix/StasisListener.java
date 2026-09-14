package net.misakplak.stasisPaperFix;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class StasisListener implements Listener {

    private final BooberEntityManager manager;

    public StasisListener(StasisPaperFix plugin) {
        this.manager = plugin.getBobberManager();
    }

    /**
     * Normal fishing events.
     */
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {

        switch (event.getState()) {

            case FISHING -> manager.onCast(
                    event.getPlayer(),
                    event.getHook()
            );

            /*
             * All three of these mean the real hook is gone
             * (caught a fish, caught an entity, or just reeled
             * in with nothing on the line). If we don't clear
             * state here too, the old cast location sticks
             * around and pops back up as a ghost stasis the
             * next time the player changes worl d.
             */
            case CAUGHT_FISH, CAUGHT_ENTITY, REEL_IN -> manager.onReelIn(
                    event.getPlayer().getUniqueId()
            );

            default -> {
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRodUse(PlayerInteractEvent event) {

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR &&
                action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.FISHING_ROD) {
            return;
        }

        Player player = event.getPlayer();

        if (manager.hasStasis(player)) {
            manager.reelStasis(player);
            event.setCancelled(true);
        }
    }

    /**
     * Handles any dimension change. Paper kills the real
     * FishHook the moment the owning player switches worlds,
     * so this is where we swap it out for the invisible holder
     * that keeps the pressure plate pressed. activateStasis()
     * already no-ops if there is no pending cast or the holder
     * already exists, so it's fine to just call it on every
     * world change instead of only Nether <-> ovrworld.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.activateStasis(event.getPlayer());
    }

    /*
     *PLEASE WORK THIS TOOK ME AGES TO FIGGURE OUT BRO...
     * note *there is AI used at some point because i needed to get this done.
     */
}