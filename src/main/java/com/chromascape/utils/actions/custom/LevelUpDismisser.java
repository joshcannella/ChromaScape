package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.domain.ocr.Ocr;
import java.awt.Rectangle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Detects and dismisses level-up dialogs that block game input.
 *
 * <p>Level-up dialogs appear as a chatbox message containing "congratulations" or "advanced" in
 * the game's standard black text. This utility OCR-reads the chat area and presses space to
 * dismiss if detected.
 *
 * <p>For random events, use RuneLite's Entity Hider plugin to hide random event NPCs entirely,
 * making them invisible to both the player and the bot's colour/template detection.
 *
 * <p><b>Usage:</b> Call {@code LevelUpDismisser.dismissIfPresent(this)} at the start of
 * {@code cycle()} in any script that may trigger level-ups.
 */
public class LevelUpDismisser {

  private static final Logger logger = LogManager.getLogger(LevelUpDismisser.class);

  private static final ColourObj BLACK =
      new ColourObj("black", new Scalar(0, 0, 0, 0), new Scalar(0, 0, 0, 0));

  // "Click here to continue" is rendered in blue during active dialogs
  private static final ColourObj DIALOG_BLUE =
      new ColourObj("dialogBlue", new Scalar(118, 200, 100, 0), new Scalar(122, 255, 255, 0));

  /**
   * Checks the chatbox for an active dialog ("Click here to continue" in red)
   * and dismisses it by pressing space.
   *
   * @param base the active {@link BaseScript} instance, usually passed as {@code this}
   * @return {@code true} if a dialog was detected and dismissed, {@code false} otherwise
   */
  public static boolean dismissIfPresent(BaseScript base) {
    Rectangle chatZone = base.controller().zones().getChatTabs().get("Chat");
    if (chatZone == null) {
      return false;
    }

    String blueText = Ocr.extractText(chatZone, "Plain 12", DIALOG_BLUE, true).toLowerCase();
    if (blueText.contains("click here") || blueText.contains("continue")) {
      logger.info("Level-up dialog detected, dismissing.");
      KeyPress.space(base);
      BaseScript.waitRandomMillis(300, 500);
      KeyPress.space(base);
      return true;
    }
    return false;
  }
}
