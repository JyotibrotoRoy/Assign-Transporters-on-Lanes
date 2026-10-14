package com.freightfox.transporter_assignment.optimization;


import com.freightfox.transporter_assignment.exception.NoFeasibleAssignmentException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ExhaustiveAssignmentOptimizer implements AssignmentOptimizer {

    @Override
    public AssignmentResult optimize(List<LaneInput> lanes,
                                     List<TransporterInput> transporters,
                                     int maxTransporters) {
        if (lanes == null || lanes.isEmpty()) {
            throw new IllegalArgumentException("At least one lane is required");
        }
        if (transporters == null || transporters.isEmpty()) {
            throw new IllegalArgumentException("No transporters available");
        }
        if (maxTransporters < 1) {
            throw new IllegalArgumentException("maxTransporters must be at least 1");
        }

        List<Long> laneIds = lanes.stream().map(LaneInput::id).sorted().toList();
        List<TransporterInput> sorted = transporters.stream()
                .sorted(Comparator.comparing(TransporterInput::id)).toList();

        BigDecimal[][] quote = new BigDecimal[laneIds.size()][sorted.size()];
        for (int l = 0; l < laneIds.size(); l++) {
            for (int t = 0; t < sorted.size(); t++) {
                quote[l][t] = sorted.get(t).quotesByLaneId().get(laneIds.get(l));
            }
        }

        Search search = new Search(laneIds, sorted, quote);
        int limit = Math.min(maxTransporters, sorted.size());
        for (int size = 1; size <= limit; size++) {
            search.enumerate(0, 0, new int[size]);
        }
        if (search.best == null) {
            throw new NoFeasibleAssignmentException(
                    "No assignment covers every lane with at most " + maxTransporters + " transporters");
        }
        return search.best;
    }

    private static final class Search {
        private final List<Long> laneIds;
        private final List<TransporterInput> transporters;
        private final BigDecimal[][] quote;
        private AssignmentResult best;

        Search(List<Long> laneIds, List<TransporterInput> transporters, BigDecimal[][] quote) {
            this.laneIds = laneIds;
            this.transporters = transporters;
            this.quote = quote;
        }

        void enumerate(int start, int depth, int[] chosen) {
            if (depth == chosen.length) {
                evaluate(chosen);
                return;
            }
            for (int t = start; t <= transporters.size() - (chosen.length - depth); t++) {
                chosen[depth] = t;
                enumerate(t + 1, depth + 1, chosen);
            }
        }

        private void evaluate(int[] chosen) {
            int laneCount = laneIds.size();
            BigDecimal[] cheapest = new BigDecimal[laneCount];
            BigDecimal total = BigDecimal.ZERO;
            for (int l = 0; l < laneCount; l++) {
                BigDecimal min = null;
                for (int t : chosen) {
                    BigDecimal q = quote[l][t];
                    if (q != null && (min == null || q.compareTo(min) < 0)) {
                        min = q;
                    }
                }
                if (min == null) {
                    return; // some lane has no eligible selected transporter so we should return
                }
                cheapest[l] = min;
                total = total.add(min);
            }
            if (best != null && total.compareTo(best.totalCost()) > 0) {
                return; // cannot win so skipping the matching work
            }
            AssignmentResult candidate = toResult(total, spreadAcrossTransporters(chosen, cheapest));
            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }

        private int[] spreadAcrossTransporters(int[] chosen, BigDecimal[] cheapest) {
            int laneCount = cheapest.length;
            int[][] tied = new int[laneCount][];
            for (int l = 0; l < laneCount; l++) {
                int[] candidates = new int[chosen.length];
                int count = 0;
                for (int t : chosen) {
                    if (quote[l][t] != null && quote[l][t].compareTo(cheapest[l]) == 0) {
                        candidates[count++] = t;
                    }
                }
                tied[l] = Arrays.copyOf(candidates, count);
            }

            int[] laneOf = new int[transporters.size()];
            Arrays.fill(laneOf, -1);
            for (int l = 0; l < laneCount; l++) {
                tryAssign(l, tied, laneOf, new boolean[transporters.size()]);
            }

            int[] pick = new int[laneCount];
            Arrays.fill(pick, -1);
            for (int t = 0; t < laneOf.length; t++) {
                if (laneOf[t] >= 0) {
                    pick[laneOf[t]] = t;
                }
            }
            for (int l = 0; l < laneCount; l++) {
                if (pick[l] < 0) {
                    pick[l] = tied[l][0];
                }
            }
            return pick;
        }

        private boolean tryAssign(int lane, int[][] tied, int[] laneOf, boolean[] seen) {
            for (int t : tied[lane]) {
                if (laneOf[t] < 0) {
                    laneOf[t] = lane;
                    return true;
                }
            }
            for (int t : tied[lane]) {
                if (!seen[t]) {
                    seen[t] = true;
                    if (tryAssign(laneOf[t], tied, laneOf, seen)) {
                        laneOf[t] = lane;
                        return true;
                    }
                }
            }
            return false;
        }

        private AssignmentResult toResult(BigDecimal total, int[] pick) {
            List<LaneAssignment> assignments = new ArrayList<>();
            for (int l = 0; l < pick.length; l++) {
                assignments.add(new LaneAssignment(
                        laneIds.get(l), transporters.get(pick[l]).id(), quote[l][pick[l]]));
            }
            List<Long> used = assignments.stream()
                    .map(LaneAssignment::transporterId).distinct().sorted().toList();
            return new AssignmentResult(total, List.copyOf(assignments), used);
        }

        private static boolean isBetter(AssignmentResult a, AssignmentResult b) {
            int byCost = a.totalCost().compareTo(b.totalCost());
            if (byCost != 0) {
                return byCost < 0;
            }
            int bySize = Integer.compare(b.selectedTransporters().size(), a.selectedTransporters().size());
            if (bySize != 0) {
                return bySize < 0; // more distinct transporters wins
            }
            for (int i = 0; i < a.selectedTransporters().size(); i++) {
                int byId = a.selectedTransporters().get(i).compareTo(b.selectedTransporters().get(i));
                if (byId != 0) {
                    return byId < 0;
                }
            }
            return false; // same. so keeping the first one
        }
    }
}
