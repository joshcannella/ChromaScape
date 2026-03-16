package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Minimap;
import com.chromascape.utils.actions.MovingObject;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.actions.custom.HumanBehavior;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Trains Agility on the Draynor Village Rooftop Course by continuously running laps.
 *
 * <p>Detects the next obstacle via a RuneLite Object Marker colour highlight, clicks it using
 * {@link MovingObject} for moving-target tracking, and waits for XP change to confirm completion.
 * Picks up Marks of Grace when visible. If no obstacle is found, walks back to the course start.
 *
 * <p><b>Prerequisites:</b> 1 Agility (no requirement since May 2024 update), members world.
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>Object Markers — highlight all Draynor rooftop obstacles in Green (HSV ~59-60, 254-255, 254-255)</li>
 *   <li>Ground Items — highlight Marks of Grace in Red (HSV ~0-1, 254-255, 254-255)</li>
 *   <li>XP Tracker — set permanent XP bar to visible</li>
 * </ul>
 *
 * <p><b>Source:</b> <a href="https://oldschool.runescape.wiki/w/Draynor_Village_Rooftop_Course">OSRS Wiki</a>
 */
public class DraynorAgilityScript extends BaseScript {

    private static final Logger logger = LogManager.getLogger(DraynorAgilityScript.class);

    // === Colour Definitions ===
    private static final ColourObj OBSTACLE_COLOUR =
            new ColourObj("green", new Scalar(59, 254, 254, 0), new Scalar(60, 255, 255, 0));
    private static final ColourObj MARK_COLOUR =
            new ColourObj("red", new Scalar(0, 254, 254, 0), new Scalar(1, 255, 255, 0));

    // === Course Reset Tile (start of Draynor course, near rough wall) ===
    // Source: https://oldschool.runescape.wiki/w/Draynor_Village_Rooftop_Course
    private static final Point RESET_TILE = new Point(3103, 3278);

    // === Timeouts ===
    private static final int TIMEOUT_XP_CHANGE = 15;
    private static final int TIMEOUT_OBSTACLE_APPEAR = 10;

    /**
     * Main cycle: checks for obstacles, clicks them, waits for XP change.
     * Falls back to mark of grace pickup or walker recovery if no obstacle is visible.
     */
    @Override
    protected void cycle() {
        if (HumanBehavior.runPreCycleChecks(this)) return;

        int previousXp = Minimap.getXp(this);
        if (previousXp == -1) {
            DiscordNotification.send("XP could not be read.");
            stop();
            return;
        }

        if (!isObstacleVisible()) {
            if (clickMarkOfGraceIfPresent()) {
                waitForObstacleToAppear();
            } else {
                recoverToResetTile();
            }
            return;
        }

        MovingObject.clickMovingObjectByColourObjUntilRedClick(OBSTACLE_COLOUR, this);
        waitUntilXpChange(previousXp);
        HumanBehavior.sleep(650, 800);
    }

    /**
     * Walks back to the course start tile when no obstacle or mark is visible.
     * Double-checks visibility after a short wait to guard against rendering lag.
     */
    private void recoverToResetTile() {
        HumanBehavior.sleep(600, 800);
        if (isObstacleVisible()) {
            return;
        }

        int attempts = 0;
        while (attempts < 5) {
            try {
                logger.info("Lost — walking to reset tile.");
                controller().walker().pathTo(RESET_TILE, true);
                waitRandomMillis(4000, 6000);
                return;
            } catch (IOException e) {
                logger.error("Walker error: {}", e.getMessage());
                attempts++;
            } catch (InterruptedException e) {
                logger.error("Walker interrupted");
                stop();
                return;
            }
        }
        logger.error("Failed to recover after 5 walker attempts");
        stop();
    }

    /**
     * Scans for a Mark of Grace (red highlight) and clicks it if found.
     *
     * @return true if a mark was clicked, false if none found
     */
    private boolean clickMarkOfGraceIfPresent() {
        BufferedImage gameView = controller().zones().getGameView();
        Point clickLoc = PointSelector.getRandomPointByColourObj(gameView, MARK_COLOUR, 15, 15.0);
        if (clickLoc == null) {
            return false;
        }

        HumanBehavior.click(this, clickLoc);
        return true;
    }

    /**
     * Blocks until total XP changes or the timeout is reached.
     *
     * @param previousXp the XP value before the action
     */
    private void waitUntilXpChange(int previousXp) {
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(TIMEOUT_XP_CHANGE);
        while (previousXp == Minimap.getXp(this) && LocalDateTime.now().isBefore(endTime)) {
            waitMillis(300);
        }
    }

    /** Blocks until the obstacle highlight appears or the timeout is reached. */
    private void waitForObstacleToAppear() {
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(TIMEOUT_OBSTACLE_APPEAR);
        while (!isObstacleVisible() && LocalDateTime.now().isBefore(endTime)) {
            waitMillis(300);
        }
    }

    /**
     * Checks if the obstacle colour highlight is present in the game view.
     *
     * @return true if at least one obstacle contour is detected
     */
    private boolean isObstacleVisible() {
        BufferedImage gameView = controller().zones().getGameView();
        return !ColourContours.getChromaObjsInColour(gameView, OBSTACLE_COLOUR).isEmpty();
    }
}
