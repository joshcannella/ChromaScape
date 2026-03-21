package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.ItemDropper;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.custom.HumanBehavior;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.LevelUpDismisser;
import com.chromascape.utils.actions.custom.Logout;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.screen.colour.ColourObj;
import java.awt.Point;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Fishes shrimp and anchovies at Draynor Village. Banks or drops when inventory is full.
 *
 * <p><b>Flow (banking on):</b> FISH → WALK_TO_BANK → BANKING → WALK_TO_FISH → repeat
 *
 * <p><b>Flow (banking off):</b> FISH → DROP → repeat
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>NPC Indicators — highlight "Fishing spot" in Cyan (HSV ~90, 254-255, 254-255)</li>
 *   <li>Object Markers — highlight Draynor bank booth in Red (HSV ~0-1, 254-255, 254-255)</li>
 *   <li>Idle Notifier — enabled</li>
 * </ul>
 *
 * <p><b>Starting Position:</b> Near Draynor Village fishing spot on the south shore.
 */
public class DraynorFishingScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(DraynorFishingScript.class);

  private enum State {
    FISHING, WALK_TO_BANK, BANKING, WALK_TO_FISH, DROP
  }

  // === Image Templates ===
  private static final String RAW_SHRIMP = "/images/user/Raw_shrimps.png";
  private static final String RAW_ANCHOVY = "/images/user/Raw_anchovies.png";
  private static final String NET = "/images/user/Small_fishing_net.png";
  private static final String[] KNOWN_ITEMS = {NET, RAW_SHRIMP, RAW_ANCHOVY};

  // === Colour Definitions ===
  private static final ColourObj FISHING_SPOT_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj BANK_COLOUR =
      new ColourObj("red", new Scalar(0, 254, 254, 0), new Scalar(1, 255, 255, 0));
  private static final ColourObj CHAT_BLACK =
      new ColourObj("black", new Scalar(0, 0, 0, 0), new Scalar(0, 0, 0, 0));

  // === Walker Tiles ===
  private static final Point FISHING_TILE = new Point(3087, 3228);
  private static final Point BANK_TILE = new Point(3092, 3245);

  // === Configuration ===
  private static final boolean BANKING_ENABLED = true;
  private static final double THRESHOLD = 0.07;
  private static final int MAX_STUCK_CYCLES = 10;

  private State state = State.FISHING;
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

    if (!Inventory.hasItem(this, NET, THRESHOLD)) {
      logger.error("No small fishing net in inventory.");
      DiscordNotification.send("DraynorFishing: No fishing net. Stopping.");
      stop();
      return;
    }

    logger.info("State: {} | Stuck: {}", state, stuckCounter);

    switch (state) {
      case FISHING -> fish();
      case WALK_TO_BANK -> walkToBank();
      case BANKING -> bank();
      case WALK_TO_FISH -> walkToFish();
      case DROP -> drop();
    }
  }

  private void fish() {
    if (Inventory.isFull(this, KNOWN_ITEMS, THRESHOLD)) {
      State next = BANKING_ENABLED ? State.WALK_TO_BANK : State.DROP;
      logger.info("Inventory full on entry. State: FISHING → {}", next);
      state = next;
      stuckCounter = 0;
      return;
    }

    if (!ColourClick.isVisible(this, FISHING_SPOT_COLOUR)) {
      logger.info("Fishing spot not visible, walking.");
      if (!Walk.to(this, FISHING_TILE, "fishing spot")) stuckCounter++;
      return;
    }

    Point spot = ColourClick.getClickPoint(this, FISHING_SPOT_COLOUR, 10.0);
    if (spot == null) {
      stuckCounter++;
      return;
    }

    String speed = HumanBehavior.shouldSlowApproach() ? "slow" : "medium";
    controller().mouse().moveTo(spot, speed);
    if (HumanBehavior.shouldHesitate()) HumanBehavior.performHesitation();
    if (HumanBehavior.shouldMisclick()) {
      HumanBehavior.performMisclick(this, spot);
      controller().mouse().moveTo(spot, "medium");
    }
    controller().mouse().microJitter();
    controller().mouse().leftClick();

    Instant deadline = Instant.now().plus(Duration.ofSeconds(120));
    waitMillis(600);
    while (Instant.now().isBefore(deadline)) {
      checkInterrupted();
      if (Idler.waitUntilIdle(this, 3)) break;
      if (!ColourClick.isVisible(this, FISHING_SPOT_COLOUR)) break;
      waitMillis(300);
    }

    waitMillis(HumanBehavior.adjustDelay(300, 500));
    if (LevelUpDismisser.dismissIfPresent(this)) {
      logger.info("Dismissed level-up dialog.");
      waitMillis(HumanBehavior.adjustDelay(300, 500));
    }

    if (Inventory.isFullByChat(this, CHAT_BLACK)) {
      State next = BANKING_ENABLED ? State.WALK_TO_BANK : State.DROP;
      logger.info("Inventory full. State: FISHING → {}", next);
      state = next;
      stuckCounter = 0;
    }
  }

  private void walkToBank() {
    if (ColourClick.isVisible(this, BANK_COLOUR)) {
      logger.info("State: WALK_TO_BANK → BANKING");
      state = State.BANKING;
      stuckCounter = 0;
      return;
    }
    if (!Walk.to(this, BANK_TILE, "bank")) {
      stuckCounter++;
      return;
    }
    if (ColourClick.isVisible(this, BANK_COLOUR)) {
      logger.info("State: WALK_TO_BANK → BANKING");
      state = State.BANKING;
      stuckCounter = 0;
    } else {
      stuckCounter++;
    }
  }

  private void bank() {
    Point booth = ColourClick.getClickPoint(this, BANK_COLOUR);
    if (booth == null) {
      stuckCounter++;
      return;
    }

    String speed = HumanBehavior.shouldSlowApproach() ? "slow" : "medium";
    controller().mouse().moveTo(booth, speed);
    if (HumanBehavior.shouldHesitate()) HumanBehavior.performHesitation();
    controller().mouse().microJitter();
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(1200, 1800));

    Bank.depositAll(this);
    waitMillis(HumanBehavior.adjustDelay(300, 500));

    Point netLoc = Inventory.findInGameView(this, NET, THRESHOLD);
    if (netLoc == null) {
      logger.error("Could not find net in bank to withdraw.");
      DiscordNotification.send("DraynorFishing: Lost fishing net. Stopping.");
      Bank.close(this);
      stop();
      return;
    }
    controller().mouse().moveTo(netLoc, "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(300, 500));

    Bank.close(this);
    logger.info("State: BANKING → WALK_TO_FISH");
    state = State.WALK_TO_FISH;
    stuckCounter = 0;
  }

  private void walkToFish() {
    Walk.to(this, FISHING_TILE, "fishing spot");
    logger.info("State: WALK_TO_FISH → FISHING");
    state = State.FISHING;
    stuckCounter = 0;
  }

  private void drop() {
    int netSlot = Inventory.findItemSlot(this, NET, THRESHOLD);
    int[] exclude = netSlot >= 0 ? new int[]{netSlot} : new int[0];
    ItemDropper.dropAll(this, ItemDropper.DropPattern.ZIGZAG, exclude);
    logger.info("Dropped all fish. State: DROP → FISHING");
    state = State.FISHING;
    stuckCounter = 0;
  }
}
