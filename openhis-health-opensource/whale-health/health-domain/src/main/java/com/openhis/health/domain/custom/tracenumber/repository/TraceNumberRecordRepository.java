/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.repository;

import com.openhis.health.domain.custom.tracenumber.entity.TraceNumberRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
     * 按业务单号加载有效流水（期初作废 T1 冲销按 bus_no 找当初 IN）。
     *
     * @param busNo 业务单号
     * @return 流水列表
     */
    List<TraceNumberRecord> findAllByBusNoAndIsDeletedFalse(String busNo);

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

    /**
     * 按院内出入类别/方向/操作类型 + 创建起点，去重列出业务单号（日批发现未建 msfx 账用）。
     *
     * @param category HOSPITAL_IO
     * @param direction IN/OUT
     * @param operationType 如 CABINET_INBOUND_RECEIPT
     * @param since 创建起点（含）
     * @param pageable 限流
     * @return 去重 busNo 列表
     */
    @Query("""
            select distinct r.busNo from TraceNumberRecord r
            where r.isDeleted = false
              and r.recordCategoryEnum = :category
              and r.recordDirectionEnum = :direction
              and r.operationType = :operationType
              and r.createdAt >= :since
              and r.busNo is not null
              and r.busNo <> ''
            order by r.busNo asc
            """)
    List<String> findDistinctBusNosForHospitalIo(
            @Param("category") String category,
            @Param("direction") String direction,
            @Param("operationType") String operationType,
            @Param("since") LocalDateTime since,
            Pageable pageable);
}
