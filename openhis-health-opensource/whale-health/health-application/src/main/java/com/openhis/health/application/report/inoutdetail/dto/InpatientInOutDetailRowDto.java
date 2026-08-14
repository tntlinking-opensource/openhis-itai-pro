/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院患者入出明细报表行（IF-RPT-IOE-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者入出明细报表行")
public class InpatientInOutDetailRowDto {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 住院号。 */
    @Excel(name = "住院号", sort = 1)
    @Schema(description = "住院号")
    private String busNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 2)
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别展示。 */
    @Excel(name = "性别", sort = 3)
    @Schema(description = "性别展示")
    private String genderText;

    /** 年龄展示。 */
    @Excel(name = "年龄", sort = 4)
    @Schema(description = "年龄展示")
    private String ageText;

    /** 入院科室名称。 */
    @Excel(name = "入院科室", sort = 5)
    @Schema(description = "入院科室名称")
    private String admissionOrganizationName;

    /** 入院病区名称。 */
    @Excel(name = "入院病区", sort = 6)
    @Schema(description = "入院病区名称")
    private String admissionWardName;

    /** 当前/末次病区名称。 */
    @Excel(name = "病区", sort = 7)
    @Schema(description = "当前或末次病区")
    private String wardName;

    /** 当前/末次病房名称。 */
    @Excel(name = "病房", sort = 8)
    @Schema(description = "当前或末次病房")
    private String houseName;

    /** 当前/末次床位名称。 */
    @Excel(name = "床位", sort = 9)
    @Schema(description = "当前或末次床位")
    private String bedName;

    /** 入院日。 */
    @Excel(name = "入院时间", sort = 10)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "入院时间")
    private LocalDateTime startTime;

    /** 出院日；未出院可空。 */
    @Excel(name = "出院时间", sort = 11)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "出院时间")
    private LocalDateTime endTime;

    /**
     * 住院天数：自然日，入院日计第 1 天；有出院日截至出院日，否则截至当天。
     */
    @Excel(name = "住院天数", sort = 12)
    @Schema(description = "住院天数（入院日计第1天；未出院用当天）")
    private Integer inpatientDays;

    /** 转往科室文案；无转科可空。 */
    @Excel(name = "转往", sort = 13)
    @Schema(description = "转往科室文案")
    private String transferTargetText;

    /** 费用性质合同业务号（入院身份优先账户 → fin_contract.bus_no）。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /** 费用性质展示（入院身份：用什么入院就是什么）。 */
    @Excel(name = "费用性质", sort = 14)
    @Schema(description = "费用性质展示")
    private String feeNatureDisplay;

    /**
     * 已结费用净额；无有效净额或待重结为 null（禁止 0）。
     */
    @Excel(name = "已结费用", sort = 15)
    @Schema(description = "已结费用净额；空表示未结/待重结")
    private BigDecimal settledFeeAmount;

    /** 是否存在转科（最早入院 WARD 之后仍有其它 WARD；驱动动态列，不导出）。 */
    @Schema(description = "是否已转科")
    private boolean transferred;
}
