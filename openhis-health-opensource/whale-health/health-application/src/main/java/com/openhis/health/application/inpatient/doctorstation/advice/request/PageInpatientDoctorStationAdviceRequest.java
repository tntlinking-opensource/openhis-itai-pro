/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-22：住院医生站医嘱分页查询请求（当前医嘱 / 历史医嘱 Tab；全类型医嘱统一列表）。
 *
 * <p>读口径与 IF-IP-DOR-10 一致（药品 + 诊疗，不含耗材）；{@code adviceScopeTabCode} 仅区分当前就诊与患者历史。
 * 是否按处方形式展示由前端自行处理。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院医生站-医嘱分页查询请求")
public class PageInpatientDoctorStationAdviceRequest extends AbstractHealthPageRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键（与住院快照一致性门控；历史 Tab 查询锚点）。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者主键。 */
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /**
     * 医嘱查询范围 Tab：{@code CURRENT_ADVICE}（默认）当前就诊；{@code HISTORY} 患者历史（排除当前就诊）。
     *
     * @see com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab
     */
    @Schema(description = "医嘱查询范围 Tab：CURRENT_ADVICE=当前医嘱（默认），HISTORY=历史医嘱")
    private String adviceScopeTabCode;

    /** 治疗类型筛选：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION；空表示全部。 */
    @Schema(description = "治疗类型筛选：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION；空表示全部")
    private String therapyTypeCode;

    /**
     * 开单科室组织主键（{@code adm_organization.id}）；{@code null} 表示不按科室过滤。
     */
    @Schema(description = "开单科室组织主键（adm_organization.id）；空表示不按科室过滤")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDepartmentId;

    @Schema(description = "医嘱发布态 RequestStatus code 集合")
    /** 医嘱发布态 RequestStatus code 集合。 */
    private List<String> requestStatusEnumIn;
}
