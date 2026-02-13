package pro.magisk;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import pro.magisk.databinding.ActivityMainBinding;
import pro.magisk.utils.*;

import com.topjohnwu.superuser.Shell;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.colorSurface));

        binding.versionTxt.setText(getResources().getString(R.string.installed) + ": " + MagiskInfo.getMagiskVersion().toUpperCase());
        int ver_code = MagiskInfo.getMagiskVersionCode();
        Shell.Result result = Shell.cmd("grep -q skip_initramfs /proc/cmdline && echo false || echo true").exec();

        String ramdisk = result.getOut().isEmpty() ? "unknown" : result.getOut().get(0);
        binding.ramdiskTxt.setText(getResources().getString(R.string.ramdisk) + ": " + ramdisk);
        if (MagiskInfo.getZygiskStatus()) {
            binding.zygiskStatusTxt.setText(getResources().getString(R.string.zygisk_is_enabled));
        } else {
            binding.zygiskStatusTxt.setText(getResources().getString(R.string.zygisk_is_disabled));
        }
    }
}