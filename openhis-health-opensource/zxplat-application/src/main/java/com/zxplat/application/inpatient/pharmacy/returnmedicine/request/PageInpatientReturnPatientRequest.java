/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.request;

import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询住院退药患者队列请求（IF-IPR-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/** 分页查询住院退药患者队列请求。 */
@Schema(description = "分页查询住院退药患者队列请求")
public class PageInpatientReturnPatientRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 病区库位主键；未传时不拼病区谓词。 */
    @Schema(description = "病区过滤（ward locationId）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 队列页签：PENDING=有待退；COMPLETED=仅已退且无待退；默认 PENDING。 */
    @Schema(description = "队列页签：PENDING=有待退；COMPLETED=仅已退且无待退")
    private String queueTab;
}
