/** Runs in a separate, small J9 process. Never initializes HMI applications. */
public final class DeviceLoadCheck {
    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            try {
                Class c = Class.forName(args[i], false, DeviceLoadCheck.class.getClassLoader());
                System.out.println("PASS " + args[i] + " methods=" + c.getDeclaredMethods().length);
            } catch (Throwable error) {
                System.out.println("FAIL " + args[i]);
                error.printStackTrace();
            }
        }
    }
}
