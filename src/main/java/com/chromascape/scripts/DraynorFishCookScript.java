package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.ItemDropper;
import com.chromascape.utils.actions.MovingObject;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.scripts.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Fishes shrimp at Draynor Village, chops a tree for logs, lights a fire, cooks the shrimp,
 * then drops all cooked/burnt shrimp. Repeats indefinitely.
 *
 * <p><b>Flow:</b> FISH (26 raw) → CHOP (1 log) → LIGHT FIRE → COOK ALL → DROP → repeat
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>Tinderbox in inventory</li>
 *   <li>Small fishing net in inventory</li>
 *   <li>Shift-click drop enabled in game settings</li>
 * </ul>
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>NPC Indicators — highlight fishing spot in Cyan</li>
 *   <li>Object Markers — highlight regular tree(s) in Green</li>
 *   <li>Object Markers — highlight lit fires in Orange</li>
 *   <li>Idle Notifier — enabled</li>
 * </ul>
 *
 * <p><b>Starting Position:</b> Near Draynor Village fishing spot on the south shore.
 *
 * <p><b>Image Templates:</b>
 * <ul>
 *   <li>{@code /images/user/Raw_shrimps.png} — https://oldschool.runescape.wiki/images/Raw_shrimps.png</li>
 *   <li>{@code /images/user/Shrimps.png} — https://oldschool.runescape.wiki/images/Shrimps.png</li>
 *   <li>{@code /images/user/Burnt_shrimp.png} — https://oldschool.runescape.wiki/images/Burnt_shrimp.png</li>
 *   <li>{@code /images/user/Logs.png} — https://oldschool.runescape.wiki/images/Logs.png</li>
 *   <li>{@code /images/user/Tinderbox.png} — https://oldschool.runescape.wiki/images/Tinderbox.png</li>
 *   <li>{@code /images/user/Small_fishing_net.png} — https://oldschool.runescape.wiki/images/Small_fishing_net.png</li>
 * </ul>
 *
 * <p><b>Source:</b> <a href="https://oldschool.runescape.wiki/w/Raw_shrimps">OSRS Wiki</a>
 */
public class DraynorFishCookScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(DraynorFishCookScript.class);

  // === Image Templates ===
  private static final String RAW_SHRIMP = "/images/user/Raw_shrimps.png";
  private static final String COOKED_SHRIMP = "/images/user/Shrimps.png";
  private static final String BURNT_SHRIMP = "/images/user/Burnt_shrimp.png";
  private static final String LOGS = "/images/user/Logs.png";
  private static final String TINDERBOX = "/images/user/Tinderbox.png";
  private static final String NET = "/images/user/Small_fishing_net.png";

  // === Colour Definitions ===
  private static final ColourObj FISHING_SPOT_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj TREE_COLOUR =
      new ColourObj("green", new Scalar(59, 254, 254, 0), new Scalar(60, 255, 255, 0));
  private static final ColourObj FIRE_COLOUR =
      new ColourObj("orange", new Scalar(15, 254, 254, 0), new Scalar(16, 255, 255, 0));

  // === Walker Tiles (Draynor Village) ===
  private static final Point FISHING_TILE = new Point(3087, 3228);
  private static final Point TREE_TILE = new Point(3080, 3230);

  // === Configuration ===
  private static final boolean COOKING_ENABLED = true; // set false to fish-and-drop only

  // === Constants ===
  private static final double INV_THRESHOLD = 0.07;
  private static final int TARGET_RAW = COOKING_ENABLED ? 26 : 27; // reserve slots for tools
  private static final int FIRE_TIMEOUT_SECONDS = 10;
  private static final int MAX_STUCK_CYCLES = 10;

  // === State ===
  private enum State { FISH, CHOP, LIGHT_FIRE, COOK, DROP }
  private State state = State.FISH;
  private int stuckCounter = 0;

  @Override
  protected void cycle() {
    // Humanization
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

    // Stuck detection
    if (stuckCounter >= MAX_STUCK_CYCLES) {
      logger.error("Stuck for {} cycles, logging out.", MAX_STUCK_CYCLES);
      DiscordNotification.send("DraynorFishCook: stuck, logging out.");
      pressLogout();
      stop();
      return;
    }

    // Prerequisites
    if (COOKING_ENABLED && !hasItem(TINDERBOX)) {
      logger.error("No tinderbox in inventory.");
      DiscordNotification.send("DraynorFishCook: No tinderbox. Stopping.");
      stop();
      return;
    }
    if (!hasItem(NET)) {
      logger.error("No small fishing net in inventory.");
      DiscordNotification.send("DraynorFishCook: No fishing net. Stopping.");
      stop();
      return;
    }

    // Read inventory
    int rawCount = countItem(RAW_SHRIMP);
    boolean hasCooked = hasItem(COOKED_SHRIMP) || hasItem(BURNT_SHRIMP);
    boolean hasLogs = hasItem(LOGS);

    // Determine state
    if (rawCount == 0 && hasCooked) {
      state = State.DROP;
    } else if (COOKING_ENABLED && rawCount >= TARGET_RAW && !hasLogs) {
      state = State.CHOP;
    } else if (COOKING_ENABLED && rawCount >= TARGET_RAW && hasLogs && !isColourVisible(FIRE_COLOUR)) {
      state = State.LIGHT_FIRE;
    } else if (COOKING_ENABLED && rawCount >= TARGET_RAW && isColourVisible(FIRE_COLOUR)) {
      state = State.COOK;
    } else if (!COOKING_ENABLED && rawCount >= TARGET_RAW) {
      state = State.DROP;
    } else {
      state = State.FISH;
    }

    logger.info("State: {} | Raw: {} | Logs: {} | Cooked: {} | Stuck: {}",
        state, rawCount, hasLogs, hasCooked, stuckCounter);

    switch (state) {
      case FISH -> fish(rawCount);
      case CHOP -> chop(rawCount);
      case LIGHT_FIRE -> lightFire();
      case COOK -> cook(rawCount);
      case DROP -> drop();
    }
  }

  // ======================== FISH ========================

  private void fish(int rawBefore) {
    if (!isColourVisible(FISHING_SPOT_COLOUR)) {
      logger.info("Fishing spot not visible, walking.");
      walkTo(FISHING_TILE);
      stuckCounter++;
      return;
    }

    if (!MovingObject.clickMovingObjectByColourObjUntilRedClick(FISHING_SPOT_COLOUR, this)) {
      logger.warn("Failed to click fishing spot.");
      stuckCounter++;
      return;
    }

    Idler.waitUntilIdle(this, 120);

    // Check progress
    if (countItem(RAW_SHRIMP) > rawBefore) {
      stuckCounter = 0;
    } else {
      stuckCounter++;
    }
  }

  // ======================== CHOP ========================

  private void chop(int rawCount) {
    if (hasItem(LOGS)) {
      stuckCounter = 0;
      return;
    }

    // Need empty slot — if rawCount(26) + tinderbox(1) + net(1) = 28, drop one cooked/burnt
    if (rawCount + 2 >= 28) {
      dropOneCooked();
      return;
    }

    if (!isColourVisible(TREE_COLOUR)) {
      logger.info("Tree not visible, walking.");
      walkTo(TREE_TILE);
      stuckCounter++;
      return;
    }

    Point treeLoc = getColourClickPoint(TREE_COLOUR);
    if (treeLoc == null) {
      logger.warn("Could not get tree click point.");
      stuckCounter++;
      return;
    }
    controller().mouse().moveTo(treeLoc, "medium");
    controller().mouse().leftClick();

    Idler.waitUntilIdle(this, 15);

    if (hasItem(LOGS)) {
      stuckCounter = 0;
    } else {
      stuckCounter++;
    }
  }

  // ======================== LIGHT FIRE ========================

  private void lightFire() {
    if (!hasItem(LOGS)) {
      stuckCounter++;
      return;
    }

    if (isColourVisible(FIRE_COLOUR)) {
      stuckCounter = 0;
      return;
    }

    clickInventoryItem(TINDERBOX);
    waitMillis(HumanBehavior.adjustDelay(200, 400));
    clickInventoryItem(LOGS);
    waitMillis(HumanBehavior.adjustDelay(3000, 5000));

    // Wait for fire to appear
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(FIRE_TIMEOUT_SECONDS);
    while (!isColourVisible(FIRE_COLOUR) && LocalDateTime.now().isBefore(deadline)) {
      waitMillis(500);
    }

    if (isColourVisible(FIRE_COLOUR)) {
      stuckCounter = 0;
    } else {
      // "Can't light fire here" — walk a tile in a random direction and retry
      logger.warn("Fire failed, moving a tile.");
      String[] dirs = {"up", "down", "left", "right"};
      String dir = dirs[ThreadLocalRandom.current().nextInt(4)];
      controller().keyboard().sendArrowKey(401, dir);
      waitMillis(HumanBehavior.adjustDelay(80, 120));
      controller().keyboard().sendArrowKey(402, dir);
      waitMillis(HumanBehavior.adjustDelay(1000, 1500));
      stuckCounter++;
    }
  }

  // ======================== COOK ========================

  private void cook(int rawBefore) {
    if (!hasItem(RAW_SHRIMP)) {
      stuckCounter = 0;
      return;
    }

    if (!isColourVisible(FIRE_COLOUR)) {
      logger.info("Fire not visible, need to re-light.");
      stuckCounter++;
      return;
    }

    // Use raw shrimp on fire
    clickInventoryItem(RAW_SHRIMP);
    waitMillis(HumanBehavior.adjustDelay(200, 400));

    Point fireLoc = getColourClickPoint(FIRE_COLOUR);
    if (fireLoc == null) {
      logger.warn("Could not get fire click point.");
      stuckCounter++;
      return;
    }
    controller().mouse().moveTo(fireLoc, "medium");
    controller().mouse().leftClick();

    // Wait for cook dialog then press space
    waitMillis(HumanBehavior.adjustDelay(1500, 2500));
    pressSpace();

    // Quick check — if idle returns almost instantly with no change, dialog didn't open
    boolean quickIdle = Idler.waitUntilIdle(this, 5);
    if (quickIdle && countItem(RAW_SHRIMP) == rawBefore) {
      logger.warn("Cook dialog may not have opened, retrying.");
      stuckCounter++;
      return;
    }

    // Wait for full cook
    Idler.waitUntilIdle(this, 90);
    stuckCounter = 0; // progress was made even if fire died mid-cook
  }

  // ======================== DROP ========================

  private void drop() {
    int tinderboxSlot = findItemSlot(TINDERBOX);
    int netSlot = findItemSlot(NET);

    int[] exclude;
    if (tinderboxSlot >= 0 && netSlot >= 0) {
      exclude = new int[]{tinderboxSlot, netSlot};
    } else if (tinderboxSlot >= 0) {
      exclude = new int[]{tinderboxSlot};
    } else if (netSlot >= 0) {
      exclude = new int[]{netSlot};
    } else {
      exclude = new int[0];
    }

    ItemDropper.dropAll(this, ItemDropper.DropPattern.ZIGZAG, exclude);
    waitMillis(HumanBehavior.adjustDelay(300, 500));
    stuckCounter = 0;
    logger.info("Dropped all fish.");
  }

  // ======================== INVENTORY UTILITIES ========================

  private boolean hasItem(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, INV_THRESHOLD).success()) {
        return true;
      }
    }
    return false;
  }

  private int countItem(String templatePath) {
    int count = 0;
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, INV_THRESHOLD).success()) {
        count++;
      }
    }
    return count;
  }

  private int findItemSlot(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, INV_THRESHOLD).success()) {
        return i;
      }
    }
    return -1;
  }

  private void clickInventoryItem(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, INV_THRESHOLD).success()) {
        Point clickLoc = ClickDistribution.generateRandomPoint(slot);
        controller().mouse().moveTo(clickLoc, "medium");
        controller().mouse().leftClick();
        return;
      }
    }
    logger.warn("Item not found in inventory: {}", templatePath);
  }

  private void dropOneCooked() {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(COOKED_SHRIMP, slotImg, INV_THRESHOLD).success()
          || TemplateMatching.match(BURNT_SHRIMP, slotImg, INV_THRESHOLD).success()) {
        controller().keyboard().sendModifierKey(401, "shift");
        waitRandomMillis(80, 150);
        Point clickLoc = ClickDistribution.generateRandomPoint(slot);
        controller().mouse().moveTo(clickLoc, "fast");
        controller().mouse().leftClick();
        waitRandomMillis(80, 150);
        controller().keyboard().sendModifierKey(402, "shift");
        return;
      }
    }
  }

  // ======================== COLOUR UTILITIES ========================

  /** Checks colour visibility. Always releases ChromaObj Mats. */
  private boolean isColourVisible(ColourObj colour) {
    BufferedImage gameView = controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
    boolean found = !objs.isEmpty();
    for (ChromaObj obj : objs) {
      obj.release();
    }
    return found;
  }

  /**
   * Gets a click point via ColourContours + ClickDistribution.
   * Avoids PointSelector to prevent OpenCV crash on malformed contours.
   */
  private Point getColourClickPoint(ColourObj colour) {
    BufferedImage gameView = controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
    if (objs.isEmpty()) {
      return null;
    }
    try {
      ChromaObj closest = ColourContours.getChromaObjClosestToCentre(objs);
      return ClickDistribution.generateRandomPoint(closest.boundingBox());
    } catch (Exception e) {
      logger.warn("Failed to generate click point: {}", e.getMessage());
      return null;
    } finally {
      for (ChromaObj obj : objs) {
        obj.release();
      }
    }
  }

  // ======================== NAVIGATION ========================

  /** Catches Exception broadly to handle OCR failures in walker. */
  private void walkTo(Point tile) {
    try {
      controller().walker().pathTo(tile, false);
      waitMillis(HumanBehavior.adjustDelay(3000, 5000));
    } catch (InterruptedException e) {
      logger.error("Walker interrupted.");
      stop();
    } catch (Exception e) {
      logger.error("Walker error: {}", e.getMessage());
    }
  }

  // ======================== INPUT HELPERS ========================

  private void pressSpace() {
    controller().keyboard().sendModifierKey(401, "space");
    waitMillis(HumanBehavior.adjustDelay(80, 120));
    controller().keyboard().sendModifierKey(402, "space");
  }

  private void pressLogout() {
    Rectangle logoutTab = controller().zones().getCtrlPanel().get("logoutTab");
    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(logoutTab), "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(400, 600));
    // Click the logout button within the panel
    Rectangle panel = controller().zones().getCtrlPanel().get("inventoryPanel");
    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(panel), "medium");
    controller().mouse().leftClick();
  }
}
