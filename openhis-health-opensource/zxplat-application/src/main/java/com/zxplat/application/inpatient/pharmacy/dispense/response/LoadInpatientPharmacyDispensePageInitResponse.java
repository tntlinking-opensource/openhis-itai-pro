/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.response;

import com.zxplat.application.inpatient.pharmacy.dispense.dto.PractitionerOptionDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 住院发药页初始化响应（IF-IPP-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药页初始化响应")
public class LoadInpatientPharmacyDispensePageInitResponse {

    /** 当前用户管辖内启用药房下拉（{@code adm_location.id}；无岗位时为空列表）。 */
    @Schema(description = "可操作药房位置选项（adm_location.id）")
    private List<OptionDto> pharmacyOrganizationOptions;

    /** 发放状态筛选项（{@link com.zxplat.domain.share.enums.DispenseStatus} 码表）。 */
    @Schema(description = "发放状态筛选项")
    private List<OptionDto> dispenseStatusFilterOptions;

    /**
     * 未发药原因选项；枚举类未落库前为空列表（与门诊发药一致），作废接口仍提交 {@code notPerformedReasonEnum} 字符串 code。
     */
    @Schema(description = "未发药原因选项")
    private List<OptionDto> notPerformedReasonOptions;

    /** 会话机构下病区候选（value=病区库位 id，供队列 Tab 收窄）。 */
    @Schema(description = "病区候选（value=病区库位 id）")
    private List<OptionDto> wardOptions;

    /** 调配药师候选分页（药师岗 + 目标药房 {@code adm_pr_assign.location_id} 收窄）。 */
    @Schema(description = "调配药师候选分页")
    private Page<PractitionerOptionDto> preparerCandidatePage;

    /** 领药人候选分页（住院护士岗；优先按病区库位收窄，无命中时回落全机构护士）。 */
    @Schema(description = "领药人候选分页（护士岗）")
    private Page<PractitionerOptionDto> receiverCandidatePage;
}
