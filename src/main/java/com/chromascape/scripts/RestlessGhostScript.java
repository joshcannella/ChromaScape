package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.actions.custom.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Completes The Restless Ghost quest from start to finish.
 *
 * <p>Route: Lumbridge Church (Aereck) → Lumbridge Swamp (Urhney) → Graveyard (coffin/ghost) →
 * Wizards' Tower basement (skull) → Graveyard (use skull on coffin).
 *
 * <p>Prerequisites: None. Both quest items are obtained during the quest.
 */
public class RestlessGhostScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(RestlessGhostScript.class);

  private static final String AMULET_IMAGE = "/images/user/Ghostspeak_amulet.png";
  private static final String SKULL_IMAGE = "/images/user/Ghosts_skull.png";
  private static final double MATCH_THRESHOLD = 0.07;

  private static final Point AERECK_TILE = new Point(3243, 3210);
  private static final Point URHNEY_TILE = new Point(3147, 3175);
  private static final Point COFFIN_TILE = new Point(3250, 3193);
  private static final Point TOWER_ENTRANCE_TILE = new Point(3109, 3162);
  private static final Point ALTAR_TILE = new Point(3120, 9567);

  private static final Point CHURCH_DOOR_TILE = new Point(3244, 3204);

  private enum Step {
    ENTER_CHURCH,
    TALK_AERECK,
    WALK_TO_URHNEY,
    TALK_URHNEY,
    EQUIP_AMULET,
    OPEN_COFFIN,
    WALK_TO_TOWER,
    DESCEND_TOWER,
    GET_SKULL,
    ASCEND_TOWER,
    RETURN_TO_COFFIN,
    USE_SKULL,
    DONE
  }

  private Step step = Step.ENTER_CHURCH;
  private int urhneyAttempts = 0;
  private boolean walkedToAltar = false;

  @Override
  protected void cycle() {
    if (HumanBehavior.runPreCycleChecks(this)) return;

    skipCompletedSteps();
    logger.info("Step: {}", step);

    switch (step) {
      case ENTER_CHURCH -> {
        Walk.toOrStop(this, CHURCH_DOOR_TILE, "church door");
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        step = Step.TALK_AERECK;
      }
      case TALK_AERECK -> {
        Walk.toOrStop(this, AERECK_TILE, "Father Aereck");
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        KeyPress.space(this);
        waitMillis(HumanBehavior.adjustDelay(800, 1200));
        KeyPress.character(this, '3');
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        KeyPress.character(this, '1');
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        for (int i = 0; i < 5; i++) {
          checkInterrupted();
          KeyPress.space(this);
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
        }
        // Don't jump straight to TALK_URHNEY — walk first, then talk separately
        step = Step.WALK_TO_URHNEY;
      }
      case WALK_TO_URHNEY -> {
        // Separated from TALK_URHNEY so the walk completes before we attempt dialog.
        // If we already have the amulet, skipCompletedSteps will jump past this.
        Walk.toOrStop(this, URHNEY_TILE, "Father Urhney");
        step = Step.TALK_URHNEY;
      }
      case TALK_URHNEY -> {
        if (!Inventory.hasItem(this, AMULET_IMAGE, MATCH_THRESHOLD)) {
          urhneyAttempts++;
          if (urhneyAttempts > 3) {
            // Aereck dialog likely failed — restart from church
            logger.warn("Urhney not giving amulet after {} attempts, restarting from church.", urhneyAttempts);
            urhneyAttempts = 0;
            step = Step.ENTER_CHURCH;
            return;
          }
          // Click to open door (or talk if already open)
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          // Click again to talk to Urhney
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          KeyPress.space(this);
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
          KeyPress.character(this, '2');
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          for (int i = 0; i < 6; i++) {
            checkInterrupted();
            KeyPress.space(this);
            waitMillis(HumanBehavior.adjustDelay(800, 1200));
          }
          // Do NOT advance — let the next cycle's hasItem check confirm we got the amulet.
        } else {
          urhneyAttempts = 0;
          step = Step.EQUIP_AMULET;
        }
      }
      case EQUIP_AMULET -> {
        if (Inventory.hasItem(this, AMULET_IMAGE, MATCH_THRESHOLD)) {
          Inventory.clickItem(this, AMULET_IMAGE, MATCH_THRESHOLD, "medium");
          waitMillis(HumanBehavior.adjustDelay(600, 1000));
        }
        if (!Inventory.hasItem(this, AMULET_IMAGE, MATCH_THRESHOLD)) {
          step = Step.OPEN_COFFIN;
        }
      }
      case OPEN_COFFIN -> {
        Walk.toOrStop(this, COFFIN_TILE, "coffin");
        // Open the coffin
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));
        // Talk to the ghost that appears
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        for (int i = 0; i < 8; i++) {
          checkInterrupted();
          KeyPress.space(this);
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
        }
        step = Step.WALK_TO_TOWER;
      }
      case WALK_TO_TOWER -> {
        Walk.toOrStop(this, TOWER_ENTRANCE_TILE, "Wizards' Tower");
        step = Step.DESCEND_TOWER;
      }
      case DESCEND_TOWER -> {
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));
        // Verify we actually descended by checking if walker can reach the basement altar
        if (canWalkTo(ALTAR_TILE)) {
          step = Step.GET_SKULL;
        } else {
          logger.warn("Ladder descent may have failed, retrying...");
        }
      }
      case GET_SKULL -> {
        if (!Inventory.hasItem(this, SKULL_IMAGE, MATCH_THRESHOLD)) {
          if (!walkedToAltar) {
            Walk.toOrStop(this, ALTAR_TILE, "altar");
            walkedToAltar = true;
          }
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          KeyPress.space(this);
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
          // Don't advance — let next cycle's hasItem check confirm skull pickup
        } else {
          walkedToAltar = false;
          step = Step.ASCEND_TOWER;
        }
      }
      case ASCEND_TOWER -> {
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));
        // Verify we actually ascended by checking if walker can reach the coffin
        if (canWalkTo(COFFIN_TILE)) {
          step = Step.RETURN_TO_COFFIN;
        } else {
          logger.warn("Ladder ascent may have failed, retrying...");
        }
      }
      case RETURN_TO_COFFIN -> {
        Walk.toOrStop(this, COFFIN_TILE, "coffin");
        step = Step.USE_SKULL;
      }
      case USE_SKULL -> {
        if (Inventory.hasItem(this, SKULL_IMAGE, MATCH_THRESHOLD)) {
          Inventory.clickItem(this, SKULL_IMAGE, MATCH_THRESHOLD, "medium");
          waitMillis(HumanBehavior.adjustDelay(300, 500));
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(2000, 3000));
          for (int i = 0; i < 5; i++) {
            checkInterrupted();
            KeyPress.space(this);
            waitMillis(HumanBehavior.adjustDelay(800, 1200));
          }
          // Don't advance — let next cycle's hasItem check confirm skull is gone
        } else {
          step = Step.DONE;
        }
      }
      case DONE -> {
        DiscordNotification.send("The Restless Ghost quest complete!");
        logger.info("Quest complete!");
        stop();
      }
    }
  }

  /** Advances past steps whose items are already in inventory. */
  private void skipCompletedSteps() {
    if (step.ordinal() < Step.EQUIP_AMULET.ordinal() && Inventory.hasItem(this, AMULET_IMAGE, MATCH_THRESHOLD)) {
      step = Step.EQUIP_AMULET;
    }
    if (step.ordinal() >= Step.GET_SKULL.ordinal()
        && step.ordinal() <= Step.ASCEND_TOWER.ordinal()
        && Inventory.hasItem(this, SKULL_IMAGE, MATCH_THRESHOLD)) {
      step = Step.RETURN_TO_COFFIN;
    }
  }

  private void clickGameCenter() {
    Rectangle window = ScreenManager.getWindowBounds();
    int regionSize = 40;
    Rectangle centerRegion =
        new Rectangle(
            window.x + window.width / 2 - regionSize / 2,
            window.y + window.height / 2 - regionSize / 2,
            regionSize,
            regionSize);
    Point clickLoc = ClickDistribution.generateRandomPoint(centerRegion);

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

  private boolean canWalkTo(Point destination) {
    try {
      controller().walker().pathTo(destination, false);
      return true;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }
}
