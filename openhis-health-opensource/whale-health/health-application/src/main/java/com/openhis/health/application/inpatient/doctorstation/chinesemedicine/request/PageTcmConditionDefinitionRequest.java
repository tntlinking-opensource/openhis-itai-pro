/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-DOR-31：分页检索中医病名/证候目录请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页检索中医病名或证候目录请求")
public class PageTcmConditionDefinitionRequest extends AbstractHealthPageRequest {

    /** 目录角色 DISEASE/SYNDROME。 */
    @NotBlank(message = "catalogRole 不能为空")
    @Schema(description = "目录角色 DISEASE/SYNDROME", requiredMode = Schema.RequiredMode.REQUIRED)
    private String catalogRole;
}
