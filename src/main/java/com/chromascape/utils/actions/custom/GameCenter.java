package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Point;
import java.awt.Rectangle;

/** Clicks the center of the game screen. Useful for interacting with doors, NPCs, and objects
 * that the player is standing on or facing. */
public final class GameCenter {

  private GameCenter() {}

  /**
   * Clicks a random point within a 40×40 pixel region at the center of the game window.
   *
   * @param base the active script instance
   */
  public static void click(BaseScript base) {
    Rectangle window = ScreenManager.getWindowBounds();
    int regionSize = 40;
    Rectangle centerRegion = new Rectangle(
        window.x + window.width / 2 - regionSize / 2,
        window.y + window.height / 2 - regionSize / 2,
        regionSize, regionSize);
    Point clickLoc = ClickDistribution.generateRandomPoint(centerRegion);
    HumanBehavior.click(base, clickLoc);
  }
}
