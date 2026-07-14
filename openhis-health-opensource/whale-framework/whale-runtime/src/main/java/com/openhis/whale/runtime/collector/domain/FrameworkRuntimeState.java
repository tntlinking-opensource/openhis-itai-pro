/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.collector.domain;

import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 框架运行时状态快照（中心节点收集表）。
 */
@Entity
@Table(name = "whale_framework_runtime_state", indexes = {
        @Index(name = "idx_fw_runtime_state_insight_code", columnList = "insight_code"),
        @Index(name = "idx_fw_runtime_state_fingerprint", columnList = "deployment_fingerprint"),
        @Index(name = "idx_fw_runtime_state_received_at", columnList = "received_at")
})
@Getter
@Setter
public class FrameworkRuntimeState {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "insight_code", nullable = false, length = 128)
    private String insightCode;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "runtime_version", length = 64)
    private String runtimeVersion;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    @Column(name = "session_started_at")
    private LocalDateTime sessionStartedAt;

    @Column(name = "org_category", length = 128)
    private String orgCategory;

    @Column(name = "org_name", length = 256)
    private String orgName;

    @Column(name = "deployment_fingerprint", length = 128)
    private String deploymentFingerprint;

    @Column(name = "license_customer", length = 256)
    private String licenseCustomer;

    @Column(name = "license_not_after")
    private LocalDateTime licenseNotAfter;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
