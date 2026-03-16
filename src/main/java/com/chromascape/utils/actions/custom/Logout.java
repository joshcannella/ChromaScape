package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import java.awt.Point;
import java.awt.Rectangle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles logging out via the control panel. Clicks the logout tab, then clicks the logout
 * button within the panel.
 *
 * <p><b>Usage:</b> {@code Logout.perform(this)}
 */
public class Logout {

  private static final Logger logger = LogManager.getLogger(Logout.class);

  /**
   * Logs the player out by clicking the logout tab and then the logout button.
   *
   * @param base the active {@link BaseScript} instance
   */
  public static void perform(BaseScript base) {
    // Click the logout tab
    Rectangle logoutTab = base.controller().zones().getCtrlPanel().get("logoutTab");
    if (logoutTab == null) {
      logger.error("Logout tab zone not found");
      return;
    }
    base.controller().mouse().moveTo(ClickDistribution.generateRandomPoint(logoutTab), "medium");
    base.controller().mouse().leftClick();
    BaseScript.waitRandomMillis(400, 600);

    // The logout button sits in the upper-center of the panel content area
    Rectangle panel = base.controller().zones().getCtrlPanel().get("inventoryPanel");
    if (panel == null) {
      logger.error("Panel zone not found");
      return;
    }
    int btnX = panel.x + panel.width / 2 - 40;
    int btnY = panel.y + panel.height / 2 - 20;
    Rectangle logoutBtn = new Rectangle(btnX, btnY, 80, 30);
    base.controller().mouse().moveTo(ClickDistribution.generateRandomPoint(logoutBtn), "medium");
    base.controller().mouse().leftClick();
    logger.info("Logout clicked.");
  }
}
