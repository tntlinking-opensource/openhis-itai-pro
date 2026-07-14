/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 分床/入科责任医护下拉初始化请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分床责任医护下拉初始化请求")
public class LoadAdmissionParticipantInitRequest {

    @Schema(description = "入院科室主键；有值时按科室岗位分配收窄住院医师")
    /** 入院科室主键；有值时按科室岗位分配收窄住院医师。 */
    private Long organizationId;

    @Schema(description = "入院病区位置主键；有值时按病区岗位分配收窄责任护士")
    /** 入院病区位置主键；有值时按病区岗位分配收窄责任护士。 */
    private Long wardLocationId;
}
