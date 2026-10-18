/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-NUR-ATD-03：出院前待执行医嘱行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院前待执行医嘱行")
public class DischargePendingExecutionItemDto {

    @Schema(description = "药品或诊疗请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 药品或诊疗请求主键。 */
    private Long requestId;

    /** 请求来源表名。 */
    @Schema(description = "请求来源表名")
    private String requestTable;

    /** 长临属性 code。 */
    @Schema(description = "长临属性 code")
    private String therapyEnum;

    /** 请求状态 code。 */
    @Schema(description = "请求状态 code")
    private String statusEnum;

    /** 药品名或诊疗名。 */
    @Schema(description = "药品名或诊疗名")
    private String displayName;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime reqOpenedTime;

    /** 频次 code。 */
    @Schema(description = "频次 code")
    private String rateCode;

    /** 剂量或数量展示文本。 */
    @Schema(description = "剂量或数量展示文本")
    private String doseText;

    /** 阻塞出院原因码。 */
    @Schema(description = "阻塞出院原因码")
    private String blockingReason;

    /**
     * 长期已停嘱仍待执行的计划时刻（与出院门禁 pending 槽同源）。
     *
     * <p>仅 {@code blockingReason=LONG_PENDING_SLOTS} 时有值；一盒一点/拆零下限切批后通常至多一个锚点。
     * 其它阻塞原因回传空列表，禁止另算日频次点。</p>
     */
    @Schema(description = "长期已停嘱待执行计划时刻（与门禁 pending 同源）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private List<LocalDateTime> pendingPlanTimes;
}
