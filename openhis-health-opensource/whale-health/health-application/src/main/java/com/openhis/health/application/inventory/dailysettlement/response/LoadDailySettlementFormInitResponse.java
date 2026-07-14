/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 库房日结页初始化出参（IF-INV-SET-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房日结页初始化 IF-INV-SET-01")
public class LoadDailySettlementFormInitResponse {

    /** 管辖范围内药库、药房、科室药房下拉（busNo 携带 formEnum）。 */
    @Schema(description = "管辖范围内药库、药房、科室药房下拉（busNo 携带 formEnum）")
    private List<OptionDto> warehouseOptions;

    /** 租户备份单号降序列表。 */
    @Schema(description = "租户备份单号降序列表")
    private List<OptionDto> backupBusNoOptions;
}
