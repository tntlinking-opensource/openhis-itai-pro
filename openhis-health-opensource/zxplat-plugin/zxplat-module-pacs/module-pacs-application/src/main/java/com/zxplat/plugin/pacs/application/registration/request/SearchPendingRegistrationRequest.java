/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.request;

import com.zxplat.framework.core.application.paging.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待登记列表请求（IF-PACS-REG-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "检查登记-待登记列表请求")
public class SearchPendingRegistrationRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "登记/执行科室")
    private Long organizationId;

    @Schema(description = "模态码")
    private String modalityCode;
}
