/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.query;

import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊已签发处方药品行分页入参（AS-OP-PHA-005 · IF-OPQ-02）。
 *
 * <p>跨聚合条件（收费状态、发药状态、AMB 就诊、费用性质、含输液目录主键、药品类型目录主键、点评有效方号）由应用层 Loader 预解析后写入
 * {@code resolved*} 字段；本聚合 QueryService <strong>仅</strong>对本表做 IN / 同根方号收窄（B.6.4）。</p>
 */
@Getter
@Setter
public class OutpatientSignedPrescriptionDrugLinePageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 处方类别码（术语 355 {@code rx_type_code}）；空表示不限。
     */
    private String rxTypeCode;

    /**
     * 含输液筛：{@link OutpatientPharmacyPrescriptionQueryConstants#INFUSION_FILTER_ALL} /
     * {@code YES} / {@code NO}；空视为 {@code ALL}。
     */
    private String infusionFilterCode;

    /**
     * 注射类目录药品主键（{@code med_medication_definition.inject_flag=true}）；
     * 含输液 YES/NO 时由应用层写入；本聚合据此同根解析处方号集合。
     */
    private List<Long> resolvedMedicationIdsForInjectFlag;

    /**
     * 收费状态预解析的药品申请主键；非 {@code null} 时 {@code id IN}；空列表表示无命中须拒查。
     */
    private List<Long> resolvedMedicationRequestIdsForChargeStatus;

    /**
     * 发药状态预解析的药品申请主键；非 {@code null} 时 {@code id IN}；空列表表示无命中须拒查。
     */
    private List<Long> resolvedMedicationRequestIdsForDispenseStatus;

    /**
     * 费用性质分类预解析的合同主键；非 {@code null} 时 {@code contract_id IN}；空列表表示无命中须拒查。
     */
    private List<Long> resolvedContractIdsForCategory;

    /**
     * 医保类别（{@code med_type}）预解析的药品申请主键；非 {@code null} 时 {@code id IN}；空列表表示无命中须拒查。
     */
    private List<Long> resolvedMedicationRequestIdsForMedType;

    /**
     * 就诊场景/患者关键字预解析主键；非 {@code null} 时 {@code encounter_id IN}；空列表表示无命中须拒查。
     * <p>门诊 AMB 收窄须由应用层写入本字段，<strong>禁止</strong>本聚合 EXISTS {@code adm_encounter}（B.6.4）。</p>
     */
    private List<Long> resolvedEncounterIds;

    /**
     * 处方号精确匹配（存库 {@code prescription_no}）；空表示不限。
     * <p>供详情整方重载：与其它筛 AND，仍须已签发口径与开立时间窗。</p>
     */
    private String prescriptionNo;

    /**
     * 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}）；空表示不限。
     * <p>本聚合等值过滤，无需他聚合预解析（**BR-MRQ-029**）。</p>
     */
    private Long orderingDepartmentId;

    /**
     * 药品类型（药典 {@code medication_category_code} / 目录 {@code category_code}）预解析的药品定义主键；
     * 非 {@code null} 时按方级展开（任一行命中则整方明细入选）；空列表拒查。
     */
    private List<Long> resolvedMedicationIdsForCategory;

    /**
     * 点评态筛预解析的有效点评处方号；配合 {@link #reviewPrescriptionFilterMode}；
     * {@code null} 且模式空表示不按点评收窄。
     */
    private List<String> resolvedPrescriptionNosForReview;

    /**
     * 点评方号筛模式：
     * {@link OutpatientPharmacyPrescriptionQueryConstants#REVIEW_PRESCRIPTION_FILTER_INCLUDE} /
     * {@link OutpatientPharmacyPrescriptionQueryConstants#REVIEW_PRESCRIPTION_FILTER_EXCLUDE}；
     * 空表示不筛点评。
     */
    private String reviewPrescriptionFilterMode;

    /**
     * 含输液方号预解析结果（应用层一次解析后写入，避免 Spec 每次 toPredicate 重复扫 MR）。
     * <p>非 null 时 {@code applyInfusionPrescriptionFilter} 直接使用，不再二次查询。</p>
     */
    private List<String> resolvedPrescriptionNosForInfusionFilter;

    /**
     * 药品类型方号预解析结果（一次解析，Spec 复用）。
     */
    private List<String> resolvedPrescriptionNosForCategoryFilter;
}
