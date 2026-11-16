package pcm;

/** Converts a CarPlay linear-volume request to a bounded input-gain change. */
public final class PromptGain {
    private PromptGain() { }
    public static double linear(int level, double requested) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("level");
        if (Double.isNaN(requested) || Double.isInfinite(requested)
                || requested < 0.0 || requested > 1.0) return 1.0;
        if (level == 5) return 1.0;
        return requested + (1.0 - requested) * (level - 1) / 4.0;
    }
    public static int target(int level, double requested, int baseline, int min, int max) {
        if (min > max || baseline < min || baseline > max)
            throw new IllegalArgumentException("unverified gain range");
        double gain = linear(level, requested);
        int result = gain <= 0.0 ? min : baseline + (int)Math.round(20.0 * Math.log(gain) / Math.log(10.0));
        return Math.max(min, Math.min(baseline, result));
    }
}
