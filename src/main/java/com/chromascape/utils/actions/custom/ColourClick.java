package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
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

  private static final int PADDING = 7;
  private static final String[] RED_CLICK_IMAGES = {
    "/images/mouse_clicks/red_1.png", "/images/mouse_clicks/red_2.png",
    "/images/mouse_clicks/red_3.png", "/images/mouse_clicks/red_4.png"
  };

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
    return getClickPoint(base, colour, 0);
  }

  /**
   * Gets a random click point within the closest colour object with tightness control.
   * Higher tightness clusters clicks toward the center. 0 uses default distribution.
   */
  public static Point getClickPoint(BaseScript base, ColourObj colour, double tightness) {
    BufferedImage gameView = base.controller().zones().getGameView();
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(gameView, colour);
    if (objs.isEmpty()) {
      return null;
    }
    try {
      ChromaObj closest = ColourContours.getChromaObjClosestToCentre(objs);
      return tightness > 0
          ? ClickDistribution.generateRandomPoint(closest.boundingBox(), tightness)
          : ClickDistribution.generateRandomPoint(closest.boundingBox());
    } catch (Exception e) {
      logger.warn("Failed to generate click point: {}", e.getMessage());
      return null;
    } finally {
      for (ChromaObj obj : objs) {
        obj.release();
      }
    }
  }

  /**
   * Checks if a red click (interaction) sprite appeared at the given point.
   * Call ~120ms after clicking to allow the sprite to render.
   */
  public static boolean wasRedClick(Point clickPoint) {
    Rectangle area = new Rectangle(
        clickPoint.x - PADDING, clickPoint.y - PADDING, PADDING * 2, PADDING * 2);
    BufferedImage capture = ScreenManager.captureZone(area);
    for (String sprite : RED_CLICK_IMAGES) {
      if (TemplateMatching.match(sprite, capture, 0.15).success()) return true;
    }
    return false;
  }
}
