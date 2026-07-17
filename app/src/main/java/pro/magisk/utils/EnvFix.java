package pro.magisk.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class EnvFix {

    public static void exec(Context context) throws Exception {
        File install_dir = new File(context.getFilesDir(), "install");

        if (!install_dir.exists()) {
            install_dir.mkdirs();
        }

        ApplicationInfo info = context.getApplicationInfo();

        File[] libs = new File(info.nativeLibraryDir).listFiles((dir, name) ->
                name.startsWith("lib") && name.endsWith(".so")
        );

        if (libs != null) {
            for (File lib : libs) {
                String name = lib.getName();
                name = name.substring(3, name.length() - 3);
                File link = new File(install_dir, name);
                try {
                    link.delete();
                } catch (Exception ignored) {
                }

                Os.symlink(lib.getAbsolutePath(), link.getAbsolutePath());
            }
        }

        String abi32 = Build.SUPPORTED_32_BIT_ABIS.length > 0
                ? Build.SUPPORTED_32_BIT_ABIS[0]
                : null;

        if (Process.is64Bit() && abi32 != null) {
            ZipFile apk = new ZipFile(info.sourceDir);
            ZipEntry entry = apk.getEntry("lib/" + abi32 + "/libmagisk.so");

            if (entry != null) {
                InputStream input = apk.getInputStream(entry);
                write_to_file(input, new File(install_dir, "magisk32"));
                input.close();
            }

            apk.close();
        }

        String[] assets = {
                "boot_patch.sh",
                "util_functions.sh"
        };

        for (String asset : assets) {
            if (!extract_asset(context, asset, new File(install_dir, asset))) {
                throw new IOException("Failed to extract " + asset);
            }
        }
    }

    private static boolean extract_asset(Context context, String asset_name, File destination) {
        try (InputStream input = context.getAssets().open(asset_name);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void write_to_file(InputStream input, File output) throws IOException {
        try (FileOutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int len;

            while ((len = input.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
}