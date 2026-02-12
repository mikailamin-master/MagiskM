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

        binding.versionTxt.setText("Version: " + MagiskInfo.getMagiskVersion());
        int ver_code = MagiskInfo.getMagiskVersionCode();
        if (ver_code != -1) {
            binding.versionCodeTxt.setText("Version code: " + ver_code);
        } else {
            binding.versionCodeTxt.setText("Version code: N/A");
        }
        if (MagiskInfo.getZygiskStatus()) {
            binding.zygiskStatusTxt.setText("Zygisk is enabled");
        } else {
            binding.zygiskStatusTxt.setText("Zygisk is disabled");
        }
    }
}