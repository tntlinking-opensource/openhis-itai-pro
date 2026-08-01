/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.repository;

import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 药品定义仓储。
 */
public interface MedicationDefinitionRepository extends JpaRepository<MedicationDefinition, Long>,
    JpaSpecificationExecutor<MedicationDefinition> {

    /**
     * 查询未删除记录。
     *
     * @param id 主键
     * @return 药品定义
     */
    Optional<MedicationDefinition> findById(Long id);

    /**
     * 校验机构内编码是否存在（历史兼容；新口径请用 {@link #existsByBusNo}）。
     *
     * @param orgId 机构
     * @param busNo 业务编码
     * @return 是否存在
     */
    boolean existsByOrgIdAndBusNo(Long orgId, String busNo);

    /**
     * 校验租户内业务编码唯一。
     *
     * @param busNo 业务编码
     * @return 是否存在
     */
    boolean existsByBusNo(String busNo);

    /**
     * 批量查询机构+编码组合（历史兼容）。
     *
     * @param orgIds 机构集合
     * @param busNos 编码集合
     * @return 命中记录
     */
    List<MedicationDefinition> findByOrgIdInAndBusNoIn(List<Long> orgIds, List<String> busNos);

    /**
     * 按业务编码批量加载（导入/同步解析主键；调用方自行处理同码多机构歧义）。
     *
     * @param busNos 业务编码集合
     * @return 命中记录
     */
    List<MedicationDefinition> findByBusNoIn(List<String> busNos);

    /**
     * 查询未删除药品定义集合。
     *
     * @return 未删除记录
     */
    List<MedicationDefinition> findAllBy();
}
