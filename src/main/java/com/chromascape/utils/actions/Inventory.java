package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common inventory operations: checking, counting, finding, and clicking items by template.
 *
 * <p><b>Usage:</b>
 * <pre>
 * if (Inventory.hasItem(this, ITEM_IMAGE, 0.07)) { ... }
 * int count = Inventory.countItem(this, ITEM_IMAGE, 0.07);
 * Inventory.clickItem(this, ITEM_IMAGE, 0.07, "medium");
 * </pre>
 */
public class Inventory {

  private static final Logger logger = LogManager.getLogger(Inventory.class);
  private static final int SLOTS = 28;

  /**
   * Checks if an item is present anywhere in the inventory.
   *
   * @param base the active script instance
   * @param templatePath classpath path to the item image
   * @param threshold matching threshold (0.05–0.15)
   * @return true if found in any slot
   */
  public static boolean hasItem(BaseScript base, String templatePath, double threshold) {
    return findItemSlot(base, templatePath, threshold) >= 0;
  }

  /**
   * Counts how many inventory slots contain the given item.
   *
   * @param base the active script instance
   * @param templatePath classpath path to the item image
   * @param threshold matching threshold
   * @return number of matching slots (0–28)
   */
  public static int countItem(BaseScript base, String templatePath, double threshold) {
    int count = 0;
    for (int i = 0; i < SLOTS; i++) {
      Rectangle slot = base.controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, threshold).success()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Finds the first inventory slot containing the given item.
   *
   * @param base the active script instance
   * @param templatePath classpath path to the item image
   * @param threshold matching threshold
   * @return slot index (0–27), or -1 if not found
   */
  public static int findItemSlot(BaseScript base, String templatePath, double threshold) {
    for (int i = 0; i < SLOTS; i++) {
      Rectangle slot = base.controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      if (TemplateMatching.match(templatePath, slotImg, threshold).success()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Clicks the first inventory slot containing the given item.
   *
   * @param base the active script instance
   * @param templatePath classpath path to the item image
   * @param threshold matching threshold
   * @param speed mouse speed ("slow", "medium", "fast")
   * @return true if item was found and clicked, false otherwise
   */
  public static boolean clickItem(BaseScript base, String templatePath, double threshold,
      String speed) {
    int slot = findItemSlot(base, templatePath, threshold);
    if (slot < 0) {
      logger.warn("Item not found in inventory: {}", templatePath);
      return false;
    }
    Rectangle slotRect = base.controller().zones().getInventorySlots().get(slot);
    Point clickLoc = ClickDistribution.generateRandomPoint(slotRect);
    base.controller().mouse().moveTo(clickLoc, speed);
    base.controller().mouse().leftClick();
    return true;
  }

  /**
   * Checks if the inventory is full (all 28 slots occupied).
   * Tests each slot for non-empty content by checking if any pixel data differs from empty.
   *
   * @param base the active script instance
   * @param knownItems template paths of all items that could be in inventory
   * @param threshold matching threshold
   * @return true if all 28 slots contain a known item
   */
  public static boolean isFull(BaseScript base, String[] knownItems, double threshold) {
    for (int i = 0; i < SLOTS; i++) {
      Rectangle slot = base.controller().zones().getInventorySlots().get(i);
      BufferedImage slotImg = ScreenManager.captureZone(slot);
      boolean occupied = false;
      for (String item : knownItems) {
        if (TemplateMatching.match(item, slotImg, threshold).success()) {
          occupied = true;
          break;
        }
      }
      if (!occupied) {
        return false;
      }
    }
    return true;
  }
}
