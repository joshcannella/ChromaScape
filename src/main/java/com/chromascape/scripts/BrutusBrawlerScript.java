package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.Minimap;
import com.chromascape.utils.actions.MovingObject;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.actions.custom.Bank;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.custom.HumanBehavior;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.KeyPress;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Farms the Brutus cow boss for melee combat XP (+100% bonus) and loot. Uses east-positioning
 * so charge attack never fires — only dodges slams via yellow overhead text detection.
 * Banks at Lumbridge Castle when inventory is full or food is low.
 *
 * <p><b>Prerequisites:</b> "The Ides of Milk" quest complete, cowbell amulet (charged), melee
 * gear, food (trout). Must have selected "Don't ask again" on the instance entry dialogue.
 * <p><b>RuneLite Setup:</b> NPC Indicators — Brutus in cyan. Object Markers — gate in red.
 * Ground Items — loot in purple. Ground Markers — attack tile green, dodge tiles orange.
 * Camera zoom locked. Idle Notifier enabled. XP bar permanent.
 * <p><b>Image Templates:</b> Trout.png, Raw_t-bone_steak.png
 */
public class BrutusBrawlerScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(BrutusBrawlerScript.class);

  // === Colours ===
  private static final ColourObj BRUTUS_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj GATE_COLOUR =
      new ColourObj("red", new Scalar(0, 254, 254, 0), new Scalar(1, 255, 255, 0));
  private static final ColourObj LOOT_COLOUR =
      new ColourObj("purple", new Scalar(139, 200, 200, 0), new Scalar(141, 255, 255, 0));
  private static final ColourObj ATTACK_TILE_COLOUR =
      new ColourObj("green", new Scalar(60, 254, 254, 0), new Scalar(61, 255, 255, 0));
  private static final ColourObj DODGE_TILE_COLOUR =
      new ColourObj("orange", new Scalar(10, 254, 254, 0), new Scalar(11, 255, 255, 0));

  // === Yellow overhead text detection ===
  // #FFFF00 in RGB — we scan raw pixels, not HSV, for speed during dodge window
  private static final int YELLOW_RGB = new Color(255, 255, 0).getRGB();
  private static final int YELLOW_TOLERANCE = 10;
  // Region above game center where Brutus overhead text appears (calibrate with locked zoom)
  // Approximate: top-center of game view, 200px wide, 40px tall, offset above Brutus model
  private static final Rectangle OVERHEAD_ZONE = new Rectangle(170, 50, 200, 40);

  // === Food ===
  private static final String FOOD_IMAGE = "/images/user/Trout.png";
  private static final double FOOD_THRESHOLD = 0.07;
  private static final int EAT_AT_HP = 8;
  private static final int MIN_FOOD_TO_FIGHT = 3;

  // === Loot ===
  private static final String STEAK_IMAGE = "/images/user/Raw_t-bone_steak.png";
  private static final String[] KNOWN_ITEMS = {FOOD_IMAGE, STEAK_IMAGE};

  // === Tiles ===
  private static final Point COWBELL_TELEPORT = new Point(3259, 3277);
  private static final Point GATE_TILE = new Point(3263, 3298);
  private static final Point BANK_TILE = new Point(3208, 3220);

  // === Combat ===
  private static final int BRUTUS_XP = 464; // 58 HP × 4 × 2 (100% bonus)
  private static final int KILL_TIMEOUT_SECONDS = 90;
  private static final int DODGE_CYCLES = 3;
  private static final long SLAM_WAIT_MS = 1200; // ~2 ticks between slams

  // === Stuck detection ===
  private static final int MAX_STUCK_CYCLES = 10;
  private int stuckCounter = 0;

  // === State ===
  private enum State {
    TRAVEL_TO_GATE, ENTER_INSTANCE, POSITION_EAST,
    ATTACKING, DODGING, LOOTING, CHECK_SUPPLIES, BANKING
  }

  private State state = State.TRAVEL_TO_GATE;
  private int previousXp = -1;
  private boolean seeded = false;

  @Override
  protected void cycle() {
    if (!seeded) {
      Idler.seedLastMessage(this);
      seeded = true;
    }
    if (HumanBehavior.runPreCycleChecks(this)) return;

    switch (state) {
      case TRAVEL_TO_GATE -> travelToGate();
      case ENTER_INSTANCE -> enterInstance();
      case POSITION_EAST -> positionEast();
      case ATTACKING -> attacking();
      case DODGING -> dodging();
      case LOOTING -> looting();
      case CHECK_SUPPLIES -> checkSupplies();
      case BANKING -> banking();
    }
  }

  // === TRAVEL_TO_GATE ===

  private void travelToGate() {
    if (ColourClick.isVisible(this, GATE_COLOUR)) {
      logger.info("Gate visible, transitioning to ENTER_INSTANCE");
      stuckCounter = 0;
      setState(State.ENTER_INSTANCE);
      return;
    }

    // Already inside instance (Brutus visible)
    if (ColourClick.isVisible(this, BRUTUS_COLOUR)) {
      logger.info("Already inside instance, positioning east");
      stuckCounter = 0;
      setState(State.POSITION_EAST);
      return;
    }

    logger.info("Walking to gate");
    Walk.to(this, GATE_TILE, "Brutus gate");
    waitMillis(HumanBehavior.adjustDelay(1000, 1500));
  }

  // === ENTER_INSTANCE ===

  private void enterInstance() {
    // Already inside
    if (ColourClick.isVisible(this, BRUTUS_COLOUR)
        || ColourClick.isVisible(this, ATTACK_TILE_COLOUR)) {
      logger.info("Inside instance, positioning east");
      setState(State.POSITION_EAST);
      return;
    }

    Point gateLoc = ColourClick.getClickPoint(this, GATE_COLOUR);
    if (gateLoc == null) {
      logger.warn("Gate not found");
      stuckCounter++;
      if (stuckCounter >= MAX_STUCK_CYCLES) {
        fail("Gate not found after " + MAX_STUCK_CYCLES + " attempts");
      }
      setState(State.TRAVEL_TO_GATE);
      return;
    }

    logger.info("Clicking gate to enter instance");
    humanClick(gateLoc, "medium");
    waitMillis(HumanBehavior.adjustDelay(3000, 4000));
    stuckCounter = 0;
  }

  // === POSITION_EAST ===

  private void positionEast() {
    // If Brutus is already visible and we're on the attack tile, start fighting
    if (ColourClick.isVisible(this, BRUTUS_COLOUR)
        && !ColourClick.isVisible(this, ATTACK_TILE_COLOUR)) {
      // Attack tile marker disappears when standing on it — we're in position
      logger.info("On attack tile, transitioning to ATTACKING");
      setState(State.ATTACKING);
      return;
    }

    Point tileLoc = ColourClick.getClickPoint(this, ATTACK_TILE_COLOUR);
    if (tileLoc == null) {
      // Tile not visible — might already be standing on it
      if (ColourClick.isVisible(this, BRUTUS_COLOUR)) {
        logger.info("Attack tile not visible (standing on it), transitioning to ATTACKING");
        setState(State.ATTACKING);
        return;
      }
      logger.warn("Attack tile not found and Brutus not visible");
      stuckCounter++;
      if (stuckCounter >= MAX_STUCK_CYCLES) {
        fail("Cannot find attack tile or Brutus");
      }
      return;
    }

    logger.info("Clicking attack tile to position east");
    humanClick(tileLoc, "fast");
    waitMillis(HumanBehavior.adjustDelay(1500, 2000));
    stuckCounter = 0;
  }

  // === ATTACKING ===

  private void attacking() {
    // Brutus dead?
    if (!ColourClick.isVisible(this, BRUTUS_COLOUR)) {
      logger.info("Brutus dead, transitioning to LOOTING");
      HumanBehavior.sleep(600, 900);
      setState(State.LOOTING);
      return;
    }

    // Eat if needed
    if (shouldEat()) {
      eatFood();
      return;
    }

    // Check for yellow overhead text (special incoming)
    if (isYellowTextVisible()) {
      logger.info("Yellow overhead text detected — slam incoming, transitioning to DODGING");
      setState(State.DODGING);
      return;
    }

    // Snapshot XP if not yet set
    if (previousXp == -1) {
      try {
        previousXp = Minimap.getXp(this);
      } catch (Exception e) {
        logger.warn("XP read failed: {}", e.getMessage());
        return;
      }
    }

    // Engage Brutus
    if (!MovingObject.clickMovingObjectByColourObjUntilRedClick(BRUTUS_COLOUR, this)) {
      logger.warn("Failed to click Brutus");
      return;
    }

    // Wait and monitor — poll for kill, special, or timeout
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(KILL_TIMEOUT_SECONDS);
    while (LocalDateTime.now().isBefore(deadline)) {
      checkInterrupted();

      // Check for special
      if (isYellowTextVisible()) {
        logger.info("Yellow text detected mid-combat — dodging");
        setState(State.DODGING);
        return;
      }

      // Check for kill
      if (!ColourClick.isVisible(this, BRUTUS_COLOUR)) {
        logger.info("Brutus dead (highlight gone)");
        try {
          int delta = Minimap.getXp(this) - previousXp;
          logger.info("XP gained: {}", delta);
        } catch (Exception ignored) {}
        previousXp = -1;
        HumanBehavior.sleep(600, 900);
        setState(State.LOOTING);
        return;
      }

      // Eat mid-combat
      if (shouldEat()) {
        eatFood();
      }

      waitMillis(200);
    }

    logger.warn("Kill timeout reached");
    previousXp = -1;
  }

  // === DODGING ===

  private void dodging() {
    for (int i = 0; i < DODGE_CYCLES; i++) {
      checkInterrupted();

      // Click dodge tile (orange)
      Point dodgeLoc = ColourClick.getClickPoint(this, DODGE_TILE_COLOUR);
      if (dodgeLoc == null) {
        logger.warn("Dodge tile not found, cycle {}", i + 1);
        waitMillis(SLAM_WAIT_MS);
        continue;
      }

      logger.info("Dodge cycle {}/{} — sidestepping", i + 1, DODGE_CYCLES);
      controller().mouse().moveTo(dodgeLoc, "fast");
      controller().mouse().leftClick();
      waitMillis(SLAM_WAIT_MS);

      // Click back to attack tile
      Point attackLoc = ColourClick.getClickPoint(this, ATTACK_TILE_COLOUR);
      if (attackLoc != null) {
        controller().mouse().moveTo(attackLoc, "fast");
        controller().mouse().leftClick();
        waitMillis(600);
      }
    }

    logger.info("Dodge sequence complete, re-engaging");
    setState(State.ATTACKING);
  }

  // === LOOTING ===

  private void looting() {
    // Pick up all visible purple loot
    for (int i = 0; i < 10; i++) {
      checkInterrupted();

      Point lootLoc = ColourClick.getClickPoint(this, LOOT_COLOUR, 15.0);
      if (lootLoc == null) {
        break;
      }

      logger.info("Picking up loot");
      humanClick(lootLoc, "fast");
      waitMillis(HumanBehavior.adjustDelay(800, 1200));
    }

    setState(State.CHECK_SUPPLIES);
  }

  // === CHECK_SUPPLIES ===

  private void checkSupplies() {
    int foodCount = Inventory.countItem(this, FOOD_IMAGE, FOOD_THRESHOLD);
    boolean invFull = Inventory.isFull(this, KNOWN_ITEMS, FOOD_THRESHOLD);

    logger.info("Food: {}, Inventory full: {}", foodCount, invFull);

    if (foodCount < MIN_FOOD_TO_FIGHT || invFull) {
      logger.info("Need to bank (food={}, full={})", foodCount, invFull);
      setState(State.BANKING);
      return;
    }

    // Ring cowbell for fast respawn then wait
    ringCowbell();
    waitMillis(HumanBehavior.adjustDelay(2000, 3000));

    // Wait for Brutus to respawn
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(15);
    while (LocalDateTime.now().isBefore(deadline)) {
      checkInterrupted();
      if (ColourClick.isVisible(this, BRUTUS_COLOUR)) {
        logger.info("Brutus respawned");
        setState(State.ATTACKING);
        return;
      }
      waitMillis(600);
    }

    logger.info("Brutus didn't respawn in time, re-ringing");
    setState(State.CHECK_SUPPLIES);
  }

  // === BANKING ===

  private void banking() {
    // Exit instance — click gate if visible
    if (ColourClick.isVisible(this, ATTACK_TILE_COLOUR)
        || ColourClick.isVisible(this, BRUTUS_COLOUR)) {
      // Still inside instance, walk south to gate
      logger.info("Exiting instance");
      Walk.to(this, GATE_TILE, "instance gate");
      waitMillis(HumanBehavior.adjustDelay(1000, 1500));

      Point gateLoc = ColourClick.getClickPoint(this, GATE_COLOUR);
      if (gateLoc != null) {
        humanClick(gateLoc, "medium");
        waitMillis(HumanBehavior.adjustDelay(2000, 3000));
      }
    }

    // Walk to bank
    logger.info("Walking to Lumbridge bank");
    Walk.to(this, BANK_TILE, "Lumbridge bank");
    Idler.waitUntilIdle(this, 30);

    // Open bank, deposit, withdraw food
    Bank.open(this, "Cyan");
    waitMillis(HumanBehavior.adjustDelay(800, 1200));

    Bank.depositAll(this);
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    // Withdraw food — click food in bank (first tab, search, or preset)
    // Use template matching on the bank interface to find trout
    withdrawFood();

    Bank.close(this);
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    // Teleport back
    logger.info("Teleporting back to cow field");
    setState(State.TRAVEL_TO_GATE);
  }

  // === Helpers ===

  private boolean isYellowTextVisible() {
    BufferedImage gameView = controller().zones().getGameView();
    int startX = Math.max(0, OVERHEAD_ZONE.x);
    int startY = Math.max(0, OVERHEAD_ZONE.y);
    int endX = Math.min(gameView.getWidth(), OVERHEAD_ZONE.x + OVERHEAD_ZONE.width);
    int endY = Math.min(gameView.getHeight(), OVERHEAD_ZONE.y + OVERHEAD_ZONE.height);

    for (int y = startY; y < endY; y += 2) {
      for (int x = startX; x < endX; x += 2) {
        int rgb = gameView.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (r >= 255 - YELLOW_TOLERANCE && g >= 255 - YELLOW_TOLERANCE
            && b <= YELLOW_TOLERANCE) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean shouldEat() {
    try {
      int hp = Minimap.getHp(this);
      return hp != -1 && hp <= EAT_AT_HP;
    } catch (Exception e) {
      return false;
    }
  }

  private void eatFood() {
    if (!Inventory.hasItem(this, FOOD_IMAGE, FOOD_THRESHOLD)) {
      logger.warn("No food left");
      return;
    }
    Inventory.clickItem(this, FOOD_IMAGE, FOOD_THRESHOLD, "fast");
    waitMillis(HumanBehavior.adjustDelay(1600, 2000));
  }

  private void ringCowbell() {
    // Right-click equipment tab to access cowbell amulet "Ring" option
    // Open equipment tab, right-click neck slot, select "Ring"
    Rectangle equipTab = controller().zones().getCtrlPanel().get("equipmentTab");
    if (equipTab == null) {
      logger.warn("Equipment tab not found");
      return;
    }
    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(equipTab), "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    // Neck slot is in the equipment panel — approximate position
    Rectangle panel = controller().zones().getCtrlPanel().get("inventoryPanel");
    if (panel == null) {
      logger.warn("Inventory panel not found");
      return;
    }
    // Neck slot: center-top area of equipment panel
    Rectangle neckSlot = new Rectangle(
        panel.x + panel.width / 2 - 10, panel.y + 35, 20, 20);
    Point neckLoc = ClickDistribution.generateRandomPoint(neckSlot);

    controller().mouse().moveTo(neckLoc, "medium");
    controller().mouse().rightClick();
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    // "Ring" option in right-click menu — offset below
    Point ringOption = new Point(neckLoc.x, neckLoc.y + 45);
    controller().mouse().moveTo(ringOption, "fast");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(300, 500));

    // Switch back to inventory tab
    Rectangle invTab = controller().zones().getCtrlPanel().get("inventoryTab");
    if (invTab != null) {
      controller().mouse().moveTo(ClickDistribution.generateRandomPoint(invTab), "medium");
      controller().mouse().leftClick();
      waitMillis(HumanBehavior.adjustDelay(200, 400));
    }

    logger.info("Rang cowbell for fast respawn");
  }

  private void withdrawFood() {
    // Search for trout in bank via template matching on the bank view
    BufferedImage gameView = controller().zones().getGameView();
    Point foodLoc = PointSelector.getRandomPointInImage(FOOD_IMAGE, gameView, 0.10);
    if (foodLoc == null) {
      logger.error("Food not found in bank");
      DiscordNotification.send("Brutus Brawler: no food in bank, stopping.");
      stop();
      return;
    }

    // Right-click → Withdraw-All
    controller().mouse().moveTo(foodLoc, "medium");
    controller().mouse().rightClick();
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    Point withdrawAll = new Point(foodLoc.x, foodLoc.y + 85);
    controller().mouse().moveTo(withdrawAll, "fast");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    logger.info("Withdrew food from bank");
  }

  private void humanClick(Point target, String speed) {
    String actualSpeed = HumanBehavior.shouldSlowApproach() ? "slow" : speed;
    controller().mouse().moveTo(target, actualSpeed);
    if (HumanBehavior.shouldHesitate()) HumanBehavior.performHesitation();
    if (HumanBehavior.shouldMisclick()) {
      HumanBehavior.performMisclick(this, target);
      controller().mouse().moveTo(target, "medium");
    }
    controller().mouse().microJitter();
    controller().mouse().leftClick();
  }

  private void setState(State newState) {
    logger.info("State: {} → {}", state, newState);
    state = newState;
    stuckCounter = 0;
  }

  private void fail(String reason) {
    logger.error("Unrecoverable: {}", reason);
    DiscordNotification.send("Brutus Brawler: " + reason);
    stop();
  }
}
