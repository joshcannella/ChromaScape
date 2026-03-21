package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Detects combat state via the RuneLite Opponent Information health bar overlay.
 *
 * <p>When in combat, a green/red HP bar appears in the top-left of the game view. This detects
 * the green portion to provide an instant combat check, complementing the Idler "out of combat"
 * chat message for authoritative end-of-combat events.
 *
 * <p><b>RuneLite:</b> Opponent Information plugin must be enabled (default).
 */
public final class Combat {

  private Combat() {}

  private static final Logger logger = LogManager.getLogger(Combat.class);

  private static final ColourObj HP_BAR_GREEN =
      new ColourObj("hpBarGreen", new Scalar(67, 230, 137, 0), new Scalar(72, 245, 145, 0));

  private static final Rectangle OPPONENT_INFO_ZONE = new Rectangle(0, 0, 160, 50);

  /**
   * Returns true if the Opponent Information health bar is visible (player is in combat).
   * Detects any green in the health bar — always present while the NPC is alive.
   */
  public static boolean isInCombat(BaseScript base) {
    BufferedImage gameView = base.controller().zones().getGameView();
    // Crop to top-left where opponent info renders
    BufferedImage zone = gameView.getSubimage(
        OPPONENT_INFO_ZONE.x, OPPONENT_INFO_ZONE.y,
        OPPONENT_INFO_ZONE.width, OPPONENT_INFO_ZONE.height);
    List<ChromaObj> objs = ColourContours.getChromaObjsInColour(zone, HP_BAR_GREEN);
    boolean found = !objs.isEmpty();
    logger.info("isInCombat={} (contours={})", found, objs.size());
    for (ChromaObj obj : objs) {
      obj.release();
    }
    return found;
  }
}
