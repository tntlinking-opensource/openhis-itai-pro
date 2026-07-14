/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.medtech.response;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 医技预约台初始化响应（7.1）。
 */
@Getter
@Setter
public class LoadMedTechFormInitResponse {

    /** 诊室/执行地点选项。 */
    private List<OptionDto> clinicLocations;

    /** 医疗服务选项。 */
    private List<OptionDto> healthcareServices;
}
