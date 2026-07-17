package pro.magisk.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Patch {
    
    public interface PatchCallback {
        void onLog(String line, int color);
        void onResult(boolean success, String message);
    }

    public interface EnvironmentModifier {
        void modify(Map<String, String> env);
    }

    private static final int COLOR_INFO = 0xffffffff;
    private static final int COLOR_SUCCESS = 0xff00e676;
    private static final int COLOR_ERROR = 0xffff5252;
    private static final int COLOR_WARN = 0xffffd740;

    private final Context context;
    private final PatchCallback callback;
    private final ExecutorService executor;

    public Patch(Context context, PatchCallback callback) {
        this.context = context;
        this.callback = callback;
        this.executor = Executors.newSingleThreadExecutor();
    }

    private void prepare_binaries(File install_dir) throws Exception {
        ApplicationInfo info = context.getApplicationInfo();
        File[] libs = new File(info.nativeLibraryDir).listFiles((dir, name) -> name.startsWith("lib") && name.endsWith(".so"));
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
        String abi32 = Build.SUPPORTED_32_BIT_ABIS.length > 0 ? Build.SUPPORTED_32_BIT_ABIS[0] : null;
        if (android.os.Process.is64Bit() && abi32 != null) {
            try (ZipFile apk = new ZipFile(info.sourceDir)) {
                ZipEntry entry = apk.getEntry("lib/" + abi32 + "/libmagisk.so");
                if (entry != null) {
                    try (InputStream input = apk.getInputStream(entry)) {
                        write_to_file(input, new File(install_dir, "magisk32"));
                    }
                }
            }
        }
    }

    private void write_to_file(InputStream input, File output) throws IOException {
        try (FileOutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
    
    public void patch_file(final Uri boot_image_uri) {
        executor.execute(() -> {
            callback.onLog("== Magisk Boot Patcher ==", COLOR_INFO);
            File filesDir = context.getFilesDir();
            callback.onLog("- filesDir: " + filesDir, COLOR_INFO);
            callback.onLog("- filesDir exists: " + filesDir.exists(), COLOR_INFO);
            callback.onLog("- filesDir canWrite: " + filesDir.canWrite(), COLOR_INFO);
            callback.onLog("- filesDir canRead: " + filesDir.canRead(), COLOR_INFO);
            File install_dir = new File(filesDir, "patch");
            try {
                if (install_dir.exists()) {
                    callback.onLog("- Cleaning existing directory", COLOR_INFO);
                    File[] children = install_dir.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            delete_recursive(child);
                        }
                    }
                } else {
                    callback.onLog("- Creating directory: " + install_dir.getAbsolutePath(), COLOR_INFO);
                    if (!install_dir.mkdirs()) {
                        callback.onLog("- mkdirs returned false", COLOR_ERROR);
                        callback.onLog("- exists after failure: " + install_dir.exists(), COLOR_ERROR);
                        callback.onResult(false, "Failed to create working directory");
                        return;
                    }
                    callback.onLog("- Directory created successfully", COLOR_SUCCESS);
                }
                prepare_binaries(install_dir);
                String[] assets = {
                    "boot_patch.sh",
                    "util_functions.sh"
                };
                for (String asset : assets) {
                    callback.onLog("- Extracting " + asset, COLOR_INFO);
                    if (!extract_asset(asset, new File(install_dir, asset))) {
                        delete_recursive(install_dir);
                        callback.onResult(false, "Failed to extract " + asset);
                        return;
                    }
                }
                File boot_img = new File(install_dir, "boot.img");
                callback.onLog("- Copying boot image", COLOR_INFO);
                if (!copy_from_uri(boot_image_uri, boot_img)) {
                    delete_recursive(install_dir);
                    return;
                }
                callback.onLog("- Creating config", COLOR_INFO);
                File config = new File(install_dir, ".config");
                String cfg =
                        "KEEPVERITY=true\n" +
                        "KEEPFORCEENCRYPT=true\n" +
                        "RECOVERYMODE=false\n" +
                        "VENDORBOOT=false\n" +
                        "PATCHVBMETAFLAG=false\n" +
                        "LEGACYSAR=false\n" +
                        "SYSTEM_ROOT=false\n" +
                        "BOOTMODE=true\n";
                try (FileOutputStream out = new FileOutputStream(config)) {
                    out.write(cfg.getBytes());
                }
                run_process(new String[]{
                        "chmod",
                        "-R",
                        "755",
                        install_dir.getAbsolutePath()
                }, install_dir, null);
                callback.onLog("- Environment ready", COLOR_SUCCESS);

                // CRITICAL FIX: The process stopped here originally. 
                // Adding the execution call to trigger the actual shell script.
                patch_boot(install_dir);

            } catch (Exception e) {
                callback.onLog(e.toString(), COLOR_ERROR);
                delete_recursive(install_dir);
                callback.onResult(false, e.getMessage());
            }
        });
    }

    private boolean patch_boot(File install_dir) throws Exception {
        callback.onLog("- Running boot_patch.sh", COLOR_WARN);
        boolean success = run_process(new String[]{
                "/system/bin/sh",
                "boot_patch.sh",
                "boot.img"
        }, install_dir, env -> {
            env.put("KEEPVERITY", "true");
            env.put("KEEPFORCEENCRYPT", "true");
            env.put("RECOVERYMODE", "false");
            env.put("VENDORBOOT", "false");
            env.put("PATCHVBMETAFLAG", "false");
            env.put("LEGACYSAR", "false");
            env.put("SYSTEM_ROOT", "false");
            env.put("BOOTMODE", "true");
        });
        if (!success) {
            callback.onResult(false, "boot_patch.sh failed");
            return false;
        }
        File patched = new File(install_dir, "patched_boot.img");
        if (!patched.exists()) {
            callback.onResult(false, "patched_boot.img not found");
            return false;
        }
        File download_dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File output = new File(download_dir, "patched_boot.img");
        if (!copy_file(patched, output)) {
            callback.onResult(false, "Failed to export patched image");
            return false;
        }
        
        delete_recursive(install_dir);
        callback.onLog("****************************", COLOR_INFO);
        callback.onLog("Patched boot image created", COLOR_SUCCESS);
        callback.onLog(output.getAbsolutePath(), COLOR_INFO);
        callback.onLog("****************************", COLOR_INFO);
        delete_recursive(install_dir);
        callback.onResult(true, "Success");
        return true;
    }

    private boolean run_process(String[] command, File working_dir, EnvironmentModifier modifier) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(working_dir);
        builder.redirectErrorStream(true);
        if (modifier != null) {
            modifier.modify(builder.environment());
        }
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                callback.onLog(line, COLOR_INFO);
            }
        }
        int exit = process.waitFor();
        return exit == 0;
    }

    private boolean extract_asset(String asset_name, File destination) {
        try (InputStream input = context.getAssets().open(asset_name);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return true;
        } catch (Exception e) {
            callback.onLog("Asset extraction failed: " + e.getMessage(), COLOR_ERROR);
            return false;
        }
    }

    private boolean copy_from_uri(Uri uri, File destination) {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                callback.onLog("Unable to open input stream", COLOR_ERROR);
                return false;
            }
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return true;
        } catch (Exception e) {
            callback.onLog("Copy failed: " + e.getMessage(), COLOR_ERROR);
            return false;
        }
    }

    private boolean copy_file(File source, File destination) {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return true;
        } catch (Exception e) {
            callback.onLog("Export failed: " + e.getMessage(), COLOR_ERROR);
            return false;
        }
    }

    private void delete_recursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete_recursive(child);
                }
            }
        }
        file.delete();
    }
}
