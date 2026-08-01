/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfratiorequest.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公费比例调整申请（cmp_gf_ratio_request）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_gf_ratio_request")
public class GfRatioRequest extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "request_no", nullable = false, length = 64)
    private String requestNo;

    @Column(name = "charge_mode_enum", nullable = false, length = 32)
    private String chargeModeEnum;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "old_ratio", precision = 10, scale = 6)
    private BigDecimal oldRatio;

    @Column(name = "new_ratio", nullable = false, precision = 10, scale = 6)
    private BigDecimal newRatio;

    @Column(name = "status_enum", nullable = false, length = 32)
    private String statusEnum;

    @Column(name = "flow_instance_id", length = 128)
    private String flowInstanceId;

    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "applicant_name", length = 128)
    private String applicantName;

    @Column(name = "approve_time")
    private LocalDateTime approveTime;

    @Column(name = "remark", length = 512)
    private String remark;
}
