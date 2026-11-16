import pcm.PromptLevelController;
import de.audi.atip.hmi.modelaccess.RangeModelApp;
import java.lang.reflect.*;

public class ControllerTest {
    static class Store implements PromptLevelController.Backend {
        int saved = 3;
        boolean fail;
        int errors;
        public int load() { return saved; }
        public void applyAndSave(int value) throws Exception {
            if (fail) throw new Exception("simulated persistence failure");
            saved = value;
        }
        public void reportFailure(Exception e) { errors++; }
    }
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        final int[] displayed = {0};
        RangeModelApp model = (RangeModelApp) Proxy.newProxyInstance(
            ControllerTest.class.getClassLoader(), new Class[] {RangeModelApp.class},
            new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("setValue")) displayed[0] = ((Integer)args[0]).intValue();
                    if (method.getReturnType() == Integer.TYPE) return Integer.valueOf(displayed[0]);
                    if (method.getReturnType() == Boolean.TYPE) return Boolean.FALSE;
                    return null;
                }
            });
        Store store = new Store();
        PromptLevelController c = new PromptLevelController(model, store, 5);
        require(c.getLevel() == 3 && displayed[0] == 3, "restore saved level");
        c.increment(0, Integer.MAX_VALUE, 0);
        require(c.getLevel() == 5 && c.isDuckingBypassed(), "upper limit and bypass");
        c.decrement(0, Integer.MAX_VALUE, 0);
        require(c.getLevel() == 1 && !c.isDuckingBypassed(), "lower limit");
        c.increment(0, -1, 0);
        require(c.getLevel() == 1, "negative step ignored");
        store.fail = true;
        c.select(4);
        require(c.getLevel() == 1 && displayed[0] == 1 && store.saved == 1 && store.errors == 1,
            "failed backend must not falsely update selection");
        store.fail = false;
        c.select(4);
        require(c.getLevel() == 4 && store.saved == 4, "successful change");
        store.saved = 99;
        c = new PromptLevelController(model, store, 5);
        require(c.getLevel() == 5, "invalid stored setting falls back to no reduction");
        System.out.println("PASS: persistence restore, limits, bypass, invalid inputs, backend failure");
    }
}
