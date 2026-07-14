/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院就诊药品发药单分页请求（IF-IPP-03）。
 *
 * <p>分页与通用检索字段见 {@link AbstractHealthPageRequest}（B.10.11）；
 * 与 IF-OPP-03 一致：{@code locationId} 未传时不按发放库房收窄，工作站选患者后通常仅传 {@code encounterId} 与状态子集。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院就诊药品发药单分页请求")
public class PageEncounterMedicationDispenseSlipsRequest extends AbstractHealthPageRequest {

    /** 住院就诊主键；须为在院且存在待发/在途发放事实的 encounter。 */
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 发药库房。 */
    @Schema(description = "发药库房（库位）主键；null 表示不按库房过滤")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 仅中药。 */
    @Schema(description = "仅中药")
    private Boolean tcmOnly;

    /** 仅西药。 */
    @Schema(description = "仅西药")
    private Boolean westernOnly;

    /** 发放状态 code 集合。 */
    @Schema(description = "发放状态 code 集合")
    private List<String> dispenseStatusEnumIn;
}
