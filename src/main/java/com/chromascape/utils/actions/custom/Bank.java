package com.chromascape.utils.actions.custom;

import com.chromascape.utils.actions.PointSelector;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common banking operations: open, close, deposit-all.
 *
 * <p><b>Usage:</b>
 * <pre>
 * Bank.open(this, "Cyan");
 * Bank.depositAll(this);
 * Bank.close(this);
 * </pre>
 */
public final class Bank {

  private static final Logger logger = LogManager.getLogger(Bank.class);

  private Bank() {}

  /**
   * Clicks a colour-highlighted bank booth and waits for the interface to open.
   * Stops the script if the bank is not found.
   *
   * @param base the script instance
   * @param colour the RuneLite highlight colour name (e.g. "Cyan")
   */
  public static void open(BaseScript base, String colour) {
    BufferedImage gameView = base.controller().zones().getGameView();
    Point bankLoc = PointSelector.getRandomPointInColour(gameView, colour, 15);
    if (bankLoc == null) {
      logger.error("Bank booth not found");
      base.stop();
      return;
    }
    String speed = HumanBehavior.shouldSlowApproach() ? "slow" : "medium";
    base.controller().mouse().moveTo(bankLoc, speed);
    base.controller().mouse().microJitter();
    base.controller().mouse().leftClick();
    base.waitMillis(HumanBehavior.adjustDelay(1200, 1800));
  }

  /**
   * Deposits all items by right-clicking the first inventory slot and selecting Deposit-All.
   */
  public static void depositAll(BaseScript base) {
    Rectangle firstSlot = base.controller().zones().getInventorySlots().get(0);
    Point slotLoc = ClickDistribution.generateRandomPoint(firstSlot);
    base.controller().mouse().moveTo(slotLoc, "medium");
    base.controller().mouse().rightClick();
    base.waitMillis(HumanBehavior.adjustDelay(400, 600));

    Point depositOption = new Point(slotLoc.x, slotLoc.y + 85);
    base.controller().mouse().moveTo(depositOption, "fast");
    base.controller().mouse().leftClick();
    base.waitMillis(HumanBehavior.adjustDelay(300, 500));
  }

  /**
   * Closes the bank interface by pressing Escape.
   */
  public static void close(BaseScript base) {
    KeyPress.escape(base);
    base.waitMillis(HumanBehavior.adjustDelay(400, 600));
  }
}
