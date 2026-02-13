package pro.magisk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.DataOutputStream;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import pro.magisk.databinding.ActivityRequestBinding;

public class RequestActivity extends AppCompatActivity {

    private ActivityRequestBinding binding;
    private String action;
    private String app_pkg;
    private String app_name;
    private int uid = -1;
    private int pid = -1;
    private File output;
    private boolean isWritten = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRequestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
	        getWindow().setBackgroundBlurRadius(15);
        }
        if (!getIntent().hasExtra("action")) finishAndRemoveTask();
        if (!getIntent().hasExtra("action")) return;
        action = getIntent().getStringExtra("action");
        assert action != null;
        if (action.equals("request")) {
            uid = getIntent().getIntExtra("uid", -1);
        } else {
            uid = getIntent().getIntExtra("from.uid", -1);
            binding.getRoot().setVisibility(View.GONE);
        }
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null && packages.length > 0) {
            app_pkg = packages[0];
            try {
                ApplicationInfo app_info = pm.getApplicationInfo(app_pkg, 0);

                app_name = pm.getApplicationLabel(app_info).toString();
                Drawable app_icon = pm.getApplicationIcon(app_info);

                binding.appNameTxt.setText(app_name);
                binding.appPkgTxt.setText(app_pkg);
                binding.appIconImg.setImageDrawable(app_icon);

            } catch (PackageManager.NameNotFoundException e) {
                binding.appNameTxt.setText("N/A");
                binding.appPkgTxt.setText("N/A");
                app_name = "N/A";
            }
        }
        pid = getIntent().getIntExtra("pid", -1);
        if (action.equals("request")) {
            String fifoPath = getIntent().getStringExtra("fifo");
            assert fifoPath != null;
            output = new File(fifoPath);
        } else {
            int policy = getIntent().getIntExtra("policy", -1);
            if (policy == 2) {
                Toast.makeText(getApplicationContext(), app_name + " " + getResources().getString(R.string.authorised) + "!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), app_name + " " + getResources().getString(R.string.rejected) + "!", Toast.LENGTH_SHORT).show();
            }
            finishAndRemoveTask();
        }
        binding.allow.setOnClickListener(view -> write_response(2));
        binding.deny.setOnClickListener(view -> write_response(1));
        binding.untilSlider.setValue(0);
        binding.notiSwitch.setEnabled(binding.untilSlider.getValue() != -1);
        binding.untilSlider.setLabelFormatter(value -> {
            if ((int) value == -1) {
                binding.notiSwitch.setEnabled(false);
                return " " + getResources().getString(R.string.allow_only_for_this_process) + " ";
            } else if ((int) value == 0) {
                if (!binding.notiSwitch.isChecked()) binding.notiSwitch.setChecked(true);
                binding.notiSwitch.setEnabled(true);
                return " " + getResources().getString(R.string.allow_for_forever) + " ";
            } else {
                if (!binding.notiSwitch.isChecked()) binding.notiSwitch.setChecked(true);
                binding.notiSwitch.setEnabled(true);
                return "  " + getResources().getString(R.string.allow_for) + ": (" + (int) value + ") " + getResources().getString(R.string.minute) +  " ";
            }
        });
    }

    @Override
    public void onPause() {
        write_response(1);
        super.onPause();
    }

    private void write_response(int _policy) {
        if (!isWritten) {
            isWritten = true;
            try {
                FileOutputStream fos = new FileOutputStream(output);
                DataOutputStream dos = new DataOutputStream(fos);
                dos.writeInt(_policy);
                dos.flush();
                dos.close();
                fos.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        finishAndRemoveTask();
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }
}