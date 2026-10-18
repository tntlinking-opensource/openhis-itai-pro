/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.response;

import com.zxplat.application.outpatient.doctorstation.chinesemedicine.dto.TcmDiagnosisGroupRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** IF-DOR-30：加载中医病证组列表响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "加载中医病证组列表响应")
public class ListTcmDiagnosisGroupsResponse {

    /** 病证组列表。 */
    @Schema(description = "病证组列表")
    private List<TcmDiagnosisGroupRowDto> groups = new ArrayList<>();

    /** 组数。 */
    @Schema(description = "组数")
    private Integer totalCount;
}
