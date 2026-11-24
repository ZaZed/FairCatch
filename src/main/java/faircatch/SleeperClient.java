package faircatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SleeperClient {

private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

private final ObjectMapper playerMapper = new ObjectMapper();

private final Path playerCache = Path.of(
                System.getProperty("java.io.tmpdir"),
                "faircatch-nfl-players.json"
        );

        private CompletableFuture<JsonNode> playersRequest;
        private volatile long playersLoadedAt;

    public CompletableFuture<String> getLeague(String leagueId) {
        return getJson("/league/" + leagueId);
    }

    public CompletableFuture<String> getRosters(String leagueId) {
        return getJson("/league/" + leagueId + "/rosters");
    }

    public CompletableFuture<String> getUsers(String leagueId) {
        return getJson("/league/" + leagueId + "/users");
    }

    public synchronized CompletableFuture<JsonNode> getPlayers() {
    long oneDay = Duration.ofDays(1).toMillis();

    // Reuse a current download or a recently loaded player list.
    if (playersRequest != null
            && !playersRequest.isCompletedExceptionally()
            && (!playersRequest.isDone()
                || System.currentTimeMillis() - playersLoadedAt < oneDay)) {
        return playersRequest;
    }

    playersRequest = CompletableFuture.supplyAsync(() -> {
        // Check for a saved copy from the last 24 hours.
        try {
            long savedAt = Files.getLastModifiedTime(playerCache).toMillis();

            if (System.currentTimeMillis() - savedAt < oneDay) {
                JsonNode saved = playerMapper.readTree(
                        Files.readString(playerCache)
                );

                if (saved != null && saved.isObject()) {
                    playersLoadedAt = savedAt;
                    return saved;
                }
            }
        } catch (IOException ignored) {
            // No usable saved copy. Download one below.
        }

        return null;

    }).thenCompose(saved -> {
        if (saved != null) {
            return CompletableFuture.completedFuture(saved);
        }

        return getJson("/players/nfl").thenApply(data -> {
            JsonNode players;

            try {
                players = playerMapper.readTree(data);
            } catch (IOException error) {
                throw new IllegalStateException(
                        "Could not read player information.", error
                );
            }

            if (players == null || !players.isObject()) {
                throw new IllegalStateException(
                        "Sleeper returned unexpected player information."
                );
            }

            try {
                Files.writeString(playerCache, data);
            } catch (IOException error) {
                System.err.println(
                        "Could not save player cache: " + error.getMessage()
                );
            }

            playersLoadedAt = System.currentTimeMillis();
            return players;
        });
    });

    return playersRequest;
}

    private CompletableFuture<String> getJson(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sleeper.app/v1" + path))
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
                        "No data found. Check your league ID."
                );
            }

            return data;
        });
    }

    public CompletableFuture<String> getProjections(int season, int week) {
    if (season < 2000 || week < 1 || week > 18) {
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid season or week.")
        );
    }

    String url = "https://api.sleeper.com/projections/nfl/"
            + season + "/" + week + "?season_type=regular";

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

    return client.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
    ).thenApply(response -> {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Could not load projections. HTTP "
                    + response.statusCode()
            );
        }

        return response.body();
    });
}
}