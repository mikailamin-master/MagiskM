package pro.magisk.views;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Console extends ScrollView {

    private static final int COLOR_INFO = Color.WHITE;
    private static final int COLOR_SUCCESS = Color.parseColor("#00E676");
    private static final int COLOR_WARN = Color.parseColor("#FFD740");
    private static final int COLOR_ERROR = Color.parseColor("#FF5252");
    private static final int COLOR_HEADER = Color.parseColor("#40C4FF");

    private LinearLayout container;

    public Console(@NonNull Context context) {
        super(context);
        init();
    }

    public Console(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Console(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container);

        setOnLongClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < container.getChildCount(); i++) {
                TextView tv = (TextView) container.getChildAt(i);
                sb.append(tv.getText()).append("\n");
            }
            String text = sb.toString().trim();
            if (text.isEmpty()) return true;
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("== logs ==", text));
            Toast.makeText(getContext(), "Console logs was copied!", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    public void appendLog(final String line) {
        appendLog(line, COLOR_INFO);
    }

    public void appendLog(final String line, int color) {
        post(() -> {
            TextView tv = new TextView(getContext());
            tv.setText(line);
            tv.setTextColor(color);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setLineSpacing(4f, 1f);
            tv.setPadding(0, dp(1), 0, dp(1));
            container.addView(tv);
            scrollToBottom();
        });
    }

    public void appendSuccess(final String line) {
        appendLog(line, COLOR_SUCCESS);
    }

    public void appendWarn(final String line) {
        appendLog(line, COLOR_WARN);
    }

    public void appendError(final String line) {
        appendLog(line, COLOR_ERROR);
    }

    public void appendHeader(final String line) {
        appendLog(line, COLOR_HEADER);
    }

    public void clear() {
        post(() -> container.removeAllViews());
    }

    private void scrollToBottom() {
        post(() -> fullScroll(android.view.View.FOCUS_DOWN));
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
