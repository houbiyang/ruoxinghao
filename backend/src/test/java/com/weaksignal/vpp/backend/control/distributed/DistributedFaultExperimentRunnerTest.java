package com.weaksignal.vpp.backend.control.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 分布式故障对照实验导出测试。
 */
class DistributedFaultExperimentRunnerTest {

    @Test
    void shouldExportThreeStrategiesFiveScenariosAndMultipleSeeds() throws Exception {
        DistributedDispatchProperties properties = new DistributedDispatchProperties(
                "experiment-test-v1", 0.05, 0.03, 0.01, 0.02,
                200, 500, 1000, 500, 1000, 3000, 1500, 2000,
                0.34, 0.33, 0.33, 1.0, 0.01, 2, 0.5
        );
        Path outputDirectory = Files.createTempDirectory("distributed-fault-experiment-");

        DistributedFaultExperimentRunner.ExperimentExport exported = new DistributedFaultExperimentRunner(properties)
                .run(outputDirectory, List.of(101L, 202L, 303L));

        assertEquals(3 * 5 * 3 * 80, exported.rawRecordCount());
        assertEquals(3 * 5, exported.summaryCount());
        assertTrue(Files.size(exported.rawCsv()) > 0);
        assertTrue(Files.readString(exported.summaryCsv()).contains("rmse_kw"));
        assertTrue(Files.readString(exported.summaryCsv()).contains("recovery_time_seconds"));
        assertTrue(Files.readString(exported.curveSvg()).contains("<polyline"));
        assertTrue(Files.readAllLines(exported.rawCsv()).stream()
                .filter(line -> line.startsWith("ADAPTIVE,NORMAL,"))
                .anyMatch(line -> line.contains(",true,")));
    }
}
