/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.inoutdetail.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 按入院科室加载病区选项响应（IF-RPT-IOE-01a）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按入院科室加载病区选项响应")
public class ListInpatientInOutDetailWardOptionsResponse {

    /** 该科下启用病区；科室空时为空列表。 */
    @Schema(description = "病区下拉")
    private List<OptionDto> wardOptions = new ArrayList<>();
}
