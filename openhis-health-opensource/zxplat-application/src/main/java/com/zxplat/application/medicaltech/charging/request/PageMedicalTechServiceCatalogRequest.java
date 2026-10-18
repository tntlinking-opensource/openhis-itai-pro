/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-MT-CHG-06：分页查询医技诊疗选品（执行科室=登录 TECH）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询医技诊疗选品")
public class PageMedicalTechServiceCatalogRequest extends AbstractHealthPageRequest {

    /** 住院就诊主键。 */
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 可选诊疗类目 code（关键字见父类 AbstractPageQuery）。 */
    @Schema(description = "诊疗类目 code")
    private String categoryCode;
}
