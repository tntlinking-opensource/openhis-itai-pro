/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 排班台初始化响应（2.1）。
 */
@Getter
@Setter
public class LoadScheduleFormInitResponse {

    /** 科室下拉。 */
    private List<OptionDto> departments;

    /** 医师下拉。 */
    private List<OptionDto> practitioners;

    /** 诊室下拉。 */
    private List<OptionDto> clinicLocations;

    /** 号源类型下拉。 */
    private List<OptionDto> healthcareServices;

    /** 参与者类型下拉。 */
    private List<OptionDto> actorTypeOptions;
}
