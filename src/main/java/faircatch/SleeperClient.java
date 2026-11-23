package faircatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class SleeperClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CompletableFuture<String> getLeague(String leagueId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://api.sleeper.app/v1/league/" + leagueId
                ))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return client.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Sleeper returned HTTP " + response.statusCode()
                        + ". Check your league ID and try again."
                );
            }

            String data = response.body().trim();

            if (data.isEmpty() || data.equals("null")) {
                throw new IllegalStateException(
                        "No league found. Check your league ID."
                );
            }

            return data;
        });
    }
}