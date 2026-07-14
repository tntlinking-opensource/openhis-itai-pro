/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.repository;

import com.openhis.health.domain.custom.tracenumber.entity.TraceNumberRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

/**
 * 追溯码记录子表仓储（DM-CUS-010 · 只 INSERT）。
 */
public interface TraceNumberRecordRepository extends JpaRepository<TraceNumberRecord, Long>,
    JpaSpecificationExecutor<TraceNumberRecord> {

    /**
     * 按追溯码加载有效记录，按主键升序（时间序）。
     *
     * @param traceNo 追溯码
     * @return 记录列表
     */
    List<TraceNumberRecord> findAllByTraceNoAndIsDeletedFalseOrderByIdAsc(String traceNo);

    /**
     * 按库位加载有效记录（库位聚合读侧）。
     *
     * @param locationId 库位标识
     * @return 记录列表
     */
    List<TraceNumberRecord> findAllByLocationIdAndIsDeletedFalse(Long locationId);

    /**
     * 按主档标识批量加载有效记录。
     *
     * @param traceNumberIds 主档标识集合
     * @return 记录列表
     */
    List<TraceNumberRecord> findAllByTraceNumberIdInAndIsDeletedFalse(Collection<Long> traceNumberIds);

    /**
     * 判断业务单号是否已有记录（幂等 Gate 辅助）。
     *
     * @param busNo 业务单号
     * @return 已存在为 true
     */
    boolean existsByBusNoAndIsDeletedFalse(String busNo);
}
