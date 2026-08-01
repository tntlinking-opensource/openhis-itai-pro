/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 初始化财务审批页响应（IF-APR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "初始化财务审批页响应")
public class InitFinanceApprovalResponse {

    /** 单据类型选项（全部 + 财务族供应码 + 调价）。 */
    @Schema(description = "单据类型选项")
    private List<OptionDto> typeOptions = new ArrayList<>();

    /** 审批状态选项（待审/已同意/已驳回；value 以供应码为准，调价由 Loader 映射）。 */
    @Schema(description = "审批状态选项")
    private List<OptionDto> statusOptions = new ArrayList<>();

    /** 库房选项（药库/药房/科室药房并集，管辖收窄）。 */
    @Schema(description = "库房选项")
    private List<OptionDto> locationOptions = new ArrayList<>();

    /** 供应商选项。 */
    @Schema(description = "供应商选项")
    private List<OptionDto> supplierOptions = new ArrayList<>();

    /** 品类选项（药品/耗材）。 */
    @Schema(description = "品类选项")
    private List<OptionDto> itemTableOptions = new ArrayList<>();

    /** 制单人选项（首版可空列表）。 */
    @Schema(description = "制单人选项")
    private List<OptionDto> applicantOptions = new ArrayList<>();

    /** 审批人选项（首版可空列表）。 */
    @Schema(description = "审批人选项")
    private List<OptionDto> approverOptions = new ArrayList<>();
}
