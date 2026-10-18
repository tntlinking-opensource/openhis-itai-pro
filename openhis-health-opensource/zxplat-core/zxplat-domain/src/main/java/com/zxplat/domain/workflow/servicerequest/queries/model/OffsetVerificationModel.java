/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 服务申请冲销校验模型。
 */
@Getter
@Setter
/**
 * OffsetVerificationModel。
 */
public class OffsetVerificationModel {
    /** source主键。 */
    /**
     * sourceId。
     */
    private Long sourceId;
    /** remaining。 */
    /**
     * remaining。
     */
    private BigDecimal remaining;
}
