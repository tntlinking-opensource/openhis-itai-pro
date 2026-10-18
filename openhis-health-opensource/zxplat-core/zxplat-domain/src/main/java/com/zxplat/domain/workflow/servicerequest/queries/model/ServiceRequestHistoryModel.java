/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者历史服务申请列表行模型。
 */
@Getter
@Setter
public class ServiceRequestHistoryModel {
    /** 主键。 */
    private Long id;
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
}
