package id.ganicahyo.agroiot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String PREFS = "thingspeak_config";
    static final int PROFILE_COUNT = 5;
    static final int REQUEST_CREATE_CSV = 720;
    static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    static final long REFRESH_MS = 30_000L;

    private static final int PARAM_CUSTOM = -1;
    private static final int PARAM_UNSET = -2;
    // Parameter preset untuk delapan field ThingSpeak. ET₀ dan Δ tekanan dapat dipetakan ke field mana pun.
    private static final String[] PARAM_LABELS = {
            "SUHU UDARA", "KELEMBAPAN UDARA", "TEKANAN UDARA", "DELTA TEKANAN UDARA",
            "ALTITUDE", "INTENSITAS CAHAYA", "PAR", "VPD", "LAMA PENYINARAN MATAHARI",
            "ET₀", "KECEPATAN ANGIN", "ARAH ANGIN", "CURAH HUJAN", "HUJAN / TIDAK HUJAN"
    };
    private static final String[] PARAM_UNITS = {
            "°C", "%", "hPa", "hPa", "mdpl", "lux", "µmol/m²/s", "kPa", "jam",
            "mm/hari", "m/s", "°", "mm", ""
    };
    // Ikon visual yang mengikuti parameter; PAR dan ET₀ memakai kombinasi ikon.
    private static final String[] PARAM_ICONS = {
            "🌡️", "💧", "◉", "↕", "⛰️", "☀️", "☼🌱", "💧🌡️", "☀️⏱️",
            "🌱💧", "💨", "🧭", "🌧️", "🌦️"
    };
    private static final String[] DEFAULT_LABELS = {
            "SUHU UDARA", "KELEMBAPAN UDARA", "TEKANAN UDARA", "ALTITUDE",
            "INTENSITAS CAHAYA", "PAR", "VPD", "LAMA PENYINARAN MATAHARI"
    };
    private static final String[] DEFAULT_UNITS = {"°C", "%", "hPa", "mdpl", "lux", "µmol/m²/s", "kPa", "jam"};
    private static final int[] DEFAULT_PARAM_BY_FIELD = {0, 1, 2, 4, 5, 6, 7, 8};
    private static final int[] CARD_FILL = {
            0xFF07314A, 0xFF073923, 0xFF41230D, 0xFF29113F,
            0xFF3D3510, 0xFF063842, 0xFF40131B, 0xFF082650
    };
    private static final int[] CARD_STROKE = {
            0xFF22D8F1, 0xFF46E66F, 0xFFFF8D23, 0xFFB94DF7,
            0xFFFFD52A, 0xFF12D8E7, 0xFFFF5159, 0xFF168DFF
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView stationTitle, date, time, dataTime, accessTime, netMode, rssi, channelView;
    private TextView weather, heat, sourceStatus, apiStatus, fieldStatus, status, refresh, download;
    private View statusDot;
    private final TextView[] valueViews = new TextView[8];
    private final TextView[] labelViews = new TextView[8];
    private final TextView[] unitViews = new TextView[8];
    private final TextView[] iconViews = new TextView[8];
    private boolean requestRunning = false;
    private long pendingAccessMillis = 0L;
    private String pendingCsvStart = "", pendingCsvEnd = "";
    private boolean pendingCsvAll = true;
    private int pendingCsvProfile = 0;
    private String pendingCsvChannel = "", pendingCsvKey = "";

    private final Runnable clockTick = new Runnable() {
        @Override public void run() { updateClock(); main.postDelayed(this, 1000L); }
    };
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() { loadThingSpeak(false); main.postDelayed(this, REFRESH_MS); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07131F);
        getWindow().setNavigationBarColor(0xFF07131F);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        bindViews();
        buildFieldCards();
        stationTitle.setOnClickListener(v -> openSettings());
        findViewById(R.id.settingsButton).setOnClickListener(v -> openSettings());
        findViewById(R.id.downloadButton).setOnClickListener(v -> showCsvDialog());
        refresh.setOnClickListener(v -> loadThingSpeak(true));
        sourceStatus.setOnClickListener(v -> chooseActiveProfile());
        updateClock();
        loadCachedData(activeSlot());
        updateNetworkInfo();
        showStatus("MENYIAPKAN", 0xFFF3B64A, "Menyiapkan koneksi ThingSpeak.");
    }

    @Override protected void onResume() {
        super.onResume();
        main.removeCallbacks(clockTick);
        main.removeCallbacks(refreshTick);
        main.post(clockTick);
        main.post(refreshTick);
        updateHeader();
        updateNetworkInfo();
        loadCachedData(activeSlot());
        loadThingSpeak(false);
    }

    @Override protected void onPause() {
        super.onPause();
        main.removeCallbacks(clockTick);
        main.removeCallbacks(refreshTick);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        stationTitle = findViewById(R.id.stationTitle);
        date = findViewById(R.id.date);
        time = findViewById(R.id.time);
        dataTime = findViewById(R.id.dataTime);
        accessTime = findViewById(R.id.access);
        netMode = findViewById(R.id.netMode);
        rssi = findViewById(R.id.rssi);
        channelView = findViewById(R.id.channel);
        weather = findViewById(R.id.weather);
        heat = findViewById(R.id.heat);
        sourceStatus = findViewById(R.id.sourceStatus);
        apiStatus = findViewById(R.id.apiStatus);
        fieldStatus = findViewById(R.id.fieldStatus);
        status = findViewById(R.id.status);
        statusDot = findViewById(R.id.statusDot);
        refresh = findViewById(R.id.refresh);
        download = findViewById(R.id.downloadButton);
    }

    private void buildFieldCards() {
        GridLayout grid = findViewById(R.id.grid);
        grid.removeAllViews();
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(9), dp(8), dp(8), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColors(new int[]{blend(CARD_FILL[i], CARD_STROKE[i], 0.28f), CARD_FILL[i]});
            bg.setOrientation(GradientDrawable.Orientation.TL_BR);
            bg.setCornerRadius(dp(13));
            bg.setStroke(dp(2), CARD_STROKE[i]);
            card.setBackground(bg);
            GridLayout.LayoutParams cp = new GridLayout.LayoutParams();
            cp.width = 0;
            cp.height = dp(100);
            cp.columnSpec = GridLayout.spec(i % 2, 1f);
            cp.rowSpec = GridLayout.spec(i / 2);
            cp.setMargins(dp(3), dp(3), dp(3), dp(3));
            card.setLayoutParams(cp);

            iconViews[i] = text(PARAM_ICONS[DEFAULT_PARAM_BY_FIELD[i]], 25, CARD_STROKE[i], true);
            iconViews[i].setGravity(Gravity.CENTER);
            iconViews[i].setContentDescription("Pilih parameter field " + (i + 1));
            iconViews[i].setOnClickListener(v -> chooseFieldParameter(idx));
            card.addView(iconViews[i], new LinearLayout.LayoutParams(dp(48), -1));

            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -1, 1f);
            bp.leftMargin = dp(5);
            card.addView(body, bp);

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            labelViews[i] = text(defaultLabel(i), 10, 0xFFF4F7F9, true);
            labelViews[i].setMaxLines(2);
            labelViews[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            labelViews[i].setOnClickListener(v -> chooseFieldParameter(idx));
            labelViews[i].setContentDescription("Ketuk untuk mengubah parameter field " + (i + 1));
            top.addView(labelViews[i], new LinearLayout.LayoutParams(0, -2, 1f));
            TextView index = text((i + 1) + "/8", 10, CARD_STROKE[i], true);
            top.addView(index);
            body.addView(top);

            LinearLayout valueRow = new LinearLayout(this);
            valueRow.setGravity(Gravity.BOTTOM | Gravity.START);
            valueViews[i] = text("--", 23, Color.WHITE, true);
            valueViews[i].setSingleLine(true);
            valueViews[i].setTextSize(20);
            valueViews[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            valueViews[i].setContentDescription("Ketuk nilai untuk memasukkan nilai manual");
            valueViews[i].setOnClickListener(v -> showManualValueDialog(idx));
            valueRow.addView(valueViews[i], new LinearLayout.LayoutParams(0, -2, 1f));
            unitViews[i] = text(defaultUnit(i), 9, CARD_STROKE[i], true);
            unitViews[i].setGravity(Gravity.BOTTOM);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2);
            up.leftMargin = dp(3);
            valueRow.addView(unitViews[i], up);
            body.addView(valueRow);
            card.setOnClickListener(v -> chooseFieldParameter(idx));
            card.setContentDescription("Field " + (i + 1) + ". Ketuk kartu untuk memilih parameter; ketuk nilainya untuk memasukkan angka manual.");
            grid.addView(card);
        }
    }

    private int blend(int base, int accent, float amount) {
        int r = Math.round(Color.red(base) * (1f - amount) + Color.red(accent) * amount);
        int g = Math.round(Color.green(base) * (1f - amount) + Color.green(accent) * amount);
        int b = Math.round(Color.blue(base) * (1f - amount) + Color.blue(accent) * amount);
        return Color.rgb(r, g, b);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int activeSlot() { return Math.max(0, Math.min(4, prefs.getInt("active_slot", 0))); }
    private String profileKey(int slot, String suffix) { return "s" + slot + "_" + suffix; }
    private String idFor(int slot) { return prefs.getString(profileKey(slot, "id"), "").trim(); }
    private String readKeyFor(int slot) { return prefs.getString(profileKey(slot, "key"), "").trim(); }
    private String profileName(int slot) {
        String s = prefs.getString(profileKey(slot, "name"), "").trim();
        if (!s.isEmpty()) return s;
        String meta = prefs.getString(profileKey(slot, "meta_name"), "").trim();
        if (!meta.isEmpty()) return meta;
        String id = idFor(slot);
        return id.isEmpty() ? "Channel " + (slot + 1) : "Channel " + (slot + 1) + " · " + id;
    }
    private String appTitle() {
        String s = prefs.getString("app_title", "STASIUN CUACA DESA TURUS").trim();
        return s.isEmpty() ? "STASIUN CUACA DESA TURUS" : s;
    }

    private void updateHeader() {
        stationTitle.setText(appTitle());
        int slot = activeSlot();
        String id = idFor(slot);
        String name = profileName(slot);
        channelView.setText("--");
        String sourceLabel = name.length() > 18 ? name.substring(0, 17) + "…" : name;
        sourceStatus.setText(sourceLabel);
        sourceStatus.setContentDescription("Profil aktif: " + name + ", Channel ID: " + id);
        updateNetworkInfo();
        for (int i = 0; i < 8; i++) updateFieldPresentation(slot, i);
    }

    private String defaultLabel(int i) { return DEFAULT_LABELS[i]; }
    private String defaultUnit(int i) { return DEFAULT_UNITS[i]; }

    private int explicitFieldType(int slot, int field) {
        return prefs.getInt(profileKey(slot, "f" + field + "_type"), PARAM_UNSET);
    }

    private String manualFieldName(int slot, int field) {
        return prefs.getString(profileKey(slot, "f" + field + "_name"), "").trim();
    }

    private String metadataFieldName(int slot, int field) {
        return prefs.getString(profileKey(slot, "meta_f" + (field + 1)), "").trim();
    }

    private String fieldName(int slot, int field) {
        int chosen = explicitFieldType(slot, field);
        if (chosen >= 0 && chosen < PARAM_LABELS.length) return PARAM_LABELS[chosen];
        String manual = manualFieldName(slot, field);
        if (!manual.isEmpty()) return manual;
        String meta = metadataFieldName(slot, field);
        return meta.isEmpty() ? defaultLabel(field) : meta;
    }

    private String fieldUnit(int slot, int field) {
        int chosen = explicitFieldType(slot, field);
        if (chosen >= 0 && chosen < PARAM_UNITS.length) return PARAM_UNITS[chosen];
        String unit = prefs.getString(profileKey(slot, "f" + field + "_unit"), "").trim();
        if (chosen == PARAM_CUSTOM) return unit;
        return unit.isEmpty() ? defaultUnit(field) : unit;
    }

    private int inferParameterType(String label) {
        if (label == null) return PARAM_CUSTOM;
        String s = label.toLowerCase(Locale.ROOT).replace('₀', '0').replace('Δ', 'd').trim();
        if (s.isEmpty()) return PARAM_CUSTOM;
        if ((s.contains("status") || s.contains("tidak") || s.contains("rain/no")) && (s.contains("hujan") || s.contains("rain"))) return 13;
        if (s.contains("et0") || s.contains("evapotransp") || s.contains("evapotranspirasi")) return 9;
        if (s.contains("delta") || s.startsWith("d tekanan") || s.contains("perubahan tekanan") || s.contains("pressure change") || s.contains("tekanan diferensial")) return 3;
        if (s.contains("kelemb") || s.contains("humidity") || s.equals("rh") || s.contains("relative humidity")) return 1;
        if (s.contains("suhu") || s.contains("temperature") || s.contains("temp udara") || s.equals("temp")) return 0;
        if (s.contains("arah angin") || s.contains("wind direction") || s.contains("wind dir")) return 11;
        if (s.contains("kecepatan angin") || s.contains("wind speed") || s.contains("windspeed")) return 10;
        if (s.contains("curah hujan") || s.contains("rainfall") || s.contains("precipitation") || s.contains("hujan (mm)")) return 12;
        if (s.contains("hujan") || s.contains("rain")) return 13;
        if (s.contains("par") || s.contains("ppfd")) return 6;
        if (s.contains("vpd") || s.contains("defisit tekanan uap")) return 7;
        if (s.contains("tekanan") || s.contains("pressure") || s.contains("barometer")) return 2;
        if (s.contains("altitude") || s.contains("mdpl") || s.contains("elevasi") || s.contains("ketinggian")) return 4;
        if (s.contains("lux") || s.contains("cahaya") || s.contains("light") || s.contains("illuminance")) return 5;
        if (s.contains("durasi sinar") || s.contains("sunshine") || s.contains("lama penyinaran")) return 8;
        if (s.equals("et0") || s.contains("evapotransp")) return 9;
        return PARAM_CUSTOM;
    }

    private int displayedParameterType(int slot, int field) {
        int chosen = explicitFieldType(slot, field);
        if (chosen >= 0 && chosen < PARAM_LABELS.length) return chosen;
        if (chosen == PARAM_CUSTOM) return PARAM_CUSTOM;
        String manual = manualFieldName(slot, field);
        if (!manual.isEmpty()) return inferParameterType(manual);
        String meta = metadataFieldName(slot, field);
        if (!meta.isEmpty()) return inferParameterType(meta);
        return DEFAULT_PARAM_BY_FIELD[field];
    }

    private String fieldIcon(int slot, int field) {
        int type = displayedParameterType(slot, field);
        return type >= 0 && type < PARAM_ICONS.length ? PARAM_ICONS[type] : "✎";
    }

    private void updateFieldPresentation(int slot, int field) {
        labelViews[field].setText(fieldName(slot, field));
        unitViews[field].setText(fieldUnit(slot, field));
        int parameterType = displayedParameterType(slot, field);
        String icon = fieldIcon(slot, field);
        if (parameterType == 13) {
            String currentValue = valueViews[field].getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!currentValue.isEmpty() && !currentValue.equals("--")) {
                boolean statusKnown = false;
                boolean noRain = false;
                if (currentValue.contains("tidak") || currentValue.equals("false") || currentValue.equals("no rain") || currentValue.equals("kering")) {
                    noRain = true;
                    statusKnown = true;
                } else if (currentValue.equals("true") || currentValue.contains("hujan") || currentValue.contains("rain")) {
                    statusKnown = true;
                } else {
                    try { noRain = Double.parseDouble(currentValue.replace(',', '.')) == 0.0; statusKnown = true; }
                    catch (NumberFormatException ignored) { }
                }
                if (statusKnown) icon = noRain ? "☀️" : "🌧️";
            }
        }
        iconViews[field].setText(icon);
        iconViews[field].setTextSize((parameterType == 6 || parameterType == 7 || parameterType == 8 || parameterType == 9) ? 17 : 25);
        iconViews[field].setContentDescription("Ikon " + fieldName(slot, field) + ". Ketuk untuk memilih parameter.");
    }

    private void updateClock() {
        ZonedDateTime n = ZonedDateTime.now(WIB);
        Locale id = new Locale("id", "ID");
        date.setText(cap(n.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", id))));
        time.setText(n.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)));
    }
    private String cap(String s) { return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase(new Locale("id", "ID")) + s.substring(1); }

    private void updateNetworkInfo() {
        String mode = "OFFLINE", signal = "N/A", ip = "--";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities nc = cm == null || n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    mode = "CLIENT / Wi-Fi";
                    try {
                        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                        WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                        if (wi != null && wi.getRssi() >= -127 && wi.getRssi() <= 0) signal = wi.getRssi() + " dBm";
                        if (wi != null && wi.getIpAddress() != 0) {
                            int a = wi.getIpAddress();
                            ip = (a & 255) + "." + ((a >> 8) & 255) + "." + ((a >> 16) & 255) + "." + ((a >> 24) & 255);
                        }
                    } catch (SecurityException ignored) { }
                } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    mode = "SELULER";
                } else mode = "INTERNET";
            }
        } catch (Exception ignored) { }
        netMode.setText(mode);
        rssi.setText(signal);
        channelView.setText(ip);
        channelView.setContentDescription("Alamat IP perangkat: " + ip + "; profil aktif: " + profileName(activeSlot()) + "; Channel ID: " + idFor(activeSlot()));
    }

    private void chooseActiveProfile() {
        String[] items = new String[PROFILE_COUNT];
        for (int i = 0; i < PROFILE_COUNT; i++) items[i] = (i + 1) + ". " + profileName(i);
        new AlertDialog.Builder(this).setTitle("PILIH STASIUN / CHANNEL")
                .setSingleChoiceItems(items, activeSlot(), (dialog, which) -> {
                    prefs.edit().putInt("active_slot", which).apply();
                    dialog.dismiss(); updateHeader(); loadCachedData(which); loadThingSpeak(true);
                }).setNegativeButton("BATAL", null).setNeutralButton("PENGATURAN", (d, w) -> openSettings()).show();
    }

    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    private void loadThingSpeak(boolean manual) {
        if (requestRunning) return;
        final int slot = activeSlot();
        final String channelId = idFor(slot);
        final String key = readKeyFor(slot);
        if (channelId.isEmpty()) {
            showStatus("PENGATURAN DIPERLUKAN", 0xFFF3B64A, "Isi Channel ID melalui ikon pengaturan.");
            return;
        }
        requestRunning = true;
        if (manual) showStatus("MENGAMBIL DATA", 0xFFF3B64A, "Menghubungi ThingSpeak...");
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "https://api.thingspeak.com/channels/" + enc(channelId) + "/feeds/last.json?timezone=Asia%2FJakarta&status=true";
                if (!key.isEmpty()) endpoint += "&api_key=" + enc(key);
                JSONObject feed;
                try (InputStream in = openStream(endpoint, 9000, 12000)) { feed = new JSONObject(readAll(in)); }
                if (feed.optString("created_at", "").isEmpty()) throw new java.io.IOException("Respons tidak berisi created_at.");

                // Metadata lookup is best-effort; failure must not discard a valid latest feed.
                boolean needsMetadata = prefs.getString(profileKey(slot, "name"), "").trim().isEmpty();
                for (int i = 0; i < 8 && !needsMetadata; i++) {
                    if (prefs.getString(profileKey(slot, "f" + i + "_name"), "").trim().isEmpty()
                            && prefs.getString(profileKey(slot, "meta_f" + (i + 1)), "").trim().isEmpty()) needsMetadata = true;
                }
                JSONObject metadata = null;
                if (needsMetadata) {
                    try {
                        String metaUrl = "https://api.thingspeak.com/channels/" + enc(channelId) + ".json";
                        if (!key.isEmpty()) metaUrl += "?api_key=" + enc(key);
                        try (InputStream in = openStream(metaUrl, 7000, 9000)) { metadata = new JSONObject(readAll(in)); }
                    } catch (Exception ignored) { }
                }

                long accessed = System.currentTimeMillis();
                SharedPreferences.Editor editor = prefs.edit()
                        .putString(profileKey(slot, "cache"), feed.toString())
                        .putLong(profileKey(slot, "last_access"), accessed)
                        .putString(profileKey(slot, "last_data_time"), feed.optString("created_at", ""));
                if (metadata != null) {
                    JSONObject channel = metadata.optJSONObject("channel");
                    if (channel == null) channel = metadata;
                    if (prefs.getString(profileKey(slot, "name"), "").trim().isEmpty()) {
                        String metadataName = channel.optString("name", "").trim();
                        if (!metadataName.isEmpty()) editor.putString(profileKey(slot, "meta_name"), metadataName);
                    }
                    for (int i = 0; i < 8; i++) {
                        String custom = prefs.getString(profileKey(slot, "f" + i + "_name"), "").trim();
                        String k = "field" + (i + 1);
                        if (custom.isEmpty()) {
                            String metadataLabel = channel.optString(k, "").trim();
                            if (!metadataLabel.isEmpty()) editor.putString(profileKey(slot, "meta_f" + (i + 1)), metadataLabel);
                        }
                    }
                }
                editor.apply();
                JSONObject finalMetadata = metadata;
                runOnUiThread(() -> {
                    if (slot == activeSlot()) {
                        updateHeader();
                        applyFeed(feed, slot);
                        accessTime.setText(formatAccess(accessed));
                        showStatus("ONLINE", 0xFF45D483, "Data terbaru berhasil diterima dari ThingSpeak.");
                        apiStatus.setText("OK"); apiStatus.setTextColor(0xFF45D483);
                    }
                    requestRunning = false;
                    if (slot != activeSlot()) loadThingSpeak(false);
                });
            } catch (Exception ex) {
                JSONObject cached = cachedFeed(slot);
                long oldAccess = prefs.getLong(profileKey(slot, "last_access"), 0L);
                runOnUiThread(() -> {
                    if (slot == activeSlot()) {
                        if (cached != null) {
                            updateHeader(); applyFeed(cached, slot);
                            if (oldAccess > 0) accessTime.setText(formatAccess(oldAccess));
                            showStatus("OFFLINE · CACHE", 0xFFF3B64A, "Koneksi gagal; menampilkan data terakhir tersimpan.");
                        } else {
                            showStatus("OFFLINE", 0xFFFF6B6B, "Gagal mengakses ThingSpeak: " + safeMessage(ex));
                        }
                        apiStatus.setText("FAIL"); apiStatus.setTextColor(0xFFFF6B6B);
                    }
                    requestRunning = false;
                    if (slot != activeSlot()) loadThingSpeak(false);
                });
            }
        });
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage(); return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void applyFeed(JSONObject feed, int slot) {
        int present = 0;
        for (int i = 0; i < 8; i++) {
            String k = "field" + (i + 1);
            String raw = feed.optString(k, "").trim();
            if (raw.isEmpty() || raw.equalsIgnoreCase("null")) raw = "--";
            boolean manualEnabled = prefs.getBoolean(profileKey(slot, "f" + i + "_manual_enabled"), false);
            if (manualEnabled) raw = prefs.getString(profileKey(slot, "f" + i + "_manual_value"), raw);
            if (!raw.isEmpty() && !raw.equals("--") && !raw.equalsIgnoreCase("null")) present++;
            valueViews[i].setText(raw);
            valueViews[i].setTextColor(manualEnabled ? 0xFFF3B64A : Color.WHITE);
            valueViews[i].setContentDescription((manualEnabled ? "Nilai manual: " : "Nilai ThingSpeak: ") + raw + ". Ketuk untuk mengubah.");
            updateFieldPresentation(slot, i);
        }
        fieldStatus.setText(present + "/8");
        fieldStatus.setTextColor(present > 0 ? 0xFF45D483 : 0xFFFF6B6B);
        dataTime.setText(formatThingSpeakTime(feed.optString("created_at", "")));
        String chName = prefs.getString(profileKey(slot, "name"), "").trim();
        if (chName.isEmpty()) chName = prefs.getString(profileKey(slot, "meta_name"), "").trim();
        if (chName.isEmpty()) chName = "ThingSpeak";
        sourceStatus.setText(chName.length() > 16 ? chName.substring(0, 15) + "…" : chName);
        String forecast = estimateWeather(feed, slot);
        weather.setText(forecast);
        heat.setText(estimateHeat(feed, slot));
        if (present == 0) showStatus("ONLINE · DATA KOSONG", 0xFFF3B64A, "Channel terhubung tetapi field belum berisi nilai.");
    }

    private String estimateWeather(JSONObject feed, int slot) {
        double temp = findField(feed, slot, new String[]{"suhu", "temp"});
        double humidity = findField(feed, slot, new String[]{"kelemb", "humidity", "\brh\b"});
        double lux = findField(feed, slot, new String[]{"lux", "cahaya", "light"});
        if (Double.isNaN(temp) && Double.isNaN(humidity) && Double.isNaN(lux)) return "Data ThingSpeak";
        if (!Double.isNaN(lux) && lux > 20000 && (Double.isNaN(humidity) || humidity < 80)) return "Cerah";
        if (!Double.isNaN(humidity) && humidity > 88 && (Double.isNaN(lux) || lux < 8000)) return "Mendung / lembap";
        if (!Double.isNaN(lux) && lux > 5000) return "Cerah Berawan";
        return "Kondisi terpantau";
    }

    private String estimateHeat(JSONObject feed, int slot) {
        double t = findField(feed, slot, new String[]{"suhu", "temp"});
        if (Double.isNaN(t)) return "--";
        if (t >= 35) return "TINGGI";
        if (t >= 32) return "WASPADA";
        return "NORMAL";
    }

    private double findField(JSONObject feed, int slot, String[] keywords) {
        for (int i = 0; i < 8; i++) {
            String label = fieldName(slot, i).toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String k : keywords) if (label.contains(k.replace("\\b", ""))) { hit = true; break; }
            if (!hit) continue;
            String v = feed.optString("field" + (i + 1), "").trim().replace(',', '.');
            try { return Double.parseDouble(v); } catch (Exception ignored) { }
        }
        return Double.NaN;
    }

    private JSONObject cachedFeed(int slot) {
        try { String s = prefs.getString(profileKey(slot, "cache"), ""); return s.isEmpty() ? null : new JSONObject(s); }
        catch (Exception ignored) { return null; }
    }

    private void loadCachedData(int slot) {
        JSONObject cached = cachedFeed(slot);
        long access = prefs.getLong(profileKey(slot, "last_access"), 0L);
        if (access > 0) accessTime.setText(formatAccess(access)); else accessTime.setText("Belum ada akses");
        updateHeader();
        if (cached != null) applyFeed(cached, slot);
        else {
            for (int i = 0; i < 8; i++) {
                boolean manualEnabled = prefs.getBoolean(profileKey(slot, "f" + i + "_manual_enabled"), false);
                valueViews[i].setText(manualEnabled ? prefs.getString(profileKey(slot, "f" + i + "_manual_value"), "--") : "--");
                valueViews[i].setTextColor(manualEnabled ? 0xFFF3B64A : Color.WHITE);
                updateFieldPresentation(slot, i);
            }
            dataTime.setText("Belum ada data ThingSpeak");
        }
    }

    private void showStatus(String state, int color, String detail) {
        status.setText(state);
        status.setTextColor(color);
        status.setContentDescription(detail);
        GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL); dot.setColor(color);
        statusDot.setBackground(dot);
        sourceStatus.setTextColor(color == 0xFF45D483 ? 0xFF45D483 : 0xFFF3B64A);
    }

    private String formatThingSpeakTime(String value) {
        if (value == null || value.isEmpty()) return "--";
        try { return Instant.parse(value).atZone(WIB).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", new Locale("id", "ID"))) + " WIB"; }
        catch (Exception e) { return value; }
    }
    private String formatAccess(long millis) {
        return Instant.ofEpochMilli(millis).atZone(WIB).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", new Locale("id", "ID"))) + " WIB";
    }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close(); return b.toString();
    }
    private InputStream openStream(String url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectTimeout); c.setReadTimeout(readTimeout); c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "StasiunCuacaAndroid/2.0");
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) { c.disconnect(); throw new java.io.IOException("HTTP " + code); }
        return new DisconnectingInputStream(c.getInputStream(), c);
    }


    private void showCsvDialog() {
        int slot = activeSlot();
        if (idFor(slot).isEmpty()) { showStatus("CHANNEL BELUM DIATUR", 0xFFF3B64A, "Isi Channel ID pada Pengaturan dahulu."); openSettings(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), 0);
        TextView description = text("Pilih rentang tanggal untuk mengunduh histori CSV ThingSpeak. Ketuk tanggal mulai dan tanggal selesai untuk membuka kalender.", 12, 0xFF9DB0BC, false);
        box.addView(description);

        final LocalDate[] selectedDates = {LocalDate.now(WIB).minusDays(7), LocalDate.now(WIB)};
        final TextView[] dateButtons = new TextView[2];
        dateButtons[0] = dateControl("TANGGAL MULAI · " + formatCalendarDate(selectedDates[0]));
        dateButtons[1] = dateControl("TANGGAL SELESAI · " + formatCalendarDate(selectedDates[1]));
        dateButtons[0].setOnClickListener(v -> chooseCsvDate(selectedDates, 0, dateButtons[0]));
        dateButtons[1].setOnClickListener(v -> chooseCsvDate(selectedDates, 1, dateButtons[1]));
        box.addView(dateButtons[0]);
        box.addView(dateButtons[1]);

        CheckBox all = new CheckBox(this);
        all.setText("UNDUH SEMUA HISTORY");
        all.setTextColor(Color.WHITE);
        all.setChecked(false);
        all.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF29C6C7));
        all.setOnCheckedChangeListener((button, checked) -> {
            dateButtons[0].setEnabled(!checked);
            dateButtons[1].setEnabled(!checked);
            dateButtons[0].setAlpha(checked ? 0.45f : 1f);
            dateButtons[1].setAlpha(checked ? 0.45f : 1f);
        });
        // Rentang tanggal aktif secara default; centang semua histori untuk menonaktifkannya.
        box.addView(all);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("UNDUH HISTORY / CSV")
                .setView(box)
                .setPositiveButton("PILIH FILE & UNDUH", null)
                .setNegativeButton("BATAL", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!all.isChecked() && selectedDates[0].isAfter(selectedDates[1])) {
                Toast.makeText(this, "Tanggal mulai tidak boleh melewati tanggal selesai.", Toast.LENGTH_LONG).show();
                return;
            }
            pendingCsvStart = all.isChecked() ? "" : selectedDates[0].format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)) + " 00:00:00";
            pendingCsvEnd = all.isChecked() ? "" : selectedDates[1].format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)) + " 23:59:59";
            pendingCsvAll = all.isChecked();
            pendingCsvProfile = activeSlot();
            pendingCsvChannel = idFor(pendingCsvProfile);
            pendingCsvKey = readKeyFor(pendingCsvProfile);
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            String csvName = "stasiun_cuaca_" + pendingCsvChannel + "_history.csv";
            if (!pendingCsvAll) {
                String startStamp = pendingCsvStart.substring(0, 10).replace("-", "");
                String endStamp = pendingCsvEnd.substring(0, 10).replace("-", "");
                csvName = "stasiun_cuaca_" + pendingCsvChannel + "_" + startStamp + "-" + endStamp + ".csv";
            }
            intent.putExtra(Intent.EXTRA_TITLE, csvName);
            startActivityForResult(intent, REQUEST_CREATE_CSV);
        }));
        dialog.show();
    }

    private TextView dateControl(String value) {
        TextView control = text(value, 12, 0xFFF4F7F9, true);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(dp(12), 0, dp(12), 0);
        control.setBackgroundResource(R.drawable.bg_edit);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(5);
        control.setLayoutParams(lp);
        return control;
    }

    private String formatCalendarDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("id", "ID")));
    }

    private void chooseCsvDate(LocalDate[] selectedDates, int index, TextView button) {
        LocalDate existing = selectedDates[index];
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDates[index] = LocalDate.of(year, month + 1, day);
            button.setText((index == 0 ? "TANGGAL MULAI · " : "TANGGAL SELESAI · ") + formatCalendarDate(selectedDates[index]));
        }, existing.getYear(), existing.getMonthValue() - 1, existing.getDayOfMonth());
        picker.show();
    }

    private void startFieldSelection(int slot, int field, int type) {
        SharedPreferences.Editor editor = prefs.edit();
        String nameKey = profileKey(slot, "f" + field + "_name");
        String unitKey = profileKey(slot, "f" + field + "_unit");
        String typeKey = profileKey(slot, "f" + field + "_type");
        if (type == PARAM_CUSTOM) {
            // Keep the current title/unit as a starting point for editing a custom parameter.
            String currentName = fieldName(slot, field);
            String currentUnit = fieldUnit(slot, field);
            String savedName = manualFieldName(slot, field);
            if (!savedName.isEmpty()) currentName = savedName;
            String savedUnit = prefs.getString(unitKey, "").trim();
            if (!savedUnit.isEmpty()) currentUnit = savedUnit;
            showCustomParameterDialog(slot, field, currentName, currentUnit);
            return;
        }
        if (type < 0 || type >= PARAM_LABELS.length) return;
        editor.putInt(typeKey, type).remove(nameKey).remove(unitKey).apply();
        updateHeader();
        loadCachedData(slot);
        Toast.makeText(this, PARAM_LABELS[type] + " · satuan " + (PARAM_UNITS[type].isEmpty() ? "tanpa satuan" : PARAM_UNITS[type]), Toast.LENGTH_SHORT).show();
    }

    private void chooseFieldParameter(int field) {
        int slot = activeSlot();
        String[] choices = new String[PARAM_LABELS.length + 1];
        for (int i = 0; i < PARAM_LABELS.length; i++) {
            choices[i] = PARAM_ICONS[i] + "  " + PARAM_LABELS[i] + (PARAM_UNITS[i].isEmpty() ? "" : "  (" + PARAM_UNITS[i] + ")");
        }
        choices[PARAM_LABELS.length] = "✎  MANUAL / KUSTOM (isi nama dan satuan sendiri)";
        int current = displayedParameterType(slot, field);
        if (current < 0 || current >= PARAM_LABELS.length) current = PARAM_LABELS.length;
        new AlertDialog.Builder(this)
                .setTitle("PILIH PARAMETER · FIELD " + (field + 1))
                .setSingleChoiceItems(choices, current, (dialog, which) -> {
                    dialog.dismiss();
                    startFieldSelection(slot, field, which == PARAM_LABELS.length ? PARAM_CUSTOM : which);
                })
                .setNegativeButton("BATAL", null)
                .show();
    }

    private void showCustomParameterDialog(int slot, int field, String currentName, String currentUnit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), 0);
        EditText name = edit("Nama parameter, misalnya suhu tanah", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(currentName);
        EditText unit = edit("Satuan, misalnya °C atau kPa", InputType.TYPE_CLASS_TEXT);
        unit.setText(currentUnit);
        box.addView(name);
        box.addView(unit);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PARAMETER MANUAL · FIELD " + (field + 1))
                .setMessage("Nama dan satuan ini akan ditampilkan di dashboard. Ketuk nilai pada kartu untuk memasukkan angka manual.")
                .setView(box)
                .setPositiveButton("SIMPAN", null)
                .setNegativeButton("BATAL", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String u = unit.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Nama parameter harus diisi");
                return;
            }
            prefs.edit()
                    .putInt(profileKey(slot, "f" + field + "_type"), PARAM_CUSTOM)
                    .putString(profileKey(slot, "f" + field + "_name"), n)
                    .putString(profileKey(slot, "f" + field + "_unit"), u)
                    .apply();
            dialog.dismiss();
            updateHeader();
            loadCachedData(slot);
        }));
        dialog.show();
    }

    private void showManualValueDialog(int field) {
        int slot = activeSlot();
        String baseKey = profileKey(slot, "f" + field + "_manual_");
        boolean wasManual = prefs.getBoolean(baseKey + "enabled", false);
        String initial = wasManual ? prefs.getString(baseKey + "value", "") : valueViews[field].getText().toString();
        if ("--".equals(initial)) initial = "";
        EditText value = edit("Nilai " + fieldUnit(slot, field), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        value.setText(initial);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), 0);
        box.addView(text("FIELD " + (field + 1) + " · " + fieldName(slot, field) + " (" + (fieldUnit(slot, field).isEmpty() ? "tanpa satuan" : fieldUnit(slot, field)) + ")", 12, 0xFF9DB0BC, true));
        box.addView(value);
        box.addView(text("Nilai manual menggantikan tampilan live untuk field ini pada perangkat ini. Tekan KEMBALI LIVE untuk memakai data ThingSpeak lagi.", 11, 0xFF9DB0BC, false));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("INPUT NILAI MANUAL")
                .setView(box)
                .setPositiveButton("SIMPAN MANUAL", null)
                .setNegativeButton("KEMBALI LIVE", null)
                .setNeutralButton("BATAL", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String number = value.getText().toString().trim().replace(',', '.');
                if (number.isEmpty()) {
                    value.setError("Masukkan nilai");
                    return;
                }
                try {
                    double parsed = Double.parseDouble(number);
                    if (!Double.isFinite(parsed)) { value.setError("Nilai harus berupa angka terbatas"); return; }
                } catch (NumberFormatException ex) { value.setError("Nilai harus berupa angka"); return; }
                prefs.edit().putBoolean(baseKey + "enabled", true).putString(baseKey + "value", number).apply();
                valueViews[field].setText(number);
                valueViews[field].setTextColor(0xFFF3B64A);
                valueViews[field].setContentDescription("Nilai manual: " + number + ". Ketuk untuk mengubah atau kembali ke data live.");
                showStatus("NILAI MANUAL", 0xFFF3B64A, "Field " + (field + 1) + " memakai nilai manual di perangkat ini.");
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                prefs.edit().putBoolean(baseKey + "enabled", false).remove(baseKey + "value").apply();
                JSONObject feed = cachedFeed(slot);
                if (feed != null) applyFeed(feed, slot);
                else { valueViews[field].setText("--"); valueViews[field].setTextColor(Color.WHITE); }
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATE_CSV || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        showStatus("MENGUNDUH CSV", 0xFFF3B64A, "Mengambil histori ThingSpeak dan menyimpan CSV...");
        io.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new java.io.IOException("Tidak dapat membuka file tujuan.");
                int rows = writeCsv(out, pendingCsvProfile, pendingCsvChannel, pendingCsvKey, pendingCsvStart, pendingCsvEnd, pendingCsvAll);
                runOnUiThread(() -> showStatus("CSV TERSIMPAN", 0xFF45D483, rows + " baris berhasil disimpan."));
            } catch (Exception e) {
                runOnUiThread(() -> showStatus("UNDUH GAGAL", 0xFFFF6B6B, safeMessage(e)));
            }
        });
    }

    private int writeCsv(OutputStream out, int slot, String channelId, String key, String start, String end, boolean all) throws Exception {
        int offset = 0, total = 0, pageSize = 8000;
        boolean headerWritten = false;
        while (true) {
            String url = "https://api.thingspeak.com/channels/" + enc(channelId) + "/feeds.json?results=" + pageSize + "&offset=" + offset + "&timezone=Asia%2FJakarta";
            if (!all) url += "&start=" + enc(start).replace("+", "%20") + "&end=" + enc(end).replace("+", "%20");
            if (!key.isEmpty()) url += "&api_key=" + enc(key);
            JSONObject obj;
            try (InputStream in = openStream(url, 12000, 25000)) { obj = new JSONObject(readAll(in)); }
            JSONArray feeds = obj.optJSONArray("feeds");
            if (feeds == null || feeds.length() == 0) break;
            if (!headerWritten) {
                StringBuilder header = new StringBuilder("created_at");
                for (int i = 0; i < 8; i++) {
                    String name = fieldName(slot, i), unit = fieldUnit(slot, i);
                    header.append(',').append(csvQuote("field" + (i + 1) + " - " + name + (unit.isEmpty() ? "" : " (" + unit + ")")));
                }
                header.append(",status\r\n"); out.write(header.toString().getBytes(StandardCharsets.UTF_8)); headerWritten = true;
            }
            StringBuilder page = new StringBuilder(Math.min(feeds.length() * 100, 1_200_000));
            for (int i = 0; i < feeds.length(); i++) {
                JSONObject feed = feeds.optJSONObject(i); if (feed == null) continue;
                page.append(csvQuote(feed.optString("created_at", "")));
                for (int f = 1; f <= 8; f++) page.append(',').append(csvQuote(feed.optString("field" + f, "")));
                page.append(',').append(csvQuote(feed.optString("status", ""))).append("\r\n");
                total++;
            }
            out.write(page.toString().getBytes(StandardCharsets.UTF_8)); out.flush();
            offset += feeds.length();
            if (feeds.length() < pageSize) break;
            if (offset > 50_000_000) throw new java.io.IOException("Batas keamanan jumlah entry histori tercapai.");
        }
        if (!headerWritten) out.write("created_at,field1,field2,field3,field4,field5,field6,field7,field8,status\r\n".getBytes(StandardCharsets.UTF_8));
        return total;
    }

    private String csvQuote(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private EditText edit(String hint, int inputType) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setHintTextColor(0xFF8EA5B4); e.setTextColor(Color.WHITE);
        e.setTextSize(13); e.setInputType(inputType); e.setPadding(dp(12), 0, dp(12), 0); e.setBackgroundResource(R.drawable.bg_edit);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46)); lp.topMargin = dp(6); lp.bottomMargin = dp(5); e.setLayoutParams(lp);
        return e;
    }

    private static class DisconnectingInputStream extends InputStream {
        private final InputStream in; private final HttpURLConnection connection;
        DisconnectingInputStream(InputStream in, HttpURLConnection connection) { this.in = in; this.connection = connection; }
        @Override public int read() throws java.io.IOException { return in.read(); }
        @Override public int read(byte[] b, int o, int l) throws java.io.IOException { return in.read(b, o, l); }
        @Override public void close() throws java.io.IOException { try { in.close(); } finally { connection.disconnect(); } }
    }
}
