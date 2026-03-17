package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.ItemDropper;
import com.chromascape.utils.actions.custom.Logout;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.actions.custom.HumanBehavior;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.core.screen.colour.ColourObj;
import java.awt.Point;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Fishes shrimp and anchovies at Draynor Village. Banks or drops when inventory is full.
 *
 * <p><b>Flow (banking on):</b> FISH until 27 raw → WALK to bank → DEPOSIT ALL → WITHDRAW net →
 * WALK back → repeat
 *
 * <p><b>Flow (banking off):</b> FISH until 27 raw → DROP all except net → repeat
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>NPC Indicators — highlight fishing spot in Cyan (HSV ~90, 254-255, 254-255)</li>
 *   <li>Object Markers — highlight Draynor bank booth in Magenta (HSV ~150, 254-255, 254-255)</li>
 *   <li>Idle Notifier — enabled</li>
 * </ul>
 *
 * <p><b>Starting Position:</b> Near Draynor Village fishing spot on the south shore.
 */
public class DraynorFishingScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(DraynorFishingScript.class);

  // === Image Templates ===
  private static final String RAW_SHRIMP = "/images/user/Raw_shrimps.png";
  private static final String RAW_ANCHOVY = "/images/user/Raw_anchovies.png";
  private static final String NET = "/images/user/Small_fishing_net.png";

  // === Colour Definitions ===
  private static final ColourObj FISHING_SPOT_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj BANK_COLOUR =
      new ColourObj("red", new Scalar(0, 254, 254, 0), new Scalar(1, 255, 255, 0));

  // === Walker Tiles ===
  private static final Point FISHING_TILE = new Point(3087, 3228);
  private static final Point BANK_TILE = new Point(3092, 3245);

  // === Configuration ===
  private static final boolean BANKING_ENABLED = true;

  // === Constants ===
  private static final double INV_THRESHOLD = 0.07;
  private static final int TARGET_RAW = 27; // 28 slots minus net
  private static final int MAX_STUCK_CYCLES = 10;

  private int stuckCounter = 0;

  @Override
  protected void cycle() {
    if (HumanBehavior.runPreCycleChecks(this)) return;

    if (stuckCounter >= MAX_STUCK_CYCLES) {
      logger.error("Stuck for {} cycles, logging out.", MAX_STUCK_CYCLES);
      DiscordNotification.send("DraynorFishing: stuck, logging out.");
      Logout.perform(this);
      stop();
      return;
    }

    if (!Inventory.hasItem(this, NET, INV_THRESHOLD)) {
      logger.error("No small fishing net in inventory.");
      DiscordNotification.send("DraynorFishing: No fishing net. Stopping.");
      stop();
      return;
    }

    int rawCount = Inventory.countItem(this, RAW_SHRIMP, INV_THRESHOLD)
        + Inventory.countItem(this, RAW_ANCHOVY, INV_THRESHOLD);

    logger.info("Raw: {} | Stuck: {}", rawCount, stuckCounter);

    // Inventory full
    if (rawCount >= TARGET_RAW) {
      if (BANKING_ENABLED) {
        bank();
      } else {
        drop();
      }
      return;
    }

    // Fish
    fish();
  }

  private void fish() {
    if (!ColourClick.isVisible(this, FISHING_SPOT_COLOUR)) {
      logger.info("Fishing spot not visible, walking.");
      if (!Walk.to(this, FISHING_TILE, "fishing spot")) stuckCounter++;
      return;
    }

    Point spotLoc = ColourClick.getClickPoint(this, FISHING_SPOT_COLOUR);
    if (spotLoc == null) {
      stuckCounter++;
      return;
    }

    controller().mouse().moveTo(spotLoc, "medium");
    controller().mouse().leftClick();
    Idler.waitUntilIdle(this, 120);
    stuckCounter = 0;
  }

  private void bank() {
    // Walk to bank
    if (!ColourClick.isVisible(this, BANK_COLOUR)) {
      logger.info("Bank not visible, walking.");
      if (!Walk.to(this, BANK_TILE, "bank")) {
        stuckCounter++;
        return;
      }
    }

    // Open bank
    Bank.open(this, BANK_COLOUR.name());

    // Deposit all then withdraw net
    Bank.depositAll(this);
    waitMillis(HumanBehavior.adjustDelay(300, 500));

    // Net is now in bank — click it in the bank tab to withdraw
    // The bank grid starts at a fixed offset; first item will be the net if bank was empty
    // Use template matching on the game view to find and click it
    Point netLoc = findImageInGameView(NET, INV_THRESHOLD);
    if (netLoc != null) {
      controller().mouse().moveTo(netLoc, "medium");
      controller().mouse().leftClick();
      waitMillis(HumanBehavior.adjustDelay(300, 500));
    } else {
      logger.error("Could not find net in bank to withdraw.");
      DiscordNotification.send("DraynorFishing: Lost fishing net. Stopping.");
      Bank.close(this);
      stop();
      return;
    }

    Bank.close(this);

    // Walk back to fishing spot
    Walk.to(this, FISHING_TILE, "fishing spot");
    stuckCounter = 0;
    logger.info("Banked all fish.");
  }

  private Point findImageInGameView(String image, double threshold) {
    java.awt.image.BufferedImage gameView = controller().zones().getGameView();
    return com.chromascape.utils.actions.PointSelector.getRandomPointInImage(
        image, gameView, threshold);
  }

  private void drop() {
    int netSlot = Inventory.findItemSlot(this, NET, INV_THRESHOLD);
    int[] exclude = netSlot >= 0 ? new int[]{netSlot} : new int[0];
    ItemDropper.dropAll(this, ItemDropper.DropPattern.ZIGZAG, exclude);
    stuckCounter = 0;
    logger.info("Dropped all fish.");
  }
}

