package in.ac.iitm.evenhand.estimate;

import in.ac.iitm.evenhand.core.AnchoredDesign;
import in.ac.iitm.evenhand.core.Anchoring;
import in.ac.iitm.evenhand.core.AuditVerdict;
import in.ac.iitm.evenhand.core.DesignCell;
import in.ac.iitm.evenhand.core.FittedModel;
import in.ac.iitm.evenhand.core.MarkingDesign;
import in.ac.iitm.evenhand.core.Response;
import in.ac.iitm.evenhand.design.IdentifiabilityAudit;
import in.ac.iitm.evenhand.ingest.CsvIngest;
import in.ac.iitm.evenhand.ingest.Pseudonymiser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeverityEstimatorTest {

    @Test
    @DisplayName("noise-free additive marks: backfitting recovers the severities exactly")
    void exactRecoveryOnAdditiveData() {
        // 40 students x 4 questions, each question double-marked by two of three assistants,
        // marks generated exactly from mean + ability + easiness - severity.
        double[] severity = { 6, -2, -4 };            // already averages zero
        double[] easiness = { 10, -5, 0, -5 };
        List<Response> responses = new ArrayList<>();
        int students = 40;
        for (int s = 0; s < students; s++) {
            double ability = (s % 10) - 4.5;
            for (int q = 0; q < 4; q++) {
                for (int g : new int[] { q % 3, (q + 1) % 3 }) {
                    int score = (int) Math.round(10 * (60 + ability + easiness[q] - severity[g]));
                    responses.add(new Response(new DesignCell(s, q, g, 0, DesignCell.NO_POSITION, g == q % 3), score, 1000, 10));
                }
            }
        }
        MarkingDesign design = MarkingDesign.fromResponses(responses, labels("S", students), labels("Q", 4), labels("A", 3));
        Settings noShrink = Settings.defaults().withShrinkage(0, 0).withDraws(0, 0);

        FittedModel m = fit(design, responses, noShrink);

        for (int g = 0; g < 3; g++) assertThat(m.severity(g)).isCloseTo(severity[g], within(0.01));
        assertThat(m.diagnostics().converged()).isTrue();
    }

    @Test
    @DisplayName("a noisy marks file with known severities: each estimate within 1.5 points, inside its interval")
    void recoversSeveritiesFromANoisyFile() {
        double[] truth = { 4, 2, -6 };
        Course course = Course.doubleMarked(truth, 120, 1);
        CsvIngest.IngestResult in = ingest(course.csv());

        FittedModel m = fit(in.design(), in.responses(), Settings.defaults().withDraws(200, 0));

        double[] expected = course.truthByLabel(in);
        for (int g = 0; g < expected.length; g++) {
            assertThat(m.severity(g)).isCloseTo(expected[g], within(1.5));
            double[] ci = m.interval95(g);
            assertThat(expected[g]).isBetween(ci[0] - 0.5, ci[1] + 0.5);
        }
    }

    @Test
    @DisplayName("identical assistants: the no-effect test does not cry wolf")
    void noEffectTestAcceptsIdenticalAssistants() {
        CsvIngest.IngestResult in = ingest(Course.doubleMarked(new double[] { 0, 0, 0 }, 120, 2).csv());

        FittedModel m = fit(in.design(), in.responses(), Settings.defaults().withDraws(0, 49));

        assertThat(m.noEffect().pValue()).isGreaterThan(0.05);
    }

    @Test
    @DisplayName("one pile per assistant, identical assistants: a lucky pile is not called a harsh assistant")
    void noEffectTestAllowsForTheLuckOfTheDeal() {
        // Regression: the no-effect worlds once kept every student in their real pile, so the
        // chance that one assistant got the stronger pile was never allowed for and was reported
        // as severity. Abilities are now dealt afresh in every no-effect world.
        int falseAlarms = 0;
        for (long seed = 1; seed <= 4; seed++) {
            CsvIngest.IngestResult in = ingest(Course.piles(new double[] { 0, 0, 0, 0 }, 120, seed).csv());
            FittedModel m = fit(in.design(), in.responses(), Settings.defaults().withDraws(0, 49));
            if (m.noEffect().rejectsAt(0.05)) falseAlarms++;
        }
        assertThat(falseAlarms).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clearly different assistants: the no-effect test rejects")
    void noEffectTestRejectsRealDifferences() {
        CsvIngest.IngestResult in = ingest(Course.doubleMarked(new double[] { 6, 0, -6 }, 120, 3).csv());

        FittedModel m = fit(in.design(), in.responses(), Settings.defaults().withDraws(0, 49));

        assertThat(m.noEffect().pValue()).isLessThan(0.05);
    }

    @Test
    @DisplayName("the same seed gives the same report, however the threads are scheduled")
    void reproducible() {
        CsvIngest.IngestResult in = ingest(Course.doubleMarked(new double[] { 3, -1, -2 }, 90, 5).csv());
        Settings s = Settings.defaults().withDraws(50, 19);

        FittedModel a = fit(in.design(), in.responses(), s);
        FittedModel b = fit(in.design(), in.responses(), s);

        assertThat(a.severityDraws()).isDeepEqualTo(b.severityDraws());
        assertThat(a.noEffect().pValue()).isEqualTo(b.noEffect().pValue());
    }

    @Test
    @DisplayName("shrinkage pulls a thinly-evidenced severity towards the panel, never past it")
    void shrinkageOnlyPullsTowardsZero() {
        CsvIngest.IngestResult in = ingest(Course.doubleMarked(new double[] { 5, 0, -5 }, 90, 4).csv());

        FittedModel loose = fit(in.design(), in.responses(), Settings.defaults().withShrinkage(0, 0).withDraws(0, 0));
        FittedModel tight = fit(in.design(), in.responses(), Settings.defaults().withShrinkage(0, 5000).withDraws(0, 0));

        double looseSpread = Arrays.stream(loose.graders().estimates()).map(Math::abs).sum();
        double tightSpread = Arrays.stream(tight.graders().estimates()).map(Math::abs).sum();
        assertThat(tightSpread).isLessThan(looseSpread);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A marks file built for a test, with the assistants' severities known. Marks are
     * mean + ability + easiness - severity + noise, in percent, rounded to half marks on
     * 10-mark questions. Fixed seeds keep every test reproducible.
     */
    private record Course(String csv, double[] severity) {

        private static final int QUESTIONS = 6;

        /** Script-wise piles (student s goes to assistant s mod k); every fifth script marked again by the next assistant. */
        static Course doubleMarked(double[] severity, int students, long seed) {
            return build(severity, students, seed, true);
        }

        /** Script-wise piles and nothing else: comparison rests on the piles being alike. */
        static Course piles(double[] severity, int students, long seed) {
            return build(severity, students, seed, false);
        }

        private static Course build(double[] severity, int students, long seed, boolean secondMarks) {
            Random rng = new Random(seed);
            double[] easiness = new double[QUESTIONS];
            for (int q = 0; q < QUESTIONS; q++) easiness[q] = rng.nextGaussian() * 8;
            StringBuilder csv = new StringBuilder("roll_no,question,max_marks,marks,ta,role\n");
            for (int s = 0; s < students; s++) {
                String roll = String.format(Locale.ROOT, "CE23B%03d", s + 1);
                double ability = rng.nextGaussian() * 12;
                int pile = s % severity.length;
                for (int q = 0; q < QUESTIONS; q++) {
                    double base = 60 + ability + easiness[q];
                    double luck = rng.nextGaussian() * 6;    // the same answer, so the same luck for both markers
                    row(csv, roll, q, pile, base - severity[pile] + luck + rng.nextGaussian() * 3, "record");
                    if (secondMarks && s % 5 == 0) {
                        int other = (pile + 1) % severity.length;
                        row(csv, roll, q, other, base - severity[other] + luck + rng.nextGaussian() * 3, "second");
                    }
                }
            }
            return new Course(csv.toString(), severity.clone());
        }

        private static void row(StringBuilder csv, String roll, int q, int ta, double percent, String role) {
            double marks = Math.max(0, Math.min(10, Math.round(percent / 10 * 2) / 2.0));
            csv.append(roll).append(",Q").append(q + 1).append(",10,").append(marks)
               .append(",TA").append(ta + 1).append(',').append(role).append('\n');
        }

        /** True severities lined up with the pseudonymised assistants, centred like the estimates. */
        double[] truthByLabel(CsvIngest.IngestResult in) {
            Map<String, String> realByLabel = new HashMap<>();
            for (String line : in.keyFileContents().split("\n")) {
                String[] p = line.split(",");
                if (p.length == 3 && p[1].startsWith("Assistant ")) realByLabel.put(p[1], p[0]);
            }
            List<String> labels = in.design().graderLabels();
            double[] t = new double[labels.size()];
            for (int g = 0; g < t.length; g++) {
                t[g] = severity[Integer.parseInt(realByLabel.get(labels.get(g)).substring(2)) - 1];
            }
            double bar = Arrays.stream(t).average().orElse(0);
            for (int g = 0; g < t.length; g++) t[g] -= bar;
            return t;
        }
    }

    private static FittedModel fit(MarkingDesign design, List<Response> responses, Settings settings) {
        AuditVerdict verdict = IdentifiabilityAudit.audit(design);
        return SeverityEstimator.fit(AnchoredDesign.of(design, verdict, Anchoring.standard()), responses, settings);
    }

    private static CsvIngest.IngestResult ingest(String csv) {
        try {
            return CsvIngest.read(new BufferedReader(new StringReader(csv)), Pseudonymiser.withSalt(new byte[32]));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static List<String> labels(String prefix, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(prefix + i);
        return out;
    }
}
