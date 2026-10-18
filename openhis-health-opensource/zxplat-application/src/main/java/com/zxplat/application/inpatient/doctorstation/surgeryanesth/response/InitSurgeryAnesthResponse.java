/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：InitSurgeryAnesth。
 */
@Data
@Accessors(chain = true)
@Schema(description = "InitSurgeryAnesthResponse")
public class InitSurgeryAnesthResponse {

    /** 手术级别下拉。 */
    @Schema(description = "手术级别下拉")
    private List<OptionDto> procedureLevelOptions;

    /** 切口类别下拉。 */
    @Schema(description = "切口类别下拉")
    private List<OptionDto> incisionTypeOptions;

    /** 麻醉方法下拉。 */
    @Schema(description = "麻醉方法下拉")
    private List<OptionDto> anesthesiaMethodOptions;

    /** 是/否下拉。 */
    @Schema(description = "是/否下拉")
    private List<OptionDto> yesNoOptions;

    /** 频次下拉（与医嘱开立同口径，cus_frequency）。 */
    @Schema(description = "频次下拉")
    private List<OptionDto> rateOptions;

    /** 用法/给药途径下拉（药品开立）。 */
    @Schema(description = "用法下拉")
    private List<OptionDto> methodOptions;

    /**
     * 单次剂量单位下拉（术语 {@code dose_unit}）。
     *
     * <p>麻醉药品不按剂量单位换算总量，开立端可自由改选并按名称/码检索。</p>
     */
    @Schema(description = "单次剂量单位下拉（dose_unit）")
    private List<OptionDto> doseUnitOptions;

    /**
     * 麻醉医师下拉（配置麻醉室等科室上激活医师出诊岗；未配置时为空）。
     */
    @Schema(description = "麻醉医师下拉")
    private List<OptionDto> anesthetistOptions;
}
