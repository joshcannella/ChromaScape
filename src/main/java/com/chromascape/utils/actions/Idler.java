package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.domain.ocr.Ocr;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.opencv.opencv_core.Scalar;

/**
 * Utility class for handling idle behavior in scripts.
 *
 * <p>This class provides functionality to pause execution for a given amount of time, or until the
 * game client indicates the player has become idle again through a chat message.
 *
 * <p>Detects three types of idle events from the RuneLite Idle Notifier plugin and native game
 * messages:
 * <ul>
 *   <li>{@link IdleType#ANIMATION} — "You are now idle!" (skilling/interaction stopped)</li>
 *   <li>{@link IdleType#COMBAT} — "You are now out of combat!" or "no longer in combat"</li>
 *   <li>{@link IdleType#MOVEMENT} — "You have stopped moving!"</li>
 * </ul>
 */
public class Idler {

  private static final Logger logger = LogManager.getLogger(Idler.class);
  private static volatile String lastMessage = "";

  /** Seeds the dedup timestamp from the current chat so stale messages are ignored. */
  public static void seedLastMessage(BaseScript base) {
    Rectangle latestMessage = base.controller().zones().getChatTabs().get("Latest Message");
    if (latestMessage != null) {
      lastMessage = Ocr.extractText(latestMessage, "Plain 12", black, true);
    }
  }

  private static final ColourObj black =
      new ColourObj("black", new Scalar(0, 0, 0, 0), new Scalar(0, 0, 0, 0));
  // RuneLite Idle Notifier plugin red text
  private static final ColourObj chatRed =
      new ColourObj("chatRed", new Scalar(177, 229, 239, 0), new Scalar(179, 240, 240, 0));
  // Game's native red text (e.g. "You are no longer in combat!")
  private static final ColourObj gameRed =
      new ColourObj("gameRed", new Scalar(0, 200, 200, 0), new Scalar(5, 255, 255, 0));

  /**
   * Waits until the player goes idle or the timeout is reached, returning the type of idle event.
   *
   * <p>Monitors the "Latest Message" chat zone for idle notifications from the RuneLite Idle
   * Notifier plugin (in {@code chatRed}) and native game messages (in {@code gameRed}).
   *
   * @param base the active {@link BaseScript} instance, usually passed as {@code this}
   * @param timeoutSeconds the maximum number of seconds to wait
   * @return the {@link IdleType} detected, or {@link IdleType#TIMEOUT} if none found
   */
  public static IdleType waitUntilIdleType(BaseScript base, int timeoutSeconds) {
    BaseScript.waitMillis(600);
    BaseScript.checkInterrupted();
    Instant deadline = Instant.now().plus(Duration.ofSeconds(timeoutSeconds));
    while (Instant.now().isBefore(deadline)) {
      BaseScript.waitMillis(300);
      Rectangle latestMessage = base.controller().zones().getChatTabs().get("Latest Message");
      String pluginText = Ocr.extractText(latestMessage, "Plain 12", chatRed, true);
      String gameText = Ocr.extractText(latestMessage, "Plain 12", gameRed, true);
      String timeStamp = Ocr.extractText(latestMessage, "Plain 12", black, true);
      if (timeStamp.equals(lastMessage)) continue;

      String combined = pluginText + gameText;
      if (combined.contains("combat")) {
        lastMessage = timeStamp;
        return IdleType.COMBAT;
      }
      if (combined.contains("moving")) {
        lastMessage = timeStamp;
        return IdleType.MOVEMENT;
      }
      if (combined.contains("idle")) {
        lastMessage = timeStamp;
        return IdleType.ANIMATION;
      }
    }
    return IdleType.TIMEOUT;
  }

  /**
   * Waits until the player goes idle or the timeout is reached.
   *
   * <p>This is a convenience wrapper around {@link #waitUntilIdleType} for callers that only need
   * a boolean result.
   *
   * @param base the active {@link BaseScript} instance, usually passed as {@code this}
   * @param timeoutSeconds the maximum number of seconds to wait
   * @return {@code true} if an idle message was found, {@code false} if the timeout was reached
   */
  public static boolean waitUntilIdle(BaseScript base, int timeoutSeconds) {
    return waitUntilIdleType(base, timeoutSeconds) != IdleType.TIMEOUT;
  }
}
