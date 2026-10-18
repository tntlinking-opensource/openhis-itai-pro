/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.response;

import com.zxplat.application.outpatient.nursestation.treatment.dto.NetRemainingProcedureRecordItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 净剩可取消执行记录列表响应（IF-NUR-03B）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "净剩可取消执行记录列表响应")
public class ListNetRemainingProcedureRecordsForNurseStationResponse {

    /**
     * 净剩 {@code COMPLETED} 行（已对冲的 COMPLETED/CANCEL 均不出现）。
     */
    @Schema(description = "净剩可取消执行记录列表")
    private List<NetRemainingProcedureRecordItemDto> records;
}
