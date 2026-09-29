/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.integrationlog.repository;

import com.openhis.health.domain.pacs.integrationlog.entity.PacsIntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * PACS 集成日志仓储。
 */
public interface PacsIntegrationLogRepository
    extends JpaRepository<PacsIntegrationLog, Long>, JpaSpecificationExecutor<PacsIntegrationLog> {

    /**
     * 按结果与动作码倒序列表（补发用）。
     *
     * @param outcomeEnum 结果
     * @param actionCode 动作码
     * @return 日志列表
     */
    List<PacsIntegrationLog> findByOutcomeEnumAndActionCodeOrderByCreatedAtDesc(String outcomeEnum, String actionCode);
}
