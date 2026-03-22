package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.ChromaObj;
import com.chromascape.utils.core.screen.topology.ColourContours;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Detects and dismisses level-up dialogs that block game input.
 *
 * <p>Level-up dialogs show "Click here to continue" in a distinctive blue colour
 * ({@code RGB(0, 0, 128)} → {@code HSV(120, 255, 128)}). Instead of OCR, this utility
 * checks for the presence of blue pixels in the chat zone via colour contour detection,
 * which is faster and more reliable.
 *
 * <p><b>Usage:</b> Call {@code LevelUpDismisser.dismissIfPresent(this)} after any action
 * that may trigger level-ups.
 */
public class LevelUpDismisser {

  private static final Logger logger = LogManager.getLogger(LevelUpDismisser.class);

  // "Click here to continue" blue: RGB(0, 0, 128) → HSV(120, 255, 128)
  private static final ColourObj DIALOG_BLUE =
      new ColourObj("dialogBlue", new Scalar(120, 254, 254, 0), new Scalar(121, 255, 255, 0));

  /**
   * Checks the chatbox for blue dialog pixels and dismisses by pressing space.
   *
   * @param base the active {@link BaseScript} instance, usually passed as {@code this}
   * @return {@code true} if a dialog was detected and dismissed, {@code false} otherwise
   */
  public static boolean dismissIfPresent(BaseScript base) {
    Rectangle chatZone = base.controller().zones().getChatTabs().get("Chat");
    if (chatZone == null) {
      return false;
    }

    BufferedImage chatImg = ScreenManager.captureZone(chatZone);
    List<ChromaObj> blueObjs = ColourContours.getChromaObjsInColour(chatImg, DIALOG_BLUE);
    boolean detected = false;
    for (ChromaObj obj : blueObjs) {
      if (obj.boundingBox().width > 50) detected = true;
      obj.release();
    }

    if (detected) {
      logger.info("Level-up dialog detected (blue pixels in chat), dismissing.");
      KeyPress.space(base);
      BaseScript.waitRandomMillis(300, 500);
      KeyPress.space(base);
      return true;
    }
    return false;
  }
}
