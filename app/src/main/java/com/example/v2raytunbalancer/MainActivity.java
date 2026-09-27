package com.example.v2raytunbalancer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button generateBtn = findViewById(R.id.generate_btn);
        EditText subUrl = findViewById(R.id.sub_url);

        generateBtn.setOnClickListener(v -> {
            try {
                // Fetch subscription
                URL url = new URL(subUrl.getText().toString());
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();

                // Generate config (simplified)
                String config = "{\"outbounds\":[{\"tag\":\"server\",\"protocol\":\"vless\"}]}";
                String deepLink = "v2raytun://import/" + android.util.Base64.encodeToString(config.getBytes(), android.util.Base64.DEFAULT);

                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}