package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.actions.MouseOver;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.actions.custom.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Completes the Cook's Assistant quest by gathering the three required ingredients
 * (egg, bucket of milk, pot of flour) and delivering them to the Lumbridge cook.
 *
 * <p>This script does NOT require any RuneLite object marker highlights. It navigates
 * entirely via the Dax walker to known object tiles, then clicks at the game view center
 * where the target object is located. Inventory items are detected via template matching.
 *
 * <p>The script follows a linear state machine: pick up a pot and bucket from Lumbridge Castle,
 * collect an egg from the chicken coop, milk a dairy cow, pick grain and mill it into flour,
 * then return to the cook and complete the quest through dialogue.
 *
 * <p><b>Prerequisites:</b> None (free-to-play, no skill requirements).
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>XP Tracker — not required for this script</li>
 *   <li>No object markers or NPC indicators needed</li>
 * </ul>
 *
 * <p><b>Starting Position:</b> Anywhere in Lumbridge (the script walks to each location).
 *
 * <p><b>Image Templates (auto-downloaded from OSRS Wiki):</b>
 * <ul>
 *   <li>{@code /images/user/Pot.png} — https://oldschool.runescape.wiki/images/Pot.png</li>
 *   <li>{@code /images/user/Bucket.png} — https://oldschool.runescape.wiki/images/Bucket.png</li>
 *   <li>{@code /images/user/Egg.png} — https://oldschool.runescape.wiki/images/Egg.png</li>
 *   <li>{@code /images/user/Bucket_of_milk.png} — https://oldschool.runescape.wiki/images/Bucket_of_milk.png</li>
 *   <li>{@code /images/user/Grain.png} — https://oldschool.runescape.wiki/images/Grain.png</li>
 *   <li>{@code /images/user/Pot_of_flour.png} — https://oldschool.runescape.wiki/images/Pot_of_flour.png</li>
 * </ul>
 *
 * <p><b>Source:</b> <a href="https://oldschool.runescape.wiki/w/Cook%27s_Assistant">OSRS Wiki</a>
 */
public class CooksAssistantScript extends BaseScript {

    private static final Logger logger = LogManager.getLogger(CooksAssistantScript.class);

    // === Image Templates ===
    private static final String POT_IMAGE = "/images/user/Pot.png";
    private static final String BUCKET_IMAGE = "/images/user/Bucket.png";
    private static final String EGG_IMAGE = "/images/user/Egg.png";
    private static final String MILK_IMAGE = "/images/user/Bucket_of_milk.png";
    private static final String GRAIN_IMAGE = "/images/user/Grain.png";
    private static final String FLOUR_IMAGE = "/images/user/Pot_of_flour.png";

    private static final double MATCH_THRESHOLD = 0.07;

    // === Walker Destinations (all from OSRS Wiki) ===
    // Pot spawn: Lumbridge Castle kitchen table
    private static final Point POT_TILE = new Point(3211, 3214);
    // Lumbridge Castle cellar trapdoor (kitchen)
    private static final Point TRAPDOOR_TILE = new Point(3209, 3216);
    // Bucket spawn in Lumbridge Castle cellar
    private static final Point BUCKET_TILE = new Point(3209, 9616);
    // Ladder up from cellar
    private static final Point CELLAR_LADDER_TILE = new Point(3209, 9616);
    // Egg spawn at chicken coop NW of Fred the Farmer
    private static final Point EGG_TILE = new Point(3172, 3301);
    // Dairy cow in Lumbridge east cow field
    private static final Point DAIRY_COW_TILE = new Point(3254, 3272);
    // Wheat field near Mill Lane Mill
    private static final Point WHEAT_TILE = new Point(3161, 3295);
    // Mill Lane Mill ground floor entrance
    private static final Point MILL_GROUND_TILE = new Point(3166, 3306);
    // Cook in Lumbridge Castle kitchen
    private static final Point COOK_TILE = new Point(3208, 3214);

    /** Quest progress state machine. */
    private enum Step {
        GET_POT,
        GO_TO_CELLAR,
        GET_BUCKET,
        LEAVE_CELLAR,
        GET_EGG,
        GET_MILK,
        GET_GRAIN,
        MILL_CLIMB_UP,
        MILL_USE_HOPPER,
        MILL_PULL_LEVER,
        MILL_CLIMB_DOWN,
        MILL_GET_FLOUR,
        DELIVER,
        DONE
    }

    private Step step = Step.GET_POT;

    @Override
    protected void cycle() {
        if (HumanBehavior.runPreCycleChecks(this)) return;

        skipCompletedSteps();
        logger.info("Step: {}", step);

        switch (step) {
            case GET_POT -> {
                if (!Inventory.hasItem(this, POT_IMAGE, MATCH_THRESHOLD)) {
                    Walk.toOrStop(this, POT_TILE, "pot");
                    Point potLoc = findImageInGameView(POT_IMAGE);
                    if (potLoc != null) {
                        controller().mouse().moveTo(potLoc, "medium");
                        controller().mouse().leftClick();
                        HumanBehavior.sleep(1200, 1800);
                    } else {
                        logger.warn("Pot not found in game view, retrying...");
                        HumanBehavior.sleep(500, 1000);
                    }
                } else {
                    step = Step.GO_TO_CELLAR;
                }
            }
            case GO_TO_CELLAR -> {
                // Click trapdoor to enter cellar
                Walk.toOrStop(this, TRAPDOOR_TILE, "trapdoor");
                clickGameCenter();
                HumanBehavior.sleep(2500, 3500);
                step = Step.GET_BUCKET;
            }
            case GET_BUCKET -> {
                if (!Inventory.hasItem(this, BUCKET_IMAGE, MATCH_THRESHOLD)) {
                    Walk.toOrStop(this, BUCKET_TILE, "bucket");
                    Point bucketLoc = findImageInGameView(BUCKET_IMAGE);
                    if (bucketLoc != null) {
                        controller().mouse().moveTo(bucketLoc, "medium");
                        controller().mouse().leftClick();
                        HumanBehavior.sleep(1200, 1800);
                    } else {
                        logger.warn("Bucket not found in game view, retrying...");
                        HumanBehavior.sleep(500, 1000);
                    }
                } else {
                    step = Step.LEAVE_CELLAR;
                }
            }
            case LEAVE_CELLAR -> {
                // Climb ladder up from cellar
                Walk.toOrStop(this, CELLAR_LADDER_TILE, "ladder");
                clickGameCenter();
                HumanBehavior.sleep(2500, 3500);
                step = Step.GET_EGG;
            }
            case GET_EGG -> {
                if (!Inventory.hasItem(this, EGG_IMAGE, MATCH_THRESHOLD)) {
                    Walk.toOrStop(this, EGG_TILE, "egg");
                    // Try template matching first with relaxed threshold
                    Point eggLoc = findImageInGameView(EGG_IMAGE, 0.15);
                    if (eggLoc == null) {
                        // Fallback: just click center, eggs are common at spawn
                        logger.warn("Egg not detected, clicking center");
                        clickGameCenter();
                    } else {
                        controller().mouse().moveTo(eggLoc, "medium");
                        controller().mouse().leftClick();
                    }
                    HumanBehavior.sleep(1200, 1800);
                } else {
                    step = Step.GET_MILK;
                }
            }
            case GET_MILK -> {
                if (!Inventory.hasItem(this, MILK_IMAGE, MATCH_THRESHOLD)) {
                    Walk.toOrStop(this, DAIRY_COW_TILE, "dairy cow");
                    Inventory.clickItem(this, BUCKET_IMAGE, MATCH_THRESHOLD, "medium");
                    HumanBehavior.sleep(300, 500);
                    clickGameCenter();
                    HumanBehavior.sleep(3000, 4000);
                } else {
                    step = Step.GET_GRAIN;
                }
            }
            case GET_GRAIN -> {
                if (!Inventory.hasItem(this, GRAIN_IMAGE, MATCH_THRESHOLD)) {
                    Walk.toOrStop(this, WHEAT_TILE, "wheat");
                    clickGameCenter(); // Wheat is tall and visible at center
                    HumanBehavior.sleep(1200, 1800);
                } else {
                    step = Step.MILL_CLIMB_UP;
                }
            }
            case MILL_CLIMB_UP -> {
                Walk.toOrStop(this, MILL_GROUND_TILE, "mill");
                // Climb two flights of stairs
                clickGameCenter();
                HumanBehavior.sleep(2000, 3000);
                clickGameCenter();
                HumanBehavior.sleep(2000, 3000);
                step = Step.MILL_USE_HOPPER;
            }
            case MILL_USE_HOPPER -> {
                if (Inventory.hasItem(this, GRAIN_IMAGE, MATCH_THRESHOLD)) {
                    Inventory.clickItem(this, GRAIN_IMAGE, MATCH_THRESHOLD, "medium");
                    HumanBehavior.sleep(300, 500);
                    clickGameCenter();
                    HumanBehavior.sleep(1500, 2500);
                } else {
                    step = Step.MILL_PULL_LEVER;
                }
            }
            case MILL_PULL_LEVER -> {
                clickGameCenter();
                HumanBehavior.sleep(1500, 2500);
                step = Step.MILL_CLIMB_DOWN;
            }
            case MILL_CLIMB_DOWN -> {
                // Climb down two flights
                clickGameCenter();
                HumanBehavior.sleep(2000, 3000);
                clickGameCenter();
                HumanBehavior.sleep(2000, 3000);
                step = Step.MILL_GET_FLOUR;
            }
            case MILL_GET_FLOUR -> {
                if (!Inventory.hasItem(this, FLOUR_IMAGE, MATCH_THRESHOLD)) {
                    Inventory.clickItem(this, POT_IMAGE, MATCH_THRESHOLD, "medium");
                    HumanBehavior.sleep(300, 500);
                    clickGameCenter();
                    HumanBehavior.sleep(1500, 2500);
                } else {
                    step = Step.DELIVER;
                }
            }
            case DELIVER -> {
                Walk.toOrStop(this, COOK_TILE, "cook");
                clickGameCenter();
                HumanBehavior.sleep(1500, 2500);
                // Dialogue: "What's wrong?" → 1, "Can I help?" → 1
                KeyPress.character(this, '1');
                HumanBehavior.sleep(1500, 2500);
                KeyPress.character(this, '1');
                HumanBehavior.sleep(1500, 2500);
                // Continue through remaining dialogue
                for (int i = 0; i < 8; i++) {
                    checkInterrupted();
                    KeyPress.space(this);
                    HumanBehavior.sleep(800, 1200);
                }
                step = Step.DONE;
            }
            case DONE -> {
                DiscordNotification.send("Cook's Assistant quest complete!");
                logger.info("Quest complete!");
                stop();
            }
        }
    }

    /**
     * Advances past steps whose items are already in inventory.
     */
    private void skipCompletedSteps() {
        if (step == Step.GET_POT && Inventory.hasItem(this, POT_IMAGE, MATCH_THRESHOLD)) {
            step = Step.GO_TO_CELLAR;
        }
        if ((step == Step.GO_TO_CELLAR || step == Step.GET_BUCKET || step == Step.LEAVE_CELLAR)
                && (Inventory.hasItem(this, BUCKET_IMAGE, MATCH_THRESHOLD) || Inventory.hasItem(this, MILK_IMAGE, MATCH_THRESHOLD))) {
            step = Step.GET_EGG;
        }
        if (step == Step.GET_EGG && Inventory.hasItem(this, EGG_IMAGE, MATCH_THRESHOLD)) {
            step = Step.GET_MILK;
        }
        if (step == Step.GET_MILK && Inventory.hasItem(this, MILK_IMAGE, MATCH_THRESHOLD)) {
            step = Step.GET_GRAIN;
        }
        if (step.ordinal() >= Step.GET_GRAIN.ordinal()
                && step.ordinal() <= Step.MILL_GET_FLOUR.ordinal()
                && Inventory.hasItem(this, FLOUR_IMAGE, MATCH_THRESHOLD)) {
            step = Step.DELIVER;
        }
        if (step == Step.GET_GRAIN && Inventory.hasItem(this, GRAIN_IMAGE, MATCH_THRESHOLD)) {
            step = Step.MILL_CLIMB_UP;
        }
    }

    /**
     * Clicks near the center of the game view where the target object is located
     * after walking to its tile. Uses a Gaussian-distributed point within a region
     * around screen center for human-like variance.
     */
    private void clickGameCenter() {
        Rectangle window = ScreenManager.getWindowBounds();
        // Define a region around the center of the game view
        int regionSize = 40;
        Rectangle centerRegion = new Rectangle(
                window.x + window.width / 2 - regionSize / 2,
                window.y + window.height / 2 - regionSize / 2,
                regionSize, regionSize);
        Point clickLoc = ClickDistribution.generateRandomPoint(centerRegion);

        HumanBehavior.click(this, clickLoc);
    }

    /**
     * Finds and clicks an item in the inventory by template matching.
     *
     * @param templatePath classpath path to the item image
     */
    private Point findImageInGameView(String templatePath) {
        return findImageInGameView(templatePath, MATCH_THRESHOLD);
    }

    private Point findImageInGameView(String templatePath, double threshold) {
        BufferedImage gameView = controller().zones().getGameView();
        var match = TemplateMatching.match(templatePath, gameView, threshold);
        if (match.success()) {
            return ClickDistribution.generateRandomPoint(match.bounds());
        }
        return null;
    }
}
