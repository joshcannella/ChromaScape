package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.custom.HumanBehavior;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.actions.custom.LevelUpDismisser;
import com.chromascape.utils.actions.custom.Logout;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.colour.ColourObj;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Fly fishes trout/salmon at Barbarian Village, cooks on the permanent fire, banks at Edgeville.
 *
 * <p><b>Flow:</b> FISH → COOK_TROUT → COOK_SALMON → DROP_BURNT → BANK → repeat
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>NPC Indicators — "Rod Fishing spot" in Cyan</li>
 *   <li>Object Markers — permanent fire in Red, Edgeville bank booth in Green</li>
 *   <li>Idle Notifier — enabled</li>
 *   <li>Shift-click drop — enabled</li>
 * </ul>
 */
public class BarbFishCookScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(BarbFishCookScript.class);

  private enum State {
    FISHING, COOK_TROUT, COOK_SALMON, DROP_BURNT, WALK_TO_BANK, BANKING, WALK_TO_FISH
  }

  // === Templates ===
  private static final String RAW_TROUT = "/images/user/Raw_trout.png";
  private static final String RAW_SALMON = "/images/user/Raw_salmon.png";
  private static final String BURNT_FISH = "/images/user/Burnt_fish.png";
  private static final String ROD = "/images/user/Fly_fishing_rod.png";
  private static final String FEATHER = "/images/user/Feather.png";

  // === Colours ===
  private static final ColourObj SPOT_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj FIRE_COLOUR =
      new ColourObj("red", new Scalar(0, 254, 254, 0), new Scalar(1, 255, 255, 0));
  private static final ColourObj BANK_COLOUR =
      new ColourObj("green", new Scalar(60, 254, 254, 0), new Scalar(61, 255, 255, 0));
  private static final ColourObj CHAT_BLACK =
      new ColourObj("black", new Scalar(0, 0, 0, 0), new Scalar(0, 0, 0, 0));

  // === Tiles ===
  private static final Point FISHING_TILE = new Point(3104, 3424);
  private static final Point FIRE_TILE = new Point(3100, 3426);
  private static final Point BANK_TILE = new Point(3094, 3492);

  private static final double THRESHOLD = 0.07;
  private static final int MAX_STUCK_CYCLES = 10;

  private State state = State.FISHING;
  private int stuckCounter = 0;

  @Override
  protected void cycle() {
    if (HumanBehavior.runPreCycleChecks(this)) return;

    if (stuckCounter >= MAX_STUCK_CYCLES) {
      logger.error("Stuck for {} cycles, logging out.", MAX_STUCK_CYCLES);
      DiscordNotification.send("BarbFishCook: stuck, logging out.");
      Logout.perform(this);
      stop();
      return;
    }

    if (!Inventory.hasItem(this, ROD, THRESHOLD)) {
      logger.error("No fly fishing rod.");
      DiscordNotification.send("BarbFishCook: No rod. Stopping.");
      stop();
      return;
    }
    if (!Inventory.hasItem(this, FEATHER, THRESHOLD)) {
      logger.error("No feathers.");
      DiscordNotification.send("BarbFishCook: No feathers. Stopping.");
      stop();
      return;
    }

    logger.info("State: {} | Stuck: {}", state, stuckCounter);

    switch (state) {
      case FISHING -> fish();
      case COOK_TROUT -> cookFish(RAW_TROUT, State.COOK_SALMON);
      case COOK_SALMON -> cookFish(RAW_SALMON, State.DROP_BURNT);
      case DROP_BURNT -> dropBurnt();
      case WALK_TO_BANK -> walkToBank();
      case BANKING -> bank();
      case WALK_TO_FISH -> walkToFish();
    }
  }

  private void fish() {
    if (!ColourClick.isVisible(this, SPOT_COLOUR)) {
      logger.info("Spot not visible, walking.");
      if (!Walk.to(this, FISHING_TILE, "fishing spot")) stuckCounter++;
      return;
    }

    Point spot = ColourClick.getClickPoint(this, SPOT_COLOUR, 10.0);
    if (spot == null) { stuckCounter++; return; }

    controller().mouse().moveTo(spot, "medium");
    controller().mouse().leftClick();

    // Poll: break on idle OR spot disappearing (moved)
    Instant deadline = Instant.now().plus(Duration.ofSeconds(120));
    waitMillis(600);
    while (Instant.now().isBefore(deadline)) {
      checkInterrupted();
      if (Idler.waitUntilIdle(this, 3)) break;
      if (!ColourClick.isVisible(this, SPOT_COLOUR)) break;
    }

    LevelUpDismisser.dismissIfPresent(this);

    if (Inventory.isFullByChat(this, CHAT_BLACK)) {
      logger.info("Inventory full, cooking.");
      state = State.COOK_TROUT;
    }
    stuckCounter = 0;
  }

  private void cookFish(String rawTemplate, State nextState) {
    // Skip if none of this fish type
    if (!Inventory.hasItem(this, rawTemplate, THRESHOLD)) {
      state = nextState;
      return;
    }

    // Walk to fire if not visible
    if (!ColourClick.isVisible(this, FIRE_COLOUR)) {
      logger.info("Fire not visible, walking.");
      if (!Walk.to(this, FIRE_TILE, "fire")) { stuckCounter++; return; }
      if (!ColourClick.isVisible(this, FIRE_COLOUR)) { stuckCounter++; return; }
    }

    // Use raw fish on fire
    if (!Inventory.clickItem(this, rawTemplate, THRESHOLD, "medium")) { stuckCounter++; return; }
    waitMillis(HumanBehavior.adjustDelay(250, 400));

    Point fire = ColourClick.getClickPoint(this, FIRE_COLOUR);
    if (fire == null) { stuckCounter++; return; }
    controller().mouse().moveTo(fire, "medium");
    controller().mouse().leftClick();

    // Cooking interface → space → wait
    waitMillis(HumanBehavior.adjustDelay(1100, 1400));
    KeyPress.space(this);
    Idler.waitUntilIdle(this, 120);
    LevelUpDismisser.dismissIfPresent(this);

    // Advance only when all of this type are cooked (level-up may have interrupted)
    if (!Inventory.hasItem(this, rawTemplate, THRESHOLD)) {
      state = nextState;
    }
    stuckCounter = 0;
  }

  private void dropBurnt() {
    if (!Inventory.hasItem(this, BURNT_FISH, THRESHOLD)) {
      state = State.WALK_TO_BANK;
      return;
    }

    controller().keyboard().sendModifierKey(401, "shift");
    waitMillis(HumanBehavior.adjustDelay(80, 150));
    try {
      int slot;
      while ((slot = Inventory.findItemSlot(this, BURNT_FISH, THRESHOLD)) >= 0) {
        Rectangle rect = controller().zones().getInventorySlots().get(slot);
        controller().mouse().moveTo(ClickDistribution.generateRandomPoint(rect), "fast");
        controller().mouse().leftClick();
        waitMillis(HumanBehavior.adjustDelay(40, 90));
      }
    } finally {
      controller().keyboard().sendModifierKey(402, "shift");
    }

    state = State.WALK_TO_BANK;
    stuckCounter = 0;
  }

  private void walkToBank() {
    if (ColourClick.isVisible(this, BANK_COLOUR)) { state = State.BANKING; return; }
    if (!Walk.to(this, BANK_TILE, "bank")) { stuckCounter++; return; }
    if (ColourClick.isVisible(this, BANK_COLOUR)) {
      state = State.BANKING;
    } else {
      stuckCounter++;
    }
  }

  private void bank() {
    Point booth = ColourClick.getClickPoint(this, BANK_COLOUR);
    if (booth == null) { stuckCounter++; return; }

    controller().mouse().moveTo(booth, "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(1200, 1800));

    Bank.depositAll(this);
    waitMillis(HumanBehavior.adjustDelay(300, 500));

    if (!withdrawItem(ROD, "rod") || !withdrawItem(FEATHER, "feathers")) return;

    Bank.close(this);
    state = State.WALK_TO_FISH;
    stuckCounter = 0;
    logger.info("Banked all fish.");
  }

  private boolean withdrawItem(String template, String name) {
    Point loc = Inventory.findInGameView(this, template, THRESHOLD);
    if (loc == null) {
      logger.error("{} not found in bank.", name);
      DiscordNotification.send("BarbFishCook: Lost " + name + ". Stopping.");
      Bank.close(this);
      stop();
      return false;
    }
    controller().mouse().moveTo(loc, "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(300, 500));
    return true;
  }

  private void walkToFish() {
    Walk.to(this, FISHING_TILE, "fishing spot");
    state = State.FISHING;
    stuckCounter = 0;
  }
}
