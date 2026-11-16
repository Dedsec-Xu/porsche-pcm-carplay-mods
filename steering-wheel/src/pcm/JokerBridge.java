package pcm;

import de.audi.app.terminalmode.IContext;
import de.audi.app.terminalmode.audio.AudioConnectionState;
import de.audi.app.terminalmode.audio.TMAudioConnection;
import de.audi.app.terminalmode.smartphone.carplay.CarPlayDSIManager;
import de.audi.atip.hmi.model.ButtonListener;
import de.audi.atip.hmi.model.ButtonModel;
import de.audi.atip.hmi.modelaccess.ButtonModelApp;

/** Subscribe to the existing programmable-key channel without changing other listeners. */
public final class JokerBridge implements ButtonListener {
    private static volatile CarPlayDSIManager active;
    private static volatile int generation;
    private static JokerBridge instance;
    private final IContext context;
    private final ButtonModel model;
    private final ButtonModel longModel;
    private boolean pressed;
    private int meaningAtPress;
    private int generationAtPress;

    private JokerBridge(IContext ctx) {
        context = ctx;
        model = new ButtonModel(3200999);
        model.setButtonListener(this);
        longModel = new ButtonModel(3200998);
        longModel.setButtonListener(new ButtonListener() {
            public void keyPressed(int id, int key, int terminal) { cancelPress(); }
            public void keyReleased(int id, int key, int terminal) { }
            public void keyTyped(int id, int key, int terminal) { }
            public void keyLongTyped(int id, int key, int terminal) { cancelPress(); }
        });
    }
    public static synchronized ButtonModelApp model(IContext ctx, int virtualKey) {
        if (instance == null || instance.context != ctx) instance = new JokerBridge(ctx);
        return virtualKey == 39 ? instance.longModel : instance.model;
    }
    public static synchronized void activate(CarPlayDSIManager manager) {
        if (active != manager) { generation++; active = manager; }
    }
    public static synchronized void deactivate(CarPlayDSIManager manager) {
        if (active == manager) { active = null; generation++; }
    }
    public static synchronized void detach(IContext ctx) {
        if (instance != null && instance.context == ctx) {
            instance.pressed = false;
            instance = null;
        }
    }
    private int meaning() {
        return context.getFramework().getHmiServiceApp().getChoiceModel(395).getValue();
    }
    private boolean allowed() {
        if (active == null || context.getAudioManager().isStandbyMuted()
                || !context.getAudioManager().hasAudioFocus()
                || context.getFramework().getHmiServiceApp().getChoiceModel(409).getValue() != 0) return false;
        AudioConnectionState state = (AudioConnectionState) context.getAudioManager()
                .getAudioObservableForAudioConnection(TMAudioConnection.MEDIA).get();
        return state != null && state.isAudible();
    }
    private void log(String text) {
        context.getLogger().hmi().log(1000000, "[PCM-Joker] " + text);
    }
    private synchronized void cancelPress() { pressed = false; }
    public synchronized void keyPressed(int id, int key, int terminal) {
        if (pressed) return;
        meaningAtPress = meaning();
        generationAtPress = generation;
        pressed = (meaningAtPress == 9 || meaningAtPress == 14) && allowed();
        log("press meaning=" + meaningAtPress + " eligible=" + pressed);
    }
    public synchronized void keyReleased(int id, int key, int terminal) {
        boolean send = pressed;
        pressed = false;
        if (!send || generationAtPress != generation || meaning() != meaningAtPress || !allowed()) return;
        final CarPlayDSIManager manager = active;
        final int session = generation;
        final int selected = meaningAtPress;
        context.getDispatcher().execute(new Runnable() {
            public void run() {
                if (active != manager || generation != session || meaning() != selected || !allowed()) return;
                manager.skip(selected == 9, 1);
                log(selected == 9 ? "skip next" : "skip previous");
            }
        });
    }
    public void keyTyped(int id, int key, int terminal) { }
    public synchronized void keyLongTyped(int id, int key, int terminal) { pressed = false; }
}
