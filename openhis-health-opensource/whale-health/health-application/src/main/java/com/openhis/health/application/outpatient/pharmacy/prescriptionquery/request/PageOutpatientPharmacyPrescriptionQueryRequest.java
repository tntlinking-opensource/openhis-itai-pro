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
     * 患者关键字（可选）：与门诊队列同族，模糊匹配姓名/院内编码/身份证/拼音/五笔；
     * 重名时可用身份证精确定位（领域侧先精确后模糊，B.2.1）。
     */
    @Schema(description = "患者关键字（姓名/身份证/门诊号等）")
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

    /**
     * 点评状态筛：{@code PENDING}（已发药且待点评）/ {@code REVIEWED}（已点评）；空表示不限。
     * <p>与 {@link #reasonableFlag} 组合：待点评且传是否合理 → 空结果；仅传是否合理视为已点评收窄。
     * PENDING 另叠加发放 COMPLETED，排除未发药等不可评方。</p>
     */
    @Schema(description = "点评状态 PENDING=已发药待点评 / REVIEWED；空=不限")
    private String reviewStatusEnum;

    /**
     * 是否合理筛；空表示不限。单独传时按已点评且该结论收窄。
     */
    @Schema(description = "是否合理；空=不限")
    private Boolean reasonableFlag;

    /**
     * 药品类型 code（药典术语 {@code medication_category_code}，目录 {@code category_code}）；空表示不限。
     * <p>方级：任一行命中该类型则整方明细入选（与含输液 YES 同构）。</p>
     */
    @Schema(description = "药品类型（药典 category_code）；空=不限")
    private String medicationCategoryCode;
}
