package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.Idler;
import java.awt.Point;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared walker utility that wraps pathTo with standard error handling.
 *
 * <p><b>Usage:</b>
 * <pre>
 * if (!Walk.to(this, BANK_TILE, "bank")) { stuckCounter++; }
 * Walk.toOrStop(this, CHURCH_TILE, "church");
 * </pre>
 */
public final class Walk {

  private static final Logger logger = LogManager.getLogger(Walk.class);

  private Walk() {}

  /**
   * Walks to a tile. Returns false on IOException (path failure), stops on InterruptedException.
   *
   * @param base the script instance
   * @param tile the world tile to walk to
   * @param label a label for logging
   * @return true if walk succeeded, false on path error
   */
  public static boolean to(BaseScript base, Point tile, String label) {
    try {
      base.controller().walker().pathTo(tile, false);
      Idler.waitUntilIdle(base, 30);
      return true;
    } catch (InterruptedException e) {
      logger.error("Walker interrupted going to {}", label);
      base.stop();
      return false;
    } catch (Exception e) {
      logger.error("Walker error going to {}: {}", label, e.getMessage());
      return false;
    }
  }

  /**
   * Walks to a tile. Stops the script on any failure.
   *
   * @param base the script instance
   * @param tile the world tile to walk to
   * @param label a label for logging
   */
  public static void toOrStop(BaseScript base, Point tile, String label) {
    if (!to(base, tile, label)) {
      base.stop();
    }
  }
}
