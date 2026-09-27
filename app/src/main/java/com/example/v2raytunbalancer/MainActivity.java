package com.example.v2raytunbalancer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import java.util.Base64;
import java.util.regex.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        
        TextView title = new TextView(this);
        title.setText("V2RayTun Balancer");
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
                        runOnUiThread(() -> Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        });
        layout.addView(btn);
        
        setContentView(layout);
    }
    
    String generateConfig(String subUrl) throws Exception {
        java.net.URL url = new java.net.URL(subUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        String subData = response.toString();
        if (!subData.startsWith("http")) {
            subData = new String(Base64.getDecoder().decode(subData));
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