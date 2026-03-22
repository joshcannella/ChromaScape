package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Common inventory operations: checking, counting, finding, and clicking items by template.
 *
 * <p><b>Usage:</b>
 * <pre>
 * if (Inventory.hasItem(this, ITEM_IMAGE, 0.07)) { ... }
 * int count = Inventory.countItem(this, ITEM_IMAGE, 0.07);
 * Inventory.clickItem(this, ITEM_IMAGE, 0.07, "medium");
 * Point loc = Inventory.findInGameView(this, ITEM_IMAGE, 0.07);  // bank withdrawals
 * boolean full = Inventory.isFullByChat(this, CHAT_BLACK);
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

  /**
   * Finds an item by template anywhere in the game view (not just inventory slots).
   * Useful for clicking items in the bank interface after deposit-all.
   *
   * @param base the active script instance
   * @param templatePath classpath path to the item image
   * @param threshold matching threshold
   * @return a random point within the matched region, or null if not found
   */
  public static Point findInGameView(BaseScript base, String templatePath, double threshold) {
    BufferedImage gameView = base.controller().zones().getGameView();
    return PointSelector.getRandomPointInImage(templatePath, gameView, threshold);
  }

  // "Click here to continue" blue: RGB(0, 0, 128) → HSV(120, 255, 128)
  private static final ColourObj DIALOG_BLUE =
      new ColourObj("dialogBlue", new Scalar(118, 200, 100, 0), new Scalar(122, 255, 255, 0));

  /**
   * Checks if a dialog is present in the chatbox (blue "Click here to continue" pixels).
   * Detects inventory-full notifications, level-up dialogs, and other game dialogs.
   *
   * @param base the active script instance
   * @return true if a dialog is present in the chat zone
   */
  public static boolean isFullByChat(BaseScript base) {
    Rectangle chat = base.controller().zones().getChatTabs().get("Chat");
    if (chat == null) return false;
    BufferedImage chatImg = ScreenManager.captureZone(chat);
    List<ChromaObj> blueObjs = ColourContours.getChromaObjsInColour(chatImg, DIALOG_BLUE);
    boolean detected = false;
    for (ChromaObj obj : blueObjs) {
      if (obj.boundingBox().width > 50) detected = true;
      obj.release();
    }
    return detected;
  }
}