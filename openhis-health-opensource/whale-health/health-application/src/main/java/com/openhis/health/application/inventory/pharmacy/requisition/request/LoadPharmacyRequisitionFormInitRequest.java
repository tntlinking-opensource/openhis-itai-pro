/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药房请领页初始化请求（IF-PHA-QL-01）。
 *
 * <p>机构上下文由 {@code CurrentManagedOrganizationIdsResolver} 从会话解析，请求体可空。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房请领页初始化请求 IF-PHA-QL-01")
public class LoadPharmacyRequisitionFormInitRequest {

    /** 机构主键，兼容字段，服务端忽略。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键，兼容字段，服务端忽略")
    private Long organizationId;
}
