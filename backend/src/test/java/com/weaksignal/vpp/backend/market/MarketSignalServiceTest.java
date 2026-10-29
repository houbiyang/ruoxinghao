package com.weaksignal.vpp.backend.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.MarketSignalCreateRequest;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketSignalSnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketSignalSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 市场信号服务测试。
 */
class MarketSignalServiceTest {

    private MarketSignalSnapshotRepository marketSignalSnapshotRepository;
    private MarketSignalService marketSignalService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        marketSignalSnapshotRepository = mock(MarketSignalSnapshotRepository.class);
        clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        marketSignalService = new MarketSignalService(
                marketSignalSnapshotRepository,
                new ObjectMapper().findAndRegisterModules(),
                clock
        );
    }

    @Test
    void shouldRejectExpiredMarketSignalRequest() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        MarketSignalCreateRequest request = new MarketSignalCreateRequest(
                "TEST_MARKET",
                now.plusMinutes(10),
                now.plusMinutes(70),
                280.0,
                new BigDecimal("0.452000"),
                new BigDecimal("0.486000"),
                new BigDecimal("0.518000"),
                320.0,
                180.0,
                42.0,
                28.0,
                now.minusMinutes(20),
                now.minusMinutes(10),
                now.minusMinutes(1),
                "TEST_MARKET_DATA",
                QualityCode.GOOD,
                "market-v1",
                now.minusMinutes(19)
        );

        ApiException exception = assertThrows(ApiException.class, () -> marketSignalService.createSnapshot(request));

        assertEquals("MARKET_SIGNAL_EXPIRED", exception.getCode());
    }

    @Test
    void shouldReturnLatestActiveSnapshot() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        MarketSignalSnapshotEntity entity = new MarketSignalSnapshotEntity();
        entity.setSnapshotId("market-signal-seed-001");
        entity.setMarketType("TEST_MARKET");
        entity.setIntervalStart(now.plusMinutes(5));
        entity.setIntervalEnd(now.plusMinutes(65));
        entity.setContractPower(280.0);
        entity.setContractPrice(new BigDecimal("0.452000"));
        entity.setDayAheadPrice(new BigDecimal("0.486000"));
        entity.setRealTimePrice(new BigDecimal("0.518000"));
        entity.setGridImportLimit(320.0);
        entity.setGridExportLimit(180.0);
        entity.setReserveUpRequirement(42.0);
        entity.setReserveDownRequirement(28.0);
        entity.setSource("TEST_MARKET_DATA");
        entity.setQualityCode(QualityCode.GOOD.name());
        entity.setVersion("market-v1");
        entity.setGeneratedAt(now.minusMinutes(5));
        entity.setValidFrom(now);
        entity.setValidUntil(now.plusHours(2));
        entity.setReceivedAt(now.minusMinutes(4));
        when(marketSignalSnapshotRepository.findFirstByOrderByGeneratedAtDesc()).thenReturn(Optional.of(entity));

        MarketSignalSnapshot snapshot = marketSignalService.getLatestActiveSnapshot();

        assertEquals("market-signal-seed-001", snapshot.snapshotId());
        assertEquals(280.0, snapshot.contractPower());
    }

    @Test
    void shouldRefreshExpiredSignalForExperiment() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        MarketSignalSnapshotEntity entity = new MarketSignalSnapshotEntity();
        entity.setSnapshotId("market-signal-expired-001");
        entity.setMarketType("TEST_MARKET");
        entity.setIntervalStart(now.minusMinutes(50));
        entity.setIntervalEnd(now.minusMinutes(20));
        entity.setContractPower(280.0);
        entity.setContractPrice(new BigDecimal("0.452000"));
        entity.setDayAheadPrice(new BigDecimal("0.486000"));
        entity.setRealTimePrice(new BigDecimal("0.518000"));
        entity.setGridImportLimit(320.0);
        entity.setGridExportLimit(180.0);
        entity.setReserveUpRequirement(42.0);
        entity.setReserveDownRequirement(28.0);
        entity.setSource("TEST_MARKET_DATA");
        entity.setQualityCode(QualityCode.GOOD.name());
        entity.setVersion("market-v1");
        entity.setGeneratedAt(now.minusMinutes(50));
        entity.setValidFrom(now.minusMinutes(50));
        entity.setValidUntil(now.minusMinutes(20));
        entity.setReceivedAt(now.minusMinutes(50));
        when(marketSignalSnapshotRepository.findFirstByOrderByGeneratedAtDesc()).thenReturn(Optional.of(entity));

        MarketSignalSnapshot snapshot = marketSignalService.ensureActiveForExperiment();

        ArgumentCaptor<MarketSignalSnapshotEntity> savedEntity = ArgumentCaptor.forClass(MarketSignalSnapshotEntity.class);
        verify(marketSignalSnapshotRepository).save(savedEntity.capture());
        assertEquals("EXPERIMENT_SIGNAL_REFRESH", snapshot.source());
        assertEquals(now.plusMinutes(30), snapshot.validUntil());
        assertEquals(280.0, snapshot.contractPower());
        assertEquals(now.plusMinutes(30), savedEntity.getValue().getIntervalEnd());
    }
}
