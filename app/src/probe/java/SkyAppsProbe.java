import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/** Runs as the ADB shell user, using the same context setup as the bundled scrcpy server. */
public final class SkyAppsProbe {
    private static String encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    private static Intent launcher(PackageManager pm, String packageName, boolean television) {
        Intent intent = television ? pm.getLeanbackLaunchIntentForPackage(packageName)
                : pm.getLaunchIntentForPackage(packageName);
        if (intent == null) intent = television ? pm.getLaunchIntentForPackage(packageName)
                : pm.getLeanbackLaunchIntentForPackage(packageName);
        return intent;
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class.forName("com.genymobile.scrcpy.Workarounds").getMethod("apply").invoke(null);
        Context context = (Context) Class.forName("com.genymobile.scrcpy.FakeContext")
                .getMethod("get").invoke(null);
        PackageManager pm = context.getPackageManager();
        boolean television = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        PrintWriter output = new PrintWriter(System.out, true);
        output.println("READY");
        String command;
        while ((command = input.readLine()) != null) {
            if (command.equals("LIST")) {
                for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS)) {
                    String label = pm.getApplicationLabel(app).toString();
                    Intent launch = launcher(pm, app.packageName, television);
                    if (launch != null && launch.getComponent() != null) {
                        try {
                            label = pm.getActivityInfo(launch.getComponent(), 0).loadLabel(pm).toString();
                        } catch (PackageManager.NameNotFoundException ignored) { }
                    }
                    output.println("APP\t" + app.packageName + "\t" + encode(label.getBytes("UTF-8"))
                            + "\t" + ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? "1" : "0")
                            + "\t" + (app.enabled ? "1" : "0"));
                }
                output.println("END");
            } else if (command.startsWith("ICON\t")) {
                String name = command.substring(5);
                try {
                    ApplicationInfo app = pm.getApplicationInfo(name, PackageManager.GET_DISABLED_COMPONENTS);
                    Intent intent = launcher(pm, name, television);
                    Drawable icon = intent == null ? pm.getApplicationIcon(app) : pm.getActivityIcon(intent);
                    Bitmap image = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                    icon.setBounds(0, 0, 96, 96);
                    icon.draw(new Canvas(image));
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    image.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                    image.recycle();
                    output.println("ICON\t" + encode(bytes.toByteArray()));
                } catch (Exception e) {
                    output.println("ERROR");
                }
            } else if (command.equals("QUIT")) {
                break;
            }
        }
    }
}
