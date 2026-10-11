package in.ac.iitm.evenhand.app;

import in.ac.iitm.evenhand.core.AuditVerdict;
import in.ac.iitm.evenhand.core.Exposure;
import in.ac.iitm.evenhand.estimate.Settings;
import in.ac.iitm.evenhand.ingest.Pseudonymiser;
import in.ac.iitm.evenhand.outcome.Outcome;
import in.ac.iitm.evenhand.report.HtmlReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineTest {

    /** 39 no-effect worlds: the fewest for which p can fall below 0.05 at all (1/40). */
    private static final Settings QUICK = Settings.defaults().withDraws(40, 39);

    private static final Map<String, Double> EXAMPLE_WEIGHTS = Map.of("Quiz 1", 20.0, "Quiz 2", 20.0, "End-sem", 60.0);

    /** A marks file from the classpath: the bundled example, or one of the test files. */
    static String file(String name) throws IOException {
        try (InputStream in = PipelineTest.class.getResourceAsStream("/" + name)) {
            if (in == null) throw new IOException("missing " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Pipeline.Run run(String csv, CourseConfig config) throws IOException {
        return Pipeline.run(new BufferedReader(new StringReader(csv)), Pseudonymiser.withSalt(new byte[32]), config, QUICK);
    }

    private static CourseConfig config(Map<String, Double> weights) {
        return new CourseConfig("Example course", weights, CourseConfig.parseRule("relative"), true);
    }

    @Test
    @DisplayName("end to end: a marks file goes in, a report with a re-check list comes out")
    void endToEnd() throws IOException {
        Pipeline.Run r = run(file("example-marks.csv"), config(EXAMPLE_WEIGHTS));

        assertThat(r.verdict()).isInstanceOf(AuditVerdict.Estimable.class);
        assertThat(r.model()).isPresent();
        assertThat(r.model().get().noEffect().rejectsAt(0.05)).isTrue();
        assertThat(r.outcome().orElseThrow().shortlist()).isInstanceOf(Outcome.Emitted.class);
        assertThat(HtmlReport.instructor(r.report())).contains("Bottom line", "Scripts worth a second look");
    }

    @Test
    @DisplayName("no roll number and no TA name reaches either report")
    void reportsCarryNoIdentifiers() throws IOException {
        Pipeline.Run r = run(file("example-marks.csv"), config(EXAMPLE_WEIGHTS));
        String both = HtmlReport.instructor(r.report()) + HtmlReport.committee(r.report());

        assertThat(both).doesNotContainPattern("[A-Z]{2}\\d{2}B\\d{3}").doesNotContain("Ravi", "Meena", "Arjun", "Divya");
    }

    @Test
    @DisplayName("one marker per question: safe by design, nothing estimated, no list")
    void questionWiseIsSafeByDesign() throws IOException {
        Pipeline.Run r = run(file("question-wise.csv"), config(Map.of()));

        assertThat(r.verdict().permitsEstimation()).isFalse();
        assertThat(r.verdict().exposure().status()).isEqualTo(Exposure.Status.NONE);
        assertThat(r.model()).isEmpty();
    }

    @Test
    @DisplayName("piles that follow the department code: estimated, but no re-check list is offered")
    void departmentPilesAreRefused() throws IOException {
        Pipeline.Run r = run(file("department-piles.csv"), config(Map.of()));

        assertThat(r.verdict()).isInstanceOf(AuditVerdict.EstimableUnderExchangeability.class);
        assertThat(r.outcome().orElseThrow().shortlist()).isInstanceOfSatisfying(Outcome.Refused.class,
                ref -> assertThat(ref.reason()).contains("department"));
    }

    @Test
    @DisplayName("a one-row-per-student mark sheet runs through the same pipeline")
    void oneRowPerStudentSheet() throws IOException {
        Pipeline.Run r = run(file("one-row-per-student.csv"), config(Map.of()));

        assertThat(r.ingest().responses()).isNotEmpty();
        assertThat(r.verdict().permitsEstimation()).isTrue();
        assertThat(HtmlReport.instructor(r.report())).contains("Bottom line");
    }

    @Test
    @DisplayName("a weight for an assessment that is not in the file is an error, not a silent zero")
    void misnamedWeightIsRejected() {
        CourseConfig wrong = config(Map.of("Quiz 1", 20.0, "Quiz 2", 20.0, "Endsem", 60.0));

        assertThatThrownBy(() -> run(file("example-marks.csv"), wrong)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End-sem");
    }

    @Test
    @DisplayName("rules parse from the command line: relative, sigma-only and absolute")
    void parsesRules() {
        assertThat(CourseConfig.parseRule("relative").topFraction()).isEqualTo(0.10);
        assertThat(CourseConfig.parseRule("relative-sigma").topFraction()).isZero();
        assertThat(CourseConfig.parseRule("absolute:S=90,A=80,B=70,C=60,D=50,E=40").absolute()).hasSize(6);
        assertThatThrownBy(() -> CourseConfig.parseRule("curve")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the in-memory key maps pseudonyms back for the terminal, and only there")
    void keyLookup() {
        Main.KeyLookup key = Main.KeyLookup.of("# header\nsalt,00\nidentifier,label,digest\nCE23B092,Student 4,ab12\nTA01,Assistant 2,cd34\n");

        assertThat(key.real("Student 4")).isEqualTo("CE23B092");
        assertThat(key.real("Assistant 2")).isEqualTo("TA01");
        assertThat(key.real("Student 99")).isEqualTo("?");
    }
}
