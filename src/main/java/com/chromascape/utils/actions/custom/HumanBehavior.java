package com.chromascape.utils.actions.custom;

import com.chromascape.base.BaseScript;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized human-like behavior simulation for ChromaScape scripts.
 *
 * <p>Provides probabilistic misclicks, idle drifts, mouse hesitation, variable action cadence,
 * attention breaks, camera fidgeting, and a fatigue model that escalates imperfections over time.
 *
 * <p>Call {@link #runPreCycleChecks(BaseScript)} as the first line of every {@code cycle()} to
 * handle tempo drift, breaks, camera fidgets, idle drifts, and level-up dismissal automatically.
 */
public final class HumanBehavior {

  private static final Logger logger = LogManager.getLogger(HumanBehavior.class);

  // === Base Probability Constants ===

  public static final double MISCLICK_BASE_RATE = 0.03;
  public static final double MISCLICK_MAX_RATE = 0.08;
  public static final double MISCLICK_FATIGUE_STEP = 0.005;
  public static final double IDLE_DRIFT_RATE = 0.02;
  public static final double LONG_DRIFT_RATE = 0.003;
  public static final double HESITATION_RATE = 0.07;
  public static final double SLOW_APPROACH_RATE = 0.03;
  public static final double BREAK_RATE = 0.01;
  public static final double EXTENDED_BREAK_RATE = 0.001;
  public static final double CAMERA_FIDGET_RATE = 0.03;

  // === Fatigue Model Constants ===

  private static final double FATIGUE_DELAY_PER_HOUR = 0.05;
  private static final double MAX_FATIGUE_MULTIPLIER = 1.25;

  // === Tempo Constants ===

  private static final long TEMPO_DRIFT_INTERVAL_MIN_MS = 10L * 60 * 1000;
  private static final long TEMPO_DRIFT_INTERVAL_MAX_MS = 20L * 60 * 1000;

  // === Session State ===

  private static long sessionStart = System.currentTimeMillis();
  private static double tempoMultiplier;
  private static long nextTempoDriftTime;
  private static long breakWarmupMs =
      ThreadLocalRandom.current().nextLong(20L * 60 * 1000, 30L * 60 * 1000 + 1);
  private static long lastExtendedBreakTime = sessionStart;

  static {
    tempoMultiplier = 0.85 + ThreadLocalRandom.current().nextDouble(0.30);
    scheduleNextTempoDrift();
  }

  private HumanBehavior() {}

  /**
   * Resets all session state (fatigue, tempo, break timers). Call when starting a new script
   * to avoid carrying over state from a previous run in the same JVM.
   */
  public static void reset() {
    sessionStart = System.currentTimeMillis();
    lastExtendedBreakTime = sessionStart;
    tempoMultiplier = 0.85 + ThreadLocalRandom.current().nextDouble(0.30);
    breakWarmupMs = ThreadLocalRandom.current().nextLong(20L * 60 * 1000, 30L * 60 * 1000 + 1);
    scheduleNextTempoDrift();
    logger.info("HumanBehavior session state reset");
  }

  // ========== Pre-Cycle Boilerplate ==========

  /**
   * Runs all standard pre-cycle checks: tempo drift, breaks, camera fidgets, idle drifts,
   * and level-up dismissal. Call this as the first line of every {@code cycle()}.
   *
   * @param base the active script instance, usually {@code this}
   * @return true if the cycle should be skipped (a break was taken), false to continue
   */
  public static boolean runPreCycleChecks(BaseScript base) {
    updateTempoDrift();
    if (shouldTakeExtendedBreak()) {
      performBreak(base, true);
      return true;
    }
    if (shouldTakeBreak()) {
      performBreak(base, false);
      return true;
    }
    if (shouldFidgetCamera()) {
      performCameraFidget(base);
    }
    if (shouldIdleDrift()) {
      performIdleDrift(base);
    }
    LevelUpDismisser.dismissIfPresent(base);
    return false;
  }

  // ========== Decision Methods ==========

  public static boolean shouldMisclick() {
    double rate = Math.min(MISCLICK_MAX_RATE,
        MISCLICK_BASE_RATE + MISCLICK_FATIGUE_STEP * (elapsedMinutes() / 30.0));
    return roll(rate);
  }

  public static boolean shouldIdleDrift() {
    return roll(IDLE_DRIFT_RATE + fatigueBonus(0.005));
  }

  public static boolean shouldLongDrift() {
    return roll(LONG_DRIFT_RATE);
  }

  public static boolean shouldHesitate() {
    return roll(HESITATION_RATE);
  }

  public static boolean shouldSlowApproach() {
    return roll(SLOW_APPROACH_RATE);
  }

  public static boolean shouldTakeBreak() {
    if (System.currentTimeMillis() - sessionStart < breakWarmupMs) return false;
    return roll(BREAK_RATE);
  }

  public static boolean shouldTakeExtendedBreak() {
    if (System.currentTimeMillis() - sessionStart < breakWarmupMs) return false;
    return roll(EXTENDED_BREAK_RATE);
  }

  public static boolean shouldFidgetCamera() {
    return roll(CAMERA_FIDGET_RATE);
  }

  // ========== Humanized Click ==========

  /**
   * Waits for a human-adjusted random duration between min and max milliseconds.
   * Convenience wrapper for {@code waitMillis(adjustDelay(min, max))}.
   */
  public static void sleep(long min, long max) {
    BaseScript.waitMillis(adjustDelay(min, max));
  }

  /**
   * Performs a human-like left click at the given point: random speed, optional hesitation,
   * optional misclick (with correction), micro-jitter, then click.
   *
   * @param script the active script instance
   * @param target the intended click point
   */
  public static void click(BaseScript script, Point target) {
    BaseScript.checkInterrupted();
    String speed = shouldSlowApproach() ? "slow" : "medium";
    script.controller().mouse().moveTo(target, speed);

    if (shouldHesitate()) {
      performHesitation();
    }
    if (shouldMisclick()) {
      performMisclick(script, target);
      script.controller().mouse().moveTo(target, "medium");
    }

    script.controller().mouse().microJitter();
    script.controller().mouse().leftClick();
  }

  // ========== Action Methods ==========

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

  public static void performIdleDrift(BaseScript script) {
    BaseScript.checkInterrupted();
    boolean isLong = shouldLongDrift();
    long min = isLong ? 15_000 : 2_000;
    long max = isLong ? 45_000 : 8_000;
    logger.debug("Idle drift for {}-{}ms", min, max);
    BaseScript.waitRandomMillis(min, max);
  }

  public static void performHesitation() {
    BaseScript.checkInterrupted();
    BaseScript.waitRandomMillis(200, 600);
  }

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

  public static void performCameraFidget(BaseScript script) {
    BaseScript.checkInterrupted();
    int dragX = ThreadLocalRandom.current().nextInt(-40, 41);
    int dragY = ThreadLocalRandom.current().nextInt(-15, 16);
    logger.debug("Camera fidget dx={} dy={}", dragX, dragY);

    PointerInfo pi = MouseInfo.getPointerInfo();
    Point anchor = (pi != null) ? pi.getLocation() : new Point(400, 300);

    script.controller().mouse().middleClick(501);
    BaseScript.waitRandomMillis(50, 120);
    script.controller().mouse().moveTo(
        new Point(anchor.x + dragX, anchor.y + dragY), "fast");
    BaseScript.waitRandomMillis(50, 100);
    script.controller().mouse().middleClick(502);
  }

  // ========== Tempo & Fatigue ==========

  public static long adjustDelay(long baseMin, long baseMax) {
    double factor = tempoMultiplier * getFatigueMultiplier();
    long adjMin = Math.round(baseMin * factor);
    long adjMax = Math.round(baseMax * factor);
    return ThreadLocalRandom.current().nextLong(adjMin, adjMax + 1);
  }

  public static void updateTempoDrift() {
    if (System.currentTimeMillis() >= nextTempoDriftTime) {
      double drift = ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
      tempoMultiplier = Math.max(0.85, Math.min(1.15, tempoMultiplier + drift));
      scheduleNextTempoDrift();
      logger.debug("Tempo drift applied, new multiplier: {}", tempoMultiplier);
    }
  }

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
    return (System.currentTimeMillis() - sessionStart) / (1000.0 * 60);
  }

  private static double fatigueBonus(double perHour) {
    double hours = elapsedMinutes() / 60.0;
    return perHour * hours;
  }

  private static void scheduleNextTempoDrift() {
    nextTempoDriftTime = System.currentTimeMillis()
        + ThreadLocalRandom.current().nextLong(TEMPO_DRIFT_INTERVAL_MIN_MS, TEMPO_DRIFT_INTERVAL_MAX_MS + 1);
  }
}
