/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 在院患者列表分页查询请求（IF-IP-NUR-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "在院患者列表分页查询请求", requiredProperties = {"pageNo", "pageSize"})
public class PagePatientQueueRequest extends AbstractHealthPageRequest {

    @Schema(description = "病区位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 病区位置主键。 */
    private Long wardId;

    /** 病房位置主键。 */
    @Schema(description = "病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    /** 任务筛选：pending-audit / pending-execute / processed。 */
    @Schema(description = "任务筛选：pending-audit / pending-execute / processed")
    private String nurseStationTaskFilter;

    /**
     * 列表状态筛选（已废止，忽略）。
     *
     * <p>空=默认在院；历史 {@code SETTLED_DISCHARGED} 已迁出，服务端非空拒绝。</p>
     */
    @Schema(description = "列表状态筛选（已废止，请勿传）")
    /** 列表状态筛选（已废止）。 */
    private String nurseStationStatusFilter;

    /** 待执行计数自然日。 */
    @Schema(description = "待执行计数自然日")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate nurseStationExecutionDate;
}
