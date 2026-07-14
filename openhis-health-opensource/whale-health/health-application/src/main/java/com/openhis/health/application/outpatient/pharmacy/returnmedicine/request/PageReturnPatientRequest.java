/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询退药患者队列请求（IF-PHA-02-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/** 分页查询退药患者队列请求。 */
@Schema(description = "分页查询退药患者队列请求")
public class PageReturnPatientRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "科室过滤（门诊信息 organization_id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    /** 模糊查询关键字（姓名/就诊号/拼音/五笔/证件号片段）；写入分页关键字语义。 */
    @Schema(description = "模糊查询关键字（姓名/就诊号/拼音/五笔/证件号片段）")
    private String searchKey;
}
