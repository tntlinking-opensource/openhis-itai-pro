/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 排班台初始化请求（2.1）。
 */
@Getter
@Setter
public class LoadScheduleFormInitRequest {

    /** 科室主键；可空表示加载全院医师/诊室前不过滤科室。 */
    private Long organizationId;

    /** 医师主键；非空时号源类型下拉收窄为该医师在用户管理中配置的可接诊号源。 */
    private Long practitionerId;
}
