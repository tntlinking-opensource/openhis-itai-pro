/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材发放列表读模型。
 */
@Getter
@Setter
public class DeviceDispenseListModel {

    /** 主键。 */
    private Long id;

    /** 就诊主键。 */
    private Long encounterId;

    /** 状态枚举编码。 */
    private String statusEnum;

    /** 业务号。 */
    private String busNo;

    /** 设备申请主键。 */
    private Long deviceReqId;

    /**
     * 源发放主键（待退/已退对冲行指向原已发事实；正向行通常为空）。
     */
    private Long basedOnId;
}
