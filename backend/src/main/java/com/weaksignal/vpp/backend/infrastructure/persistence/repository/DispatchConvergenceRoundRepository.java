package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchConvergenceRoundEntity;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 调度收敛轮次审计仓储。
 */
public interface DispatchConvergenceRoundRepository extends JpaRepository<DispatchConvergenceRoundEntity, Long> {
    Optional<DispatchConvergenceRoundEntity> findByExperimentRunIdAndRoundNumber(String experimentRunId, int roundNumber);

    /** 查询实验的最新轮次。 */
    Optional<DispatchConvergenceRoundEntity> findFirstByExperimentRunIdOrderByRoundNumberDesc(String experimentRunId);

    /** 查询实验的全部收敛轮次。 */
    List<DispatchConvergenceRoundEntity> findByExperimentRunIdOrderByRoundNumberAsc(String experimentRunId);

    /** 查询最近一个仍需遥测推进的收敛轮次。 */
    Optional<DispatchConvergenceRoundEntity> findFirstByStatusInOrderByStartedAtDesc(Collection<String> statuses);
}
