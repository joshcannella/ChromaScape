package com.chromascape.scripts;

import com.chromascape.base.BaseScript;
import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized human-like behavior simulation for ChromaScape scripts.
 *
 * <p>Provides probabilistic misclicks, idle drifts, mouse hesitation, variable action cadence,
 * attention breaks, camera fidgeting, and a fatigue model that escalates imperfections over time.
 *
 * <p>All base probabilities are exposed as constants at the top of this class for easy tuning.
 * Scripts call the {@code should*()} methods at natural decision points in their cycle, then
 * invoke the corresponding {@code perform*()} method when true.
 */
public final class HumanBehavior {

  private static final Logger logger = LogManager.getLogger(HumanBehavior.class);

  // === Base Probability Constants ===

  /** Base probability of a misclick per click action (FR-5.1). */
  public static final double MISCLICK_BASE_RATE = 0.03;
  /** Maximum misclick rate after fatigue scaling. */
  public static final double MISCLICK_MAX_RATE = 0.08;
  /** Misclick rate increase per 30 minutes of runtime. */
  public static final double MISCLICK_FATIGUE_STEP = 0.005;

  /** Probability of a short idle drift (2-8s) per check (FR-5.2). */
  public static final double IDLE_DRIFT_RATE = 0.02;
  /** Probability of a long idle drift (15-45s) per check. */
  public static final double LONG_DRIFT_RATE = 0.003;

  /** Probability of mouse hesitation before a click (FR-5.3). */
  public static final double HESITATION_RATE = 0.07;
  /** Probability of using slow mouse speed regardless of default (FR-5.3). */
  public static final double SLOW_APPROACH_RATE = 0.03;

  /** Probability of a short attention break (1-5 min) per check (FR-5.5). */
  public static final double BREAK_RATE = 0.01;
  /** Probability of an extended break (5-15 min) per check. */
  public static final double EXTENDED_BREAK_RATE = 0.001;

  /** Probability of a camera fidget per cycle (FR-5.6). */
  public static final double CAMERA_FIDGET_RATE = 0.03;

  // === Fatigue Model Constants (FR-5.8) ===

  /** Delay multiplier increase per hour of runtime, capped at MAX_FATIGUE_MULTIPLIER. */
  private static final double FATIGUE_DELAY_PER_HOUR = 0.05;
  private static final double MAX_FATIGUE_MULTIPLIER = 1.25;

  // === Tempo Constants (FR-5.4) ===

  /** Minimum interval (ms) between tempo drift adjustments. */
  private static final long TEMPO_DRIFT_INTERVAL_MIN_MS = 10L * 60 * 1000;
  /** Maximum interval (ms) between tempo drift adjustments. */
  private static final long TEMPO_DRIFT_INTERVAL_MAX_MS = 20L * 60 * 1000;

  // === Session State ===

  private static final long SESSION_START = System.currentTimeMillis();
  private static double tempoMultiplier;
  private static long nextTempoDriftTime;
  /** Minimum session warmup (ms) before breaks can trigger — randomized 20-30 min. */
  private static final long BREAK_WARMUP_MS =
      ThreadLocalRandom.current().nextLong(20L * 60 * 1000, 30L * 60 * 1000 + 1);
  private static long lastExtendedBreakTime = SESSION_START;

  static {
    tempoMultiplier = 0.85 + ThreadLocalRandom.current().nextDouble(0.30); // 0.85–1.15
    scheduleNextTempoDrift();
  }

  private HumanBehavior() {}

  // ========== Decision Methods ==========

  /**
   * Returns true if a misclick should occur, factoring in fatigue.
   *
   * @return true with probability scaling from {@link #MISCLICK_BASE_RATE} upward over time
   */
  public static boolean shouldMisclick() {
    double rate = Math.min(MISCLICK_MAX_RATE,
        MISCLICK_BASE_RATE + MISCLICK_FATIGUE_STEP * (elapsedMinutes() / 30.0));
    return roll(rate);
  }

  /**
   * Returns true if a short idle drift (2-8s) should occur.
   *
   * @return true with probability {@link #IDLE_DRIFT_RATE} plus fatigue bonus
   */
  public static boolean shouldIdleDrift() {
    return roll(IDLE_DRIFT_RATE + fatigueBonus(0.005));
  }

  /**
   * Returns true if a long idle drift (15-45s) should occur.
   *
   * @return true with probability {@link #LONG_DRIFT_RATE}
   */
  public static boolean shouldLongDrift() {
    return roll(LONG_DRIFT_RATE);
  }

  /**
   * Returns true if the mouse should hesitate (pause 200-600ms) before clicking.
   *
   * @return true with probability {@link #HESITATION_RATE}
   */
  public static boolean shouldHesitate() {
    return roll(HESITATION_RATE);
  }

  /**
   * Returns true if the mouse should use slow speed for this movement.
   *
   * @return true with probability {@link #SLOW_APPROACH_RATE}
   */
  public static boolean shouldSlowApproach() {
    return roll(SLOW_APPROACH_RATE);
  }

  /**
   * Returns true if a short attention break (1-5 min) should occur.
   *
   * @return true with probability {@link #BREAK_RATE}
   */
  public static boolean shouldTakeBreak() {
    if (System.currentTimeMillis() - SESSION_START < BREAK_WARMUP_MS) return false;
    return roll(BREAK_RATE);
  }

  /**
   * Returns true if an extended attention break (5-15 min) should occur.
   *
   * @return true with probability {@link #EXTENDED_BREAK_RATE}
   */
  public static boolean shouldTakeExtendedBreak() {
    if (System.currentTimeMillis() - SESSION_START < BREAK_WARMUP_MS) return false;
    return roll(EXTENDED_BREAK_RATE);
  }

  /**
   * Returns true if the camera should be fidgeted this cycle.
   *
   * @return true with probability {@link #CAMERA_FIDGET_RATE}
   */
  public static boolean shouldFidgetCamera() {
    return roll(CAMERA_FIDGET_RATE);
  }

  // ========== Action Methods ==========

  /**
   * Performs a misclick: clicks 15-60px away from the intended target, pauses 300-800ms as if
   * noticing the error. The caller should re-attempt the correct click afterward.
   *
   * @param script the running script instance
   * @param intended the point that was meant to be clicked
   */
  public static void performMisclick(BaseScript script, Point intended) {
    BaseScript.checkInterrupted();
    int offset = ThreadLocalRandom.current().nextInt(15, 61);
    double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
    Point miss = new Point(
        intended.x + (int) (offset * Math.cos(angle)),
        intended.y + (int) (offset * Math.sin(angle)));
    logger.debug("Misclick at {} (intended {})", miss, intended);
    script.controller().mouse().moveTo(miss, "fast");
    script.controller().mouse().leftClick();
    BaseScript.waitRandomMillis(300, 800);
  }

  /**
   * Performs an idle drift: the mouse stays still for 2-8s (short) or 15-45s (long).
   * Call {@link #shouldLongDrift()} separately to decide duration tier.
   *
   * @param script the running script instance
   */
  public static void performIdleDrift(BaseScript script) {
    BaseScript.checkInterrupted();
    boolean isLong = shouldLongDrift();
    long min = isLong ? 15_000 : 2_000;
    long max = isLong ? 45_000 : 8_000;
    logger.debug("Idle drift for {}-{}ms", min, max);
    BaseScript.waitRandomMillis(min, max);
  }

  /**
   * Pauses 200-600ms to simulate the player visually confirming a target before clicking.
   */
  public static void performHesitation() {
    BaseScript.checkInterrupted();
    BaseScript.waitRandomMillis(200, 600);
  }

  /**
   * Performs an attention break. Short breaks are 1-5 minutes, extended breaks are 5-15 minutes.
   * Extended breaks partially reset the fatigue timer.
   *
   * @param script the running script instance
   * @param extended true for a 5-15 min break, false for 1-5 min
   */
  public static void performBreak(BaseScript script, boolean extended) {
    BaseScript.checkInterrupted();
    long min;
    long max;
    if (extended) {
      min = 5L * 60 * 1000;
      max = 15L * 60 * 1000;
      logger.info("Taking extended break (5-15 min)");
    } else {
      min = 30L * 1000;
      max = 2L * 60 * 1000;
      logger.info("Taking short break (30s-2 min)");
    }
    BaseScript.waitRandomMillis(min, max);
    if (extended) {
      lastExtendedBreakTime = System.currentTimeMillis();
    }
  }

  /**
   * Performs a small camera rotation via middle-click drag to simulate habitual view adjustment.
   *
   * @param script the running script instance
   */
  public static void performCameraFidget(BaseScript script) {
    BaseScript.checkInterrupted();
    int dragX = ThreadLocalRandom.current().nextInt(-40, 41);
    int dragY = ThreadLocalRandom.current().nextInt(-15, 16);
    logger.debug("Camera fidget dx={} dy={}", dragX, dragY);

    // Middle press
    script.controller().mouse().middleClick(501);
    BaseScript.waitRandomMillis(50, 120);

    // Small drag from current position
    Point current = new Point(400 + dragX, 300 + dragY); // approximate center + offset
    script.controller().mouse().moveTo(current, "fast");
    BaseScript.waitRandomMillis(50, 100);

    // Middle release
    script.controller().mouse().middleClick(502);
  }

  // ========== Tempo & Fatigue ==========

  /**
   * Applies the session tempo multiplier and fatigue scaling to a base delay range.
   * Returns a randomized delay within the adjusted range.
   *
   * @param baseMin minimum delay in ms before adjustment
   * @param baseMax maximum delay in ms before adjustment
   * @return adjusted delay in ms
   */
  public static long adjustDelay(long baseMin, long baseMax) {
    double factor = tempoMultiplier * getFatigueMultiplier();
    long adjMin = Math.round(baseMin * factor);
    long adjMax = Math.round(baseMax * factor);
    return ThreadLocalRandom.current().nextLong(adjMin, adjMax + 1);
  }

  /**
   * Checks if the tempo drift interval has elapsed and shifts the multiplier by ±0.05.
   * Call once per cycle.
   */
  public static void updateTempoDrift() {
    if (System.currentTimeMillis() >= nextTempoDriftTime) {
      double drift = ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
      tempoMultiplier = Math.max(0.85, Math.min(1.15, tempoMultiplier + drift));
      scheduleNextTempoDrift();
      logger.debug("Tempo drift applied, new multiplier: {}", tempoMultiplier);
    }
  }

  /**
   * Returns the current fatigue delay multiplier based on elapsed runtime.
   * Ranges from 1.0 (fresh) to {@link #MAX_FATIGUE_MULTIPLIER}.
   * Partially resets after extended breaks.
   *
   * @return fatigue multiplier (1.0–1.25)
   */
  public static double getFatigueMultiplier() {
    double hoursSinceBreak =
        (System.currentTimeMillis() - lastExtendedBreakTime) / (1000.0 * 60 * 60);
    return Math.min(MAX_FATIGUE_MULTIPLIER, 1.0 + FATIGUE_DELAY_PER_HOUR * hoursSinceBreak);
  }

  // ========== Internal Helpers ==========

  private static boolean roll(double probability) {
    return ThreadLocalRandom.current().nextDouble() < probability;
  }

  private static double elapsedMinutes() {
    return (System.currentTimeMillis() - SESSION_START) / (1000.0 * 60);
  }

  /** Returns a small fatigue bonus added to base probabilities over time. */
  private static double fatigueBonus(double perHour) {
    double hours = elapsedMinutes() / 60.0;
    return perHour * hours;
  }

  private static void scheduleNextTempoDrift() {
    nextTempoDriftTime = System.currentTimeMillis()
        + ThreadLocalRandom.current().nextLong(TEMPO_DRIFT_INTERVAL_MIN_MS, TEMPO_DRIFT_INTERVAL_MAX_MS + 1);
  }
}
