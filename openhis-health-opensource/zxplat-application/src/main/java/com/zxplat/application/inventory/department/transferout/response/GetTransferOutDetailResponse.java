/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.transferout.response;

import com.zxplat.application.inventory.department.transferout.dto.TransferOutDetailLineDto;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室调出单详情响应（IF-DEPT-TR-03）。
 *
 * <p>头字段取自同号多行写模型的首行约定列；类型与状态字段为持久化 {@code code}。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室调出单详情 IF-DEPT-TR-03")
public class GetTransferOutDetailResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 类型 DEPT_TRANSFER_OUT。 */
    @Schema(description = "类型 DEPT_TRANSFER_OUT")
    private String typeEnum;

    /** 状态。 */
    @Schema(description = "状态")
    private String statusEnum;

    /** 源位置主键。 */
    @Schema(description = "源位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 源位置类型。 */
    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    /** 供应类别。 */
    @Schema(description = "供应类别")
    private String categoryEnum;

    /** 关联上游单号。 */
    @Schema(description = "关联上游单号")
    private String originalBusNo;

    /** 经手人医务人员主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 经手人姓名（详情回显下拉用）。 */
    @Schema(description = "经手人姓名")
    private String practitionerName;


    /** 明细行。 */
    @Schema(description = "明细行")
    private List<TransferOutDetailLineDto> lines;
}
