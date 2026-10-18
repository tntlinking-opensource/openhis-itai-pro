/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-23：住院暂存前绑定解析响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-暂存前绑定解析响应")
public class ResolveInpatientDoctorStationAdviceDraftBindsResponse {

    /** 绑定明细（**BR-IP-ORD-036**：住院开立恒空列表）。 */
    @Schema(description = "绑定明细（住院开立恒空）")
    private List<DoctorStationAdviceDraftBindItemDto> bindItems;
}
