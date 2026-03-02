package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
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
 * Completes The Restless Ghost quest from start to finish.
 *
 * <p>Route: Lumbridge Church (Aereck) → Lumbridge Swamp (Urhney) → Graveyard (coffin/ghost) →
 * Wizards' Tower basement (skull) → Graveyard (use skull on coffin).
 *
 * <p>Prerequisites: None. Both quest items are obtained during the quest.
 */
public class RestlessGhostScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(RestlessGhostScript.class);

  // === Image Templates ===
  private static final String AMULET_IMAGE = "/images/user/Ghostspeak_amulet.png";
  private static final String SKULL_IMAGE = "/images/user/Ghosts_skull.png";

  private static final double MATCH_THRESHOLD = 0.07;

  // === Walker Destinations ===
  private static final Point AERECK_TILE = new Point(3243, 3210);
  private static final Point URHNEY_TILE = new Point(3147, 3175);
  private static final Point COFFIN_TILE = new Point(3250, 3193);
  private static final Point TOWER_ENTRANCE_TILE = new Point(3109, 3162);
  private static final Point ALTAR_TILE = new Point(3120, 9567);

  private enum Step {
    TALK_AERECK,
    TALK_URHNEY,
    EQUIP_AMULET,
    OPEN_COFFIN,
    TALK_GHOST,
    WALK_TO_TOWER,
    DESCEND_TOWER,
    GET_SKULL,
    ASCEND_TOWER,
    RETURN_TO_COFFIN,
    USE_SKULL,
    DONE
  }

  private Step step = Step.TALK_AERECK;

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

    skipCompletedSteps();
    logger.info("Step: {}", step);

    switch (step) {
      case TALK_AERECK -> {
        walkTo(AERECK_TILE, "Father Aereck");
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        // "I'm looking for a quest!" is option 3
        pressSpace();
        waitMillis(HumanBehavior.adjustDelay(800, 1200));
        pressKey('3');
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        // "Ok, let me help." is option 1
        pressKey('1');
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        // Space through remaining dialogue
        for (int i = 0; i < 5; i++) {
          checkInterrupted();
          pressSpace();
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
        }
        step = Step.TALK_URHNEY;
      }
      case TALK_URHNEY -> {
        if (!hasItem(AMULET_IMAGE)) {
          walkTo(URHNEY_TILE, "Father Urhney");
          // Click to open door (or talk if already open)
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          // Click again to talk to Urhney
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          // "Father Aereck sent me to talk to you." is option 2
          pressSpace();
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
          pressKey('2');
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          // Space through remaining dialogue until amulet received
          for (int i = 0; i < 6; i++) {
            checkInterrupted();
            pressSpace();
            waitMillis(HumanBehavior.adjustDelay(800, 1200));
          }
        } else {
          step = Step.EQUIP_AMULET;
        }
      }
      case EQUIP_AMULET -> {
        if (hasItem(AMULET_IMAGE)) {
          clickInventoryItem(AMULET_IMAGE);
          waitMillis(HumanBehavior.adjustDelay(600, 1000));
        }
        // Amulet gone from inventory means it's equipped
        if (!hasItem(AMULET_IMAGE)) {
          step = Step.OPEN_COFFIN;
        }
      }
      case OPEN_COFFIN -> {
        walkTo(COFFIN_TILE, "coffin");
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));
        step = Step.TALK_GHOST;
      }
      case TALK_GHOST -> {
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(1500, 2500));
        // Space through all ghost dialogue
        for (int i = 0; i < 8; i++) {
          checkInterrupted();
          pressSpace();
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
        }
        step = Step.WALK_TO_TOWER;
      }
      case WALK_TO_TOWER -> {
        walkTo(TOWER_ENTRANCE_TILE, "Wizards' Tower");
        step = Step.DESCEND_TOWER;
      }
      case DESCEND_TOWER -> {
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));
        step = Step.GET_SKULL;
      }
      case GET_SKULL -> {
        if (!hasItem(SKULL_IMAGE)) {
          walkTo(ALTAR_TILE, "altar");
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(1500, 2500));
          // Press space if any dialogue/warning appears
          pressSpace();
          waitMillis(HumanBehavior.adjustDelay(800, 1200));
        } else {
          step = Step.ASCEND_TOWER;
        }
      }
      case ASCEND_TOWER -> {
        clickGameCenter();
        waitMillis(HumanBehavior.adjustDelay(2500, 3500));
        step = Step.RETURN_TO_COFFIN;
      }
      case RETURN_TO_COFFIN -> {
        walkTo(COFFIN_TILE, "coffin");
        step = Step.USE_SKULL;
      }
      case USE_SKULL -> {
        if (hasItem(SKULL_IMAGE)) {
          // Click skull in inventory to activate "use" cursor
          clickInventoryItem(SKULL_IMAGE);
          waitMillis(HumanBehavior.adjustDelay(300, 500));
          // Click coffin to use skull on it
          clickGameCenter();
          waitMillis(HumanBehavior.adjustDelay(2000, 3000));
          // Space through quest completion dialogue
          for (int i = 0; i < 5; i++) {
            checkInterrupted();
            pressSpace();
            waitMillis(HumanBehavior.adjustDelay(800, 1200));
          }
        }
        if (!hasItem(SKULL_IMAGE)) {
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
    if (step.ordinal() < Step.EQUIP_AMULET.ordinal() && hasItem(AMULET_IMAGE)) {
      step = Step.EQUIP_AMULET;
    }
    if (step.ordinal() >= Step.GET_SKULL.ordinal()
        && step.ordinal() <= Step.ASCEND_TOWER.ordinal()
        && hasItem(SKULL_IMAGE)) {
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

  private void clickInventoryItem(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, MATCH_THRESHOLD).success()) {
        Point clickLoc = ClickDistribution.generateRandomPoint(slot);
        controller().mouse().moveTo(clickLoc, "medium");
        controller().mouse().leftClick();
        return;
      }
    }
    logger.error("Item not found in inventory: {}", templatePath);
    stop();
  }

  private boolean hasItem(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, MATCH_THRESHOLD).success()) {
        return true;
      }
    }
    return false;
  }

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

  private void pressSpace() {
    controller().keyboard().sendModifierKey(401, "space");
    waitMillis(HumanBehavior.adjustDelay(80, 120));
    controller().keyboard().sendModifierKey(402, "space");
  }

  private void pressKey(char key) {
    controller().keyboard().sendKeyChar(key);
  }
}
