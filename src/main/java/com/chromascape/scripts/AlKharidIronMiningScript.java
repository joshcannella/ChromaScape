package com.chromascape.scripts;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.actions.custom.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mines iron ore at Al Kharid mine and banks at Al Kharid bank when inventory is full.
 *
 * <p>Clicks RuneLite Cyan-highlighted iron rocks in the game view, waits until idle,
 * then walks to Al Kharid bank to deposit ore via the deposit-all button. The three
 * clustered iron rocks at Al Kharid allow mining without moving.
 *
 * <p><b>Prerequisites:</b> 15 Mining, any pickaxe equipped
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>Object Markers — mark the 3 clustered iron rocks AND the bank booth with Cyan (#00FFFF)
 * </ul>
 *
 * <p><b>Inventory Layout:</b> Pickaxe equipped. All 28 slots free for ore.
 *
 * <p><b>Image Templates (auto-downloaded from OSRS Wiki):</b>
 * <ul>
 *   <li>{@code /images/user/Iron_ore.png} — https://oldschool.runescape.wiki/w/File:Iron_ore.png
 * </ul>
 *
 * <p><b>Wiki source:</b>
 * <a href="https://oldschool.runescape.wiki/w/Al_Kharid_mine">Al Kharid mine</a> — (3298, 3293),
 * <a href="https://oldschool.runescape.wiki/w/Al_Kharid_bank">Al Kharid bank</a> — (3269, 3167).
 */
@ScriptVersion(major = 0, minor = 1)
public class AlKharidIronMiningScript extends BaseScript {

    private static final Logger logger = LogManager.getLogger(AlKharidIronMiningScript.class);

    // === Image Templates ===
    private static final String IRON_ORE_IMAGE = "/images/user/Iron_ore.png";

    // === Script Constants ===
    private static final String ROCK_COLOUR = "Cyan";
    private static final String BANK_COLOUR = "Cyan";
    private static final int IDLE_TIMEOUT = 20;

    // === Walker Destinations (from OSRS Wiki) ===
    private static final Point MINE_TILE = new Point(3298, 3293);
    private static final Point BANK_TILE = new Point(3269, 3167);

    @Override
    protected void cycle() {
        if (HumanBehavior.runPreCycleChecks(this)) return;

        if (isInventoryFull()) {
            logger.info("Inventory full, banking ore");
            bankOre();
            return;
        }

        clickRock();
        HumanBehavior.sleep(800, 1000);
        Idler.waitUntilIdle(this, IDLE_TIMEOUT);
    }

    /**
     * Walks to Al Kharid bank, opens the bank booth, deposits all ore,
     * then walks back to the mine.
     */
    private void bankOre() {
        Walk.toOrStop(this, BANK_TILE, "bank");
        HumanBehavior.sleep(600, 900);
        Bank.open(this, BANK_COLOUR);
        Bank.depositAll(this);
        Bank.close(this);
        Walk.toOrStop(this, MINE_TILE, "mine");
        HumanBehavior.sleep(600, 900);
    }

    /**
     * Locates a Cyan-highlighted iron rock in the game view and clicks it
     * with human-like mouse behavior including hesitation and misclick simulation.
     */
    private void clickRock() {
        BufferedImage gameView = controller().zones().getGameView();
        Point clickLoc = PointSelector.getRandomPointInColour(gameView, ROCK_COLOUR, 15);
        if (clickLoc == null) {
            logger.error("No iron rock found in game view");
            stop();
            return;
        }

        HumanBehavior.click(this, clickLoc);
    }

    /**
     * Checks whether the inventory is full by template-matching iron ore
     * in the last inventory slot (index 27).
     *
     * @return {@code true} if the last slot contains iron ore
     */
    private boolean isInventoryFull() {
        Rectangle lastSlot = controller().zones().getInventorySlots().get(27);
        BufferedImage slotImg = ScreenManager.captureZone(lastSlot);
        return TemplateMatching.match(IRON_ORE_IMAGE, slotImg, 0.05).bounds() != null;
    }
}
