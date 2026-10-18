/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-TRC-RPT-01：追溯码查询报表页初始化出参。
 */
@Getter
@Setter
public class LoadTraceNumberReportFormInitResponse {

    /** 查询范围选项（HOSPITAL / LOCATION）。 */
    private List<OptionDto> queryScopeOptions;

    /** 在院状态选项。 */
    private List<OptionDto> inHospitalStatusOptions;

    /** 本库状态选项。 */
    private List<OptionDto> locationStockStatusOptions;

    /** 药库+药房+科室药房并集库位选项。 */
    private List<OptionDto> locationOptions;
}
