package com.weaksignal.vpp.backend.control.distributed;

import java.nio.file.Path;
import java.util.List;

/**
 * 分布式故障对照实验命令行入口。
 */
public final class DistributedFaultExperimentMain {

    private DistributedFaultExperimentMain() {
    }

    /**
     * 执行三策略、五场景、三随机种子的故障对照实验。
     *
     * @param args 第一个参数为输出目录；省略时输出到 {@code target/distributed-fault-experiment}
     * @throws Exception 导出失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Path outputDirectory = args.length == 0 ? Path.of("target", "distributed-fault-experiment") : Path.of(args[0]);
        DistributedDispatchProperties properties = new DistributedDispatchProperties(
                "distributed-fault-experiment-v1", 0.05, 0.03, 0.01, 0.02,
                200, 500, 1000, 500, 1000, 3000, 1500, 2000,
                0.34, 0.33, 0.33, 1.0, 0.01, 2, 0.5
        );
        DistributedFaultExperimentRunner.ExperimentExport exported = new DistributedFaultExperimentRunner(properties)
                .run(outputDirectory, List.of(101L, 202L, 303L));
        System.out.printf("已导出 %d 条原始记录和 %d 条统计记录：%s%n", exported.rawRecordCount(), exported.summaryCount(), outputDirectory);
    }
}
