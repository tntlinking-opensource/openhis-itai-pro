/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.invoke.entity;

import com.zxplat.plugin.einvoice.boss.domain.invoke.support.EinvBossLogRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 博思 HTTP 调用审计日志（表 {@code einv_boss_invoke_log}）。
 *
 * <p>记录每次出站 serviceId、结果码、耗时与脱敏摘要；{@code task_id} 可空（冲红/查票/入账查询）。
 * 冲红状态兜底展示可回查本表（{@link com.zxplat.plugin.einvoice.boss.application.internal.issue.BossWriteOffStatusSupport}）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "einv_boss_invoke_log")
public class EinvBossInvokeLog extends EinvBossLogRowEntity {

    /** 关联任务主键（可空：直连查票场景）。 */
    @Column(name = "task_id")
    private Long taskId;

    /** 博思 serviceId。 */
    @Column(name = "service_id", nullable = false, length = 50)
    private String serviceId;

    /** 业务流水号。 */
    @Column(name = "bus_no", length = 50)
    private String busNo;

    /** 博思结果码。 */
    @Column(name = "result_code", length = 20)
    private String resultCode;

    /** 调用耗时毫秒。 */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 请求摘要（脱敏）。 */
    @Column(name = "request_digest", length = 500)
    private String requestDigest;

    /** 响应摘要（脱敏）。 */
    @Column(name = "response_digest", length = 500)
    private String responseDigest;
}
