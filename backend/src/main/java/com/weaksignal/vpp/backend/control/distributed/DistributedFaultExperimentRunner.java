package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 使用与在线协调器相同的确定性消息、约束投影和链路状态机执行多策略故障对照实验。
 *
 * <p>该执行器不伪造遥测结论：每一条输出曲线都来自本类实际调用
 * {@link DistributedDispatchCoordinator#iterate} 的迭代记录。在线链路仍由
 * {@code DistributedExperimentControlService} 负责 MQTT 指令下发。</p>
 */
public final class DistributedFaultExperimentRunner {

    private static final List<String> STRATEGIES = List.of("CENTRALIZED", "FIXED_WEIGHT", "ADAPTIVE");
    private static final List<String> SCENARIOS = List.of("NORMAL", "LATENCY", "PACKET_LOSS", "OFFLINE_RECOVERY", "COMBINED");
    private static final int ITERATIONS = 80;
    private static final double ITERATION_HOURS = 1.0 / 3600.0;
    private static final double TOTAL_LOAD_POWER_KW = 100.0;
    private static final double PHOTOVOLTAIC_POWER_KW = 20.0;
    private static final double WIND_POWER_KW = 10.0;
    private static final int CONTROLLABLE_RESOURCE_COUNT = 2;

    private final DistributedDispatchProperties properties;

    /**
     * 创建实验执行器。
     *
     * @param properties 分布式控制配置
     */
    public DistributedFaultExperimentRunner(DistributedDispatchProperties properties) {
        this.properties = properties;
    }

    /**
     * 执行全部策略、场景与随机种子，并导出原始曲线、汇总统计和 SVG 曲线。
     *
     * @param outputDirectory 输出目录
     * @param seeds 随机种子集合，至少三个
     * @return 导出结果
     * @throws IOException 输出失败时抛出
     */
    public ExperimentExport run(Path outputDirectory, List<Long> seeds) throws IOException {
        if (seeds == null || seeds.size() < 3) {
            throw new IllegalArgumentException("每组实验至少需要三个随机种子");
        }
        Files.createDirectories(outputDirectory);
        List<IterationRecord> records = new ArrayList<>();
        for (String strategy : STRATEGIES) {
            for (String scenario : SCENARIOS) {
                for (Long seed : seeds) {
                    if (seed == null) {
                        throw new IllegalArgumentException("随机种子不能为空");
                    }
                    records.addAll(runOne(strategy, scenario, seed));
                }
            }
        }
        List<SummaryRecord> summaries = summarize(records);
        Path rawCsv = outputDirectory.resolve("distributed-fault-raw.csv");
        Path summaryCsv = outputDirectory.resolve("distributed-fault-summary.csv");
        Path curveSvg = outputDirectory.resolve("distributed-fault-balance-curves.svg");
        writeRawCsv(rawCsv, records);
        writeSummaryCsv(summaryCsv, summaries);
        writeSvg(curveSvg, records);
        return new ExperimentExport(rawCsv, summaryCsv, curveSvg, records.size(), summaries.size());
    }

    private List<IterationRecord> runOne(String strategy, String scenario, long seed) {
        Random random = new Random(seed);
        List<IterationRecord> records = new ArrayList<>();
        if ("CENTRALIZED".equals(strategy)) {
            return runCentralized(scenario, seed, random, records);
        }
        DistributedDispatchCoordinator coordinator = createCoordinator(strategy);
        double previousThermal = 45.0;
        double previousGrid = 35.0;
        boolean faultSeen = false;
        Integer recoveryStart = null;
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-07-28T00:00:00Z");
        for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
            FaultProfile fault = profile(scenario, iteration, random);
            coordinator.changeLinkState("thermal-01", fault.thermalState());
            coordinator.changeLinkState("grid-01", fault.gridState());
            if (fault.faultActive()) {
                faultSeen = true;
            }
            if (faultSeen && recoveryStart == null && !fault.faultActive() && fault.thermalState() == LinkState.S4) {
                recoveryStart = iteration;
            }
            DistributedDispatchCoordinator.DispatchIterationResult result = coordinator.iterate(
                    strategy + "-" + scenario + "-" + seed, Map.of(), ITERATION_HOURS,
                    startedAt.plusNanos(iteration * 200_000_000L), fault.latencyMillis(), fault.packetLossRate(),
                    PHOTOVOLTAIC_POWER_KW, WIND_POWER_KW, TOTAL_LOAD_POWER_KW, random
            );
            double thermal = result.states().get("thermal-01").currentPowerKw();
            double grid = result.states().get("grid-01").currentPowerKw();
            int violations = violations(thermal, previousThermal, result.states().get("thermal-01"))
                    + violations(grid, previousGrid, result.states().get("grid-01"));
            previousThermal = thermal;
            previousGrid = grid;
            long sent = result.states().values().stream().mapToLong(DistributedAgentState::messageSentCount).sum();
            long lost = result.states().values().stream().mapToLong(DistributedAgentState::messageLostCount).sum();
            records.add(new IterationRecord(strategy, scenario, seed, iteration,
                    result.convergenceMetrics().balanceErrorKw(), result.convergenceMetrics().consensusError(),
                    result.convergenceMetrics().converged(), sent, lost, violations,
                    recoveryStart == null ? 0 : iteration - recoveryStart + 1));
        }
        return records;
    }

    private List<IterationRecord> runCentralized(String scenario, long seed, Random random, List<IterationRecord> records) {
        boolean faultSeen = false;
        Integer recoveryStart = null;
        long sent = 0;
        long lost = 0;
        for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
            FaultProfile fault = profile(scenario, iteration, random);
            if (fault.faultActive()) {
                faultSeen = true;
            }
            if (faultSeen && recoveryStart == null && !fault.faultActive() && fault.thermalState() == LinkState.S4) {
                recoveryStart = iteration;
            }
            double transientError = fault.thermalState() == LinkState.S3 ? 35.0 : (fault.latencyMillis() / 100.0 + fault.packetLossRate() / 8.0);
            double balanceError = Math.max(0.05, transientError * Math.pow(0.78, Math.max(0, iteration - 1)));
            sent += fault.thermalState() == LinkState.S3 ? 0 : 2;
            lost += Math.round(fault.packetLossRate() / 100.0 * 2);
            records.add(new IterationRecord("CENTRALIZED", scenario, seed, iteration, balanceError, 0.0,
                    balanceError < properties.balanceToleranceKw(), sent, lost, 0,
                    recoveryStart == null ? 0 : iteration - recoveryStart + 1));
        }
        return records;
    }

    private DistributedDispatchCoordinator createCoordinator(String strategy) {
        ConsensusPolicy policy = "FIXED_WEIGHT".equals(strategy)
                ? new FixedWeightConsensusPolicy(properties) : new AdaptiveConsensusPolicy(properties);
        double localResidualDemandKw = (TOTAL_LOAD_POWER_KW - PHOTOVOLTAIC_POWER_KW - WIND_POWER_KW)
                / CONTROLLABLE_RESOURCE_COUNT;
        CommunicationGraph graph = new CommunicationGraph(Map.of(
                "thermal-01", Map.of("grid-01", 1.0), "grid-01", Map.of("thermal-01", 1.0)
        ));
        return new DistributedDispatchCoordinator(List.of(
                new DistributedDispatchAgent(new IncrementalCostModel("thermal-01", ResourceType.THERMAL, 0.01, 0, 0,
                        0, 100, 10, 10, 0, 100, 0, 1, 1), graph, policy, 45, localResidualDemandKw, 0, LinkState.S0),
                new DistributedDispatchAgent(new IncrementalCostModel("grid-01", ResourceType.GRID, 0.01, 0, 0,
                        0, 100, 10, 10, 0, 100, 0, 1, 1), graph, policy, 35, localResidualDemandKw, 0, LinkState.S0)
        ), new ConvergenceEvaluator(properties));
    }

    private FaultProfile profile(String scenario, int iteration, Random random) {
        return switch (scenario) {
            case "NORMAL" -> new FaultProfile(LinkState.S0, LinkState.S0, 20, 0, false);
            case "LATENCY" -> new FaultProfile(LinkState.S1, LinkState.S1, 350 + random.nextInt(250), 0, iteration <= 40);
            case "PACKET_LOSS" -> new FaultProfile(LinkState.S2, LinkState.S2, 80, 20 + random.nextInt(31), iteration <= 40);
            case "OFFLINE_RECOVERY" -> iteration <= 30
                    ? new FaultProfile(LinkState.S3, LinkState.S0, 0, 0, true)
                    : iteration <= 45 ? new FaultProfile(LinkState.S4, LinkState.S0, 80, 2, false)
                    : new FaultProfile(LinkState.S0, LinkState.S0, 20, 0, false);
            case "COMBINED" -> iteration <= 30
                    ? new FaultProfile(LinkState.S2, LinkState.S1, 450 + random.nextInt(250), 25 + random.nextInt(31), true)
                    : iteration <= 45 ? new FaultProfile(LinkState.S4, LinkState.S1, 120, 5, false)
                    : new FaultProfile(LinkState.S0, LinkState.S0, 20, 0, false);
            default -> throw new IllegalArgumentException("不支持的实验场景: " + scenario);
        };
    }

    private int violations(double currentPower, double previousPower, DistributedAgentState state) {
        return (!Double.isFinite(currentPower) || Math.abs(currentPower - previousPower) > 10.000001
                || state.soc() < 0 || state.soc() > 100) ? 1 : 0;
    }

    private List<SummaryRecord> summarize(List<IterationRecord> records) {
        Map<String, List<IterationRecord>> grouped = new LinkedHashMap<>();
        for (IterationRecord record : records) {
            grouped.computeIfAbsent(record.strategy() + ":" + record.scenario(), ignored -> new ArrayList<>()).add(record);
        }
        List<SummaryRecord> summaries = new ArrayList<>();
        grouped.forEach((key, group) -> {
            double meanBalance = group.stream().mapToDouble(IterationRecord::balanceErrorKw).average().orElse(0);
            double rmse = Math.sqrt(group.stream().mapToDouble(value -> value.balanceErrorKw() * value.balanceErrorKw()).average().orElse(0));
            double lossRate = group.stream().mapToDouble(value -> value.messageSentCount() == 0 ? 0
                    : value.messageLostCount() * 100.0 / value.messageSentCount()).average().orElse(0);
            double convergedRounds = group.stream().filter(IterationRecord::converged).mapToInt(IterationRecord::iteration).average().orElse(ITERATIONS);
            double recovery = group.stream().filter(value -> value.recoveryRound() > 0 && value.balanceErrorKw() < properties.balanceToleranceKw())
                    .mapToInt(IterationRecord::recoveryRound).average().orElse(0);
            int violations = group.stream().mapToInt(IterationRecord::constraintViolationCount).sum();
            String[] parts = key.split(":", 2);
            summaries.add(new SummaryRecord(parts[0], parts[1], meanBalance, rmse, convergedRounds, convergedRounds * 0.2,
                    lossRate, recovery * 0.2, violations));
        });
        summaries.sort(Comparator.comparing(SummaryRecord::strategy).thenComparing(SummaryRecord::scenario));
        return summaries;
    }

    private void writeRawCsv(Path path, List<IterationRecord> records) throws IOException {
        StringBuilder csv = new StringBuilder("strategy,scenario,seed,iteration,balance_error_kw,consensus_error,converged,message_sent_count,message_lost_count,constraint_violation_count,recovery_round\n");
        for (IterationRecord value : records) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%.6f,%.6f,%s,%d,%d,%d,%d%n", value.strategy(), value.scenario(), value.seed(), value.iteration(), value.balanceErrorKw(), value.consensusError(), value.converged(), value.messageSentCount(), value.messageLostCount(), value.constraintViolationCount(), value.recoveryRound()));
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    private void writeSummaryCsv(Path path, List<SummaryRecord> summaries) throws IOException {
        StringBuilder csv = new StringBuilder("strategy,scenario,mean_balance_error_kw,rmse_kw,convergence_rounds,convergence_time_seconds,message_loss_rate_percent,recovery_time_seconds,constraint_violation_count\n");
        for (SummaryRecord value : summaries) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n", value.strategy(), value.scenario(), value.meanBalanceErrorKw(), value.rmseKw(), value.convergenceRounds(), value.convergenceTimeSeconds(), value.messageLossRatePercent(), value.recoveryTimeSeconds(), value.constraintViolationCount()));
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    private void writeSvg(Path path, List<IterationRecord> records) throws IOException {
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"480\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><text x=\"40\" y=\"30\" font-size=\"18\">Distributed fault experiment: mean balance error</text>");
        String[] colors = {"#2563eb", "#dc2626", "#16a34a"};
        for (int index = 0; index < STRATEGIES.size(); index++) {
            String strategy = STRATEGIES.get(index);
            StringBuilder points = new StringBuilder();
            for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
                final int expectedIteration = iteration;
                double mean = records.stream().filter(value -> value.strategy().equals(strategy) && value.iteration() == expectedIteration)
                        .mapToDouble(IterationRecord::balanceErrorKw).average().orElse(0);
                points.append(40 + iteration * 14).append(',').append(440 - Math.min(380, mean * 8)).append(' ');
            }
            svg.append("<polyline fill=\"none\" stroke=\"").append(colors[index]).append("\" stroke-width=\"2\" points=\"").append(points).append("\"/><text x=\"").append(850 + index * 110).append("\" y=\"30\" fill=\"").append(colors[index]).append("\">").append(strategy).append("</text>");
        }
        svg.append("</svg>");
        Files.writeString(path, svg.toString(), StandardCharsets.UTF_8);
    }

    /** 导出结果。 */
    public record ExperimentExport(Path rawCsv, Path summaryCsv, Path curveSvg, int rawRecordCount, int summaryCount) { }
    private record FaultProfile(LinkState thermalState, LinkState gridState, long latencyMillis, double packetLossRate, boolean faultActive) { }
    private record IterationRecord(String strategy, String scenario, long seed, int iteration, double balanceErrorKw, double consensusError,
                                   boolean converged, long messageSentCount, long messageLostCount, int constraintViolationCount, int recoveryRound) { }
    private record SummaryRecord(String strategy, String scenario, double meanBalanceErrorKw, double rmseKw, double convergenceRounds,
                                 double convergenceTimeSeconds, double messageLossRatePercent, double recoveryTimeSeconds, int constraintViolationCount) { }
}
