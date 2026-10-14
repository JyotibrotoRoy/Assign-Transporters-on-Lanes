package com.freightfox.transporter_assignment.optimization;

import com.freightfox.transporter_assignment.exception.NoFeasibleAssignmentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhaustiveAssignmentOptimizerTest {

    private final AssignmentOptimizer optimizer = new ExhaustiveAssignmentOptimizer();

    private static List<LaneInput> lanes(long... ids) {
        List<LaneInput> list = new ArrayList<>();
        for (long id : ids) {
            list.add(new LaneInput(id));
        }
        return list;
    }

    /** t(1, 10, "500", 11, "700") = transporter 1 quoting lane 10 at 500 and lane 11 at 700. */
    private static TransporterInput t(long id, Object... laneAndQuote) {
        Map<Long, BigDecimal> quotes = new HashMap<>();
        for (int i = 0; i < laneAndQuote.length; i += 2) {
            quotes.put(((Number) laneAndQuote[i]).longValue(),
                    new BigDecimal(String.valueOf(laneAndQuote[i + 1])));
        }
        return new TransporterInput(id, quotes);
    }

    private static void assertCost(String expected, AssignmentResult result) {
        assertEquals(0, new BigDecimal(expected).compareTo(result.totalCost()),
                "expected " + expected + " but was " + result.totalCost());
    }

    private static Map<Long, Long> laneToTransporter(AssignmentResult result) {
        Map<Long, Long> map = new HashMap<>();
        result.assignments().forEach(a -> map.put(a.laneId(), a.transporterId()));
        return map;
    }

    /** Every invariant a valid result must satisfy. */
    private static void assertInvariants(AssignmentResult r, List<LaneInput> lanes,
                                         List<TransporterInput> transporters, int max) {
        Map<Long, TransporterInput> byId = new HashMap<>();
        transporters.forEach(tr -> byId.put(tr.id(), tr));

        assertEquals(lanes.size(), r.assignments().size());
        Set<Long> coveredLanes = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        Set<Long> used = new HashSet<>();
        for (LaneAssignment a : r.assignments()) {
            assertTrue(coveredLanes.add(a.laneId()), "lane assigned twice: " + a.laneId());
            BigDecimal submitted = byId.get(a.transporterId()).quotesByLaneId().get(a.laneId());
            assertTrue(submitted != null, "assignment uses a quote that was never submitted");
            assertEquals(0, submitted.compareTo(a.quote()));
            sum = sum.add(a.quote());
            used.add(a.transporterId());
        }
        lanes.forEach(l -> assertTrue(coveredLanes.contains(l.id()), "lane not covered: " + l.id()));
        assertEquals(0, sum.compareTo(r.totalCost()));
        assertTrue(used.size() <= max, "limit exceeded");
        List<Long> usedSorted = new ArrayList<>(used);
        Collections.sort(usedSorted);
        assertEquals(usedSorted, r.selectedTransporters());
    }

    // ---------- small hand-calculated cases ----------

    @Test
    void singleLaneSingleTransporter() {
        List<TransporterInput> ts = List.of(t(1, 1, 500));
        AssignmentResult r = optimizer.optimize(lanes(1), ts, 1);
        assertCost("500", r);
        assertEquals(List.of(1L), r.selectedTransporters());
        assertInvariants(r, lanes(1), ts, 1);
    }

    @Test
    void oneTransporterCoversEverythingEvenWhenLimitIsLarger() {
        // T1 is cheapest on both lanes, so only T1 should be selected although K=3.
        List<TransporterInput> ts = List.of(t(1, 1, 10, 2, 10), t(2, 1, 20, 2, 20), t(3, 1, 30, 2, 30));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 3);
        assertCost("20", r);
        assertEquals(List.of(1L), r.selectedTransporters());
    }

    @Test
    void cheapestPerLaneNeedsTooManyTransportersSoLimitChangesTheAnswer() {
        // Lane cheapest: L1->T1(10), L2->T2(11), L3->T3(10) = 31 using 3 transporters.
        // T4 covers every lane: 15 / 15 / 12.
        List<TransporterInput> ts = List.of(
                t(1, 1, 10), t(2, 2, 11), t(3, 3, 10), t(4, 1, 15, 2, 15, 3, 12));
        List<LaneInput> ls = lanes(1, 2, 3);

        AssignmentResult k3 = optimizer.optimize(ls, ts, 3);
        assertCost("31", k3);
        assertEquals(List.of(1L, 2L, 3L), k3.selectedTransporters());

        // K=2: {1,4} = 10+15+12 = 37, {2,4} = 15+11+12 = 38, {3,4} = 15+15+10 = 40.
        AssignmentResult k2 = optimizer.optimize(ls, ts, 2);
        assertCost("37", k2);
        assertEquals(List.of(1L, 4L), k2.selectedTransporters());
        assertInvariants(k2, ls, ts, 2);

        // K=1: only T4 can cover everything = 42.
        AssignmentResult k1 = optimizer.optimize(ls, ts, 1);
        assertCost("42", k1);
        assertEquals(List.of(4L), k1.selectedTransporters());
    }

    @Test
    void missingQuoteIsNotTreatedAsZero() {
        // T2 would be "free" on lane 2 if a missing quote counted as 0; it must not be eligible.
        List<TransporterInput> ts = List.of(t(1, 1, 5, 2, 5), t(2, 1, 1));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 1);
        assertCost("10", r);
        assertEquals(List.of(1L), r.selectedTransporters());
    }

    @Test
    void zeroQuotesAreValidAndSummedCorrectly() {
        List<TransporterInput> ts = List.of(t(1, 1, 0, 2, 0));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 1);
        assertCost("0", r);
    }

    @Test
    void largeMonetaryValuesAreSummedExactly() {
        List<TransporterInput> ts = List.of(t(1, 1, "999999999999999.99", 2, "999999999999999.99"));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 1);
        assertCost("1999999999999999.98", r);
    }

    @Test
    void limitLargerThanAvailableTransportersIsAccepted() {
        List<TransporterInput> ts = List.of(t(1, 1, 5), t(2, 2, 7));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 50);
        assertCost("12", r);
        assertEquals(List.of(1L, 2L), r.selectedTransporters());
    }

    @Test
    void laneWithNoQuotesIsInfeasible() {
        List<TransporterInput> ts = List.of(t(1, 1, 5), t(2, 1, 6));
        assertThrows(NoFeasibleAssignmentException.class,
                () -> optimizer.optimize(lanes(1, 2), ts, 2));
    }

    @Test
    void everyLaneQuotedButLimitTooSmallIsInfeasible() {
        List<TransporterInput> ts = List.of(t(1, 1, 5), t(2, 2, 6));
        assertThrows(NoFeasibleAssignmentException.class,
                () -> optimizer.optimize(lanes(1, 2), ts, 1));
    }

    @Test
    void invalidArgumentsAreRejected() {
        List<TransporterInput> ts = List.of(t(1, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(lanes(1), ts, 0));
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(lanes(), ts, 1));
    }

    @Test
    void tieOnCostPrefersMoreDistinctTransporters() {
        // {2} = 5+3 = 8 and {1,2} = 5+3 = 8 -> same cost, so the solution using both transporters wins.
        List<TransporterInput> ts = List.of(t(1, 1, 5), t(2, 1, 5, 2, 3));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 2);
        assertCost("8", r);
        assertEquals(List.of(1L, 2L), r.selectedTransporters());
        assertEquals(1L, laneToTransporter(r).get(1L));
        assertEquals(2L, laneToTransporter(r).get(2L));
    }

    @Test
    void costStillBeatsTransporterCount() {
        // Any lane given to T2 costs more (9 or 6 instead of 5), so T1 alone at 10 wins.
        List<TransporterInput> ts = List.of(t(1, 1, 5, 2, 5), t(2, 1, 9, 2, 6));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 2);
        assertCost("10", r);
        assertEquals(List.of(1L), r.selectedTransporters());
    }

    @Test
    void equalQuotesInsideOneGroupAreSpreadAcrossTransporters() {
        // Both lanes cost 5 from either transporter: 10 either way, so use both transporters.
        List<TransporterInput> ts = List.of(t(1, 1, 5, 2, 5), t(2, 1, 5, 2, 5));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 2);
        assertCost("10", r);
        assertEquals(List.of(1L, 2L), r.selectedTransporters());
        assertEquals(1L, laneToTransporter(r).get(1L));
        assertEquals(2L, laneToTransporter(r).get(2L));
        assertInvariants(r, lanes(1, 2), ts, 2);
    }

    @Test
    void spreadingMovesAnEarlierLaneWhenALaterLaneHasNoOtherChoice() {
        // L2 can only go to T1, so L1 must move to T2 for both transporters to be used.
        List<TransporterInput> ts = List.of(t(1, 1, 5, 2, 5), t(2, 1, 5));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 2);
        assertCost("10", r);
        assertEquals(List.of(1L, 2L), r.selectedTransporters());
        assertEquals(2L, laneToTransporter(r).get(1L));
        assertEquals(1L, laneToTransporter(r).get(2L));
    }

    @Test
    void spreadingNeverExceedsTheLimit() {
        // Three lanes tied across three transporters, but only two may be used.
        List<TransporterInput> ts = List.of(
                t(1, 1, 5, 2, 5, 3, 5), t(2, 1, 5, 2, 5, 3, 5), t(3, 1, 5, 2, 5, 3, 5));
        AssignmentResult r = optimizer.optimize(lanes(1, 2, 3), ts, 2);
        assertCost("15", r);
        assertEquals(List.of(1L, 2L), r.selectedTransporters());
        assertInvariants(r, lanes(1, 2, 3), ts, 2);
    }

    @Test
    void tieOnCostAndCountPrefersLexicographicallySmallestIds() {
        List<TransporterInput> ts = List.of(t(3, 1, 4), t(2, 1, 4), t(9, 1, 4));
        AssignmentResult r = optimizer.optimize(lanes(1), ts, 1);
        assertEquals(List.of(2L), r.selectedTransporters());
    }

    @Test
    void tiedLanesAreSpreadInIdOrderWhateverTheInputOrder() {
        // Transporter 2 is listed first on purpose; lowest ids still get the lowest lanes.
        List<TransporterInput> ts = List.of(t(2, 1, 4, 2, 9), t(1, 1, 4, 2, 9));
        AssignmentResult r = optimizer.optimize(lanes(1, 2), ts, 2);
        assertCost("13", r);
        assertEquals(List.of(1L, 2L), r.selectedTransporters());
        assertEquals(1L, laneToTransporter(r).get(1L));
        assertEquals(2L, laneToTransporter(r).get(2L));
    }

    @Test
    void insertionOrderDoesNotChangeTheResult() {
        List<TransporterInput> ts = new ArrayList<>(List.of(
                t(1, 1, 10), t(2, 2, 11), t(3, 3, 10), t(4, 1, 15, 2, 15, 3, 12)));
        List<LaneInput> ls = lanes(1, 2, 3);
        AssignmentResult expected = optimizer.optimize(ls, ts, 2);
        Collections.reverse(ts);
        Collections.reverse(ls);
        assertEquals(expected, optimizer.optimize(ls, ts, 2));
    }

    @Test
    void nonSequentialIdentifiersWork() {
        List<TransporterInput> ts = List.of(t(900, 77, 5, 5, 8), t(12, 77, 6, 5, 7));
        AssignmentResult r = optimizer.optimize(lanes(77, 5), ts, 2);
        assertCost("12", r); // 77 -> 900 (5), 5 -> 12 (7)
        assertEquals(List.of(12L, 900L), r.selectedTransporters());
    }

    // ---------- supplied / sample datasets (expected values from an independent brute force) ----------

    /** Rows = lanes 1..9, columns = T1..T7, taken from transporter_assignment_data.csv. */
    private static final int[][] CSV = {
            {20835, 48844, 39020, 14400, 11601, 35095, 26070},
            {10512, 31326, 20648, 44514, 19760, 12494, 41098},
            {22105, 18640, 31438, 14316, 40870, 17808, 20932},
            {42481, 45828, 36447, 10678, 20635, 36210, 16897},
            {19862, 18297, 12789, 13032, 26421, 39444, 27938},
            {13567, 45810, 49985, 46024, 28809, 29948, 43517},
            {10015, 49573, 35285, 42342, 27815, 17320, 25881},
            {17886, 45122, 10281, 35742, 17024, 10492, 46136},
            {43996, 31856, 40092, 48921, 44691, 37864, 31286}};

    private static List<TransporterInput> fromMatrix(int[][] matrix) {
        List<TransporterInput> list = new ArrayList<>();
        for (int tr = 0; tr < matrix[0].length; tr++) {
            Map<Long, BigDecimal> quotes = new HashMap<>();
            for (int lane = 0; lane < matrix.length; lane++) {
                quotes.put((long) lane + 1, BigDecimal.valueOf(matrix[lane][tr]));
            }
            list.add(new TransporterInput((long) tr + 1, quotes));
        }
        return list;
    }

    private static List<LaneInput> laneRange(int count) {
        List<LaneInput> list = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            list.add(new LaneInput(i));
        }
        return list;
    }

    @Test
    void suppliedCsvWithThreeTransporters() {
        List<TransporterInput> ts = fromMatrix(CSV);
        AssignmentResult r = optimizer.optimize(laneRange(9), ts, 3);
        assertCost("134876", r);
        assertEquals(List.of(1L, 4L, 6L), r.selectedTransporters());
        assertInvariants(r, laneRange(9), ts, 3);
    }

    @Test
    void suppliedCsvOtherLimits() {
        List<TransporterInput> ts = fromMatrix(CSV);
        assertCost("201259", optimizer.optimize(laneRange(9), ts, 1));
        assertCost("148402", optimizer.optimize(laneRange(9), ts, 2));
        AssignmentResult all = optimizer.optimize(laneRange(9), ts, 7);
        assertCost("125045", all);
        assertEquals(List.of(1L, 3L, 4L, 5L, 7L), all.selectedTransporters());
    }

    @Test
    void pdfSampleCaseOneFiveLanes() {
        int[][] m = {
                {20835, 48844, 39020, 14400, 11601, 35095, 26070},
                {10512, 31326, 20648, 44514, 19760, 12494, 41098},
                {22105, 18640, 31438, 14316, 40870, 17808, 20932},
                {42481, 45828, 36447, 10678, 20635, 36210, 16897},
                {19862, 18297, 12789, 13032, 26421, 39444, 27938}};
        AssignmentResult r = optimizer.optimize(laneRange(5), fromMatrix(m), 3);
        assertCost("60139", r);
        assertEquals(List.of(1L, 4L, 5L), r.selectedTransporters());
    }

    @Test
    void pdfSampleCaseTwoSparseQuotes() {
        List<TransporterInput> ts = List.of(
                t(1, 1, 1000, 2, 1500), t(2, 3, 2000, 4, 1800), t(3, 5, 1200), t(4, 1, 1100, 4, 1750));
        AssignmentResult r = optimizer.optimize(lanes(1, 2, 3, 4, 5), ts, 3);
        assertCost("7500", r);
        assertEquals(List.of(1L, 2L, 3L), r.selectedTransporters());
        assertThrows(NoFeasibleAssignmentException.class,
                () -> optimizer.optimize(lanes(1, 2, 3, 4, 5), ts, 2));
    }

    private record Best(BigDecimal cost, int used) {
    }

    /** Tries every lane and transporter combination and deliberately shares no logic with the optimizer. */
    private static Best bruteForce(List<LaneInput> lanes, List<TransporterInput> ts, int max,
                                   int lane, Set<Long> used, BigDecimal running) {
        if (lane == lanes.size()) {
            return new Best(running, used.size());
        }
        Best best = null;
        for (TransporterInput tr : ts) {
            BigDecimal q = tr.quotesByLaneId().get(lanes.get(lane).id());
            if (q == null) {
                continue;
            }
            boolean added = used.add(tr.id());
            if (used.size() <= max) {
                Best candidate = bruteForce(lanes, ts, max, lane + 1, used, running.add(q));
                if (candidate != null && isBetter(candidate, best)) {
                    best = candidate;
                }
            }
            if (added) {
                used.remove(tr.id());
            }
        }
        return best;
    }

    /** Lower cost wins at equal cost, */
    private static boolean isBetter(Best candidate, Best current) {
        if (current == null) {
            return true;
        }
        int byCost = candidate.cost().compareTo(current.cost());
        return byCost < 0 || (byCost == 0 && candidate.used() > current.used());
    }

    @Test
    void matchesBruteForceOnRandomSmallDatasets() {
        Random random = new Random(42);
        for (int trial = 0; trial < 600; trial++) {
            int laneCount = 1 + random.nextInt(4);
            int transporterCount = 1 + random.nextInt(4);
            int max = 1 + random.nextInt(4);
            int quoteRange = trial % 2 == 0 ? 4 : 20; // small range forces many ties
            List<LaneInput> ls = laneRange(laneCount);
            List<TransporterInput> ts = new ArrayList<>();
            for (long id = 1; id <= transporterCount; id++) {
                Map<Long, BigDecimal> quotes = new HashMap<>();
                for (long lane = 1; lane <= laneCount; lane++) {
                    if (random.nextInt(10) < 6) {
                        quotes.put(lane, BigDecimal.valueOf(1 + random.nextInt(quoteRange)));
                    }
                }
                ts.add(new TransporterInput(id, quotes));
            }
            Best expected = bruteForce(ls, ts, max, 0, new HashSet<>(), BigDecimal.ZERO);
            if (expected == null) {
                assertThrows(NoFeasibleAssignmentException.class, () -> optimizer.optimize(ls, ts, max));
            } else {
                AssignmentResult r = optimizer.optimize(ls, ts, max);
                assertEquals(0, expected.cost().compareTo(r.totalCost()), "cost, trial " + trial);
                assertEquals(expected.used(), r.selectedTransporters().size(), "usage, trial " + trial);
                assertInvariants(r, ls, ts, max);
            }
        }
    }
}
