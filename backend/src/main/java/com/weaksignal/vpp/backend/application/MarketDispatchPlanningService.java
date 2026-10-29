package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.config.MpcConvergenceProperties;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanAuditChainResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingResponse;
import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.domain.DispatchCommandStatus;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanItemEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanItemRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.marketdispatch.DispatchOptimizer;
import com.weaksignal.vpp.backend.marketdispatch.DispatchPlan;
import com.weaksignal.vpp.backend.marketdispatch.DispatchPlanItem;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationContext;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.ResourceDispatchTarget;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshot;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 市场调度计划服务。
 */
@Service
public class MarketDispatchPlanningService {

    private final DispatchContextSnapshotService dispatchContextSnapshotService;
    private final DispatchPlanRepository dispatchPlanRepository;
    private final DispatchPlanItemRepository dispatchPlanItemRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final DispatchOptimizer dispatchOptimizer;
    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private MpcConvergenceProperties mpcConvergenceProperties = new MpcConvergenceProperties(
            1.0, 1.0, 1.0, 3, java.time.Duration.ofSeconds(45), java.time.Duration.ofSeconds(15),
            java.time.Duration.ofMinutes(2), java.time.Duration.ofSeconds(15), 3, java.time.Duration.ofMinutes(10),
            java.time.Duration.ofSeconds(30), java.time.Duration.ofSeconds(30));
    private SolverObservabilityService solverObservabilityService;

    /**
     * 注入 MPC 收敛参数。
     *
     * @param mpcConvergenceProperties MPC 收敛参数
     */
    @Autowired
    public void setMpcConvergenceProperties(MpcConvergenceProperties mpcConvergenceProperties) {
        this.mpcConvergenceProperties = mpcConvergenceProperties;
    }

    /**
     * 注入求解器审计服务，用于在计划标识生成后补齐审计关联。
     *
     * @param solverObservabilityService 求解器审计服务
     */
    @Autowired
    public void setSolverObservabilityService(SolverObservabilityService solverObservabilityService) {
        this.solverObservabilityService = solverObservabilityService;
    }

    /**
     * 注入市场调度计划服务依赖。
     *
     * @param dispatchContextSnapshotService 调度上下文服务
     * @param dispatchPlanRepository 计划仓储
     * @param dispatchPlanItemRepository 计划时间片仓储
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param dispatchOptimizer 调度优化器
     * @param dispatchService 调度执行服务
     * @param objectMapper JSON 处理器
     * @param clock 系统时钟
     */
    public MarketDispatchPlanningService(
            DispatchContextSnapshotService dispatchContextSnapshotService,
            DispatchPlanRepository dispatchPlanRepository,
            DispatchPlanItemRepository dispatchPlanItemRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            DispatchOptimizer dispatchOptimizer,
            DispatchService dispatchService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dispatchContextSnapshotService = dispatchContextSnapshotService;
        this.dispatchPlanRepository = dispatchPlanRepository;
        this.dispatchPlanItemRepository = dispatchPlanItemRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.dispatchOptimizer = dispatchOptimizer;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 预览计划，不持久化。
     *
     * @param request 计划请求
     * @return 调度计划响应
     */
    @Transactional
    public DispatchPlanResponse preview(DispatchPlanCreateRequest request) {
        DispatchContextSnapshot snapshot = resolveSnapshot(request.dispatchContextSnapshotId());
        PlanBuildResult planBuildResult = buildPlan(
                snapshot,
                request.conservativePlan(),
                request.experimentScenarioCode(),
                DispatchPlanStatus.DRAFT
        );
        return toResponse(planBuildResult.plan());
    }

    /**
     * 创建并持久化计划。
     *
     * @param request 计划请求
     * @return 调度计划响应
     */
    @Transactional
    public DispatchPlanResponse create(DispatchPlanCreateRequest request) {
        DispatchContextSnapshot snapshot = resolveSnapshot(request.dispatchContextSnapshotId());
        PlanBuildResult planBuildResult = buildPlan(
                snapshot,
                request.conservativePlan(),
                request.experimentScenarioCode(),
                DispatchPlanStatus.DRAFT
        );
        persistPlan(planBuildResult.plan(), planBuildResult.optimizationResult());
        return toResponse(planBuildResult.plan());
    }

    /**
     * 持久化由确定性外部控制器生成的优化结果。
     *
     * <p>该入口不调用集中式优化器，也不重新计算逐资源目标。安全门和控制执行只能继续读取
     * 本次持久化的 {@link ResourceDispatchTarget}。</p>
     *
     * @param request 计划创建请求
     * @param optimizationResult 已完成收敛与约束检查的优化结果
     * @return 已持久化的草稿计划
     */
    @Transactional
    public DispatchPlanResponse createFromOptimizationResult(
            DispatchPlanCreateRequest request,
            OptimizationResult optimizationResult
    ) {
        DispatchContextSnapshot snapshot = resolveSnapshot(request.dispatchContextSnapshotId());
        if (optimizationResult == null || !snapshot.snapshotId().equals(optimizationResult.snapshotId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_OPTIMIZATION_SNAPSHOT_MISMATCH",
                    "外部优化结果与调度上下文快照不一致", Map.of("snapshotId", snapshot.snapshotId()));
        }
        validateExecutableOptimizationResult(optimizationResult);
        DispatchPlan plan = new DispatchPlan(
                "dispatch-plan-" + UUID.randomUUID(),
                snapshot.snapshotId(),
                snapshot.intervalMinutes(),
                request.conservativePlan(),
                DispatchPlanStatus.DRAFT,
                OffsetDateTime.now(clock),
                snapshot.validFrom(),
                snapshot.validUntil(),
                snapshot.source(),
                snapshot.qualityCode(),
                "dispatch-plan-v1",
                OffsetDateTime.now(clock),
                optimizationResult.items()
        );
        persistPlan(plan, optimizationResult);
        return toResponse(plan);
    }

    /**
     * 查询计划详情。
     *
     * @param planId 计划标识
     * @return 计划响应
     */
    @Transactional
    public DispatchPlanResponse getPlan(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        expireActivePlanIfNeeded(entity, OffsetDateTime.now(clock));
        List<DispatchPlanItem> items = dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(planId).stream()
                .map(this::toPlanItem)
                .toList();
        DispatchPlan plan = new DispatchPlan(
                entity.getPlanId(),
                entity.getInputSnapshotId(),
                entity.getIntervalMinutes(),
                entity.isConservativePlan(),
                DispatchPlanStatus.valueOf(entity.getStatus()),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                com.weaksignal.vpp.backend.domain.QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt(),
                items
        );
        return toResponse(plan);
    }

    /**
     * 查询最新计划。
     *
     * @return 计划响应
     */
    @Transactional
    public DispatchPlanResponse getLatestPlan() {
        DispatchPlanEntity entity = dispatchPlanRepository.findFirstByOrderByGeneratedAtDesc()
                .orElseThrow(() -> notFound("DISPATCH_PLAN_NOT_FOUND", "尚无调度计划", "latest"));
        return getPlan(entity.getPlanId());
    }

    /**
     * 查询当前仍有效且处于执行状态的计划。
     *
     * @return 当前计划；不存在时返回空
     */
    @Transactional
    public DispatchPlanResponse getCurrentActivePlan() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        expireActivePlans(now);
        return dispatchPlanRepository.findByStatusOrderByGeneratedAtDesc(DispatchPlanStatus.ACTIVE.name()).stream()
                .filter(plan -> !now.isBefore(plan.getValidFrom()) && now.isBefore(plan.getValidUntil()))
                .findFirst()
                .map(plan -> getPlan(plan.getPlanId()))
                .orElse(null);
    }

    /**
     * 周期性失效超过有效期的活动计划，并停止其尚未完成的控制指令。
     *
     * @return 本轮失效的计划数量
     */
    @Transactional
    public int expireActivePlans() {
        return expireActivePlans(OffsetDateTime.now(clock));
    }

    /**
     * 校验计划并切换为 VALIDATED。
     *
     * @param planId 计划标识
     * @return 计划响应
     */
    @Transactional
    public DispatchPlanResponse validate(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        ensureStatus(entity, DispatchPlanStatus.DRAFT);
        validatePersistedPlanTargets(entity, OffsetDateTime.now(clock));
        entity.setStatus(DispatchPlanStatus.VALIDATED.name());
        entity.setUpdatedAt(OffsetDateTime.now(clock));
        dispatchPlanRepository.save(entity);
        return getPlan(planId);
    }

    /**
     * 自动放行计划。
     *
     * @param planId 计划标识
     * @return 计划响应
     */
    @Transactional
    public DispatchPlanResponse approve(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        if (DispatchPlanStatus.DRAFT.name().equals(entity.getStatus())) {
            validatePersistedPlanTargets(entity, OffsetDateTime.now(clock));
            entity.setStatus(DispatchPlanStatus.VALIDATED.name());
        } else if (!DispatchPlanStatus.VALIDATED.name().equals(entity.getStatus())) {
            throw conflict("DISPATCH_PLAN_STATUS_INVALID", "只有 DRAFT 或 VALIDATED 计划可以自动放行");
        }
        entity.setStatus(DispatchPlanStatus.APPROVED.name());
        entity.setUpdatedAt(OffsetDateTime.now(clock));
        dispatchPlanRepository.save(entity);
        return getPlan(planId);
    }

    /**
     * 拒绝计划。
     *
     * @param planId 计划标识
     * @return 计划响应
     */
    @Transactional
    public DispatchPlanResponse reject(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        if (DispatchPlanStatus.APPROVED.name().equals(entity.getStatus())
                || DispatchPlanStatus.ACTIVE.name().equals(entity.getStatus())
                || DispatchPlanStatus.COMPLETED.name().equals(entity.getStatus())) {
            throw conflict("DISPATCH_PLAN_STATUS_INVALID", "当前计划状态不允许拒绝");
        }
        entity.setStatus(DispatchPlanStatus.REJECTED.name());
        entity.setUpdatedAt(OffsetDateTime.now(clock));
        dispatchPlanRepository.save(entity);
        return getPlan(planId);
    }

    /**
     * 激活已自动放行计划。
     *
     * @param planId 计划标识
     * @return 计划响应
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DispatchPlanResponse activate(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        if (!DispatchPlanStatus.APPROVED.name().equals(entity.getStatus())) {
            throw conflict("DISPATCH_PLAN_STATUS_INVALID", "只有已自动放行计划可以激活");
        }
        if (!entity.getValidUntil().isAfter(OffsetDateTime.now(clock).plus(mpcConvergenceProperties.planMinimumRemainingValidity()))) {
            OffsetDateTime detectedAt = OffsetDateTime.now(clock);
            throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_PLAN_EXPIRED", "计划已过期，不能激活",
                    Map.of("planId", entity.getPlanId(), "validUntil", entity.getValidUntil(), "detectedAt", detectedAt));
        }
        List<DispatchPlanEntity> activePlans = dispatchPlanRepository.findByStatusOrderByGeneratedAtDesc(DispatchPlanStatus.ACTIVE.name());
        for (DispatchPlanEntity activePlan : activePlans) {
            if (!activePlan.getPlanId().equals(planId)) {
                activePlan.setStatus(DispatchPlanStatus.SUPERSEDED.name());
                activePlan.setUpdatedAt(OffsetDateTime.now(clock));
                dispatchService.invalidatePendingCommandsForPlan(activePlan.getPlanId());
            }
        }
        dispatchPlanRepository.saveAll(activePlans);
        entity.setStatus(DispatchPlanStatus.ACTIVE.name());
        entity.setUpdatedAt(OffsetDateTime.now(clock));
        dispatchPlanRepository.save(entity);
        return getPlan(planId);
    }

    /**
     * 执行计划中的下一个时间片。
     *
     * @param planId 计划标识
     * @return 执行概览
     */
    @Transactional(noRollbackFor = ApiException.class)
    public synchronized DispatchPlanExecutionResponse executeNext(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        if (!DispatchPlanStatus.ACTIVE.name().equals(entity.getStatus())) {
            throw conflict("DISPATCH_PLAN_STATUS_INVALID", "只有 ACTIVE 计划可以执行");
        }
        OffsetDateTime detectedAt = OffsetDateTime.now(clock);
        if (expireActivePlanIfNeeded(entity, detectedAt)) {
            throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_PLAN_EXPIRED", "计划已过期，不能下发控制指令",
                    Map.of("planId", entity.getPlanId(), "validUntil", entity.getValidUntil(), "detectedAt", detectedAt));
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DispatchPlanItemEntity> items = dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(planId);
        DispatchPlanItemEntity nextItem = items.stream()
                .filter(item -> !now.isBefore(item.getIntervalStart()) && now.isBefore(item.getIntervalEnd()))
                .filter(item -> dispatchService.getCommandsByPlanId(planId).stream()
                        .noneMatch(command -> item.getItemId().equals(command.dispatchPlanItemId())))
                .findFirst()
                .orElseThrow(() -> conflict("DISPATCH_PLAN_ITEM_NOT_FOUND", "计划中没有可执行的后续时间片"));

        DispatchContextSnapshot snapshot = toDomain(dispatchContextSnapshotService.getSnapshot(entity.getInputSnapshotId()));
        validateExecutableTargets(entity, nextItem, snapshot, now);
        List<PlannedDispatchCommand> commands = buildPlannedCommands(entity, nextItem, snapshot);
        dispatchService.executePlannedCommands(commands);
        return getExecution(planId);
    }

    /**
     * 废弃计划并失效未完成指令。
     *
     * @param planId 计划标识
     * @return 计划响应
     */
    @Transactional
    public DispatchPlanResponse supersede(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        if (DispatchPlanStatus.COMPLETED.name().equals(entity.getStatus())
                || DispatchPlanStatus.REJECTED.name().equals(entity.getStatus())
                || DispatchPlanStatus.EXPIRED.name().equals(entity.getStatus())) {
            throw conflict("DISPATCH_PLAN_STATUS_INVALID", "当前计划状态不允许废弃");
        }
        if (!dispatchService.releasePlannedControls(planId)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DISPATCH_CONTROL_RELEASE_FAILED",
                    "模拟器调度控制释放失败，计划暂未结束",
                    Map.of("planId", planId)
            );
        }
        dispatchService.invalidatePendingCommandsForPlan(planId);
        entity.setStatus(DispatchPlanStatus.SUPERSEDED.name());
        entity.setUpdatedAt(OffsetDateTime.now(clock));
        dispatchPlanRepository.save(entity);
        return getPlan(planId);
    }

    /**
     * 查询计划执行概览。
     *
     * @param planId 计划标识
     * @return 执行概览
     */
    @Transactional(readOnly = true)
    public DispatchPlanExecutionResponse getExecution(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        List<com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse> commands = dispatchService.getCommandsByPlanId(planId);
        String activeBatchId = commands.isEmpty() ? null : commands.get(commands.size() - 1).batchId();
        OffsetDateTime latestCommandTime = commands.isEmpty() ? null : commands.get(commands.size() - 1).sendTime();
        int terminalCount = (int) commands.stream()
                .filter(command -> DispatchCommandStatus.valueOf(command.status()).isTerminal())
                .count();
        return new DispatchPlanExecutionResponse(
                planId,
                entity.getVersion(),
                DispatchPlanStatus.valueOf(entity.getStatus()),
                activeBatchId,
                commands.size(),
                terminalCount,
                latestCommandTime,
                commands
        );
    }

    /**
     * 查询计划跟踪结果。
     *
     * @param planId 计划标识
     * @return 跟踪结果
     */
    @Transactional(readOnly = true)
    public DispatchPlanTrackingResponse getTracking(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        List<DispatchPlanTrackingItemResponse> items = dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(planId).stream()
                .map(item -> {
                    List<com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse> itemCommands = dispatchService.getCommandsByPlanId(planId).stream()
                            .filter(command -> item.getItemId().equals(command.dispatchPlanItemId()))
                            .toList();
                    DispatchContextSnapshot snapshot = toDomain(dispatchContextSnapshotService.getSnapshot(item.getInputSnapshotId()));
                    double actualNetPower = estimateActualNetPower(itemCommands, snapshot);
                    String latestStatus = itemCommands.isEmpty() ? "PENDING" : itemCommands.get(itemCommands.size() - 1).status();
                    return new DispatchPlanTrackingItemResponse(
                            item.getItemId(),
                            item.getIntervalStart(),
                            item.getIntervalEnd(),
                            item.getNetPowerTarget(),
                            actualNetPower,
                            round(actualNetPower - item.getNetPowerTarget()),
                            latestStatus
                    );
                })
                .toList();
        return new DispatchPlanTrackingResponse(planId, entity.getVersion(), items);
    }

    /**
     * 查询计划审计链。
     *
     * @param planId 计划标识
     * @return 审计链
     */
    @Transactional(readOnly = true)
    public DispatchPlanAuditChainResponse getAuditChain(String planId) {
        DispatchPlanEntity entity = requirePlanEntity(planId);
        List<com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse> commands = dispatchService.getCommandsByPlanId(planId);
        List<String> events = new ArrayList<>();
        events.add("计划创建：" + entity.getGeneratedAt());
        events.add("当前状态：" + entity.getStatus());
        dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(planId).forEach(item -> {
            List<ResourceDispatchTarget> targets = readTargets(item.getTargetPowersJson());
            events.add("时间片目标摘要：" + item.getItemId() + "=" + ResourceDispatchTarget.summaryHash(targets));
        });
        if (!commands.isEmpty()) {
            events.add("已下发指令数量：" + commands.size());
            events.add("最近指令时间：" + commands.get(commands.size() - 1).sendTime());
        }
        return new DispatchPlanAuditChainResponse(
                planId,
                entity.getVersion(),
                DispatchPlanStatus.valueOf(entity.getStatus()),
                entity.getInputSnapshotId(),
                commands.stream().map(com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse::commandId).toList(),
                events
        );
    }

    /**
     * 读取计划首个待执行时间片中已持久化的逐资源目标。
     *
     * <p>该方法只反序列化计划创建时保存的 {@code targetPowersJson}，不会重新计算、分摊或裁剪目标功率。</p>
     *
     * @param planId 调度计划标识
     * @return 按场站标识排序的逐资源目标
     */
    @Transactional(readOnly = true)
    public List<ResourceDispatchTarget> getPersistedTargets(String planId) {
        requirePlanEntity(planId);
        return dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(planId).stream()
                .findFirst()
                .map(item -> readTargets(item.getTargetPowersJson()))
                .orElse(List.of());
    }

    private DispatchContextSnapshot resolveSnapshot(String snapshotId) {
        DispatchPlanCreateRequest effectiveRequest = new DispatchPlanCreateRequest(snapshotId, false);
        String effectiveSnapshotId = effectiveRequest.dispatchContextSnapshotId();
        if (effectiveSnapshotId != null && !effectiveSnapshotId.isBlank()) {
            return toDomain(dispatchContextSnapshotService.getSnapshot(effectiveSnapshotId));
        }
        return toDomain(dispatchContextSnapshotService.createSnapshot());
    }

    private PlanBuildResult buildPlan(
            DispatchContextSnapshot snapshot,
            boolean conservativePlan,
            String experimentScenarioCode,
            DispatchPlanStatus status
    ) {
        if (!snapshot.validUntil().isAfter(OffsetDateTime.now(clock))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DISPATCH_CONTEXT_EXPIRED",
                    "调度上下文快照已过期，不能生成计划",
                    Map.of("snapshotId", snapshot.snapshotId())
            );
        }
        OptimizationResult optimizationResult = dispatchOptimizer.optimize(
                new OptimizationContext(snapshot, conservativePlan, experimentScenarioCode)
        );
        validateExecutableOptimizationResult(optimizationResult);
        DispatchPlan plan = new DispatchPlan(
                "dispatch-plan-" + UUID.randomUUID(),
                snapshot.snapshotId(),
                snapshot.intervalMinutes(),
                conservativePlan,
                status,
                OffsetDateTime.now(clock),
                snapshot.validFrom(),
                snapshot.validUntil(),
                snapshot.source(),
                snapshot.qualityCode(),
                "dispatch-plan-v1",
                OffsetDateTime.now(clock),
                optimizationResult.items()
        );
        return new PlanBuildResult(plan, optimizationResult);
    }

    private void validateExecutableOptimizationResult(OptimizationResult optimizationResult) {
        if (optimizationResult.status() != OptimizationStatus.OPTIMAL
                && optimizationResult.status() != OptimizationStatus.FEASIBLE
                && optimizationResult.status() != OptimizationStatus.TIME_LIMIT_WITH_FEASIBLE_SOLUTION
                && optimizationResult.status() != OptimizationStatus.RULE_FALLBACK) {
            throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_OPTIMIZATION_REJECTED", "优化器未返回可执行计划", Map.of(
                    "solverName", optimizationResult.solverName(),
                    "solverVersion", optimizationResult.solverVersion(),
                    "status", optimizationResult.status().name(),
                    "solveTimeMs", optimizationResult.solveTimeMs(),
                    "evidence", optimizationResult.constraintEvidence(),
                    "fallbackReason", optimizationResult.fallbackReason()
            ));
        }
        if (optimizationResult.items() == null || optimizationResult.items().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_OPTIMIZATION_ITEMS_MISSING",
                    "优化结果没有可持久化的计划时间片", Map.of("solverName", optimizationResult.solverName()));
        }
        for (DispatchPlanItem item : optimizationResult.items()) {
            List<ResourceDispatchTarget> targets = optimizationResult.targetsByItemId().get(item.itemId());
            if (targets == null || targets.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_TARGETS_MISSING",
                        "优化器未返回逐资源目标，不能持久化可执行计划", Map.of("itemId", item.itemId()));
            }
            String expectedHash = ResourceDispatchTarget.summaryHash(targets);
            String providedHash = optimizationResult.targetSummaryHashesByItemId().get(item.itemId());
            if (!expectedHash.equals(providedHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_TARGET_HASH_MISMATCH",
                        "逐资源目标摘要与目标内容不一致", Map.of("itemId", item.itemId(), "expectedHash", expectedHash));
            }
        }
    }

    private void persistPlan(DispatchPlan plan, OptimizationResult optimizationResult) {
        DispatchPlanEntity entity = new DispatchPlanEntity();
        entity.setPlanId(plan.planId());
        entity.setInputSnapshotId(plan.inputSnapshotId());
        entity.setIntervalMinutes(plan.intervalMinutes());
        entity.setConservativePlan(plan.conservativePlan());
        entity.setStatus(plan.status().name());
        entity.setSource(plan.source());
        entity.setQualityCode(plan.qualityCode().name());
        entity.setVersion(plan.version());
        entity.setGeneratedAt(plan.generatedAt());
        entity.setValidFrom(plan.validFrom());
        entity.setValidUntil(plan.validUntil());
        entity.setReceivedAt(plan.receivedAt());
        entity.setPayloadJson(writePayload(plan, optimizationResult));
        entity.setCreatedAt(plan.generatedAt());
        entity.setUpdatedAt(plan.generatedAt());
        dispatchPlanRepository.save(entity);

        for (DispatchPlanItem item : plan.items()) {
            DispatchPlanItemEntity itemEntity = new DispatchPlanItemEntity();
            itemEntity.setItemId(item.itemId());
            itemEntity.setPlanId(plan.planId());
            itemEntity.setInputSnapshotId(item.inputSnapshotId());
            itemEntity.setIntervalStart(item.intervalStart());
            itemEntity.setIntervalEnd(item.intervalEnd());
            itemEntity.setNetPowerTarget(item.netPowerTarget());
            itemEntity.setGridImportPower(item.gridImportPower());
            itemEntity.setGridExportPower(item.gridExportPower());
            itemEntity.setStorageChargePower(item.storageChargePower());
            itemEntity.setStorageDischargePower(item.storageDischargePower());
            itemEntity.setFlexibleLoadAdjustment(item.flexibleLoadAdjustment());
            itemEntity.setUpwardReserve(item.upwardReserve());
            itemEntity.setDownwardReserve(item.downwardReserve());
            itemEntity.setRenewableCurtailment(item.renewableCurtailment());
            itemEntity.setExpectedCost(item.expectedCost());
            itemEntity.setExpectedRevenue(item.expectedRevenue());
            itemEntity.setRiskCost(item.riskCost());
            List<ResourceDispatchTarget> targets = optimizationResult.targetsByItemId().get(item.itemId());
            if (targets == null || targets.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_TARGETS_MISSING", "优化器未返回逐资源目标，不能持久化可执行计划",
                        Map.of("itemId", item.itemId(), "planId", plan.planId()));
            }
            itemEntity.setTargetPowersJson(writeTargets(targets));
            itemEntity.setReasonCode(item.reasonCode());
            itemEntity.setItemOrder(item.itemOrder());
            itemEntity.setCreatedAt(plan.generatedAt());
            dispatchPlanItemRepository.save(itemEntity);
        }
        if (solverObservabilityService != null) {
            solverObservabilityService.bindBusinessContext(plan.inputSnapshotId(), plan.planId(), null, optimizationResult.optimizationRound());
        }
    }

    private String writePayload(DispatchPlan plan, OptimizationResult optimizationResult) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("planId", plan.planId());
            payload.put("inputSnapshotId", plan.inputSnapshotId());
            payload.put("conservativePlan", plan.conservativePlan());
            payload.put("itemCount", plan.items().size());
            payload.put("optimizerName", optimizationResult.solverName());
            payload.put("optimizationStatus", optimizationResult.status());
            payload.put("optimizationRound", optimizationResult.optimizationRound());
            payload.put("objectiveValue", optimizationResult.objectiveValue());
            payload.put("solveTimeMs", optimizationResult.solveTimeMs());
            payload.put("plannedBalanceErrorKw", optimizationResult.plannedBalanceError());
            payload.put("constraintEvidence", optimizationResult.constraintEvidence());
            payload.put("targetSummaryHashesByItemId", optimizationResult.targetSummaryHashesByItemId());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DISPATCH_PLAN_SERIALIZATION_FAILED",
                    "调度计划序列化失败",
                    Map.of()
            );
        }
    }

    private DispatchPlanItem toPlanItem(DispatchPlanItemEntity entity) {
        return new DispatchPlanItem(
                entity.getItemId(),
                entity.getInputSnapshotId(),
                entity.getIntervalStart(),
                entity.getIntervalEnd(),
                entity.getNetPowerTarget(),
                entity.getGridImportPower(),
                entity.getGridExportPower(),
                entity.getStorageChargePower(),
                entity.getStorageDischargePower(),
                entity.getFlexibleLoadAdjustment(),
                entity.getUpwardReserve(),
                entity.getDownwardReserve(),
                entity.getRenewableCurtailment(),
                entity.getExpectedCost(),
                entity.getExpectedRevenue(),
                entity.getRiskCost(),
                entity.getReasonCode(),
                entity.getItemOrder()
        );
    }

    private DispatchPlanResponse toResponse(DispatchPlan plan) {
        return new DispatchPlanResponse(
                plan.planId(),
                plan.inputSnapshotId(),
                plan.intervalMinutes(),
                plan.conservativePlan(),
                plan.status(),
                plan.generatedAt(),
                plan.validFrom(),
                plan.validUntil(),
                plan.source(),
                plan.qualityCode(),
                plan.version(),
                plan.receivedAt(),
                plan.items().stream()
                        .map(item -> new DispatchPlanItemResponse(
                                item.itemId(),
                                item.inputSnapshotId(),
                                item.intervalStart(),
                                item.intervalEnd(),
                                item.netPowerTarget(),
                                item.gridImportPower(),
                                item.gridExportPower(),
                                item.storageChargePower(),
                                item.storageDischargePower(),
                                item.flexibleLoadAdjustment(),
                                item.upwardReserve(),
                                item.downwardReserve(),
                                item.renewableCurtailment(),
                                item.expectedCost(),
                                item.expectedRevenue(),
                                item.riskCost(),
                                item.reasonCode(),
                                item.itemOrder()
                        ))
                        .toList()
        );
    }

    private DispatchContextSnapshot toDomain(com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse response) {
        return new DispatchContextSnapshot(
                response.snapshotId(),
                response.generatedAt(),
                response.validFrom(),
                response.validUntil(),
                response.source(),
                response.qualityCode(),
                response.version(),
                response.receivedAt(),
                response.intervalMinutes(),
                new com.weaksignal.vpp.backend.market.MarketSignalSnapshot(
                        response.marketSignal().snapshotId(),
                        response.marketSignal().marketType(),
                        response.marketSignal().intervalStart(),
                        response.marketSignal().intervalEnd(),
                        response.marketSignal().contractPower(),
                        response.marketSignal().contractPrice(),
                        response.marketSignal().dayAheadPrice(),
                        response.marketSignal().realTimePrice(),
                        response.marketSignal().gridImportLimit(),
                        response.marketSignal().gridExportLimit(),
                        response.marketSignal().reserveUpRequirement(),
                        response.marketSignal().reserveDownRequirement(),
                        response.marketSignal().generatedAt(),
                        response.marketSignal().validFrom(),
                        response.marketSignal().validUntil(),
                        response.marketSignal().source(),
                        response.marketSignal().qualityCode(),
                        response.marketSignal().version(),
                        response.marketSignal().receivedAt()
                ),
                new com.weaksignal.vpp.backend.forecast.ForecastSnapshot(
                        response.forecastSnapshot().snapshotId(),
                        response.forecastSnapshot().intervalMinutes(),
                        response.forecastSnapshot().modelVersion(),
                        response.forecastSnapshot().points().stream()
                                .map(point -> new com.weaksignal.vpp.backend.forecast.ForecastPoint(
                                        point.intervalStart(),
                                        point.intervalEnd(),
                                        point.loadP10(),
                                        point.loadP50(),
                                        point.loadP90(),
                                        point.photovoltaicP10(),
                                        point.photovoltaicP50(),
                                        point.photovoltaicP90(),
                                        point.windP10(),
                                        point.windP50(),
                                        point.windP90(),
                                        point.priceP10(),
                                        point.priceP50(),
                                        point.priceP90()
                                ))
                                .toList(),
                        response.forecastSnapshot().generatedAt(),
                        response.forecastSnapshot().validFrom(),
                        response.forecastSnapshot().validUntil(),
                        response.forecastSnapshot().source(),
                        response.forecastSnapshot().qualityCode(),
                        response.forecastSnapshot().version(),
                        response.forecastSnapshot().receivedAt()
                ),
                response.resourceCapabilities().stream()
                        .map(capability -> new com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot(
                                capability.capabilityId(),
                                capability.snapshotGroupId(),
                                capability.stationId(),
                                capability.resourceType(),
                                capability.currentPower(),
                                capability.availableCapacity(),
                                capability.minimumPower(),
                                capability.maximumPower(),
                                capability.maximumRampUp(),
                                capability.maximumRampDown(),
                                capability.availableEnergyKwh(),
                                capability.storageConstraint() == null ? null : new com.weaksignal.vpp.backend.capability.StorageConstraint(
                                        capability.storageConstraint().soc(),
                                        capability.storageConstraint().minimumSoc(),
                                        capability.storageConstraint().maximumSoc(),
                                        capability.storageConstraint().energyCapacityKWh(),
                                        capability.storageConstraint().maximumChargePowerKW(),
                                        capability.storageConstraint().maximumDischargePowerKW(),
                                        capability.storageConstraint().chargeEfficiency(),
                                        capability.storageConstraint().dischargeEfficiency(),
                                        capability.storageConstraint().cycleCost(),
                                        capability.storageConstraint().stateOfHealth(),
                                        capability.storageConstraint().reservedUpPower(),
                                        capability.storageConstraint().reservedDownPower()
                                ),
                                capability.linkState(),
                                capability.dataAge(),
                                capability.capabilityConfidence(),
                                capability.dataTrusted(),
                                capability.unavailableReason(),
                                capability.generatedAt(),
                                capability.validFrom(),
                                capability.validUntil(),
                                capability.source(),
                                capability.qualityCode(),
                                capability.version(),
                                capability.receivedAt()
                        ))
                        .toList()
        );
    }

    private DispatchPlanEntity requirePlanEntity(String planId) {
        return dispatchPlanRepository.findByPlanId(planId)
                .orElseThrow(() -> notFound("DISPATCH_PLAN_NOT_FOUND", "调度计划不存在", planId));
    }

    private int expireActivePlans(OffsetDateTime detectedAt) {
        List<DispatchPlanEntity> expiredPlans = dispatchPlanRepository.findByStatusAndValidUntilLessThanEqual(
                DispatchPlanStatus.ACTIVE.name(),
                detectedAt
        );
        expiredPlans.forEach(plan -> expireActivePlanIfNeeded(plan, detectedAt));
        return expiredPlans.size();
    }

    private boolean expireActivePlanIfNeeded(DispatchPlanEntity entity, OffsetDateTime detectedAt) {
        if (!DispatchPlanStatus.ACTIVE.name().equals(entity.getStatus())
                || entity.getValidUntil().isAfter(detectedAt)) {
            return false;
        }
        entity.setStatus(DispatchPlanStatus.EXPIRED.name());
        entity.setUpdatedAt(detectedAt);
        dispatchPlanRepository.save(entity);
        dispatchService.invalidatePendingCommandsForPlan(entity.getPlanId());
        return true;
    }

    private List<PlannedDispatchCommand> buildPlannedCommands(
            DispatchPlanEntity planEntity,
            DispatchPlanItemEntity itemEntity,
            DispatchContextSnapshot snapshot
    ) {
        List<PlannedDispatchCommand> commands = new ArrayList<>();
        Map<String, ResourceCapabilitySnapshot> capabilitiesByStationId = snapshot.resourceCapabilitySnapshots().stream()
                .collect(java.util.stream.Collectors.toMap(ResourceCapabilitySnapshot::stationId, capability -> capability));
        readTargets(itemEntity.getTargetPowersJson()).forEach(target -> {
            ResourceCapabilitySnapshot capability = capabilitiesByStationId.get(target.stationId());
            if (capability == null) {
                throw conflict("DISPATCH_TARGET_RESOURCE_NOT_FOUND", "持久化目标关联的资源不存在");
            }
            commands.add(new PlannedDispatchCommand(
                    target.stationId(),
                    target.targetPower(),
                    ControlMode.EDGE_COORDINATION,
                    Math.max(5.0, Math.min(capability.maximumRampUp(), 20.0)),
                    30,
                    planEntity.getPlanId(),
                    planEntity.getVersion(),
                    itemEntity.getItemId(),
                    itemEntity.getInputSnapshotId()
            ));
        });
        return commands;
    }

    private double findForecastPower(
            DispatchContextSnapshot snapshot,
            ResourceType resourceType,
            DispatchPlanItemEntity itemEntity
    ) {
        return findForecastPower(snapshot, resourceType, itemEntity.getIntervalStart());
    }

    private double findForecastPower(
            DispatchContextSnapshot snapshot,
            ResourceType resourceType,
            OffsetDateTime intervalStart
    ) {
        return snapshot.forecastSnapshot().points().stream()
                .filter(point -> point.intervalStart().equals(intervalStart))
                .findFirst()
                .map(point -> switch (resourceType) {
                    case PHOTOVOLTAIC -> point.photovoltaicP50();
                    case WIND -> point.windP50();
                    default -> 0.0;
                })
                .orElse(0.0);
    }

    private double estimateActualNetPower(
            List<com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse> commands,
            DispatchContextSnapshot snapshot
    ) {
        if (commands.isEmpty()) {
            return 0.0;
        }
        List<String> stationIds = commands.stream()
                .map(com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse::stationId)
                .distinct()
                .toList();
        Map<String, ResourceType> resourceTypes = snapshot.resourceCapabilitySnapshots().stream()
                .collect(java.util.stream.Collectors.toMap(ResourceCapabilitySnapshot::stationId, ResourceCapabilitySnapshot::resourceType));
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findByStationIdIn(stationIds);
        return round(snapshots.stream()
                .mapToDouble(telemetry -> signedPower(resourceTypes.get(telemetry.getStationId()), telemetry.getActivePower()))
                .sum());
    }

    private void validateExecutableTargets(
            DispatchPlanEntity plan,
            DispatchPlanItemEntity item,
            DispatchContextSnapshot snapshot,
        OffsetDateTime now
    ) {
        if (!snapshot.validUntil().isAfter(now.plus(mpcConvergenceProperties.planMinimumRemainingValidity())) || !plan.getValidUntil().isAfter(now.plus(mpcConvergenceProperties.planMinimumRemainingValidity()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DISPATCH_INPUT_EXPIRED",
                    "调度输入快照或计划已过期，不能下发控制指令",
                    Map.of(
                            "planId", plan.getPlanId(),
                            "snapshotId", snapshot.snapshotId(),
                            "validUntil", plan.getValidUntil(),
                            "snapshotValidUntil", snapshot.validUntil(),
                            "detectedAt", now
                    )
            );
        }
        List<ResourceDispatchTarget> targets = readTargets(item.getTargetPowersJson());
        if (targets.isEmpty()) {
            throw conflict("DISPATCH_LEGACY_EMPTY_TARGETS_READ_ONLY", "历史空目标计划仅可展示，禁止执行");
        }
        validateTargetCollection(plan, item, snapshot, targets);
        double balanceError = 0.0;
        for (ResourceCapabilitySnapshot capability : snapshot.resourceCapabilitySnapshots()) {
            ResourceDispatchTarget target = targets.stream().filter(value -> value.stationId().equals(capability.stationId())).findFirst().orElse(null);
            if (capability.linkState() == com.weaksignal.vpp.backend.domain.LinkState.S3 && target != null) {
                throw conflict("DISPATCH_S3_REMOTE_CONTROL_FORBIDDEN", "S3 资源不得包含远程控制目标");
            }
            if (target == null) {
                continue;
            }
            double targetPower = target.targetPower();
            double minimumTargetPower = capability.resourceType() == ResourceType.GRID
                    ? -snapshot.marketSignalSnapshot().gridExportLimit() : capability.minimumPower();
            double maximumTargetPower = capability.resourceType() == ResourceType.GRID
                    ? Math.min(capability.maximumPower(), snapshot.marketSignalSnapshot().gridImportLimit())
                    : capability.resourceType() == ResourceType.LOAD || capability.resourceType() == ResourceType.CHARGING
                    ? Math.max(capability.maximumPower(), capability.currentPower()) : capability.maximumPower();
            if (targetPower < minimumTargetPower || targetPower > maximumTargetPower) {
                throw new ApiException(HttpStatus.CONFLICT, "DISPATCH_TARGET_OUT_OF_BOUND", "逐资源目标功率超出能力边界",
                        Map.of("stationId", capability.stationId(), "resourceType", capability.resourceType().name(),
                                "targetPower", targetPower, "minimumPower", minimumTargetPower, "maximumPower", maximumTargetPower));
            }
            balanceError += signedPower(capability.resourceType(), targetPower);
        }
        if (Math.abs(balanceError - item.getNetPowerTarget()) > mpcConvergenceProperties.plannedBalanceToleranceKw()) {
            throw conflict("DISPATCH_TARGET_BALANCE_UNSATISFIED", "逐资源目标功率未满足计划供需平衡容差");
        }
    }

    private void validatePersistedPlanTargets(DispatchPlanEntity plan, OffsetDateTime now) {
        DispatchContextSnapshot snapshot = toDomain(dispatchContextSnapshotService.getSnapshot(plan.getInputSnapshotId()));
        List<DispatchPlanItemEntity> items = dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(plan.getPlanId());
        if (items.isEmpty()) {
            throw conflict("DISPATCH_PLAN_ITEMS_MISSING", "调度计划没有可校验的时间片");
        }
        for (DispatchPlanItemEntity item : items) {
            validateExecutableTargets(plan, item, snapshot, now);
        }
    }

    private double signedPower(ResourceType resourceType, double activePower) {
        if (resourceType == null) {
            return 0.0;
        }
        return switch (resourceType) {
            case PHOTOVOLTAIC, WIND, THERMAL -> activePower;
            case GRID, STORAGE -> activePower;
            case LOAD, CHARGING -> -Math.abs(activePower);
        };
    }

    private String writeTargets(List<ResourceDispatchTarget> targets) {
        try {
            return objectMapper.writeValueAsString(targets.stream()
                    .sorted(java.util.Comparator.comparing(ResourceDispatchTarget::stationId))
                    .toList());
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DISPATCH_TARGET_SERIALIZATION_FAILED", "逐资源目标功率序列化失败", Map.of());
        }
    }

    private List<ResourceDispatchTarget> readTargets(String targetPowersJson) {
        if (targetPowersJson == null || targetPowersJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(targetPowersJson, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ResourceDispatchTarget.class));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DISPATCH_TARGET_DESERIALIZATION_FAILED", "逐资源目标功率读取失败", Map.of());
        }
    }

    private void validateTargetCollection(
            DispatchPlanEntity plan,
            DispatchPlanItemEntity item,
            DispatchContextSnapshot snapshot,
            List<ResourceDispatchTarget> targets
    ) {
        if (!plan.getInputSnapshotId().equals(item.getInputSnapshotId())) {
            throw conflict("DISPATCH_TARGET_PLAN_SNAPSHOT_MISMATCH", "计划项快照与计划快照不一致");
        }
        Set<String> stationIds = new java.util.HashSet<>();
        Map<String, ResourceCapabilitySnapshot> capabilities = snapshot.resourceCapabilitySnapshots().stream()
                .collect(java.util.stream.Collectors.toMap(ResourceCapabilitySnapshot::stationId, capability -> capability));
        for (ResourceDispatchTarget target : targets) {
            if (!stationIds.add(target.stationId())) {
                throw conflict("DISPATCH_TARGET_STATION_DUPLICATE", "逐资源目标包含重复场站");
            }
            if (target.powerDirection() == null || target.optimizationStatus() == null || target.generatedAt() == null) {
                throw conflict("DISPATCH_LEGACY_EMPTY_TARGETS_READ_ONLY", "历史目标结构不完整，仅可展示，禁止执行");
            }
            if (!item.getInputSnapshotId().equals(target.snapshotId())) {
                throw conflict("DISPATCH_TARGET_SNAPSHOT_MISMATCH", "逐资源目标快照与计划项快照不一致");
            }
            ResourceCapabilitySnapshot capability = capabilities.get(target.stationId());
            if (capability == null) {
                throw conflict("DISPATCH_TARGET_RESOURCE_NOT_FOUND", "持久化目标关联的资源不存在");
            }
            if (capability.resourceType() != target.resourceType()) {
                throw conflict("DISPATCH_TARGET_RESOURCE_TYPE_MISMATCH", "逐资源目标类型与资源能力不一致");
            }
            if (!Double.isFinite(target.targetPower())) {
                throw conflict("DISPATCH_TARGET_POWER_INVALID", "逐资源目标功率非法");
            }
        }
    }

    private double round(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void ensureStatus(DispatchPlanEntity entity, DispatchPlanStatus status) {
        if (!status.name().equals(entity.getStatus())) {
            throw conflict("DISPATCH_PLAN_STATUS_INVALID", "当前计划状态不允许执行该操作");
        }
    }

    private ApiException notFound(String code, String message, String identifier) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of("identifier", identifier));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of());
    }

    /**
     * 计划与其唯一对应的优化结果。
     *
     * @param plan 调度计划
     * @param optimizationResult 优化结果
     */
    private record PlanBuildResult(DispatchPlan plan, OptimizationResult optimizationResult) {
    }
}
