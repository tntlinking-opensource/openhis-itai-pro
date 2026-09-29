/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 手术/麻醉申请横表计划字段（AS-IP-DOR-012 IF-IP-SUR-03/11/15）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "拟手术/麻醉计划字段")
public class PlannedSurgeryFieldsDto {

    /** 拟手术时间（申请侧；非 occurrence_time）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "拟手术时间")
    private LocalDateTime procedureTime;

    /** 主手术标志。 */
    @Schema(description = "主手术标志")
    private Boolean mainProcedureFlag;

    /** 手术级别字典码。 */
    @Schema(description = "手术级别字典码")
    private String procedureLevelCode;

    /** 切口类别字典码。 */
    @Schema(description = "切口类别字典码")
    private String incisionTypeCode;

    /** 麻醉方法字典码。 */
    @Schema(description = "麻醉方法字典码")
    private String anesthesiaMethodCode;

    /** 备注。 */
    @Schema(description = "备注")
    private String note;

    /** 拟参与人员列表。 */
    @Schema(description = "拟参与人员")
    private List<PlannedPerformerDto> performers;
}
