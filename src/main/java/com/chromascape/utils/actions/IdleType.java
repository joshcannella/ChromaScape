package com.chromascape.utils.actions;

/** The type of idle event detected by {@link Idler}. */
public enum IdleType {
  /** Skilling animation stopped (mining, cooking, fletching, etc.) or non-combat interaction. */
  ANIMATION,
  /** Combat interaction ended ("You are now out of combat!"). */
  COMBAT,
  /** Player stopped moving ("You have stopped moving!"). */
  MOVEMENT,
  /** No idle message detected within the timeout. */
  TIMEOUT
}
