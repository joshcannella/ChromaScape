package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common key press actions: space, escape, enter, and arbitrary characters.
 *
 * <p><b>Usage:</b>
 * <pre>
 * KeyPress.space(this);
 * KeyPress.escape(this);
 * KeyPress.enter(this);
 * KeyPress.character(this, '1');
 * </pre>
 */
public class KeyPress {

  private static final Logger logger = LogManager.getLogger(KeyPress.class);

  /** Presses and releases the Space key. */
  public static void space(BaseScript base) {
    modifier(base, "space");
  }

  /** Presses and releases the Escape key. */
  public static void escape(BaseScript base) {
    modifier(base, "esc");
  }

  /** Presses and releases the Enter key. */
  public static void enter(BaseScript base) {
    modifier(base, "enter");
  }

  /** Types a single character key. */
  public static void character(BaseScript base, char key) {
    base.controller().keyboard().sendKeyChar(key);
    BaseScript.waitRandomMillis(60, 120);
  }

  private static void modifier(BaseScript base, String key) {
    base.controller().keyboard().sendModifierKey(401, key);
    BaseScript.waitRandomMillis(60, 100);
    base.controller().keyboard().sendModifierKey(402, key);
  }
}
