/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.repository;

import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 执行记录仓储。
 */
public interface ProcedureRepository extends JpaRepository<Procedure, Long>, JpaSpecificationExecutor<Procedure> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Procedure> findById(Long id);

    /**
     * 按条件查询。
     * @param requestId 条件
     * @param requestTable 条件
     * @return 查询结果
     */
    List<Procedure> findByRequestIdAndRequestTable(Long requestId, String requestTable);

    /**
     * 按就诊主键查询该就诊下全部执行记录（护士站净剩列表等场景）。
     *
     * @param encounterId 就诊主键
     * @return 执行记录列表；无命中时返回空集合
     */
    List<Procedure> findByEncounterId(Long encounterId);

    /**
     * 按就诊与来源请求缩窄查询执行记录。
     *
     * @param encounterId  就诊主键
     * @param requestId    来源请求主键
     * @param requestTable 来源请求表持久化名
     * @return 执行记录列表；无命中时返回空集合
     */
    List<Procedure> findByEncounterIdAndRequestIdAndRequestTable(Long encounterId, Long requestId, String requestTable);

    /**
     * 按就诊主键集合批量查询执行记录（护士站当日净执行统计）。
     *
     * @param encounterIds 就诊主键集合
     * @return 执行记录列表
     */
    List<Procedure> findByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按主键集合批量查询执行记录（DM-CLN-004 §6.2.8；AS-IP-NUR-006）。
     *
     * @param ids 主键集合
     * @return 执行记录列表
     */
    List<Procedure> findAllByIdIn(Collection<Long> ids);

    /**
     * 护士工作量：按就诊集合、来源表与执行时刻窗批量查询（DM-CLN-004 §6.2.10～6.2.11；AS-RPT-002）。
     *
     * @param encounterIds  就诊主键集合
     * @param requestTable  来源表持久化名
     * @param recordedFrom  执行时刻下界（含）
     * @param recordedTo    执行时刻上界（含）
     * @return 执行记录列表
     */
    List<Procedure> findByEncounterIdInAndRequestTableAndRecordedTimeBetweenAndIsDeletedFalse(
        Collection<Long> encounterIds,
        String requestTable,
        LocalDateTime recordedFrom,
        LocalDateTime recordedTo);

    /**
     * 护士工作量：按 {@code refund_id} 批量加载取消行（DM-CLN-004 §6.2.6 对冲口径；AS-RPT-002）。
     *
     * @param refundIds     被对冲 COMPLETED 主键集合
     * @param requestTable  来源表持久化名
     * @return CANCEL 行列表
     */
    List<Procedure> findByRefundIdInAndRequestTableAndIsDeletedFalse(
        Collection<Long> refundIds,
        String requestTable);

    /**
     * 是否存在仍占用计划点的终态执行记录（GAP-EXE-002 域层门禁）。
     */
    boolean existsByRequestIdAndRequestTableAndOccurrenceTimeAndOccurrenceSlotActiveTrueAndStatusEnumInAndIsDeletedFalse(
        Long requestId,
        String requestTable,
        LocalDateTime occurrenceTime,
        Collection<String> statusEnums);
}
