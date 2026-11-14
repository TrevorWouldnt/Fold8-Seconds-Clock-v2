package com.fold8.secondsclock;

import static android.view.MotionEvent.*;
import static org.junit.Assert.*;

import org.junit.Test;

/** No Android runtime is required: the recognizer only uses inlined action constants. */
public class ClockTapTrackerTest {
    private final Object clock = new Object();

    private ClockTapTracker tracker() {
        return new ClockTapTracker(8, 40, 500, 300, 40);
    }

    private void firstTap(ClockTapTracker tracker) {
        assertFalse(tracker.onEvent(ACTION_DOWN, 100, 20, 1000, clock, 1));
        assertFalse(tracker.onEvent(ACTION_UP, 101, 20, 1050, clock, 1));
    }

    @Test public void togglesOnlyAfterSecondTapIsReleased() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        assertTrue(tracker.isPending(clock, 1100));
        assertEquals(1050, tracker.getPendingFirstTapUpTime());
        assertFalse(tracker.onEvent(ACTION_DOWN, 105, 22, 1150, clock, 1));
        assertTrue(tracker.isSecondTapInProgress(clock));
        assertTrue(tracker.onEvent(ACTION_UP, 105, 23, 1200, clock, 1));
        assertFalse(tracker.isPending(clock, 1200));
        assertFalse(tracker.isSecondTapInProgress(clock));
        assertEquals(-1, tracker.getPendingFirstTapUpTime());
    }

    @Test public void slowTapsStartANewPair() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        assertFalse(tracker.onEvent(ACTION_DOWN, 100, 20, 1400, clock, 1));
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1450, clock, 1));
        assertFalse(tracker.onEvent(ACTION_DOWN, 100, 20, 1550, clock, 1));
        assertTrue(tracker.onEvent(ACTION_UP, 100, 20, 1600, clock, 1));
    }

    @Test public void thirdTapDoesNotToggleAgain() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        assertFalse(tracker.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1));
        assertTrue(tracker.onEvent(ACTION_UP, 100, 20, 1200, clock, 1));
        assertFalse(tracker.onEvent(ACTION_DOWN, 100, 20, 1300, clock, 1));
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1350, clock, 1));
    }

    @Test public void swipeCannotBecomeTapEvenAfterReturningToStart() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1);
        tracker.onEvent(ACTION_MOVE, 100, 40, 1170, clock, 1);
        tracker.onEvent(ACTION_MOVE, 100, 20, 1190, clock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1200, clock, 1));
        assertFalse(tracker.isPending(clock, 1200));
    }

    @Test public void leavingTheClockCancelsEvenWithinTouchSlop() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1);
        tracker.onEvent(ACTION_MOVE, 101, 20, 1170, null, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1200, clock, 1));
    }

    @Test public void releasingOutsideClockCancels() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1200, null, 1));
        assertFalse(tracker.isPending(clock, 1200));
    }

    @Test public void anotherClockCannotCompleteThePair() {
        ClockTapTracker tracker = tracker();
        Object otherClock = new Object();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1150, otherClock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1200, otherClock, 1));
        assertFalse(tracker.isPending(clock, 1200));
        assertTrue(tracker.isPending(otherClock, 1200));
    }

    @Test public void separateWindowsNeverShareATapPair() {
        ClockTapTracker firstWindow = tracker();
        ClockTapTracker secondWindow = tracker();
        firstTap(firstWindow);
        secondWindow.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1);
        assertFalse(secondWindow.onEvent(ACTION_UP, 100, 20, 1200, clock, 1));
        assertTrue(firstWindow.isPending(clock, 1200));
    }

    @Test public void multiTouchAndCancelClearPendingSequence() {
        for (int cancelAction : new int[] {ACTION_CANCEL, ACTION_OUTSIDE, ACTION_POINTER_DOWN}) {
            ClockTapTracker tracker = tracker();
            firstTap(tracker);
            tracker.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1);
            tracker.onEvent(cancelAction, 100, 20, 1170, clock,
                    cancelAction == ACTION_POINTER_DOWN ? 2 : 1);
            assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1200, clock, 1));
            assertFalse(tracker.isPending(clock, 1200));
        }
    }

    @Test public void longPressCannotCompleteAPair() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1150, clock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1700, clock, 1));
        assertFalse(tracker.isPending(clock, 1700));
    }

    @Test public void distantSecondTapDoesNotCompleteAPair() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 145, 20, 1150, clock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 145, 20, 1200, clock, 1));
    }

    @Test public void tooFastSecondTapDoesNotCompleteAPair() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1060, clock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1080, clock, 1));
    }

    @Test public void pendingSingleTapWaitsForSecondUpPastOriginalTimeout() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1300, clock, 1);
        assertTrue(tracker.isPending(clock, 1400));
        assertTrue(tracker.isSecondTapInProgress(clock));
        assertTrue(tracker.onEvent(ACTION_UP, 100, 20, 1450, clock, 1));
    }

    @Test public void pendingSingleTapExpiresAndResetDropsGesture() {
        ClockTapTracker tracker = tracker();
        firstTap(tracker);
        assertSame(clock, tracker.getPendingFirstTapTarget(1350));
        assertNull(tracker.getPendingFirstTapTarget(1351));
        assertEquals(-1, tracker.getPendingFirstTapUpTime());
        tracker.reset();
        firstTap(tracker);
        tracker.reset();
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1100, clock, 1));
    }

    @Test public void repeatedDownAndBackwardsTimeAreRejected() {
        ClockTapTracker tracker = tracker();
        tracker.onEvent(ACTION_DOWN, 100, 20, 1000, clock, 1);
        tracker.onEvent(ACTION_DOWN, 100, 20, 1000, clock, 1);
        assertFalse(tracker.onEvent(ACTION_UP, 100, 20, 1050, clock, 1));
        tracker.reset();
        firstTap(tracker);
        assertFalse(tracker.onEvent(ACTION_DOWN, 100, 20, 1040, clock, 1));
        assertFalse(tracker.isPending(clock, 1100));
    }
}
