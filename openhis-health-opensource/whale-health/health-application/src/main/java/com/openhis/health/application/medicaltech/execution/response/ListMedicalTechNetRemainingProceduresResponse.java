/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.response;

import com.openhis.health.application.medicaltech.execution.dto.MedicalTechProcedureRecordDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询医技净剩可取消执行记录响应（AS-MT-EXE-001 IF-MT-EXE-03B）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技净剩可取消执行记录响应")
public class ListMedicalTechNetRemainingProceduresResponse {

    /** 净剩 COMPLETED 执行记录（本站 org_id）。 */
    @Schema(description = "净剩执行记录")
    private List<MedicalTechProcedureRecordDto> records;
}
