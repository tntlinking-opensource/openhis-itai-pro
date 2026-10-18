/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 更新手术目录请求：在 {@link CreateProcedureCatalogRequest} 字段基础上携带主键（IF-BAS-POC-05）。
 *
 * <p>挂靠 AD：非空时改绑；{@code null} 表示本轮不修改存量挂靠（与领域
 * {@link com.zxplat.domain.clinical.proceduredefinition.command.EditProcedureDefinitionCommand} 口径一致）。</p>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新手术目录请求", requiredProperties = {"id", "sourceEnum", "name"})
public class UpdateProcedureCatalogRequest extends CreateProcedureCatalogRequest {

    /** 手术定义主键；必填。 */
    @Schema(description = "主键ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
}
