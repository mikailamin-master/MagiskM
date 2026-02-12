package pro.magisk;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.DataOutputStream;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import pro.magisk.databinding.ActivityRequestBinding;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

public class RequestActivity extends AppCompatActivity {

    private ActivityRequestBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRequestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
	        getWindow().setBackgroundBlurRadius(15);
        }
        String fifoPath = getIntent().getStringExtra("fifo");
        final File output = new File(fifoPath);

        binding.allow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    FileOutputStream fos = new FileOutputStream(output);
                    DataOutputStream dos = new DataOutputStream(fos);
                    dos.writeInt(2);
                    dos.flush();
                    dos.close();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finish();
            }
        });
        binding.deny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    FileOutputStream fos = new FileOutputStream(output);
                    DataOutputStream dos = new DataOutputStream(fos);
                    dos.writeInt(1);
                    dos.flush();
                    dos.close();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finish();
            }
        });
        binding.untilSlider.setLabelFormatter(new LabelFormatter() {
            @NonNull
            @Override
            public String getFormattedValue(float value) {
                if ((int) value == -1) {
                    return "Allow for only this proccess!";
                } else if ((int) value == 0) {
                    return "Allow for forever!";
                }else {
                    return "Allow for: (" + String.valueOf((int) value) + ") minute";
                }
            }
        });
    }
}