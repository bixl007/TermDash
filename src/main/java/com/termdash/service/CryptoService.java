package com.termdash.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CryptoService {

    private final Map<String, Double> prices = new HashMap<>();
    private long lastUpdate = 0;
    private static final long UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(60);
    private boolean isUpdating = false;

    public CryptoService() {
        prices.put("bitcoin", 0.0);
        prices.put("ethereum", 0.0);
        prices.put("solana", 0.0);
        prices.put("dogecoin", 0.0);
        prices.put("monero", 0.0);
    }

    public Map<String, Double> getPrices() {
        long now = System.currentTimeMillis();
        if (now - lastUpdate > UPDATE_INTERVAL && !isUpdating) {
            updatePricesAsync();
        }
        return prices;
    }

    private void updatePricesAsync() {
        isUpdating = true;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana,dogecoin,monero&vs_currencies=usd";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "TermDash/1.0")
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        for (String key : prices.keySet()) {
                            if (json.has(key)) {
                                double price = json.getAsJsonObject(key).get("usd").getAsDouble();
                                prices.put(key, price);
                            }
                        }
                        lastUpdate = System.currentTimeMillis();
                    } catch (Exception e) {
                    } finally {
                        isUpdating = false;
                    }
                })
                .exceptionally(e -> {
                    isUpdating = false;
                    return null;
                });
    }
}
