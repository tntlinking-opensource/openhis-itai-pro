/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者历史耗材请求列表行模型。
 */
@Getter
@Setter
public class DeviceRequestHistoryModel {
    /** 主键。 */
    private Long id;
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 状态枚举编码。 */
    private String statusEnum;
}
