package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Minimap;
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
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Kills chickens at the Lumbridge chicken coop, loots feathers and bones, and buries bones for
 * Prayer XP. Rotates attack styles (Strength → Attack → Defence) based on levels gained.
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>NPC Indicators — highlight "Chicken" in cyan (HSV ~90, 254-255, 254-255)</li>
 *   <li>Ground Items — highlight "Feather" and "Bones" in purple (HSV ~150, 254-255, 254-255)</li>
 *   <li>XP bar set to permanent (required for XP-based kill detection)</li>
 *   <li>Windows Display Scaling: 100%</li>
 *   <li>RuneScape UI: "Fixed - Classic"</li>
 *   <li>Display Brightness: middle (50%)</li>
 *   <li>ChromaScape RuneLite Profile: activated</li>
 * </ul>
 */
public class ChickenKillerScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(ChickenKillerScript.class);

  // === Image Templates (inventory only) ===
  private static final String BONES_IMAGE = "/images/user/Bones.png";

  // === Colour Definitions ===
  private static final ColourObj CHICKEN_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj LOOT_COLOUR =
      new ColourObj("purple", new Scalar(140, 237, 205, 0), new Scalar(142, 255, 255, 0));

  private static final int BONES_BURY_THRESHOLD = 20;
  private static final int LOOT_EVERY_N_KILLS = 5;
  private static final double INVENTORY_THRESHOLD = 0.07;

  // === Walker ===
  private static final Point COOP_CENTER = new Point(3235, 3295);

  // === Timeouts ===
  private static final int KILL_TIMEOUT_SECONDS = 15;
  private static final int LOOT_APPEAR_TIMEOUT_SECONDS = 3;
  private static final int CHICKEN_XP = 12; // 3 HP × 4 XP per damage
  private static final int MAX_COMBAT_FAILURES = 10;
  private int combatFailures = 0;
  private int killsSinceLoot = 0;

  // === Attack Style Rotation ===
  private static final int LEVELS_PER_ROTATION_STR = 2;
  private static final int LEVELS_PER_ROTATION_ATT = 1;
  private static final int LEVELS_PER_ROTATION_DEF = 1;

  private enum AttackStyle { STRENGTH, ATTACK, DEFENCE }
  private AttackStyle currentStyle = AttackStyle.STRENGTH;
  private int levelsGainedThisRotation = 0;

  // === State Machine ===
  private enum State { FIGHT, LOOT, BURY_BONES }
  private State state = State.FIGHT;

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

    dismissLevelUp();

    // If we have enough bones, bury them
    if (countBones() >= BONES_BURY_THRESHOLD) {
      state = State.BURY_BONES;
    }

    logger.info("State: {}", state);

    switch (state) {
      case FIGHT -> fight();
      case LOOT -> loot();
      case BURY_BONES -> buryBones();
    }
  }

  /**
   * Attacks a chicken using MovingObject (red-click verified), then waits for XP change
   * to confirm the kill. This is the same proven pattern used by the agility script.
   */
  private void fight() {
    // Check if a chicken is visible
    if (!isColourVisible(CHICKEN_COLOUR)) {
      logger.warn("No chicken found, failure {}/{}", combatFailures + 1, MAX_COMBAT_FAILURES);
      combatFailures++;
      if (combatFailures >= MAX_COMBAT_FAILURES) {
        logger.error("Failed to find chickens {} times, logging out.", MAX_COMBAT_FAILURES);
        DiscordNotification.send("Chicken Killer: can't find chickens, logging out.");
        stop();
        return;
      }
      recoverToCoop();
      return;
    }

    // Click the chicken — MovingObject handles retries and red-click verification
    int previousXp = -1;
    try {
      previousXp = Minimap.getXp(this);
    } catch (Exception e) {
      logger.warn("Could not read XP, retrying next cycle");
      return;
    }
    if (!MovingObject.clickMovingObjectByColourObjUntilRedClick(CHICKEN_COLOUR, this)) {
      logger.warn("Failed to red-click chicken");
      combatFailures++;
      return;
    }

    combatFailures = 0;
    logger.info("Attacked chicken, waiting for kill...");

    // Wait until we gain at least 12 XP (one chicken kill: 3 HP × 4 XP)
    // This avoids false positives from other players' loot already on the ground
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(KILL_TIMEOUT_SECONDS);
    while (LocalDateTime.now().isBefore(deadline)) {
      try {
        int currentXp = Minimap.getXp(this);
        if (currentXp - previousXp >= CHICKEN_XP) {
          logger.info("Kill confirmed via XP (+{})", currentXp - previousXp);
          killsSinceLoot++;
          // Wait for death animation to finish before next action
          waitMillis(HumanBehavior.adjustDelay(600, 900));
          if (killsSinceLoot >= LOOT_EVERY_N_KILLS) {
            state = State.LOOT;
            killsSinceLoot = 0;
          }
          return;
        }
      } catch (Exception e) {
        // OCR misread — keep polling
      }
      waitMillis(300);
    }

    // Timed out — chicken was stolen or we failed, try again
    logger.info("Kill timeout — retrying");
  }

  private void loot() {
    // Wait briefly for loot highlight to appear if not visible yet
    if (!isColourVisible(LOOT_COLOUR)) {
      LocalDateTime deadline = LocalDateTime.now().plusSeconds(LOOT_APPEAR_TIMEOUT_SECONDS);
      while (!isColourVisible(LOOT_COLOUR) && LocalDateTime.now().isBefore(deadline)) {
        waitMillis(300);
      }
    }

    Point lootLoc = findGroundItemByColour(LOOT_COLOUR);
    if (lootLoc != null) {
      controller().mouse().moveTo(lootLoc, "medium");
      controller().mouse().leftClick();
      waitMillis(HumanBehavior.adjustDelay(800, 1200));
      // Stay in LOOT to pick up remaining items
      return;
    }

    // Nothing left — bury bones only if inventory is full
    if (countBones() >= BONES_BURY_THRESHOLD) {
      state = State.BURY_BONES;
    } else {
      checkStyleRotation();
      state = State.FIGHT;
    }
  }

  private void buryBones() {
    // Bury all bones in inventory
    while (hasItem(BONES_IMAGE)) {
      clickInventoryItem(BONES_IMAGE);
      waitMillis(HumanBehavior.adjustDelay(1200, 1600));
    }
    checkStyleRotation();
    state = State.FIGHT;
  }

  // === Colour Utilities ===

  private boolean isColourVisible(ColourObj colour) {
    BufferedImage gameView = controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
    boolean found = !objs.isEmpty();
    for (ChromaObj obj : objs) {
      obj.release();
    }
    return found;
  }

  private Point findGroundItemByColour(ColourObj colour) {
    BufferedImage gameView = controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
    if (objs.isEmpty()) {
      return null;
    }
    try {
      Rectangle box = ColourContours.getChromaObjClosestToCentre(objs).boundingBox();
      // Click near center with small random offset to stay on the tile
      int cx = box.x + box.width / 2 + ThreadLocalRandom.current().nextInt(-2, 3);
      int cy = box.y + box.height / 2 + ThreadLocalRandom.current().nextInt(-2, 3);
      return new Point(cx, cy);
    } catch (Exception e) {
      logger.warn("Failed to generate point for ground item: {}", e.getMessage());
      return null;
    } finally {
      for (ChromaObj obj : objs) {
        obj.release();
      }
    }
  }

  // === Inventory Utilities ===

  private int countBones() {
    int count = 0;
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(BONES_IMAGE, slotImg, INVENTORY_THRESHOLD).success()) {
        count++;
      }
    }
    return count;
  }

  private boolean hasItem(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, INVENTORY_THRESHOLD).success()) {
        return true;
      }
    }
    return false;
  }

  private void clickInventoryItem(String templatePath) {
    for (int i = 0; i < 28; i++) {
      Rectangle slot = controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, INVENTORY_THRESHOLD).success()) {
        Point clickLoc = ClickDistribution.generateRandomPoint(slot);
        controller().mouse().moveTo(clickLoc, "medium");
        controller().mouse().leftClick();
        return;
      }
    }
    logger.warn("Item not found in inventory: {}", templatePath);
  }

  // === Recovery ===

  private void recoverToCoop() {
    try {
      logger.info("Walking back to chicken coop");
      controller().walker().pathTo(COOP_CENTER, false);
      waitMillis(HumanBehavior.adjustDelay(3000, 5000));
    } catch (IOException e) {
      logger.error("Walker error: {}", e.getMessage());
    } catch (InterruptedException e) {
      logger.error("Walker interrupted");
      stop();
    }
  }

  // === Attack Style Rotation ===

  private void checkStyleRotation() {
    int required = getRequiredLevels(currentStyle);
    if (levelsGainedThisRotation >= required) {
      rotateStyle();
    }
  }

  private int getRequiredLevels(AttackStyle style) {
    return switch (style) {
      case STRENGTH -> LEVELS_PER_ROTATION_STR;
      case ATTACK -> LEVELS_PER_ROTATION_ATT;
      case DEFENCE -> LEVELS_PER_ROTATION_DEF;
    };
  }

  private void rotateStyle() {
    AttackStyle previous = currentStyle;
    currentStyle = switch (currentStyle) {
      case STRENGTH -> AttackStyle.ATTACK;
      case ATTACK -> AttackStyle.DEFENCE;
      case DEFENCE -> AttackStyle.STRENGTH;
    };
    levelsGainedThisRotation = 0;
    logger.info("Rotating attack style: {} → {}", previous, currentStyle);
    switchAttackStyle(currentStyle);
  }

  private void switchAttackStyle(AttackStyle style) {
    Rectangle combatTab = controller().zones().getCtrlPanel().get("combatTab");
    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(combatTab), "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(400, 600));

    Rectangle panel = controller().zones().getCtrlPanel().get("inventoryPanel");
    Rectangle styleBox = switch (style) {
      case ATTACK -> new Rectangle(panel.x, panel.y + 10, panel.width / 2 - 5, 45);
      case STRENGTH -> new Rectangle(panel.x + panel.width / 2 + 5, panel.y + 10,
          panel.width / 2 - 5, 45);
      case DEFENCE -> new Rectangle(panel.x + panel.width / 2 + 5, panel.y + 65,
          panel.width / 2 - 5, 45);
    };

    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(styleBox), "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(300, 500));

    Rectangle invTab = controller().zones().getCtrlPanel().get("inventoryTab");
    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(invTab), "medium");
    controller().mouse().leftClick();
    waitMillis(HumanBehavior.adjustDelay(200, 400));

    logger.info("Switched to {} style", style);
  }

  // === Level-Up Detection ===

  private void dismissLevelUp() {
    controller().keyboard().sendModifierKey(401, "space");
    waitMillis(HumanBehavior.adjustDelay(80, 120));
    controller().keyboard().sendModifierKey(402, "space");
    waitMillis(HumanBehavior.adjustDelay(100, 200));
  }

  public void onLevelUp() {
    levelsGainedThisRotation++;
    logger.info("Level gained! ({}/{} for {} rotation)", levelsGainedThisRotation,
        getRequiredLevels(currentStyle), currentStyle);
  }
}
