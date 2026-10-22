package id.ganicahyo.agroiot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.TransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final String PREFS = "thingspeak_config";
    private static final int PROFILE_COUNT = 5;
    private static final String[] DEFAULT_LABELS = {
            "SUHU UDARA", "KELEMBABAN UDARA", "TEKANAN UDARA", "ALTITUDE",
            "INTENSITAS CAHAYA", "PAR", "VPD", "DURASI SINAR MATAHARI"
    };
    private static final String[] DEFAULT_UNITS = {"°C", "%", "hPa", "mdpl", "lux", "µmol/m²/s", "kPa", "jam"};
    private SharedPreferences prefs;
    private EditText titleInput;
    private Spinner activeSpinner;
    private ArrayAdapter<String> activeAdapter;
    private final EditText[] profileNames = new EditText[PROFILE_COUNT];
    private final EditText[] channelIds = new EditText[PROFILE_COUNT];
    private final EditText[] readKeys = new EditText[PROFILE_COUNT];
    private final EditText[][] fieldNames = new EditText[PROFILE_COUNT][8];
    private final EditText[][] fieldUnits = new EditText[PROFILE_COUNT][8];
    private final TextView[] keyToggles = new TextView[PROFILE_COUNT];
    private final List<String> profileLabels = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07131F);
        getWindow().setNavigationBarColor(0xFF07131F);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildContent());
        loadValues();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07131F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(20));
        scroll.addView(root);

        TextView top = text("PENGATURAN STASIUN CUACA", 20, 0xFFF4F7F9, true);
        root.addView(top);
        root.addView(text("Judul dashboard dapat diubah. Simpan hingga 5 profil ThingSpeak dan pilih satu profil untuk ditampilkan.", 12, 0xFF9DB0BC, false), margin(0, 5, 0, 12));

        LinearLayout global = panel();
        root.addView(global, margin(0, 0, 0, 12));
        global.addView(text("JUDUL DASHBOARD", 11, 0xFF29C6C7, true));
        titleInput = edit("STASIUN CUACA DESA TURUS", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        global.addView(titleInput, margin(0, 6, 0, 12));
        global.addView(text("PROFIL AKTIF DI DASHBOARD", 11, 0xFF29C6C7, true));
        activeSpinner = new Spinner(this);
        activeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profileLabels);
        activeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activeSpinner.setAdapter(activeAdapter);
        global.addView(activeSpinner, margin(0, 5, 0, 0));

        for (int i = 0; i < PROFILE_COUNT; i++) root.addView(profilePanel(i), margin(0, 0, 0, 12));

        TextView save = button("SIMPAN PENGATURAN");
        save.setOnClickListener(v -> saveValues());
        root.addView(save, margin(0, 4, 0, 6));
        TextView cancel = secondaryButton("BATAL / KEMBALI");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);

        LinearLayout about = panel();
        about.setGravity(Gravity.CENTER);
        about.addView(text("AGRO-IoT", 15, 0xFF29C6C7, true));
        about.addView(text("Versi 3.0.0", 11, 0xFFF4F7F9, true), margin(0, 4, 0, 0));
        about.addView(text("ganicahyo@2026", 11, 0xFF9DB0BC, false), margin(0, 3, 0, 0));
        root.addView(about, margin(0, 16, 0, 4));
        return scroll;
    }

    private View profilePanel(int slot) {
        LinearLayout box = panel();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("PROFIL " + (slot + 1), 13, 0xFF29C6C7, true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView defaultHint = text("THINGSpeak", 9, 0xFF9DB0BC, true);
        heading.addView(defaultHint);
        box.addView(heading);

        profileNames[slot] = edit("Nama stasiun / channel", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(profileNames[slot], margin(0, 7, 0, 0));
        channelIds[slot] = edit("Channel ID", InputType.TYPE_CLASS_NUMBER);
        box.addView(channelIds[slot], margin(0, 4, 0, 0));

        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        readKeys[slot] = edit("Read API Key (kosong untuk channel publik)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyRow.addView(readKeys[slot], new LinearLayout.LayoutParams(0, dp(46), 1f));
        keyToggles[slot] = secondaryButton("SEMBUNYIKAN");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(104), dp(42)); tp.leftMargin = dp(6);
        keyRow.addView(keyToggles[slot], tp);
        final int idx = slot;
        keyToggles[slot].setOnClickListener(v -> toggleKey(idx));
        box.addView(keyRow, margin(0, 4, 0, 10));

        TextView fieldsLabel = text("NAMA FIELD & SATUAN (KOSONG = OTOMATIS / DEFAULT)", 10, 0xFF9DB0BC, true);
        box.addView(fieldsLabel, margin(0, 0, 0, 2));
        for (int f = 0; f < 8; f++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView index = text(String.format(java.util.Locale.US, "%02d", f + 1), 10, 0xFF29C6C7, true);
            index.setGravity(Gravity.CENTER);
            row.addView(index, new LinearLayout.LayoutParams(dp(25), dp(43)));
            fieldNames[slot][f] = edit("Nama Field " + (f + 1), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            fieldUnits[slot][f] = edit("Satuan", InputType.TYPE_CLASS_TEXT);
            row.addView(fieldNames[slot][f], new LinearLayout.LayoutParams(0, dp(43), 2.2f));
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(43), 0.9f); up.leftMargin = dp(5);
            row.addView(fieldUnits[slot][f], up);
            box.addView(row);
        }
        return box;
    }

    private void loadValues() {
        titleInput.setText(prefs.getString("app_title", "STASIUN CUACA DESA TURUS"));
        int active = Math.max(0, Math.min(PROFILE_COUNT - 1, prefs.getInt("active_slot", 0)));
        for (int i = 0; i < PROFILE_COUNT; i++) {
            String name = prefs.getString(key(i, "name"), "");
            String id = prefs.getString(key(i, "id"), "");
            String meta = prefs.getString(key(i, "meta_name"), "");
            profileNames[i].setText(name);
            channelIds[i].setText(id);
            readKeys[i].setText(prefs.getString(key(i, "key"), ""));
            profileLabels.add((i + 1) + ". " + (!name.trim().isEmpty() ? name.trim() : (!meta.trim().isEmpty() ? meta.trim() : "Stasiun " + (i + 1))) + (id.trim().isEmpty() ? "" : " · " + id.trim()));
            for (int f = 0; f < 8; f++) {
                fieldNames[i][f].setText(prefs.getString(key(i, "f" + f + "_name"), ""));
                fieldUnits[i][f].setText(prefs.getString(key(i, "f" + f + "_unit"), ""));
            }
        }
        activeAdapter.notifyDataSetChanged();
        activeSpinner.setSelection(active);
    }

    private void saveValues() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) title = "STASIUN CUACA";
        int active = activeSpinner.getSelectedItemPosition();
        SharedPreferences.Editor e = prefs.edit().putString("app_title", title).putInt("active_slot", Math.max(0, active));
        for (int i = 0; i < PROFILE_COUNT; i++) {
            e.putString(key(i, "name"), profileNames[i].getText().toString().trim());
            e.putString(key(i, "id"), channelIds[i].getText().toString().trim());
            e.putString(key(i, "key"), readKeys[i].getText().toString().trim());
            for (int f = 0; f < 8; f++) {
                e.putString(key(i, "f" + f + "_name"), fieldNames[i][f].getText().toString().trim());
                e.putString(key(i, "f" + f + "_unit"), fieldUnits[i][f].getText().toString().trim());
            }
        }
        e.apply();
        Toast.makeText(this, "Pengaturan tersimpan", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String key(int slot, String suffix) { return "s" + slot + "_" + suffix; }

    private void toggleKey(int slot) {
        EditText input = readKeys[slot];
        boolean masked = input.getTransformationMethod() instanceof PasswordTransformationMethod;
        input.setTransformationMethod(masked ? null : PasswordTransformationMethod.getInstance());
        keyToggles[slot].setText(masked ? "SEMBUNYIKAN" : "TAMPILKAN");
        input.setSelection(input.length());
    }

    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(13), dp(12), dp(13), dp(12));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFF122B3B); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), 0xFF214457); l.setBackground(bg); return l;
    }

    private EditText edit(String hint, int inputType) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setHintTextColor(0xFF8199A9); e.setTextColor(Color.WHITE); e.setTextSize(12); e.setInputType(inputType); e.setPadding(dp(10), 0, dp(10), 0); e.setBackgroundResource(R.drawable.bg_edit); return e;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private TextView button(String s) {
        TextView t = text(s, 12, 0xFF07131F, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(12), dp(12), dp(12), dp(12)); t.setBackgroundResource(R.drawable.bg_button); return t;
    }

    private TextView secondaryButton(String s) {
        TextView t = text(s, 9, 0xFFF4F7F9, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(4), dp(6), dp(4), dp(6)); t.setBackgroundResource(R.drawable.bg_chip); return t;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
