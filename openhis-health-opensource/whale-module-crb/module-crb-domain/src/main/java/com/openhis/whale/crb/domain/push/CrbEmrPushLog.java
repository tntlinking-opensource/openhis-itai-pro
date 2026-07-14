/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push;

import com.openhis.whale.crb.domain.support.CrbLogRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * EMR 推送结果日志（crb_emr_push_log）。
 */
@Entity
@Table(name = "crb_emr_push_log")
@Getter
@Setter
public class CrbEmrPushLog extends CrbLogRowEntity {

    /** 关联任务 id（可为空，如同步探测）。 */
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "resource", nullable = false, length = 64)
    private String resource;

    @Column(name = "business_key", nullable = false, length = 256)
    private String businessKey;

    @Column(name = "request_summary", length = 2000)
    private String requestSummary;

    @Column(name = "response_summary", length = 2000)
    private String responseSummary;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_name", length = 128)
    private String errorName;

    @Column(name = "duration_ms")
    private Long durationMs;
}
