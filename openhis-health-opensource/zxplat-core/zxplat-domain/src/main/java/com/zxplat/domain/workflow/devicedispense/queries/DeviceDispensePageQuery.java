/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 发放单分页查询入参。
 */
@Getter
@Setter
public class DeviceDispensePageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 状态枚举编码。 */
    private String statusEnum;

    /** 业务号模糊匹配关键字。 */
    private String busNoKeyword;
}
