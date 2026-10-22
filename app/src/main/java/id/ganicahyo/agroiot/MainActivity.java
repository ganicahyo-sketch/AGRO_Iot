package id.ganicahyo.agroiot;

import android.app.Activity;
import android.app.AlertDialog;
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

    private static final String[] DEFAULT_LABELS = {
            "SUHU UDARA", "KELEMBABAN UDARA", "TEKANAN UDARA", "ALTITUDE",
            "INTENSITAS CAHAYA", "PAR", "VPD", "DURASI SINAR MATAHARI"
    };
    private static final String[] DEFAULT_UNITS = {"°C", "%", "hPa", "mdpl", "lux", "µmol/m²/s", "kPa", "jam"};
    private static final String[] ICONS = {"♨", "♢", "◉", "▲", "☀", "❧", "≋", "◷"};
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

            TextView icon = text(ICONS[i], 29, CARD_STROKE[i], true);
            icon.setGravity(Gravity.CENTER);
            card.addView(icon, new LinearLayout.LayoutParams(dp(43), -1));

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
            valueRow.addView(valueViews[i], new LinearLayout.LayoutParams(0, -2, 1f));
            unitViews[i] = text(defaultUnit(i), 9, CARD_STROKE[i], true);
            unitViews[i].setGravity(Gravity.BOTTOM);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2);
            up.leftMargin = dp(3);
            valueRow.addView(unitViews[i], up);
            body.addView(valueRow);
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
        for (int i = 0; i < 8; i++) {
            labelViews[i].setText(fieldName(slot, i));
            unitViews[i].setText(fieldUnit(slot, i));
        }
    }

    private String defaultLabel(int i) { return DEFAULT_LABELS[i]; }
    private String defaultUnit(int i) { return DEFAULT_UNITS[i]; }
    private String fieldName(int slot, int field) {
        String manual = prefs.getString(profileKey(slot, "f" + field + "_name"), "").trim();
        if (!manual.isEmpty()) return manual;
        String meta = prefs.getString(profileKey(slot, "meta_f" + (field + 1)), "").trim();
        return meta.isEmpty() ? defaultLabel(field) : meta;
    }
    private String fieldUnit(int slot, int field) {
        String unit = prefs.getString(profileKey(slot, "f" + field + "_unit"), "").trim();
        return unit.isEmpty() ? defaultUnit(field) : unit;
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
            if (raw.isEmpty() || raw.equalsIgnoreCase("null")) raw = "--"; else present++;
            valueViews[i].setText(raw);
            labelViews[i].setText(fieldName(slot, i));
            unitViews[i].setText(fieldUnit(slot, i));
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
            for (int i = 0; i < 8; i++) { valueViews[i].setText("--"); labelViews[i].setText(fieldName(slot, i)); unitViews[i].setText(fieldUnit(slot, i)); }
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
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(6), dp(18), 0);
        TextView description = text("Unduh feed CSV ThingSpeak untuk rentang waktu tertentu atau seluruh histori channel aktif.", 12, 0xFF9DB0BC, false);
        box.addView(description);
        EditText start = edit("Mulai: YYYY-MM-DDTHH:MM:SS", InputType.TYPE_CLASS_TEXT);
        EditText end = edit("Selesai: YYYY-MM-DDTHH:MM:SS", InputType.TYPE_CLASS_TEXT);
        box.addView(start); box.addView(end);
        CheckBox all = new CheckBox(this); all.setText("UNDUH SEMUA HISTORY"); all.setTextColor(Color.WHITE); all.setChecked(true); all.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF29C6C7));
        all.setOnCheckedChangeListener((button, checked) -> { start.setEnabled(!checked); end.setEnabled(!checked); });
        start.setEnabled(false); end.setEnabled(false);
        box.addView(all);
        new AlertDialog.Builder(this).setTitle("UNDUH HISTORY / CSV").setView(box)
                .setPositiveButton("PILIH FILE & UNDUH", (d, which) -> {
                    if (!all.isChecked() && (start.getText().toString().trim().isEmpty() || end.getText().toString().trim().isEmpty())) {
                        Toast.makeText(this, "Isi waktu mulai dan selesai atau pilih semua history.", Toast.LENGTH_LONG).show(); return;
                    }
                    pendingCsvStart = start.getText().toString().trim(); pendingCsvEnd = end.getText().toString().trim(); pendingCsvAll = all.isChecked();
                    pendingCsvProfile = activeSlot(); pendingCsvChannel = idFor(pendingCsvProfile); pendingCsvKey = readKeyFor(pendingCsvProfile);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/csv");
                    intent.putExtra(Intent.EXTRA_TITLE, "stasiun_cuaca_" + pendingCsvChannel + "_history.csv");
                    startActivityForResult(intent, REQUEST_CREATE_CSV);
                }).setNegativeButton("BATAL", null).show();
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
            if (!all) url += "&start=" + enc(start) + "&end=" + enc(end);
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
