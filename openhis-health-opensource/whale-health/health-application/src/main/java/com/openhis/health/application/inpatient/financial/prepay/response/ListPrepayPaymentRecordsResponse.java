/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.prepay.response;

import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayPaymentRecordDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 预交金流水列表响应（IF-IP-PREP-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金流水列表响应")
public class ListPrepayPaymentRecordsResponse {

    /** 流水列表（按收费时间倒序）。 */
    @Schema(description = "流水列表（按收费时间倒序）")
    private List<PrepayPaymentRecordDto> records;
}
