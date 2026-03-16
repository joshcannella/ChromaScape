package com.chromascape.scripts;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.LevelUpDismisser;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.scripts.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mines gold ore at Al Kharid mine and banks at Al Kharid bank.
 *
 * <p>Clicks RuneLite Cyan-highlighted gold rocks, waits until idle, and when the
 * inventory is full walks to Al Kharid bank to deposit via the deposit-inventory
 * button, then walks back to the mine. Al Kharid mine has 2 gold rocks.
 *
 * <p><b>Prerequisites:</b> 40 Mining, any pickaxe equipped
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>Object Markers — mark the 2 gold rocks at Al Kharid mine with Cyan (#00FFFF)
 *   <li>Object Markers — mark the bank booth at Al Kharid bank with Cyan (#00FFFF)
 * </ul>
 *
 * <p><b>Inventory Layout:</b> Pickaxe equipped. All 28 slots free for ore.
 *
 * <p><b>Image Templates (auto-downloaded from OSRS Wiki):</b>
 * <ul>
 *   <li>{@code /images/user/Gold_ore.png} — https://oldschool.runescape.wiki/w/File:Gold_ore.png
 * </ul>
 *
 * <p><b>Wiki source:</b>
 * <a href="https://oldschool.runescape.wiki/w/Al_Kharid_mine">Al Kharid mine</a> — (3298, 3293),
 * <a href="https://oldschool.runescape.wiki/w/Al_Kharid_bank">Al Kharid bank</a> — (3269, 3167).
 * Gold ore ID 444, 40 Mining required.
 */
public class AlKharidGoldMiningScript extends BaseScript {

    private static final Logger logger = LogManager.getLogger(AlKharidGoldMiningScript.class);

    // === Image Templates ===
    private static final String GOLD_ORE_IMAGE = "/images/user/Gold_ore.png";

    // === Script Constants ===
    private static final String ROCK_COLOUR = "Cyan";
    private static final String BANK_COLOUR = "Cyan";
    private static final int IDLE_TIMEOUT = 25;

    // === Walker Destinations (from OSRS Wiki) ===
    private static final Point MINE_TILE = new Point(3298, 3293);
    private static final Point BANK_TILE = new Point(3269, 3167);

    @Override
    protected void cycle() {
        HumanBehavior.updateTempoDrift();

        if (HumanBehavior.shouldTakeExtendedBreak()) {
            HumanBehavior.performBreak(this, true);
            return;
        }
        if (HumanBehavior.shouldTakeBreak()) {
            HumanBehavior.performBreak(this, false);
            return;
        }
        if (HumanBehavior.shouldFidgetCamera()) {
            HumanBehavior.performCameraFidget(this);
        }
        if (HumanBehavior.shouldIdleDrift()) {
            HumanBehavior.performIdleDrift(this);
        }

        LevelUpDismisser.dismissIfPresent(this);

        if (isInventoryFull()) {
            logger.info("Inventory full, banking gold ore");
            bankOre();
            return;
        }

        clickRock();
        waitMillis(HumanBehavior.adjustDelay(800, 1000));
        Idler.waitUntilIdle(this, IDLE_TIMEOUT);
    }

    /**
     * Walks to Al Kharid bank, deposits all ore, then walks back to the mine.
     */
    private void bankOre() {
        walkTo(BANK_TILE, "bank");
        waitMillis(HumanBehavior.adjustDelay(600, 900));
        openBank();
        depositAll();
        closeBank();
        walkTo(MINE_TILE, "mine");
        waitMillis(HumanBehavior.adjustDelay(600, 900));
    }

    /** Clicks the Cyan-highlighted bank booth and waits for the interface to open. */
    private void openBank() {
        BufferedImage gameView = controller().zones().getGameView();
        Point bankLoc = PointSelector.getRandomPointInColour(gameView, BANK_COLOUR, 15);
        if (bankLoc == null) {
            logger.error("Bank booth not found");
            stop();
            return;
        }
        String speed = HumanBehavior.shouldSlowApproach() ? "slow" : "medium";
        controller().mouse().moveTo(bankLoc, speed);
        controller().mouse().microJitter();
        controller().mouse().leftClick();
        waitMillis(HumanBehavior.adjustDelay(1200, 1800));
    }

    /** Right-clicks the first inventory slot and selects "Deposit-All" from the context menu. */
    private void depositAll() {
        Rectangle firstSlot = controller().zones().getInventorySlots().get(0);
        Point slotLoc = ClickDistribution.generateRandomPoint(firstSlot);
        controller().mouse().moveTo(slotLoc, "medium");
        controller().mouse().rightClick();
        waitMillis(HumanBehavior.adjustDelay(400, 600));

        Point depositOption = new Point(slotLoc.x, slotLoc.y + 85);
        controller().mouse().moveTo(depositOption, "fast");
        controller().mouse().leftClick();
        waitMillis(HumanBehavior.adjustDelay(300, 500));
    }

    /** Closes the bank interface by pressing Escape. */
    private void closeBank() {
        controller().keyboard().sendModifierKey(401, "esc");
        waitMillis(HumanBehavior.adjustDelay(80, 120));
        controller().keyboard().sendModifierKey(402, "esc");
        waitMillis(HumanBehavior.adjustDelay(400, 600));
    }

    /**
     * Walks to the given tile using the Dax walker.
     *
     * @param destination the world tile to walk to
     * @param label a label for logging
     */
    private void walkTo(Point destination, String label) {
        try {
            controller().walker().pathTo(destination, false);
            waitRandomMillis(4000, 6000);
        } catch (IOException e) {
            logger.error("Walker error going to {}: {}", label, e.getMessage());
            stop();
        } catch (InterruptedException e) {
            logger.error("Walker interrupted going to {}", label);
            stop();
        }
    }

    /**
     * Locates a Cyan-highlighted gold rock and clicks it with human-like behavior.
     */
    private void clickRock() {
        BufferedImage gameView = controller().zones().getGameView();
        Point clickLoc = PointSelector.getRandomPointInColour(gameView, ROCK_COLOUR, 15);
        if (clickLoc == null) {
            logger.error("No gold rock found in game view");
            stop();
            return;
        }

        String speed = HumanBehavior.shouldSlowApproach() ? "slow" : "medium";
        controller().mouse().moveTo(clickLoc, speed);

        if (HumanBehavior.shouldHesitate()) {
            HumanBehavior.performHesitation();
        }
        if (HumanBehavior.shouldMisclick()) {
            HumanBehavior.performMisclick(this, clickLoc);
            controller().mouse().moveTo(clickLoc, "medium");
        }

        controller().mouse().microJitter();
        controller().mouse().leftClick();
    }

    /**
     * Checks whether the inventory is full by template-matching gold ore
     * in the last inventory slot (index 27).
     *
     * @return {@code true} if the last slot contains gold ore
     */
    private boolean isInventoryFull() {
        Rectangle lastSlot = controller().zones().getInventorySlots().get(27);
        BufferedImage slotImg = ScreenManager.captureZone(lastSlot);
        return TemplateMatching.match(GOLD_ORE_IMAGE, slotImg, 0.05).bounds() != null;
    }
}
