package com.chromascape.scripts;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.topology.MatchResult;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.actions.custom.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Smelts gem bracelets or amulets at the Edgeville furnace, banks at Edgeville bank,
 * withdraws materials, and repeats. Configurable via the {@code mode} and {@code gem} fields.
 *
 * <p>The script keeps the mould in inventory slot 0 at all times. Each bank trip withdraws
 * 13 gold bars and 13 gems (filling the remaining 27 slots). At the furnace it clicks
 * the furnace, presses Space to confirm, and waits for the full batch to complete.
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>Crafting level depends on gem and mode (see {@link Gem} enum)
 *   <li>Bracelet mould (for bracelets) or amulet mould (for amulets) in inventory slot 0
 *   <li>Gold bars and cut gems in the bank
 * </ul>
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>Object Markers — mark the Edgeville furnace with Cyan (#00FFFF)
 *   <li>Object Markers — mark the Edgeville bank booth with Cyan (#00FFFF)
 * </ul>
 *
 * <p><b>Inventory Layout:</b> Slot 0 = mould (bracelet or amulet). Slots 1–27 filled by script.
 *
 * <p><b>Image Templates (auto-downloaded from OSRS Wiki):</b>
 * <ul>
 *   <li>{@code /images/user/Gold_bar.png}, {@code /images/user/Bracelet_mould.png},
 *       {@code /images/user/Amulet_mould.png}
 *   <li>{@code /images/user/Sapphire.png}, {@code /images/user/Emerald.png},
 *       {@code /images/user/Ruby.png}, {@code /images/user/Diamond.png},
 *       {@code /images/user/Dragonstone.png}, {@code /images/user/Onyx.png},
 *       {@code /images/user/Zenyte.png}
 * </ul>
 *
 * <p><b>Wiki source:</b>
 * <a href="https://oldschool.runescape.wiki/w/Jewellery">Jewellery</a>,
 * <a href="https://oldschool.runescape.wiki/w/Edgeville">Edgeville</a> furnace + bank.
 */
public class EdgevilleJewelleryScript extends BaseScript {

    private static final Logger logger = LogManager.getLogger(EdgevilleJewelleryScript.class);

    // === Configuration — change these to switch product ===
    /** Set to BRACELET or AMULET. */
    private static final Mode mode = Mode.BRACELET;
    /** Set to the gem type to use. */
    private static final Gem gem = Gem.RUBY;

    // === Image Templates ===
    private static final String GOLD_BAR_IMAGE = "/images/user/Gold_bar.png";

    // === Script Constants ===
    private static final String FURNACE_COLOUR = "Cyan";
    private static final String BANK_COLOUR = "Cyan";
    private static final int MATERIALS_PER_TRIP = 13;
    private static final double MATCH_THRESHOLD = 0.07;

    // === Walker Destinations ===
    private static final Point BANK_TILE = new Point(3094, 3492);
    private static final Point FURNACE_TILE = new Point(3109, 3499);

    /** Supported jewellery types. */
    private enum Mode {
        BRACELET("/images/user/Bracelet_mould.png"),
        AMULET("/images/user/Amulet_mould.png");

        final String mouldImage;

        Mode(String mouldImage) {
            this.mouldImage = mouldImage;
        }
    }

    /** Supported gems with their inventory image paths. Bracelet / amulet crafting levels noted. */
    private enum Gem {
        SAPPHIRE("/images/user/Sapphire.png"),   // Bracelet 23, Amulet 24
        EMERALD("/images/user/Emerald.png"),     // Bracelet 30, Amulet 31
        RUBY("/images/user/Ruby.png"),           // Bracelet 42, Amulet 50
        DIAMOND("/images/user/Diamond.png"),     // Bracelet 58, Amulet 70
        DRAGONSTONE("/images/user/Dragonstone.png"), // Bracelet 74, Amulet 80
        ONYX("/images/user/Onyx.png"),           // Bracelet 84, Amulet 90
        ZENYTE("/images/user/Zenyte.png");       // Bracelet 95, Amulet 98

        final String image;

        Gem(String image) {
            this.image = image;
        }
    }

    @Override
    protected void cycle() {
        if (HumanBehavior.runPreCycleChecks(this)) return;

        if (!hasMaterials()) {
            logger.info("No materials, banking");
            Walk.toOrStop(this, BANK_TILE, "bank");
            waitMillis(HumanBehavior.adjustDelay(600, 900));
            bank();
            Walk.toOrStop(this, FURNACE_TILE, "furnace");
            waitMillis(HumanBehavior.adjustDelay(600, 900));
        }

        smelt();
    }

    /**
     * Opens the bank, deposits products, withdraws 13 gold bars and 13 rubies.
     * Mould stays in slot 0 (excluded from deposit via right-click deposit on products).
     */
    private void bank() {
        Bank.open(this, BANK_COLOUR);

        // Deposit all products — right-click slot 1 (first product slot) → Deposit-All
        Rectangle slot1 = controller().zones().getInventorySlots().get(1);
        Point slotLoc = ClickDistribution.generateRandomPoint(slot1);
        controller().mouse().moveTo(slotLoc, "medium");
        controller().mouse().rightClick();
        waitMillis(HumanBehavior.adjustDelay(400, 600));
        controller().mouse().moveTo(new Point(slotLoc.x, slotLoc.y + 85), "fast");
        controller().mouse().leftClick();
        waitMillis(HumanBehavior.adjustDelay(300, 500));

        // Withdraw 13 gold bars — click gold bar in bank
        withdrawItem(GOLD_BAR_IMAGE);
        waitMillis(HumanBehavior.adjustDelay(300, 500));

        // Withdraw 13 gems — click gem in bank
        withdrawItem(gem.image);
        waitMillis(HumanBehavior.adjustDelay(300, 500));

        Bank.close(this);
    }

    /**
     * Withdraws an item from the bank by template-matching it in the bank view,
     * right-clicking, and selecting "Withdraw-13" (the custom X amount).
     * Assumes the bank quantity is set to X=13 beforehand.
     */
    private void withdrawItem(String itemImage) {
        BufferedImage gameView = controller().zones().getGameView();
        Point itemLoc = PointSelector.getRandomPointInImage(itemImage, gameView, MATCH_THRESHOLD);
        if (itemLoc == null) {
            logger.error("Could not find item in bank: {}", itemImage);
            stop();
            return;
        }
        controller().mouse().moveTo(itemLoc, "medium");
        controller().mouse().leftClick();
    }

    /**
     * Clicks the furnace, waits for the crafting interface, selects the jewellery
     * item via template match of the mould, presses Space to confirm, and waits
     * for the batch to complete (~3 ticks per item × 13 items ≈ 23s).
     */
    private void smelt() {
        BufferedImage gameView = controller().zones().getGameView();
        Point furnaceLoc = PointSelector.getRandomPointInColour(gameView, FURNACE_COLOUR, 15);
        if (furnaceLoc == null) {
            logger.error("Furnace not found");
            stop();
            return;
        }

        HumanBehavior.click(this, furnaceLoc);
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));

        // Press Space to confirm the default selection (makes all)
        KeyPress.space(this);

        // Wait for batch to complete: 13 items × 3 ticks × 600ms = ~23.4s
        waitMillis(HumanBehavior.adjustDelay(24000, 26000));
        Idler.waitUntilIdle(this, 10);
    }

    /**
     * Checks if the player has gold bars in inventory by checking slot 1.
     *
     * @return true if gold bar found in slot 1
     */
    private boolean hasMaterials() {
        Rectangle slot = controller().zones().getInventorySlots().get(1);
        BufferedImage slotImg = ScreenManager.captureZone(slot);
        MatchResult result = TemplateMatching.match(GOLD_BAR_IMAGE, slotImg, 0.05);
        return result.bounds() != null;
    }

}
