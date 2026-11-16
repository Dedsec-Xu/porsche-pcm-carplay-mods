package pcm;

/** Time-driven click recognizer, independent of HMI services. Caller serializes access. */
public final class ClickGate {
    public static final int NONE = 0, SINGLE = 1, DOUBLE = 2;
    public static final long WINDOW_MS = 300;
    private boolean down, waiting, second;
    private long deadline;
    public int press(long now) {
        if (down) return NONE;
        int result = tick(now);
        second = waiting;
        waiting = false;
        down = true;
        return result;
    }
    public int release(long now) {
        if (!down) return NONE;
        down = false;
        if (second) { second = false; return DOUBLE; }
        waiting = true;
        deadline = now + WINDOW_MS;
        return NONE;
    }
    public int tick(long now) {
        if (waiting && now >= deadline) { waiting = false; return SINGLE; }
        return NONE;
    }
    public boolean isWaiting() { return waiting; }
    public long remaining(long now) { return Math.max(1L, deadline - now); }
    public void cancel() { down = false; waiting = false; second = false; }
}
