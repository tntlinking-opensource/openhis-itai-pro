/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.response;

import com.openhis.health.application.inventory.department.deptissuereturn.dto.LinkableIssueReturnLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 拉取领用明细预填响应（IF-DEP-TL-13）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "拉取领用明细预填 IF-DEP-TL-13")
public class GetLinkableIssueDetailResponse {

    /** 源领用单号 originalBusNo。 */
    @Schema(description = "源领用单号 originalBusNo")
    private String originalBusNo;

    /** 科室药房 purposeLocationId。 */
    @Schema(description = "科室药房 purposeLocationId")
    private String purposeLocationId;

    /** 科室药房名称 purposeLocationName。 */
    @Schema(description = "科室药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 领用科室名称 consumingOrganizationName。 */
    @Schema(description = "领用科室名称 consumingOrganizationName")
    private String consumingOrganizationName;

    /** 可退行 lines。 */
    @Schema(description = "可退行 lines")
    private List<LinkableIssueReturnLineDto> lines;
}
