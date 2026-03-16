package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.input.distribution.ClickDistribution;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Safe colour-based detection and clicking. Uses ColourContours directly to avoid the OpenCV crash
 * in PointSelector on malformed contours.
 *
 * <p><b>Usage:</b>
 * <pre>
 * if (ColourClick.isVisible(this, FISH_COLOUR)) { ... }
 * Point p = ColourClick.getClickPoint(this, ROCK_COLOUR);
 * </pre>
 */
public final class ColourClick {

  private static final Logger logger = LogManager.getLogger(ColourClick.class);

  private ColourClick() {}

  /**
   * Checks if any object of the given colour is visible in the game view.
   */
  public static boolean isVisible(BaseScript base, ColourObj colour) {
    BufferedImage gameView = base.controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
    boolean found = !objs.isEmpty();
    for (ChromaObj obj : objs) {
      obj.release();
    }
    return found;
  }

  /**
   * Gets a random click point within the closest colour object. Returns null if not found.
   * Uses bounding box + ClickDistribution to avoid PointSelector OpenCV crash.
   */
  public static Point getClickPoint(BaseScript base, ColourObj colour) {
    BufferedImage gameView = base.controller().zones().getGameView();
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
}
