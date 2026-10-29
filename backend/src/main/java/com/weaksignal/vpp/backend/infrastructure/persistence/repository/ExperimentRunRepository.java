package com.weaksignal.vpp.backend.infrastructure.persistence.repository;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 实验运行仓储。
 */
public interface ExperimentRunRepository extends JpaRepository<ExperimentRunEntity, Long> {

    /**
     * 查询最近一次未结束的实验运行。
     *
     * @param status 运行状态
     * @return 实验运行
     */
    Optional<ExperimentRunEntity> findFirstByStatusOrderByStartedAtDesc(String status);

    /**
     * 查询最近一次指定前缀的未结束实验运行。
     *
     * @param status 运行状态
     * @param scenarioCodePrefix 场景编码前缀
     * @return 实验运行
     */
    Optional<ExperimentRunEntity> findFirstByStatusAndScenarioCodeStartingWithOrderByStartedAtDesc(
            String status,
            String scenarioCodePrefix
    );

    /**
     * 查询最近一次处于指定生命周期状态的实验运行。
     *
     * @param scenarioCodePrefix 场景编码前缀
     * @param statuses 生命周期状态集合
     * @return 最近实验运行
     */
    Optional<ExperimentRunEntity> findFirstByScenarioCodeStartingWithAndStatusInOrderByStartedAtDesc(
            String scenarioCodePrefix,
            List<String> statuses
    );

    /**
     * 查询最近一次处于指定生命周期状态的实验运行。
     *
     * @param statuses 生命周期状态集合
     * @return 最近实验运行
     */
    Optional<ExperimentRunEntity> findFirstByStatusInOrderByStartedAtDesc(List<String> statuses);

    /**
     * 查询处于指定生命周期状态的实验运行。
     *
     * @param scenarioCodePrefix 场景编码前缀
     * @param statuses 生命周期状态集合
     * @return 实验运行列表
     */
    List<ExperimentRunEntity> findByScenarioCodeStartingWithAndStatusIn(String scenarioCodePrefix, List<String> statuses);

    /**
     * 查询处于指定生命周期状态的全部实验运行。
     *
     * @param statuses 生命周期状态集合
     * @return 实验运行列表
     */
    List<ExperimentRunEntity> findByStatusIn(List<String> statuses);

    /**
     * 按运行标识查询。
     *
     * @param runId 运行标识
     * @return 实验运行
     */
    Optional<ExperimentRunEntity> findByRunId(String runId);

    /**
     * 查询最近实验运行。
     *
     * @param pageable 分页参数
     * @return 实验运行列表
     */
    List<ExperimentRunEntity> findByScenarioCodeStartingWithOrderByStartedAtDesc(String scenarioCodePrefix, Pageable pageable);

    /**
     * 按开始时间倒序查询实验运行。
     *
     * @param pageable 分页参数
     * @return 实验运行列表
     */
    List<ExperimentRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
