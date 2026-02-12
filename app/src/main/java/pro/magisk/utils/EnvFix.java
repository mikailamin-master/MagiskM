package pro.magisk.utils;

import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class EnvFix {

    public static String install_dir = "/data/user_de/0/pro.magisk/install";

    public static void install(Context context) {
        try {
            File installDirFile = new File(install_dir);
            if (installDirFile.exists()) {
                deleteDir(installDirFile);
            }
            installDirFile.mkdirs();

            // Detect main ABI of the device
            String mainAbi;
            if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                mainAbi = Build.SUPPORTED_64_BIT_ABIS[0]; // e.g., arm64-v8a
            } else if (Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                mainAbi = Build.SUPPORTED_32_BIT_ABIS[0]; // fallback
            } else {
                mainAbi = Build.SUPPORTED_ABIS[0]; // generic fallback
            }

            // Copy main ABI .so files from assets
            String assetPath = "lib/" + mainAbi;
            String[] libs;
            try {
                libs = context.getAssets().list(assetPath);
            } catch (IOException e) {
                libs = null;
            }
            if (libs != null) {
                for (String libName : libs) {
                    InputStream in = context.getAssets().open(assetPath + "/" + libName);

                    // remove first "lib" এবং last ".so"
                    String cleanName = libName;
                    if (cleanName.startsWith("lib")) {
                        cleanName = cleanName.substring(3);
                    }
                    if (cleanName.endsWith(".so")) {
                        cleanName = cleanName.substring(0, cleanName.length() - 3);
                    }

                    copyStream(in, new File(install_dir, cleanName));
                }
            }

            // Copy 32-bit libmagisk if 64-bit process
            if (Process.is64Bit() && Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                String abi32 = Build.SUPPORTED_32_BIT_ABIS[0];
                String path = "lib/" + abi32 + "/libmagisk.so";
                try (InputStream entry = context.getAssets().open(path)) {
                    copyStream(entry, new File(install_dir, "magisk32"));
                } catch (IOException ignored) {
                }
            }

            // Copy scripts from assets
            String[] scripts = {
                    "util_functions.sh",
                    "boot_patch.sh",
                    "addon.d.sh"
            };
            for (String script : scripts) {
                try (InputStream in = context.getAssets().open(script)) {
                    copyStream(in, new File(install_dir, script));
                } catch (IOException ignored) {
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void copyStream(InputStream in, File dest) throws IOException {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        in.close();
    }

    private static void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        dir.delete();
    }
}