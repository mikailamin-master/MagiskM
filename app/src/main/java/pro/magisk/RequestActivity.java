package pro.magisk;

import android.os.Bundle;
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
        if (Objects.equals(getIntent().getStringExtra("action"), "request")) {
            uid = getIntent().getIntExtra("uid", -1);
        } else {
            uid = getIntent().getIntExtra("from.uid", -1);
        }
        pid = getIntent().getIntExtra("pid", -1);
        String fifoPath = getIntent().getStringExtra("fifo");
        assert fifoPath != null;
        output = new File(fifoPath);

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