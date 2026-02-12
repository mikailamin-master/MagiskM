package pro.magisk;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import pro.magisk.databinding.ActivityMainBinding;

import pro.magisk.utils.*;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorSurfaceVariant));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.colorSurface));

        binding.install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new EnvFix().install(getApplicationContext());
                android.widget.Toast.makeText(getApplicationContext(), "Env files are Copied", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
}