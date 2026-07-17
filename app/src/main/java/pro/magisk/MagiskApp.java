package pro.magisk;

import android.app.Application;
import android.content.Intent;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MagiskApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {

            StringWriter writer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));

            Intent intent = new Intent(this, DebugActivity.class);
            intent.putExtra("crash", writer.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });
    }
}