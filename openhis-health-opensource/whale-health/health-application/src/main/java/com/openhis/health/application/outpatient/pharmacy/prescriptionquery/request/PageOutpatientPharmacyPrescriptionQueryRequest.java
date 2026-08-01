/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionquery.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询门诊已签发处方药品明细请求（IF-OPQ-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询门诊处方明细请求", requiredProperties = {"openedDateFrom", "openedDateTo", "pageNo", "pageSize"})
public class PageOutpatientPharmacyPrescriptionQueryRequest extends AbstractHealthPageRequest {

    /**
     * 开立时间起（{@code yyyy-MM-dd}，闭区间日初，映射 {@code req_opened_time}，空回落 {@code req_authored_time}）。
     */
    @Schema(description = "开立时间起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String openedDateFrom;

    /**
     * 开立时间止（{@code yyyy-MM-dd}，闭区间日末，映射 {@code req_opened_time}，空回落 {@code req_authored_time}）。
     */
    @Schema(description = "开立时间止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String openedDateTo;

    /**
     * 收费状态筛（{@code ChargeItemStatus} 持久化 code）；空表示不限。
     */
    @Schema(description = "收费状态 code；空=不限")
    private String chargeStatusEnum;

    /**
     * 发药状态筛（{@code DispenseStatus} 持久化 code）；空表示不限。
     */
    @Schema(description = "发药状态 code；空=不限")
    private String dispenseStatusEnum;

    /**
     * 含输液筛：{@code ALL}/{@code YES}/{@code NO}；空视为 {@code ALL}。
     */
    @Schema(description = "含输液筛 ALL/YES/NO；默认 ALL")
    private String infusionFilterCode;

    /**
     * 处方类别 code（术语 355 {@code rx_type_code}）；空表示不限。
     */
    @Schema(description = "处方类别 code（术语 355）")
    private String rxTypeCode;

    /**
     * 医保类别 code（{@code YbMedType}）；空表示不限。
     */
    @Schema(description = "医保类别 code（YbMedType）")
    private String medTypeCode;

    /**
     * 费用性质分类 code（{@code ContractCategory}）；空表示不限。
     */
    @Schema(description = "费用性质分类 code")
    private String contractCategoryCode;

    /**
     * 患者姓名等模糊关键字；与门诊队列关键字同族。
     */
    @Schema(description = "患者关键字")
    private String patientKeyword;

    /**
     * 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}）；空表示不限。
     */
    @Schema(description = "开单科室组织主键；空=不限")
    private Long orderingDepartmentId;

    /**
     * 处方号精确匹配（存库值 {@code CP…}/{@code TCP…}）；用于详情抽屉整方重载；空表示不限。
     */
    @Schema(description = "处方号精确匹配；空=不限")
    private String prescriptionNo;
}
