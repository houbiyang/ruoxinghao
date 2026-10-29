package com.weaksignal.vpp.simulator;

/**
 * 场站运行时状态。
 */
public class StationRuntime {

    private final StationDefinition stationDefinition;
    private long sequenceNumber;
    private double activePower;
    private double availableCapacity;
    private double soc;
    private double temperature;
    private double voltage;
    private double frequency;
    private int vehicleCount;
    private boolean online;
    private Double frozenPower;
    private Double dispatchTargetPower;
    private String dispatchPlanId;
    private double dispatchRampRate;
    private String dispatchControlMode;
    private boolean localAutonomy;
    private Double lastExecutedSafeTargetPower;
    private long localAutonomyStartTick;
    private double localAutonomyReferencePower;
    private double localAutonomyActualPower;
    private long localAutonomyActionCount;
    private long localAutonomyConstraintViolationCount;
    private int recoveryStableSampleCount;
    private boolean rejoinedCommunicationGraph;
    private boolean localAutonomyEvidenceAvailable;
    private boolean dataTrusted = true;
    private int telemetryRateDivisor = 1;

    /**
     * 创建场站运行时状态。
     *
     * @param stationDefinition 场站定义
     */
    public StationRuntime(StationDefinition stationDefinition) {
        this.stationDefinition = stationDefinition;
        // 使用进程启动时间建立序号基线，避免模拟器重启后被后端判定为旧遥测。
        this.sequenceNumber = System.currentTimeMillis() * 1000L;
        this.availableCapacity = stationDefinition.capacity();
        this.activePower = stationDefinition.capacity() * 0.45;
        this.soc = 62.0;
        this.temperature = 28.0;
        this.voltage = 380.0;
        this.frequency = 50.0;
        this.vehicleCount = 6;
        this.online = true;
    }

    public StationDefinition getStationDefinition() {
        return stationDefinition;
    }

    public long nextSequenceNumber() {
        sequenceNumber += 1;
        return sequenceNumber;
    }

    /**
     * 回退序列号，用于制造重复或乱序遥测。
     */
    public void rollbackSequenceNumber() {
        if (sequenceNumber > 0) {
            sequenceNumber -= 1;
        }
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public double getActivePower() {
        return activePower;
    }

    public void setActivePower(double activePower) {
        this.activePower = activePower;
    }

    public double getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(double availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public double getSoc() {
        return soc;
    }

    public void setSoc(double soc) {
        this.soc = soc;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getVoltage() {
        return voltage;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    public double getFrequency() {
        return frequency;
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    /**
     * 获取冻结功率。
     *
     * @return 冻结功率
     */
    public Double getFrozenPower() {
        return frozenPower;
    }

    /**
     * 设置冻结功率。
     *
     * @param frozenPower 冻结功率
     */
    public void setFrozenPower(Double frozenPower) {
        this.frozenPower = frozenPower;
    }

    /**
     * 获取调度目标功率。
     *
     * @return 调度目标功率
     */
    public Double getDispatchTargetPower() {
        return dispatchTargetPower;
    }

    /**
     * 设置调度目标功率。
     *
     * @param dispatchTargetPower 调度目标功率
     */
    public void setDispatchTargetPower(Double dispatchTargetPower) {
        this.dispatchTargetPower = dispatchTargetPower;
    }

    /**
     * 获取当前生效的调度计划标识。
     *
     * @return 调度计划标识
     */
    public String getDispatchPlanId() {
        return dispatchPlanId;
    }

    /**
     * 设置当前生效的调度计划标识。
     *
     * @param dispatchPlanId 调度计划标识
     */
    public void setDispatchPlanId(String dispatchPlanId) {
        this.dispatchPlanId = dispatchPlanId;
    }

    /**
     * 获取调度爬坡限制。
     *
     * @return 每周期最大功率变化
     */
    public double getDispatchRampRate() {
        return dispatchRampRate;
    }

    /**
     * 设置调度爬坡限制。
     *
     * @param dispatchRampRate 每周期最大功率变化
     */
    public void setDispatchRampRate(double dispatchRampRate) {
        this.dispatchRampRate = dispatchRampRate;
    }

    /**
     * 获取最近一次调度指令的控制模式。
     *
     * @return 控制模式
     */
    public String getDispatchControlMode() {
        return dispatchControlMode;
    }

    /**
     * 设置最近一次调度指令的控制模式。
     *
     * @param dispatchControlMode 控制模式
     */
    public void setDispatchControlMode(String dispatchControlMode) {
        this.dispatchControlMode = dispatchControlMode;
    }

    /**
     * 查询是否处于本地自治。
     *
     * @return 是否本地自治
     */
    public boolean isLocalAutonomy() {
        return localAutonomy;
    }

    /**
     * 设置本地自治状态。
     *
     * @param localAutonomy 是否本地自治
     */
    public void setLocalAutonomy(boolean localAutonomy) {
        this.localAutonomy = localAutonomy;
    }

    /**
     * 获取最近一次已接受的安全调度目标。
     *
     * @return 最近安全目标功率，尚未接受过命令时为空
     */
    public Double getLastExecutedSafeTargetPower() {
        return lastExecutedSafeTargetPower;
    }

    /**
     * 记录最近一次已接受的安全调度目标。
     *
     * @param lastExecutedSafeTargetPower 目标功率
     */
    public void setLastExecutedSafeTargetPower(Double lastExecutedSafeTargetPower) {
        this.lastExecutedSafeTargetPower = lastExecutedSafeTargetPower;
    }

    /**
     * 启动站端本地自治并初始化审计状态。
     *
     * @param referencePower 自治参考功率
     * @param startTick 自治开始仿真时刻
     */
    public void startLocalAutonomy(double referencePower, long startTick) {
        this.localAutonomy = true;
        this.localAutonomyStartTick = startTick;
        this.localAutonomyReferencePower = referencePower;
        this.localAutonomyActualPower = activePower;
        this.localAutonomyActionCount = 0L;
        this.localAutonomyConstraintViolationCount = 0L;
        this.recoveryStableSampleCount = 0;
        this.rejoinedCommunicationGraph = false;
        this.localAutonomyEvidenceAvailable = true;
    }

    /**
     * 记录一次本地自治功率动作。
     *
     * @param actualPower 动作后的实际功率
     * @param constraintSatisfied 是否满足全部本地约束
     */
    public void recordLocalAutonomyAction(double actualPower, boolean constraintSatisfied) {
        this.localAutonomyActualPower = actualPower;
        this.localAutonomyActionCount += 1L;
        if (!constraintSatisfied) {
            this.localAutonomyConstraintViolationCount += 1L;
        }
    }

    /**
     * 记录一个 S4 有效恢复样本。
     *
     * @return 当前连续有效样本数
     */
    public int recordRecoveryStableSample() {
        recoveryStableSampleCount += 1;
        return recoveryStableSampleCount;
    }

    /**
     * 标记场站已完成 S4 同步并重新加入通信图。
     */
    public void markRejoinedCommunicationGraph() {
        this.localAutonomy = false;
        this.rejoinedCommunicationGraph = true;
    }

    /** @return 本地自治开始仿真时刻 */
    public long getLocalAutonomyStartTick() {
        return localAutonomyStartTick;
    }

    /** @return 本地自治参考功率 */
    public double getLocalAutonomyReferencePower() {
        return localAutonomyReferencePower;
    }

    /** @return 本地自治实际功率 */
    public double getLocalAutonomyActualPower() {
        return localAutonomyActualPower;
    }

    /** @return 本地自治动作次数 */
    public long getLocalAutonomyActionCount() {
        return localAutonomyActionCount;
    }

    /** @return 本地自治约束违规次数 */
    public long getLocalAutonomyConstraintViolationCount() {
        return localAutonomyConstraintViolationCount;
    }

    /** @return S4 连续有效恢复样本数 */
    public int getRecoveryStableSampleCount() {
        return recoveryStableSampleCount;
    }

    /** @return 是否已重新加入通信图 */
    public boolean isRejoinedCommunicationGraph() {
        return rejoinedCommunicationGraph;
    }

    /** @return 是否存在待补报的本地自治证据 */
    public boolean isLocalAutonomyEvidenceAvailable() {
        return localAutonomyEvidenceAvailable;
    }

    /**
     * 清除实验结束后的本地自治运行状态。
     */
    public void clearLocalAutonomyState() {
        this.localAutonomy = false;
        this.recoveryStableSampleCount = 0;
        this.rejoinedCommunicationGraph = false;
        this.localAutonomyEvidenceAvailable = false;
    }

    /**
     * 设置数据可信状态。
     *
     * @param dataTrusted 是否可信
     */
    public void setDataTrusted(boolean dataTrusted) {
        this.dataTrusted = dataTrusted;
    }

    /**
     * 查询数据可信状态。
     *
     * @return 是否可信
     */
    public boolean isDataTrusted() {
        return dataTrusted;
    }

    /**
     * 设置遥测降频倍数。
     *
     * @param telemetryRateDivisor 降频倍数
     */
    public void setTelemetryRateDivisor(int telemetryRateDivisor) {
        this.telemetryRateDivisor = Math.max(1, telemetryRateDivisor);
    }

    /**
     * 获取遥测降频倍数。
     *
     * @return 降频倍数
     */
    public int getTelemetryRateDivisor() {
        return telemetryRateDivisor;
    }
}
