/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.nursingrecord.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 护理记录纸质单抬头 DTO（IF-IP-NRC-02，取自 Encounter 读模型）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理记录纸质单抬头")
public class NursingRecordPaperHeaderDto {

    /** 入院时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "入院时间")
    private LocalDateTime admitAt;

    /** 年龄展示。 */
    @Schema(description = "年龄")
    private String age;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedNo;

    /** 出院时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "出院时间")
    private LocalDateTime dischargeAt;

    /** 性别展示。 */
    @Schema(description = "性别")
    private String gender;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String inpatientNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String name;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;
}
