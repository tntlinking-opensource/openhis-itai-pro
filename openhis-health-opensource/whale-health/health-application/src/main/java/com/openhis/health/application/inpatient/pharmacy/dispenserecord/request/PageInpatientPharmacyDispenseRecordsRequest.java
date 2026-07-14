/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispenserecord.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页查询住院发退药记录请求（IF-IPR-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询住院发退药记录请求")
public class PageInpatientPharmacyDispenseRecordsRequest extends AbstractHealthPageRequest {

    /** 发药时间起（yyyy-MM-dd，闭区间日初）。 */
    @Schema(description = "发药时间起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateFrom;

    /** 发药时间止（yyyy-MM-dd，闭区间日末）。 */
    @Schema(description = "发药时间止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateTo;

    /** 记录类型：ALL / DISPENSE / REFUND；默认 ALL。 */
    @Schema(description = "记录类型筛选项 code")
    private String recordTypeCode;

    /** 药房库位主键。 */
    @Schema(description = "药房库位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pharmacyLocationId;

    /** 项目类型多选（medication_category_code）。 */
    @Schema(description = "项目类型 code 多选")
    private List<String> medicationCategoryCodes;

    /** 发药人 practitioner 主键。 */
    @Schema(description = "发药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenserPractitionerId;

    /** 药品名称/拼音/编码模糊。 */
    @Schema(description = "药品关键字")
    private String drugKeyword;

    /** 患者姓名/住院号/床号模糊。 */
    @Schema(description = "患者关键字")
    private String patientKeyword;
}
