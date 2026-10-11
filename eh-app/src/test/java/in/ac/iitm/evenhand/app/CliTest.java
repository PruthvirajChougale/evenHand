package in.ac.iitm.evenhand.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliTest {

    @Test
    @DisplayName("run on a marks file: the instructor's two reports appear, with no roll number in either")
    void runWritesBothReports(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("marks.csv");
        Files.writeString(csv, PipelineTest.file("example-marks.csv"));

        Main.run(csv, Map.of("weights", "Quiz 1=20,Quiz 2=20,End-sem=60", "out", dir.resolve("out").toString()));

        String report = Files.readString(dir.resolve("out/report.html"));
        String committee = Files.readString(dir.resolve("out/committee.html"));
        assertThat(report).contains("Even Hand", "Bottom line", "Scripts worth a second look");
        assertThat(committee).contains("Class Committee sheet");
        assertThat(report + committee).doesNotContainPattern("[A-Z]{2}\\d{2}B\\d{3}");
        assertThat(dir.resolve("out/course.key")).doesNotExist();
    }

    @Test
    @DisplayName("flags are read as --name value pairs after the positional arguments")
    void flags() {
        Map<String, String> f = Main.flags(new String[] { "run", "marks.csv", "--rule", "relative", "--out", "x" });

        assertThat(f).containsEntry("rule", "relative").containsEntry("out", "x").hasSize(2);
    }
}
