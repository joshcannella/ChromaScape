package com.chromascape.scripts;

import com.chromascape.api.DiscordNotification;
import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.IdleType;
import com.chromascape.utils.actions.Minimap;
import com.chromascape.utils.actions.MovingObject;
import com.chromascape.utils.actions.custom.ColourClick;
import com.chromascape.utils.actions.custom.Combat;
import com.chromascape.utils.actions.custom.HumanBehavior;
import com.chromascape.utils.actions.custom.Inventory;
import com.chromascape.utils.actions.custom.Walk;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.domain.ocr.Ocr;
import com.chromascape.utils.domain.walker.Tile;
import java.awt.Point;
import java.awt.Rectangle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Kills goblins east of Lumbridge castle. Eats food when HP is low and recovers from death by
 * walking back from the Lumbridge respawn point.
 *
 * <p><b>Prerequisites:</b> Any melee weapon equipped, food in inventory.
 * <p><b>RuneLite Setup:</b> NPC Indicators — highlight Goblin in cyan. Idle Notifier — enabled
 * (combat idle detection). Ground Items — highlight loot in purple (optional).
 * <p><b>Image Templates:</b> Trout.png (or whichever food you bring)
 */
public class LumbridgeGoblinScript extends BaseScript {

  private static final Logger logger = LogManager.getLogger(LumbridgeGoblinScript.class);

  // === Colour Definitions ===
  // Highlight goblins cyan via RuneLite NPC Indicators
  private static final ColourObj GOBLIN_COLOUR =
      new ColourObj("cyan", new Scalar(90, 254, 254, 0), new Scalar(91, 255, 255, 0));

  // === Food ===
  private static final String FOOD_IMAGE = "/images/user/Shrimps.png";
  private static final double FOOD_THRESHOLD = 0.07;
  private static final int EAT_AT_HP = 5;

  // === Chat ===
  // Black text for "I'm already under attack." / "Someone else is fighting that."
  private static final ColourObj CHAT_BLACK =
      new ColourObj("black", new Scalar(0, 0, 0, 0), new Scalar(0, 0, 0, 0));

  // === Combat ===
  private static final int KILL_TIMEOUT_SECONDS = 20;
  private static final int MAX_ENGAGE_ATTEMPTS = 3;
  private static final int MAX_IDLE_CHECKS = 3; // stop waiting after this many position-unchanged checks

  // === Tiles ===
  // Goblin area east of Lumbridge castle, across the bridge
  private static final Point GOBLIN_AREA = new Point(3259, 3228);
  // Lumbridge respawn (death spawn point)
  private static final Point LUMBRIDGE_SPAWN = new Point(3222, 3218);
  private static final int DEATH_DETECT_RADIUS = 5;

  // === Stuck Detection ===
  private static final int MAX_STUCK_CYCLES = 10;
  private int stuckCounter = 0;
  private boolean inCombat = false;
  private boolean seeded = false;

  @Override
  protected void cycle() {
    if (!seeded) {
      Idler.seedLastMessage(this);
      seeded = true;
    }
    if (HumanBehavior.runPreCycleChecks(this)) return;

    // Check if we just left combat via health bar + idle detection
    if (inCombat) {
      if (Combat.isInCombat(this)) return; // health bar still up, skip cycle
      IdleType idleType = Idler.waitUntilIdleType(this, 1);
      if (idleType != IdleType.TIMEOUT) {
        logger.info("Combat ended ({})", idleType);
        inCombat = false;
      } else {
        return;
      }
    }

    // Eat if HP is low
    if (shouldEat()) {
      if (!eatFood()) {
        logger.warn("Out of food, logging out.");
        DiscordNotification.send("Goblin Killer: out of food, logging out.");
        stop();
        return;
      }
      return;
    }

    // Check if goblins are visible — if not, we may have died or wandered off
    if (!ColourClick.isVisible(this, GOBLIN_COLOUR)) {
      stuckCounter++;
      logger.warn("No goblin found ({}/{})", stuckCounter, MAX_STUCK_CYCLES);
      if (stuckCounter >= MAX_STUCK_CYCLES) {
        logger.error("Stuck for {} cycles, logging out.", MAX_STUCK_CYCLES);
        DiscordNotification.send("Goblin Killer: stuck, logging out.");
        stop();
        return;
      }
      recoverToGoblins();
      return;
    }

    // Snapshot XP before engaging
    int previousXp;
    try {
      previousXp = Minimap.getXp(this);
    } catch (Exception e) {
      logger.warn("XP read failed, skipping cycle");
      return;
    }

    // Try to engage a goblin
    for (int attempt = 0; attempt < MAX_ENGAGE_ATTEMPTS; attempt++) {
      if (!MovingObject.clickMovingObjectByColourObjUntilRedClick(GOBLIN_COLOUR, this)) {
        logger.warn("Failed to red-click goblin (attempt {})", attempt + 1);
        continue;
      }
      if (waitForFirstHit(previousXp)) {
        stuckCounter = 0;
        inCombat = true;
        waitForKill(previousXp);
        return;
      }
      // Check if we're already fighting something
      String blackMsg = getLatestBlackChat();
      if (blackMsg.contains("alreadyunder")) {
        logger.info("Already in combat, waiting for kill");
        stuckCounter = 0;
        inCombat = true;
        waitForKill(previousXp);
        return;
      }
      if (blackMsg.contains("someoneelse")) {
        logger.info("Goblin taken by another player, trying next");
        continue;
      }
      logger.warn("Goblin unreachable (attempt {}), trying another", attempt + 1);
    }

    stuckCounter++;
  }

  // === Eating ===

  private boolean shouldEat() {
    try {
      int hp = Minimap.getHp(this);
      return hp != -1 && hp <= EAT_AT_HP;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean eatFood() {
    if (!Inventory.hasItem(this, FOOD_IMAGE, FOOD_THRESHOLD)) {
      return false;
    }
    Inventory.clickItem(this, FOOD_IMAGE, FOOD_THRESHOLD, "fast");
    Idler.waitUntilIdle(this, 3);
    return true;
  }

  // === Combat ===

  /**
   * Waits for the first XP gain after clicking a goblin. Keeps waiting while the player is still
   * moving (pathing to the target). Only gives up after the player has been stationary for
   * MAX_IDLE_CHECKS consecutive polls with no XP gain.
   */
  private boolean waitForFirstHit(int previousXp) {
    int idleCount = 0;
    Tile lastPos = null;
    while (idleCount < MAX_IDLE_CHECKS) {
      try {
        if (Minimap.getXp(this) > previousXp) return true;
      } catch (Exception ignored) {}
      if (Combat.isInCombat(this)) return true;
      try {
        Tile pos = controller().walker().getPlayerPosition();
        if (lastPos != null && pos.x() == lastPos.x() && pos.y() == lastPos.y()) {
          idleCount++;
        } else {
          idleCount = 0;
        }
        lastPos = pos;
      } catch (Exception e) {
        idleCount++;
      }
      waitMillis(600);
    }
    return false;
  }

  private void waitForKill(int previousXp) {
    // Eat before blocking on Idler
    if (shouldEat()) eatFood();

    // Loop until combat actually ends or timeout
    while (true) {
      IdleType idleType = Idler.waitUntilIdleType(this, KILL_TIMEOUT_SECONDS);

      switch (idleType) {
        case COMBAT -> {
          inCombat = false;
          if (isNearSpawn()) {
            logger.warn("Died — respawned at Lumbridge");
            recoverToGoblins();
          } else {
            logger.info("Kill confirmed (combat idle)");
          }
          HumanBehavior.sleep(600, 900);
          return;
        }
        case TIMEOUT -> {
          inCombat = false;
          logger.info("Kill timeout — retrying");
          return;
        }
        case ANIMATION -> {
          if (!Combat.isInCombat(this)) { inCombat = false; logger.info("Kill confirmed (health bar gone)"); HumanBehavior.sleep(600, 900); return; }
          logger.debug("Animation idle mid-combat, continuing to wait");
          if (shouldEat()) eatFood();
        }
        case MOVEMENT -> {
          if (!Combat.isInCombat(this)) { inCombat = false; logger.info("Kill confirmed (health bar gone)"); HumanBehavior.sleep(600, 900); return; }
          logger.debug("Movement idle mid-combat, continuing to wait");
        }
      }
    }
  }

  // === Recovery ===

  /**
   * Reads the latest chat message for black game text (e.g. "already under attack",
   * "someone else is fighting that").
   */
  private String getLatestBlackChat() {
    Rectangle latestMsg = controller().zones().getChatTabs().get("Latest Message");
    if (latestMsg == null) return "";
    return Ocr.extractText(latestMsg, "Plain 12", CHAT_BLACK, true).toLowerCase();
  }

  /**
   * Returns true if the player is near the Lumbridge respawn point (likely died).
   */
  private boolean isNearSpawn() {
    try {
      Tile pos = controller().walker().getPlayerPosition();
      return Math.abs(pos.x() - LUMBRIDGE_SPAWN.x) <= DEATH_DETECT_RADIUS
          && Math.abs(pos.y() - LUMBRIDGE_SPAWN.y) <= DEATH_DETECT_RADIUS;
    } catch (Exception e) {
      return false;
    }
  }

  private void recoverToGoblins() {
    waitRandomMillis(600, 800);
    if (ColourClick.isVisible(this, GOBLIN_COLOUR)) return;

    logger.info("Walking back to goblin area");
    Walk.to(this, GOBLIN_AREA, "goblin area");
    Idler.waitUntilIdle(this, 30);
  }
}
