package pro.magisk;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class DebugActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setTextIsSelectable(true);
        text.setText(getIntent().getStringExtra("crash"));
        setContentView(text);
    }
}