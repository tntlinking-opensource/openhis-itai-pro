/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 维护端保存组套头响应（IF-BAS-OTL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "维护端保存组套头响应")
public class SaveMaintainedOrderTemplateHeaderResponse {

    @Schema(description = "组套模板头 id")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 组套模板头 id。 */
    private Long orderTemplateId;
}
