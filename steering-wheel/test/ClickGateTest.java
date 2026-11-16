import pcm.ClickGate;

public final class ClickGateTest {
    static void eq(int expected, int actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        ClickGate g = new ClickGate();
        eq(0, g.press(0)); eq(0, g.release(50));
        eq(0, g.tick(349)); eq(1, g.tick(350)); eq(0, g.tick(351));
        g.cancel(); eq(0, g.press(0)); eq(0, g.release(50));
        eq(0, g.press(250)); eq(0, g.tick(350)); eq(2, g.release(400)); eq(0, g.tick(900));
        // Long second press cancels the entire pending gesture, never sends a delayed skip.
        g.press(1000); g.release(1050); g.press(1100); g.cancel();
        eq(0, g.release(2000)); eq(0, g.tick(2300));
        // A repeat down/up does not double-count; stale timer notifications do not fire twice.
        g.press(3000); eq(0, g.press(3010)); g.release(3050); eq(0, g.release(3060));
        eq(1, g.tick(3350)); eq(0, g.tick(3350));
        // Exact boundary is two singles, not a double.
        g.press(4000); g.release(4050); eq(1, g.press(4350)); g.release(4400); eq(1, g.tick(4700));
        // Triple click yields previous, then a delayed next.
        g.press(5000); g.release(5050); g.press(5100); eq(2, g.release(5150));
        g.press(5200); g.release(5250); eq(1, g.tick(5550));
        // Session/assignment loss cancels a pending single.
        g.press(6000); g.release(6050); g.cancel(); eq(0, g.tick(6400));
        System.out.println("PASS: single, double, long press cancellation, repeats, boundary, triple, cancellation");
    }
}
