package com.example.v2raytunbalancer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.Base64;
import java.util.regex.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String VERSION_URL = "https://work.koteod.com/apk/version";
    private static final String APK_URL = "https://work.koteod.com/apk";
    private static final String LOG_URL = "https://work.koteod.com/apk/log";
    private static final int CURRENT_VERSION = 2;
    private static final String PROVIDER = "com.example.v2raytunbalancer.fileprovider";

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> reportError("CRASH: " + Log.getStackTraceString(e)));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("V2RayTun Balancer v" + CURRENT_VERSION);
        title.setTextSize(24);
        layout.addView(title);

        EditText urlInput = new EditText(this);
        urlInput.setHint("URL подписки (https://...)");
        layout.addView(urlInput);

        Button btn = new Button(this);
        btn.setText("Создать конфиг для Telegram");
        btn.setOnClickListener(v -> {
            String subUrl = urlInput.getText().toString();
            if (!subUrl.isEmpty()) {
                new Thread(() -> {
                    try {
                        String config = generateConfig(subUrl);
                        String deepLink = "v2raytun://import/" + Base64.getEncoder().encodeToString(config.getBytes());
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)));
                    } catch (Exception e) {
                        String msg = "Ошибка: " + e.getMessage();
                        reportError(msg + " | url=" + subUrl);
                        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        });
        layout.addView(btn);

        statusView = new TextView(this);
        statusView.setPadding(0, 40, 0, 0);
        layout.addView(statusView);

        setContentView(layout);
        checkUpdate();
    }

    private void setStatus(final String s) {
        runOnUiThread(() -> statusView.setText(s));
    }

    private void checkUpdate() {
        new Thread(() -> {
            try {
                String resp = httpGet(VERSION_URL).trim();
                int latest = Integer.parseInt(resp);
                if (latest > CURRENT_VERSION) {
                    setStatus("Загружаю обновление v" + latest + "...");
                    File apk = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
                    httpDownload(APK_URL, apk);
                    setStatus("Установка обновления...");
                    Uri uri = FileProvider.getUriForFile(this, PROVIDER, apk);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } else {
                    setStatus("Версия актуальна (v" + CURRENT_VERSION + ")");
                }
            } catch (Exception e) {
                setStatus("Проверка обновлений: " + e.getMessage());
                reportError("update check failed: " + e.getMessage());
            }
        }).start();
    }

    private void reportError(final String text) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(LOG_URL).openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                byte[] body = ("v" + CURRENT_VERSION + " | " + android.os.Build.MODEL + " | " + text).getBytes("UTF-8");
                c.getOutputStream().write(body);
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private String httpGet(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private void httpDownload(String url, File out) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(15000);
        InputStream in = c.getInputStream();
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        fos.close();
        in.close();
    }

    String generateConfig(String subUrl) throws Exception {
        String subData = httpGet(subUrl);
        if (!subData.contains("vless://")) {
            try { subData = new String(Base64.getDecoder().decode(subData.trim())); } catch (Exception ignored) {}
        }

        List<String> uuids = new ArrayList<>();
        List<String> hosts = new ArrayList<>();
        List<String> ports = new ArrayList<>();

        Pattern pattern = Pattern.compile("vless://([^@]+)@([^:]+):(\\d+)");
        Matcher matcher = pattern.matcher(subData);
        while (matcher.find()) {
            uuids.add(matcher.group(1));
            hosts.add(matcher.group(2));
            ports.add(matcher.group(3));
        }

        if (uuids.isEmpty()) {
            throw new RuntimeException("Серверы не найдены");
        }

        StringBuilder outbounds = new StringBuilder();
        StringBuilder selectors = new StringBuilder();
        for (int i = 0; i < uuids.size(); i++) {
            outbounds.append(String.format(",\"server%d\":{\"tag\":\"server%d\",\"protocol\":\"vless\",\"settings\":{\"vnext\":[{\"address\":\"%s\",\"port\":%s,\"users\":[{\"id\":\"%s\",\"encryption\":\"none\"}]}]}}", i, i, hosts.get(i), ports.get(i), uuids.get(i)));
            selectors.append(String.format(",\"server%d\"", i));
        }

        return String.format(
            "{\"outbounds\":{\"proxy\":{\"tag\":\"proxy\",\"protocol\":\"vless\",\"settings\":{\"vnext\":[]}}%s},\"routing\":{\"domainStrategy\":\"IPIfNonMatch\",\"rules\":[{\"type\":\"field\",\"domain\":[\"telegram.org\",\"api.telegram.org\"],\"outboundTag\":\"balancer\"}],\"balancers\":[{\"tag\":\"balancer\",\"selector\":[\"server0\"%s],\"strategy\":{\"type\":\"latency\",\"maxFails\":3,\"failTimeout\":\"15s\"}}]}}",
            outbounds.toString(),
            selectors.toString()
        );
    }
}
