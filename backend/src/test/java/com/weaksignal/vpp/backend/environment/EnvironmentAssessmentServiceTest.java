package com.weaksignal.vpp.backend.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import org.junit.jupiter.api.Test;

/**
 * 环境感知服务测试。
 */
class EnvironmentAssessmentServiceTest {

    @Test
    void shouldTreatCloudCoverAsRatioInsteadOfPercentage() {
        EnvironmentAssessmentResult result = service().assess(
                "pv-gobi-01", ResourceType.PHOTOVOLTAIC, 20.0, 168.0, LinkState.S0,
                "{\"metrics\":{\"irradiance\":160.0,\"cloudCover\":0.85,\"moduleTemperature\":45.0,\"conversionEfficiency\":0.96}}"
        );

        assertTrue(result.dataAvailable());
        assertTrue(result.correctionReasons().contains("云量 85.0%"));
        assertTrue(result.environmentFactor() > 0.0);
    }

    @Test
    void shouldRejectMissingCriticalEnvironmentMeasurements() {
        EnvironmentAssessmentResult result = service().assess(
                "pv-gobi-01", ResourceType.PHOTOVOLTAIC, 20.0, 168.0, LinkState.S0,
                "{\"metrics\":{\"irradiance\":160.0}}"
        );

        assertFalse(result.dataAvailable());
        assertEquals(1.0, result.environmentFactor());
        assertTrue(result.missingFields().contains("cloudCover"));
        assertTrue(result.missingFields().contains("moduleTemperature"));
        assertTrue(result.missingFields().contains("conversionEfficiency"));
    }

    private EnvironmentAssessmentService service() {
        return new EnvironmentAssessmentService(new ObjectMapper().findAndRegisterModules());
    }
}
