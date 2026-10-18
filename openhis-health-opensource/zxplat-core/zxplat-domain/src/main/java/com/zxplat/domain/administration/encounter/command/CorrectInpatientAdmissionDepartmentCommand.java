/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更正住院入院科室（AS-IP-FIN-001 IF-IP-REG-08）。
 *
 * <p>改的是入院时选错的科室、病区和已入科床位，不进入转科过渡态，不收口责任医护。
 * 床运营态仍由应用层 {@code occupyBed}/{@code releaseBed} 编排。</p>
 */
@Getter
@Setter
public class CorrectInpatientAdmissionDepartmentCommand {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 目标住院科室主键，写入就诊 {@code organization_id}。 */
    private Long departmentId;

    /** 目标病区 Location 主键（{@code form_enum=WARD}）。 */
    private Long wardId;

    /**
     * 目标病房 Location 主键。已入科且床位变化时必填；待入科为空。
     */
    private Long houseId;

    /**
     * 目标床位 Location 主键。已入科必填；与当前活动床相同则只改科室/病区、不动床轨迹。
     */
    private Long bedId;
}
