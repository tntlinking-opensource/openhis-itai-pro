/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.repository;

import com.openhis.health.domain.custom.tracenumber.entity.TraceNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 追溯码主档仓储（DM-CUS-010）。
 */
public interface TraceNumberRepository extends JpaRepository<TraceNumber, Long>, JpaSpecificationExecutor<TraceNumber> {

    /**
     * 按追溯码加载有效主档（租户内唯一，BR-TNR-005 软删过滤）。
     *
     * @param traceNo 追溯码
     * @return 主档；未命中为空
     */
    Optional<TraceNumber> findByTraceNoAndIsDeletedFalse(String traceNo);

    /**
     * 批量按追溯码加载有效主档（BR-TNR-015 禁止 N+1）。
     *
     * @param traceNos 追溯码集合
     * @return 主档列表
     */
    List<TraceNumber> findAllByTraceNoInAndIsDeletedFalse(Collection<String> traceNos);
}
