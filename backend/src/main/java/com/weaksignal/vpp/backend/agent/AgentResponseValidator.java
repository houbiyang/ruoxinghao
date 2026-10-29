package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import org.springframework.stereotype.Component;

/**
 * AI 结构化输出安全校验器。
 */
@Component
public class AgentResponseValidator {

    private final StationRepository stationRepository;

    /**
     * 注入场站仓储。
     *
     * @param stationRepository 场站仓储
     */
    public AgentResponseValidator(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    /**
     * 校验枚举、置信度、场站和动作白名单。
     *
     * @param response 智能体响应
     * @return 是否通过安全校验
     */
    public boolean isValid(AgentStructuredResponse response) {
        if (response == null
                || response.agentType() == null
                || response.severity() == null
                || response.recommendedMode() == null
                || response.confidence() < 0
                || response.confidence() > 1
                || response.faultCode() == null
                || response.faultType() == null
                || response.proposedActions() == null
                || response.affectedStationIds() == null) {
            return false;
        }
        boolean stationsValid = response.affectedStationIds().stream()
                .allMatch(stationId -> stationRepository.findByStationId(stationId).isPresent());
        boolean actionsValid = response.proposedActions().stream()
                .allMatch(action -> action.actionType() != null
                        && action.stationId() != null
                        && stationRepository.findByStationId(action.stationId()).isPresent()
                        && action.parameters() != null);
        return stationsValid && actionsValid;
    }
}
