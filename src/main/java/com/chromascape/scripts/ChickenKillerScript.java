package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.Minimap;
import com.chromascape.utils.actions.MovingObject;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.actions.custom.HumanBehavior;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Kills chickens at the Lumbridge chicken coop and loots feathers. Rotates attack styles
 * (Strength → Attack → Defence) based on levels gained.
 *
 * <p><b>RuneLite Setup:</b>
 * <ul>
 *   <li>NPC Indicators — highlight "Chicken" in cyan (HSV ~90, 254-255, 254-255)</li>
 *   <li>Ground Items — highlight "Feather" in purple (HSV ~140, 237-255, 205-255)</li>
 *   <li>XP bar set to permanent (required for XP-based kill detection)</li>
 *   <li>Windows Display Scaling: 100%</li>
 *   <li>RuneScape UI: "Fixed - Classic"</li>
 *   <li>Display Brightness: middle (50%)</li>
 *   <li>ChromaScape RuneLite Profile: activated</li>
 * </ul>
 */
@ScriptVersion(major = 0, minor = 1)
public class ChickenKillerScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(ChickenKillerScript.class);

  // === Colour Definitions ===
  private static final ColourObj CHICKEN_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));
  private static final ColourObj LOOT_COLOUR =
      new ColourObj("purple", new Scalar(139, 200, 200, 0), new Scalar(141, 255, 255, 0));

  // === Configuration ===
  // NOTE: Looting is not fully working — keep this false for now
  private static final boolean LOOT_FEATHERS = false;

  // === Walker ===
  private static final Point COOP_GATE = new Point(3237, 3295);
  private static final Point COOP_CENTER = new Point(3235, 3295);

  // === Timeouts ===
  private static final int KILL_TIMEOUT_SECONDS = 15;
  private static final int CHICKEN_XP = 12; // 3 HP × 4 XP per damage
  private static final int MAX_COMBAT_FAILURES = 10;
  private int combatFailures = 0;

  // === Attack Style Rotation ===
  private static final int LEVELS_PER_ROTATION_STR = 2;
  private static final int LEVELS_PER_ROTATION_ATT = 1;
  private static final int LEVELS_PER_ROTATION_DEF = 1;

  private enum AttackStyle { STRENGTH, ATTACK, DEFENCE }
  private AttackStyle currentStyle = AttackStyle.STRENGTH;
  private int levelsGainedThisRotation = 0;

  @Override
  protected void cycle() {
    if (HumanBehavior.runPreCycleChecks(this)) return;

    fight();
  }

  private static final int MAX_ENGAGE_ATTEMPTS = 3;
  private static final int ENGAGE_TIMEOUT_MS = 2000;

  private void fight() {
    if (!ColourClick.isVisible(this, CHICKEN_COLOUR)) {
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

    int previousXp;
    try {
      previousXp = Minimap.getXp(this);
    } catch (Exception e) {
      logger.warn("XP read failed: {}", e.getMessage());
      return;
    }
    logger.info("XP snapshot: {}", previousXp);

    // Try clicking chickens until one is actually engaged (first XP hit)
    for (int attempt = 0; attempt < MAX_ENGAGE_ATTEMPTS; attempt++) {
      if (!MovingObject.clickMovingObjectByColourObjUntilRedClick(CHICKEN_COLOUR, this)) {
        logger.warn("Failed to red-click chicken (attempt {})", attempt + 1);
        continue;
      }

      // Wait up to 2s for first XP gain to confirm combat engagement
      if (waitForFirstHit(previousXp)) {
        logger.info("In combat, waiting for kill...");
        combatFailures = 0;
        waitForKill(previousXp);
        return;
      }
      logger.warn("Chicken unreachable (attempt {}), trying another", attempt + 1);
    }

    logger.warn("Could not engage any chicken after {} attempts", MAX_ENGAGE_ATTEMPTS);
    combatFailures++;
  }

  private boolean waitForFirstHit(int previousXp) {
    LocalDateTime deadline = LocalDateTime.now().plusNanos(ENGAGE_TIMEOUT_MS * 1_000_000L);
    while (LocalDateTime.now().isBefore(deadline)) {
      try {
        if (Minimap.getXp(this) > previousXp) {
          return true;
        }
      } catch (Exception ignored) {}
      waitMillis(200);
    }
    return false;
  }

  private void waitForKill(int previousXp) {
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(KILL_TIMEOUT_SECONDS);
    while (LocalDateTime.now().isBefore(deadline)) {
      try {
        int delta = Minimap.getXp(this) - previousXp;
        if (delta >= CHICKEN_XP && delta < 100) {
          logger.info("Kill confirmed via XP (+{})", delta);
          HumanBehavior.sleep(600, 900);
          if (LOOT_FEATHERS) {
            Point lootLoc = findNearestLoot();
            if (lootLoc != null) {
              controller().mouse().moveTo(lootLoc, "fast");
              controller().mouse().leftClick();
              logger.info("Clicked feather loot at {}", lootLoc);
              HumanBehavior.sleep(300, 500);
            }
          }
          checkStyleRotation();
          return;
        }
      } catch (Exception ignored) {}
      waitMillis(300);
    }
    logger.info("Kill timeout — retrying");
  }

  // === Colour Utilities ===

  private Point findNearestLoot() {
    BufferedImage gameView = controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, LOOT_COLOUR);
    if (objs.isEmpty()) {
      return null;
    }
    try {
      ChromaObj nearest = ColourContours.getChromaObjClosestToCentre(objs);
      return ClickDistribution.generateRandomPoint(nearest.boundingBox(), 15.0);
    } catch (Exception e) {
      logger.warn("Failed to generate loot point: {}", e.getMessage());
      return null;
    } finally {
      for (ChromaObj obj : objs) {
        obj.release();
      }
    }
  }

  // === Recovery ===

  private void recoverToCoop() {
    try {
      // Walk to gate, click it to open if closed, then walk inside
      logger.info("Walking to coop gate");
      controller().walker().pathTo(COOP_GATE, false);
      HumanBehavior.sleep(1000, 1500);
      // Click center to interact with gate
      BufferedImage gameView = controller().zones().getGameView();
      Point center = new Point(gameView.getWidth() / 2, gameView.getHeight() / 2);
      controller().mouse().moveTo(center, "medium");
      controller().mouse().leftClick();
      HumanBehavior.sleep(1500, 2500);
      // Walk inside the coop
      logger.info("Walking into coop");
      controller().walker().pathTo(COOP_CENTER, false);
      HumanBehavior.sleep(2000, 3000);
    } catch (InterruptedException e) {
      logger.error("Walker interrupted");
      stop();
    } catch (Exception e) {
      logger.warn("Recovery error: {}", e.getMessage());
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
    HumanBehavior.sleep(400, 600);

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
    HumanBehavior.sleep(300, 500);

    Rectangle invTab = controller().zones().getCtrlPanel().get("inventoryTab");
    controller().mouse().moveTo(ClickDistribution.generateRandomPoint(invTab), "medium");
    controller().mouse().leftClick();
    HumanBehavior.sleep(200, 400);

    logger.info("Switched to {} style", style);
  }

  public void onLevelUp() {
    levelsGainedThisRotation++;
    logger.info("Level gained! ({}/{} for {} rotation)", levelsGainedThisRotation,
        getRequiredLevels(currentStyle), currentStyle);
  }
}
