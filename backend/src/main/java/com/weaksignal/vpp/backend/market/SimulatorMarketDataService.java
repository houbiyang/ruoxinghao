package com.weaksignal.vpp.backend.market;

import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ForecastPointRequest;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotCreateRequest;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalCreateRequest;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.SimulatorMarketDataRefreshResponse;
import com.weaksignal.vpp.backend.config.PlanningProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.forecast.ForecastService;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于模拟器最新遥测生成市场信号和短期预测快照。
 *
 * <p>该服务只使用持久化的模拟器遥测，不复用历史市场价格。价格为仿真调度的派生指标，
 * 不代表外部市场报价。</p>
 */
@Service
public class SimulatorMarketDataService {

    private static final String SOURCE = "SIMULATOR_LIVE_TELEMETRY";
    private static final String FORMULA_VERSION = "simulator-live-telemetry-v1";
    private static final BigDecimal BASE_PRICE = new BigDecimal("0.350000");
    private static final BigDecimal GRID_UTILIZATION_PRICE_RANGE = new BigDecimal("0.650000");

    private final StationRepository stationRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final MarketSignalService marketSignalService;
    private final ForecastService forecastService;
    private final PlanningProperties planningProperties;
    private final Clock clock;

    /**
     * 注入模拟器市场数据服务依赖。
     *
     * @param stationRepository 场站仓储
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param marketSignalService 市场信号服务
     * @param forecastService 预测快照服务
     * @param planningProperties 计划参数
     * @param clock 系统时钟
     */
    public SimulatorMarketDataService(
            StationRepository stationRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            MarketSignalService marketSignalService,
            ForecastService forecastService,
            PlanningProperties planningProperties,
            Clock clock
    ) {
        this.stationRepository = stationRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.marketSignalService = marketSignalService;
        this.forecastService = forecastService;
        this.planningProperties = planningProperties;
        this.clock = clock;
    }

    /**
     * 读取最新模拟器遥测并生成当前市场信号及短期持平预测。
     *
     * @return 本次生成的市场和预测快照
     */
    @Transactional
    public SimulatorMarketDataRefreshResponse refresh() {
        return refresh(false);
    }

    /**
     * 为故障实验读取最新模拟器遥测并生成市场信号及短期预测。
     *
     * <p>S3 场站使用进入离线前最后一份遥测作为站端自治固定功率证据，不参与远程控制；
     * 其他场站仍执行与普通刷新相同的在线、时效和质量校验。</p>
     *
     * @return 本次生成的市场和预测快照
     */
    @Transactional
    public SimulatorMarketDataRefreshResponse refreshForExperiment() {
        return refresh(true);
    }

    private SimulatorMarketDataRefreshResponse refresh(boolean allowS3Autonomy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<TelemetryStation> telemetryStations = loadTrustedTelemetry(now, allowS3Autonomy);
        DerivedTelemetry derivedTelemetry = deriveTelemetry(telemetryStations);
        OffsetDateTime intervalEnd = now.plusMinutes(planningProperties.intervalMinutes());
        BigDecimal derivedPrice = derivePrice(derivedTelemetry.gridPower(), derivedTelemetry.gridCapacity());
        QualityCode qualityCode = telemetryStations.stream().allMatch(item -> item.telemetry().getLinkState() != LinkState.S3
                && QualityCode.GOOD.name().equals(item.telemetry().getQualityCode()))
                ? QualityCode.GOOD
                : QualityCode.DEGRADED;

        MarketSignalResponse marketSignal = marketSignalService.createSnapshot(new MarketSignalCreateRequest(
                "SIMULATOR_REALTIME",
                now,
                intervalEnd,
                round(derivedTelemetry.loadPower()),
                derivedPrice,
                derivedPrice,
                derivedPrice,
                round(derivedTelemetry.gridCapacity()),
                round(derivedTelemetry.gridCapacity()),
                0.0,
                0.0,
                now,
                now,
                intervalEnd,
                SOURCE,
                qualityCode,
                FORMULA_VERSION,
                derivedTelemetry.latestReceivedAt()
        ));

        ForecastSnapshotResponse forecast = forecastService.createSnapshot(new ForecastSnapshotCreateRequest(
                planningProperties.intervalMinutes(),
                "simulator-telemetry-persistence-v1",
                List.of(new ForecastPointRequest(
                        now,
                        intervalEnd,
                        round(derivedTelemetry.loadPower()),
                        round(derivedTelemetry.loadPower()),
                        round(derivedTelemetry.loadPower()),
                        round(derivedTelemetry.photovoltaicPower()),
                        round(derivedTelemetry.photovoltaicPower()),
                        round(derivedTelemetry.photovoltaicPower()),
                        round(derivedTelemetry.windPower()),
                        round(derivedTelemetry.windPower()),
                        round(derivedTelemetry.windPower()),
                        derivedPrice,
                        derivedPrice,
                        derivedPrice
                )),
                now,
                now,
                intervalEnd,
                SOURCE,
                qualityCode,
                FORMULA_VERSION,
                derivedTelemetry.latestReceivedAt()
        ));
        return new SimulatorMarketDataRefreshResponse(
                marketSignal,
                forecast,
                derivedTelemetry.latestReceivedAt().toString(),
                FORMULA_VERSION
        );
    }

    private List<TelemetryStation> loadTrustedTelemetry(OffsetDateTime now, boolean allowS3Autonomy) {
        List<StationEntity> stations = stationRepository.findAll().stream()
                .filter(StationEntity::isEnabled)
                .sorted(Comparator.comparing(StationEntity::getStationId))
                .toList();
        if (stations.isEmpty()) {
            throw unavailable("SIMULATOR_TELEMETRY_UNAVAILABLE", "未找到已启用场站，无法生成市场数据", "stations");
        }
        return stations.stream().map(station -> {
            TelemetrySnapshotEntity telemetry = telemetrySnapshotRepository.findByStationId(station.getStationId())
                    .orElseThrow(() -> unavailable("SIMULATOR_TELEMETRY_UNAVAILABLE", "场站缺少模拟器遥测，无法生成市场数据", station.getStationId()));
            validateTelemetry(station.getStationId(), telemetry, now, allowS3Autonomy);
            return new TelemetryStation(station, telemetry);
        }).toList();
    }

    private void validateTelemetry(
            String stationId,
            TelemetrySnapshotEntity telemetry,
            OffsetDateTime now,
            boolean allowS3Autonomy
    ) {
        if (allowS3Autonomy && telemetry.getLinkState() == LinkState.S3) {
            return;
        }
        boolean qualityAccepted = QualityCode.GOOD.name().equals(telemetry.getQualityCode())
                || QualityCode.DEGRADED.name().equals(telemetry.getQualityCode());
        boolean fresh = telemetry.getReceivedTimestamp().plusSeconds(planningProperties.maximumDataAgeSeconds()).isAfter(now);
        if (!telemetry.isOnline() || !fresh || !qualityAccepted || telemetry.getLinkState() == LinkState.S3) {
            throw unavailable("SIMULATOR_TELEMETRY_UNAVAILABLE", "场站遥测离线、过期或不可用，无法生成市场数据", stationId);
        }
    }

    private DerivedTelemetry deriveTelemetry(List<TelemetryStation> telemetryStations) {
        double photovoltaicPower = sumPower(telemetryStations, ResourceType.PHOTOVOLTAIC, false);
        double windPower = sumPower(telemetryStations, ResourceType.WIND, false);
        double loadPower = sumPower(telemetryStations, ResourceType.LOAD, true)
                + sumPower(telemetryStations, ResourceType.CHARGING, true);
        double gridPower = sumPower(telemetryStations, ResourceType.GRID, true);
        double gridCapacity = telemetryStations.stream()
                .filter(item -> item.station().getResourceType() == ResourceType.GRID)
                .mapToDouble(item -> Math.max(0.0, item.telemetry().getAvailableCapacity()))
                .sum();
        OffsetDateTime latestReceivedAt = telemetryStations.stream()
                .map(item -> item.telemetry().getReceivedTimestamp())
                .max(OffsetDateTime::compareTo)
                .orElseThrow(() -> unavailable("SIMULATOR_TELEMETRY_UNAVAILABLE", "未读取到模拟器遥测", "telemetry"));
        return new DerivedTelemetry(photovoltaicPower, windPower, loadPower, gridPower, gridCapacity, latestReceivedAt);
    }

    private double sumPower(List<TelemetryStation> telemetryStations, ResourceType resourceType, boolean absolute) {
        return telemetryStations.stream()
                .filter(item -> item.station().getResourceType() == resourceType)
                .mapToDouble(item -> absolute ? Math.abs(item.telemetry().getActivePower()) : Math.max(0.0, item.telemetry().getActivePower()))
                .sum();
    }

    private BigDecimal derivePrice(double gridPower, double gridCapacity) {
        double utilization = gridCapacity <= 0.0 ? 0.0 : Math.min(1.0, Math.max(0.0, gridPower / gridCapacity));
        return BASE_PRICE.add(GRID_UTILIZATION_PRICE_RANGE.multiply(BigDecimal.valueOf(utilization)))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private ApiException unavailable(String code, String message, String identifier) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of("identifier", identifier));
    }

    /**
     * 场站及其遥测的配对数据。
     *
     * @param station 场站实体
     * @param telemetry 模拟器遥测
     */
    private record TelemetryStation(StationEntity station, TelemetrySnapshotEntity telemetry) {
    }

    /**
     * 用于构造市场数据的实时汇总。
     *
     * @param photovoltaicPower 光伏实时功率
     * @param windPower 风电实时功率
     * @param loadPower 负荷与充电设备实时功率
     * @param gridPower 市电实时功率绝对值
     * @param gridCapacity 市电可用容量
     * @param latestReceivedAt 最新遥测接收时间
     */
    private record DerivedTelemetry(
            double photovoltaicPower,
            double windPower,
            double loadPower,
            double gridPower,
            double gridCapacity,
            OffsetDateTime latestReceivedAt
    ) {
    }
}
