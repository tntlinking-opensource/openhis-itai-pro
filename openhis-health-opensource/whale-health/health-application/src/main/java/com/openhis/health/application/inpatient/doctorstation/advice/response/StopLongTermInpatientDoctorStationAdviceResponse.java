/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-20：停嘱（长期医嘱）批次处理结果。
 *
 * <p>含成功标识与提示文案；失败时整批事务回滚。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期医嘱停嘱响应")
public class StopLongTermInpatientDoctorStationAdviceResponse {

    @Schema(description = "是否成功")
    /** 提示文案。 */
    private Boolean success;

    @Schema(description = "提示文案")
    private String message;
}
