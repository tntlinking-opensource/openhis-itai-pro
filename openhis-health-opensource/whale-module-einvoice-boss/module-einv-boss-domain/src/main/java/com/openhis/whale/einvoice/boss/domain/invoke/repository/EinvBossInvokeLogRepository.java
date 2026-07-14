/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.invoke.repository;

import com.openhis.whale.einvoice.boss.domain.invoke.entity.EinvBossInvokeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 博思 HTTP 调用日志仓储（{@code einv_boss_invoke_log}）。
 *
 * <p>记录每次出站 serviceId、请求/响应摘要与耗时；冲红状态回填时可按 busNo+serviceId 取最近一条。</p>
 */
public interface EinvBossInvokeLogRepository extends JpaRepository<EinvBossInvokeLog, Long> {

    /**
     * 按业务流水号与服务标识取最近一次调用（用于冲红状态回填）。
     *
     * @param busNo 业务流水号
     * @param serviceId 博思 serviceId
     * @return 最近一条日志（若存在）
     */
    Optional<EinvBossInvokeLog> findFirstByBusNoAndServiceIdOrderByCreatedAtDesc(String busNo, String serviceId);
}
