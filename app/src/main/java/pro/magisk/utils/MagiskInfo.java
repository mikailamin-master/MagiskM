package pro.magisk.utils;

import com.topjohnwu.superuser.Shell;

public class MagiskInfo {
    public static String getMagiskVersion() {
        Shell.Result result = Shell.cmd("magisk -v").exec();
        if (!result.isSuccess()) return "N/A";
        return result.getOut().get(0);
    }

    public static int getMagiskVersionCode() {
        Shell.Result result = Shell.cmd("magisk -V").exec();
        if (!result.isSuccess()) return -1;
        return Integer.parseInt(result.getOut().get(0));
    }

    public static boolean getZygiskStatus() {
        Shell.Result result = Shell.cmd("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk';\"").exec();
        if (!result.isSuccess()) {
            return false;
        } else if (result.getOut().size() == 0) {
            Shell.cmd("magisk --sqlite \"INSERT INTO settings (key, value) VALUES ('zygisk', 0);\"").exec();
            return false;
        }
        return result.getOut().get(0).toString().replaceAll("value=", "").equals("1");
    }
}
