/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A000x 运维日志。 */
@Getter
@Setter
@Entity
@Table(name = "yb_elep_upload_log")
public class YbElepUploadLog extends BaseAuditedEntity {

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "op_code", nullable = false, length = 16)
    private String opCode;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;
}
