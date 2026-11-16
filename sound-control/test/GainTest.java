import pcm.PromptGain;
public class GainTest {
    static void require(boolean x) { if (!x) throw new AssertionError(); }
    public static void main(String[] args) {
        for (int n = 0; n <= 100; n++) {
            double requested = n / 100.0;
            double previous = -1;
            int previousTarget = -100;
            for (int level = 1; level <= 5; level++) {
                double gain = PromptGain.linear(level, requested);
                int target = PromptGain.target(level, requested, 5, -6, 6);
                require(gain >= requested && gain <= 1 && gain >= previous);
                require(target >= -6 && target <= 5 && target >= previousTarget);
                if (level == 5) require(gain == 1 && target == 5);
                previous = gain; previousTarget = target;
            }
        }
        require(PromptGain.linear(1, Double.NaN) == 1);
        require(PromptGain.linear(1, -1) == 1);
        require(PromptGain.linear(1, 2) == 1);
        for (int level = 1; level <= 5; level++)
            System.out.println(level + " gain=" + PromptGain.linear(level, 0.1)
                + " target=" + PromptGain.target(level, 0.1, 5, -6, 6));
        System.out.println("PASS: 505 level/request cases, no amplification, bounds, invalid input");
    }
}
