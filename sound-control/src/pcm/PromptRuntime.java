package pcm;

import de.audi.atip.hmi.model.RangeModel;
import de.audi.tone.app.dsi.DSISoundHandler;
import java.io.*;

/** P3645-only runtime. Uses the DIO media connection, never announcement gain. */
public final class PromptRuntime implements PromptLevelController.Backend {
    public static final int MODEL_ID = 1000162;
    private static final String PATH = "/mnt/ols/pcm-custom/carplay-prompt-level";
    private static final PromptRuntime INSTANCE = new PromptRuntime();
    private static RangeModel model;
    private static PromptLevelController controller;
    private static DSISoundHandler sound;
    private static boolean ducking, baselineKnown, rangeKnown;
    private static int baseline, minimum, maximum;
    private static double requested = 1.0;
    private static long lastDuck;
    private static boolean watchdogStarted;
    private static volatile int selected = 5;

    private PromptRuntime() { }
    public static synchronized RangeModel model() {
        if (model == null) {
            model = new RangeModel(MODEL_ID);
            controller = new PromptLevelController(model, INSTANCE, 5);
        }
        return model;
    }
    public int load() throws IOException {
        File file = new File(PATH);
        if (!file.exists()) return 5;
        DataInputStream input = new DataInputStream(new FileInputStream(file));
        try {
            int value = input.readInt();
            selected = value >= 1 && value <= 5 ? value : 5;
            return selected;
        } finally { input.close(); }
    }
    public void applyAndSave(int level) throws IOException {
        File file = new File(PATH), temp = new File(PATH + ".new"), backup = new File(PATH + ".bak");
        DataOutputStream output = new DataOutputStream(new FileOutputStream(temp));
        try { output.writeInt(level); output.flush(); } finally { output.close(); }
        if (backup.exists() && !backup.delete()) throw new IOException("cannot rotate setting backup");
        if (file.exists() && !file.renameTo(backup)) throw new IOException("cannot back up setting");
        if (!temp.renameTo(file)) {
            if (backup.exists()) backup.renameTo(file);
            throw new IOException("cannot save setting");
        }
        // The controller commits its UI value after this method returns.
        synchronized (PromptRuntime.class) { selected = level; if (ducking) apply(level); }
    }
    public void reportFailure(Exception error) { log("error: " + error.toString()); }
    private static void log(String message) { System.err.println("[PCM-PromptLevel] " + message); }

    public static synchronized void register(DSISoundHandler handler) {
        sound = handler;
        baselineKnown = false; rangeKnown = false;
        model();
        try {
            sound.getInputGainOffsetRange(0, 162);
            sound.getInputGainOffset(0, 162);
        } catch (RuntimeException e) { log(e.toString()); }
        if (!watchdogStarted) {
            Thread watchdog = new Thread(new Runnable() {
                public void run() {
                    for (;;) {
                        try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                        synchronized (PromptRuntime.class) {
                            if (ducking && System.currentTimeMillis() - lastDuck > 120000) {
                                log("missing unduck: restoring after timeout");
                                unduck();
                            }
                        }
                    }
                }
            }, "PCM-PromptRestore");
            watchdog.setDaemon(true); watchdog.start(); watchdogStarted = true;
        }
    }
    public static synchronized void gainRange(int connection, int terminal, int min, int max) {
        if (connection != 162 || min > max || min < -100 || max > 100) return;
        minimum = min; maximum = max; rangeKnown = true;
        if (ducking && baselineKnown) apply(selected);
    }
    public static synchronized void gainUpdate(int connection, int terminal, short gain, int valid) {
        if (connection != 162 || valid != 1) return;
        if (!baselineKnown) {
            baseline = gain; baselineKnown = true;
            log("baseline=" + baseline);
            if (ducking && rangeKnown) apply(selected);
        }
    }
    public static synchronized void duck(int duration, double volume) {
        model();
        if (sound == null) { log("audio service unavailable; keeping music unchanged"); return; }
        if (Double.isNaN(volume) || Double.isInfinite(volume) || volume < 0 || volume > 1) {
            log("invalid CarPlay volume; keeping music unchanged"); return;
        }
        requested = volume; lastDuck = System.currentTimeMillis();
        log("duck duration=" + duration + " volume=" + volume + " level=" + selected);
        ducking = true;
        if (baselineKnown && rangeKnown) apply(selected);
        else {
            sound.getInputGainOffsetRange(0, 162);
            sound.getInputGainOffset(0, 162);
        }
    }
    private static void apply(int level) {
        if (sound == null || !baselineKnown || !rangeKnown) return;
        try {
            int target = PromptGain.target(level, requested, baseline, minimum, maximum);
            sound.setInputGainOffSet(162, 0, (short)target);
            log("level=" + level + " inputGain=" + target);
        } catch (RuntimeException e) { log("apply failed: " + e.toString()); }
    }
    public static synchronized void unduck() {
        if (ducking && baselineKnown && sound != null) {
            try { sound.setInputGainOffSet(162, 0, (short)baseline); }
            catch (RuntimeException e) { log("restore failed: " + e.toString()); }
        }
        ducking = false;
    }
    public static synchronized void disconnect() { unduck(); }
}
