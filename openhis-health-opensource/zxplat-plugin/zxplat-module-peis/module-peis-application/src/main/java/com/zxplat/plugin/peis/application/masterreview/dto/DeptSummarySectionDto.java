/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 按服务申请分组的分科汇总段。
 */
@Data
public class DeptSummarySectionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 项目展示名。 */
    private String activityDisplay;

    /** 执行状态（checked/abandoned/deferred 等）。 */
    private String execStatusEnum;

    /** 该 SR 下观察行（含分科结论）。 */
    private List<MasterObservationRowDto> observations = new ArrayList<>();
}
