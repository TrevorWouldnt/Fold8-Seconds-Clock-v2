package com.fold8.secondsclock;

import android.view.MotionEvent;

import java.lang.ref.WeakReference;

/**
 * Recognizes a double tap from a window's observed events, without consuming or
 * changing those events. Use a separate instance for each window. Coordinates
 * must share one space (normally raw screen coordinates) throughout a sequence.
 */
final class ClockTapTracker {
    private final int touchSlop;
    private final int doubleTapSlop;
    private final long maxTapDuration;
    private final long doubleTapTimeout;
    private final long minDoubleTapInterval;

    // Weak references prevent a pending gesture from retaining a detached clock/window.
    private WeakReference<Object> firstTarget;
    private long firstUpTime = -1;
    private float firstDownX;
    private float firstDownY;
    private WeakReference<Object> downTarget;
    private long downTime = -1;
    private float downX;
    private float downY;
    private boolean secondTap;
    private long lastEventTime = -1;

    ClockTapTracker(int touchSlop, int doubleTapSlop, long maxTapDuration,
            long doubleTapTimeout, long minDoubleTapInterval) {
        if (touchSlop < 0 || doubleTapSlop < 0 || maxTapDuration <= 0
                || doubleTapTimeout <= 0 || minDoubleTapInterval < 0
                || minDoubleTapInterval > doubleTapTimeout) {
            throw new IllegalArgumentException("Invalid double-tap thresholds");
        }
        this.touchSlop = touchSlop;
        this.doubleTapSlop = doubleTapSlop;
        this.maxTapDuration = maxTapDuration;
        this.doubleTapTimeout = doubleTapTimeout;
        this.minDoubleTapInterval = minDoubleTapInterval;
    }

    /**
     * Returns true only on the UP that completes a valid second tap. Pass the
     * hit-tested clock on every event; null or another clock cancels the gesture.
     * Target comparison uses identity, not equals(), so two clocks cannot pair.
     */
    boolean onEvent(int action, float rawX, float rawY, long eventTime,
            Object target, int pointerCount) {
        if (eventTime < 0 || (lastEventTime >= 0 && eventTime < lastEventTime)) {
            reset();
            return false;
        }
        lastEventTime = eventTime;
        int masked = action & MotionEvent.ACTION_MASK;
        if (pointerCount != 1 || !Float.isFinite(rawX) || !Float.isFinite(rawY)
                || masked == MotionEvent.ACTION_CANCEL || masked == MotionEvent.ACTION_OUTSIDE
                || masked == MotionEvent.ACTION_POINTER_DOWN || masked == MotionEvent.ACTION_POINTER_UP) {
            clearSequence();
            return false;
        }

        switch (masked) {
            case MotionEvent.ACTION_DOWN:
                // A repeated/reentrant DOWN is not a second tap without an intervening UP.
                if (downTime >= 0 || target == null) {
                    clearSequence();
                    return false;
                }
                expirePending(eventTime);
                long interval = eventTime - firstUpTime;
                secondTap = firstTarget != null && firstTarget.get() == target
                        && interval >= minDoubleTapInterval && interval <= doubleTapTimeout
                        && within(rawX, rawY, firstDownX, firstDownY, doubleTapSlop);
                if (!secondTap) clearPending();
                downTarget = new WeakReference<>(target);
                downTime = eventTime;
                downX = rawX;
                downY = rawY;
                return false;

            case MotionEvent.ACTION_MOVE:
                if (downTime >= 0 && !isValidTapPosition(target, rawX, rawY, eventTime)) {
                    clearSequence();
                }
                return false;

            case MotionEvent.ACTION_UP:
                if (downTime < 0) return false;
                if (!isValidTapPosition(target, rawX, rawY, eventTime)) {
                    clearSequence();
                    return false;
                }
                if (secondTap && firstTarget != null && firstTarget.get() == target) {
                    clearSequence();
                    return true;
                }
                firstTarget = new WeakReference<>(target);
                firstUpTime = eventTime;
                firstDownX = downX;
                firstDownY = downY;
                clearActiveTap();
                return false;

            default:
                return false;
        }
    }

    /** Whether a completed first tap is still waiting for this exact clock. */
    boolean isPending(Object target, long now) {
        return target != null && getPendingFirstTapTarget(now) == target;
    }

    Object getPendingFirstTapTarget(long now) {
        expirePending(now);
        return firstTarget == null ? null : firstTarget.get();
    }

    /** Returns -1 after reset, expiration, cancellation, or a completed double tap. */
    long getPendingFirstTapUpTime() {
        return firstUpTime;
    }

    /** A delayed Samsung single-click should wait while the second tap is down. */
    boolean isSecondTapInProgress(Object target) {
        return secondTap && downTime >= 0 && downTarget != null && downTarget.get() == target;
    }

    void reset() {
        clearSequence();
        lastEventTime = -1;
    }

    private boolean isValidTapPosition(Object target, float x, float y, long now) {
        return downTarget != null && target != null && downTarget.get() == target
                && now >= downTime && now - downTime <= maxTapDuration
                && within(x, y, downX, downY, touchSlop);
    }

    private void expirePending(long now) {
        if (firstTarget == null) return;
        boolean waitingForSecondUp = secondTap && downTime >= 0
                && now >= downTime && now - downTime <= maxTapDuration;
        if (firstTarget.get() == null || now < firstUpTime
                || (!waitingForSecondUp && now - firstUpTime > doubleTapTimeout)) {
            clearPending();
        }
    }

    private void clearSequence() {
        clearActiveTap();
        clearPending();
    }

    private void clearPending() {
        firstTarget = null;
        firstUpTime = -1;
    }

    private void clearActiveTap() {
        downTarget = null;
        downTime = -1;
        secondTap = false;
    }

    private static boolean within(float x, float y, float originX, float originY, int slop) {
        double dx = (double) x - originX;
        double dy = (double) y - originY;
        return dx * dx + dy * dy <= (double) slop * slop;
    }
}
