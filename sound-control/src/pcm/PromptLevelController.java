package pcm;

import de.audi.atip.hmi.model.RangeListener;
import de.audi.atip.hmi.modelaccess.RangeModelApp;

/** Native HMI model adapter. No audio calls until a verified backend is supplied. */
public final class PromptLevelController implements RangeListener {
    public static final String LABEL = "Music Level During CarPlay Prompts";

    public interface Backend {
        /** Returns the persisted UI level, not a raw DSP preset. */
        int load() throws Exception;
        /** Must apply and persist successfully or throw; never claim success on failure. */
        void applyAndSave(int level) throws Exception;
        void reportFailure(Exception failure);
    }

    private final RangeModelApp model;
    private final Backend backend;
    private final int maximum;
    private int level;

    public PromptLevelController(RangeModelApp model, Backend backend, int maximum) {
        if (model == null || backend == null || maximum < 2 || maximum > 5) {
            throw new IllegalArgumentException("Invalid prompt level configuration");
        }
        this.model = model;
        this.backend = backend;
        this.maximum = maximum;
        this.level = maximum;
        try {
            int stored = backend.load();
            if (stored >= 1 && stored <= maximum) this.level = stored;
        } catch (Exception failure) {
            backend.reportFailure(failure);
        }
        model.setLimits(1, maximum, 1);
        model.setValue(level);
        model.setRangeListener(this);
    }

    public synchronized int getLevel() { return level; }
    public synchronized boolean isDuckingBypassed() { return level == maximum; }

    public synchronized void select(int requested) {
        int next = requested < 1 ? 1 : requested > maximum ? maximum : requested;
        if (next == level) return;
        try {
            backend.applyAndSave(next);
            level = next;
        } catch (Exception failure) {
            backend.reportFailure(failure);
        }
        model.setValue(level);
    }

    public synchronized void decrement(int id, int steps, int terminal) {
        if (steps > 0) select(level - Math.min(steps, maximum));
    }

    public synchronized void increment(int id, int steps, int terminal) {
        if (steps > 0) select(level + Math.min(steps, maximum));
    }

    public void keyPressed(int id, int key, int terminal) { }
    public void keyReleased(int id, int key, int terminal) { }
    public void keyTyped(int id, int key, int terminal) { }
    public void keyLongTyped(int id, int key, int terminal) { }
}
