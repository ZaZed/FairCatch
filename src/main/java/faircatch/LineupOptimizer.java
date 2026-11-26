package faircatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LineupOptimizer {
    private static final Map<String, Set<String>> ELIGIBILITY = Map.of(
            "QB", Set.of("QB"),
            "RB", Set.of("RB"),
            "WR", Set.of("WR"),
            "TE", Set.of("TE"),
            "K", Set.of("K"),
            "DEF", Set.of("DEF"),
            "FLEX", Set.of("RB", "WR", "TE"),
            "SUPER_FLEX", Set.of("QB", "RB", "WR", "TE"),
            "REC_FLEX", Set.of("WR", "TE"),
            "WRRB_FLEX", Set.of("WR", "RB")
    );

    public record Player(
            String id,
            Set<String> positions,
            double points
    ) {
        public Player {
            if (id == null || id.isBlank()
                    || positions == null || positions.isEmpty()
                    || !Double.isFinite(points)) {
                throw new IllegalArgumentException(
                        "Invalid player information."
                );
            }

            positions = positions.stream()
                    .map(position ->
                            position.strip().toUpperCase(Locale.ROOT)
                    )
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public record Starter(
            String slot,
            String playerId,
            double points
    ) {
    }

    public record Result(
            List<Starter> starters,
            double totalPoints
    ) {
        public Result {
            starters = List.copyOf(starters);
        }
    }

    private record Choice(
            Choice previous,
            int slot,
            Player player
    ) {
    }

    public static boolean canFill(
            String slot,
            Set<String> positions
    ) {
        Set<String> allowed = ELIGIBILITY.get(
                slot.strip().toUpperCase(Locale.ROOT)
        );

        if (allowed == null) {
            throw new IllegalArgumentException(
                    "Unsupported starting slot: " + slot
            );
        }

        return !Collections.disjoint(allowed, positions);
    }

    public static Result optimize(
            List<String> slots,
            List<Player> players
    ) {
        if (slots.isEmpty() || slots.size() > 16) {
            throw new IllegalArgumentException(
                    "This version supports 1 to 16 starting slots."
            );
        }

        List<String> normalizedSlots = slots.stream()
                .map(slot -> slot.strip().toUpperCase(Locale.ROOT))
                .toList();

        for (String slot : normalizedSlots) {
            if (!ELIGIBILITY.containsKey(slot)) {
                throw new IllegalArgumentException(
                        "Unsupported starting slot: " + slot
                );
            }
        }

        int slotCount = normalizedSlots.size();
        int stateCount = 1 << slotCount;
        int full = stateCount - 1;

        double[] best = new double[stateCount];
        Choice[] choices = new Choice[stateCount];

        Arrays.fill(best, Double.NEGATIVE_INFINITY);
        best[0] = 0;

        Set<String> seenPlayers = new HashSet<>();

        for (Player player : players) {
            if (!seenPlayers.add(player.id())) {
                throw new IllegalArgumentException(
                        "Duplicate player: " + player.id()
                );
            }

            boolean[] eligible = new boolean[slotCount];

            for (int slot = 0; slot < slotCount; slot++) {
                eligible[slot] = canFill(
                        normalizedSlots.get(slot),
                        player.positions()
                );
            }

            // Each bit represents one filled starting slot.
            // Working backward prevents using this player twice.
            for (int state = full; state >= 0; state--) {
                if (best[state] == Double.NEGATIVE_INFINITY) {
                    continue;
                }

                for (int slot = 0; slot < slotCount; slot++) {
                    int bit = 1 << slot;

                    if (!eligible[slot] || (state & bit) != 0) {
                        continue;
                    }

                    int next = state | bit;
                    double score = best[state] + player.points();

                    if (!Double.isFinite(score)) {
                        throw new IllegalArgumentException(
                                "Projected lineup total is too large."
                        );
                    }

                    if (score > best[next]) {
                        best[next] = score;

                        choices[next] = new Choice(
                                choices[state],
                                slot,
                                player
                        );
                    }
                }
            }
        }

        if (best[full] == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException(
                    "This roster cannot fill every starting position."
            );
        }

        Starter[] starters = new Starter[slotCount];

        for (Choice choice = choices[full];
                choice != null;
                choice = choice.previous()) {
            starters[choice.slot()] = new Starter(
                    normalizedSlots.get(choice.slot()),
                    choice.player().id(),
                    choice.player().points()
            );
        }

        return new Result(
                Arrays.asList(starters),
                best[full]
        );
    }
}