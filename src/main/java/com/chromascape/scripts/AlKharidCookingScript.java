package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.custom.GameCenter;
import com.chromascape.utils.actions.custom.HumanBehavior;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.actions.custom.Logout;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.screen.colour.ColourObj;
import java.awt.Point;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Cooks raw shrimp on the Al Kharid range, banks cooked/burnt shrimp, withdraws more raw shrimp,
 * and repeats. No quest requirements.
 *
 * <p><b>Prerequisites:</b> Raw shrimp in Al Kharid bank.
 * <p><b>RuneLite Setup:</b> Object Markers — range highlighted red, bank booth highlighted cyan.
 * <p><b>Start:</b> Near Al Kharid bank or range with raw shrimp in bank.
 */
@ScriptVersion(major = 0, minor = 1)
public class AlKharidCookingScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(AlKharidCookingScript.class);

  // === Colours ===
  private static final ColourObj RANGE_COLOUR_LOW =
      new ColourObj("red_low", new Scalar(0, 254, 254, 0), new Scalar(3, 255, 255, 0));
  private static final ColourObj RANGE_COLOUR_HIGH =
      new ColourObj("red_high", new Scalar(177, 254, 254, 0), new Scalar(180, 255, 255, 0));

  // === Images ===
  private static final String RAW_SHRIMP = "/images/user/Raw_shrimps.png";
  private static final String COOKED_SHRIMP = "/images/user/Shrimps.png";
  private static final String BURNT_SHRIMP = "/images/user/Burnt_shrimp.png";
  private static final double THRESHOLD = 0.07;

  // === Tiles ===
  private static final Point BANK_TILE = new Point(3269, 3167);
  private static final Point RANGE_TILE = new Point(3273, 3180);

  // === Stuck Detection ===
  private static final int MAX_STUCK_CYCLES = 15;
  private int stuckCounter = 0;

  @Override
  protected void cycle() {
    if (HumanBehavior.runPreCycleChecks(this)) return;

    // Dismiss any lingering dialogs (level-up, etc.) before doing anything
    KeyPress.space(this);
    waitMillis(300);

    if (stuckCounter >= MAX_STUCK_CYCLES) {
      logger.error("Stuck for {} cycles, logging out.", MAX_STUCK_CYCLES);
      DiscordNotification.send("AlKharidCooking: stuck, logging out.");
      Logout.perform(this);
      stop();
      return;
    }

    if (Inventory.hasItem(this, RAW_SHRIMP, THRESHOLD)) {
      logger.info("State: COOK | stuck: {}", stuckCounter);
      cook();
    } else {
      logger.info("State: BANK | stuck: {}", stuckCounter);
      bank();
    }
  }

  // === Cooking ===

  private void cook() {
    // Walk to range if not visible
    if (!isRangeVisible()) {
      logger.info("Range not visible, walking to range tile");
      Walk.to(this, RANGE_TILE, "range");
      // Try opening door if range still not visible
      if (!isRangeVisible()) {
        logger.info("Range still not visible, trying door");
        GameCenter.click(this);
        HumanBehavior.sleep(1500, 2000);
      }
      if (!isRangeVisible()) {
        logger.warn("Range not found after walk + door attempt ({}/{})", stuckCounter + 1, MAX_STUCK_CYCLES);
        stuckCounter++;
        return;
      }
    }

    // Use raw shrimp on range
    logger.info("Using raw shrimp on range");
    Inventory.clickItem(this, RAW_SHRIMP, THRESHOLD, "medium");
    HumanBehavior.sleep(200, 400);

    Point rangeLoc = getRangeClickPoint();
    if (rangeLoc == null) {
      logger.warn("Range click point null ({}/{})", stuckCounter + 1, MAX_STUCK_CYCLES);
      stuckCounter++;
      return;
    }
    controller().mouse().moveTo(rangeLoc, "medium");
    controller().mouse().leftClick();

    // Wait for cook dialog to appear, then press space
    logger.info("Waiting for cook dialog...");
    HumanBehavior.sleep(3000, 4000);
    KeyPress.space(this);
    logger.info("Pressed space, waiting for cooking to finish");

    // Wait for cooking to finish (shorter timeout — 30s is plenty for 28 shrimp)
    if (Idler.waitUntilIdle(this, 30)) {
      logger.info("Player went idle — cooking done or interrupted");
    } else {
      logger.warn("Idle timeout reached (30s)");
    }

    int remaining = Inventory.countItem(this, RAW_SHRIMP, THRESHOLD);
    if (remaining > 0) {
      logger.info("{} raw shrimp remaining — will retry next cycle", remaining);
    } else {
      logger.info("All shrimp cooked");
    }

    stuckCounter = 0;
  }

  // === Banking ===

  private void bank() {
    logger.info("Walking to bank");
    Walk.to(this, BANK_TILE, "bank");

    logger.info("Opening bank");
    Bank.open(this, "Cyan");

    // Wait for bank UI — poll for raw shrimp visible in bank grid
    if (!waitForBankOpen()) {
      logger.warn("Bank UI did not open ({}/{})", stuckCounter + 1, MAX_STUCK_CYCLES);
      stuckCounter++;
      return;
    }
    logger.info("Bank UI open");

    // Deposit cooked/burnt shrimp if present
    if (Inventory.hasItem(this, COOKED_SHRIMP, THRESHOLD)) {
      logger.info("Depositing cooked shrimp");
      Inventory.clickItem(this, COOKED_SHRIMP, THRESHOLD, "medium");
      HumanBehavior.sleep(300, 500);
    }
    if (Inventory.hasItem(this, BURNT_SHRIMP, THRESHOLD)) {
      logger.info("Depositing burnt shrimp");
      Inventory.clickItem(this, BURNT_SHRIMP, THRESHOLD, "medium");
      HumanBehavior.sleep(300, 500);
    }

    // Withdraw raw shrimp
    logger.info("Withdrawing raw shrimp");
    BufferedImage gameView = controller().zones().getGameView();
    Point shrimpLoc = PointSelector.getRandomPointInImage(RAW_SHRIMP, gameView, THRESHOLD);
    if (shrimpLoc == null) {
      logger.error("No raw shrimp in bank — out of supplies.");
      DiscordNotification.send("AlKharidCooking: no raw shrimp in bank.");
      Bank.close(this);
      Logout.perform(this);
      stop();
      return;
    }
    controller().mouse().moveTo(shrimpLoc, "medium");
    controller().mouse().leftClick();
    HumanBehavior.sleep(400, 600);

    Bank.close(this);
    logger.info("Bank closed");

    if (Inventory.hasItem(this, RAW_SHRIMP, THRESHOLD)) {
      int count = Inventory.countItem(this, RAW_SHRIMP, THRESHOLD);
      logger.info("Withdrew {} raw shrimp", count);
      stuckCounter = 0;
    } else {
      logger.warn("Withdrawal failed — no raw shrimp in inventory ({}/{})", stuckCounter + 1, MAX_STUCK_CYCLES);
      stuckCounter++;
    }
  }

  // === Utilities ===

  private boolean waitForBankOpen() {
    for (int i = 0; i < 5; i++) {
      HumanBehavior.sleep(800, 1200);
      BufferedImage gv = controller().zones().getGameView();
      if (PointSelector.getRandomPointInImage(RAW_SHRIMP, gv, THRESHOLD) != null) {
        return true;
      }
    }
    return false;
  }

  private boolean isRangeVisible() {
    return getRangeClickPoint() != null;
  }

  private Point getRangeClickPoint() {
    Point loc = ColourClick.getClickPoint(this, RANGE_COLOUR_LOW);
    if (loc != null) return loc;
    return ColourClick.getClickPoint(this, RANGE_COLOUR_HIGH);
  }
}
