/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.response;

import com.zxplat.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** IF-DOR-34：加载中药处方列表响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "加载中药处方列表响应")
public class ListTcmRxResponse {

    /** 处方列表。 */
    @Schema(description = "处方列表")
    private List<TcmRxHeaderDto> prescriptions = new ArrayList<>();
}
