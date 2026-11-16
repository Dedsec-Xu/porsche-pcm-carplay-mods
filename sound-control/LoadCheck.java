public class LoadCheck {
    public static void main(String[] args) throws Exception {
        String[] names = {"de.audi.atip.model.ToneModelBank", "de.audi.tghu.tone.hmi.pgen2high.ToneScreenBag1",
            "de.audi.tone.app.dsi.DSISoundHandler", "de.audi.tone.app.dsi.DSISoundListenerImpl",
            "de.audi.app.terminalmode.smartphone.carplay.CarPlayDSIManager", "pcm.PromptRuntime",
            "de.audi.tghu.tone.hmi.pgen2high.PromptLevelPanel"};
        for (String name: names) {
            Class<?> cls = Class.forName(name, false, LoadCheck.class.getClassLoader());
            System.out.println(name + " methods=" + cls.getDeclaredMethods().length);
        }
    }
}
