package com.termdash.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class EnvironmentService {

    private String cachedWeather = "Scanning atmosphere...";
    private long lastWeatherUpdate = 0;
    private static final long WEATHER_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15);

    public String getGitBranch() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(new File(System.getProperty("user.dir")));
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String branch = reader.readLine();
                if (process.waitFor() == 0 && branch != null) {
                    return branch.trim();
                }
            }
        } catch (Exception e) {
        }
        return "DETACHED / NO GIT";
    }

    public String getWeather() {
        long now = System.currentTimeMillis();
        if (now - lastWeatherUpdate > WEATHER_UPDATE_INTERVAL) {
            updateWeatherAsync();
            lastWeatherUpdate = now;
        }
        return cachedWeather;
    }

    private void updateWeatherAsync() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://wttr.in/Jalandhar?format=3")) 
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + response.statusCode());
                    }
                    return response.body();
                })
                .thenAccept(body -> {
                    if (body != null && !body.isBlank()) {
                        this.cachedWeather = body.trim();
                    }
                })
                .exceptionally(e -> {
                    String msg = e.getMessage();
                    if (e.getCause() != null) msg = e.getCause().getMessage();
                    if (msg != null && msg.length() > 20) msg = msg.substring(0, 20) + "..";
                    this.cachedWeather = "ERR: " + msg;
                    return null;
                });
    }
}
