/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单条药品申请参与分方时的只读快照（由应用层装配，领域层不反查外域）。
 */
@Getter
@Setter
public class PrescriptionSplitLineContext {

    /** 药品申请主键。 */
    private Long requestId;

    /** 药品目录定义主键（同药同用法重复判定以此为准；≤5 上限按 req 行数计，禁止用药名）。 */
    private Long medicationId;

    /** 给药途径编码（用法签名组成字段）。 */
    private String methodCode;

    /** 用药频次编码（用法签名组成字段）。 */
    private String rateCode;

    /** 用药说明编码（用法签名组成字段）。 */
    private String dosageInstruction;

    /** 单次剂量（用法签名组成字段）。 */
    private BigDecimal dose;

    /** 剂量单位编码（用法签名组成字段）。 */
    private String doseUnitCode;

    /**
     * 医保医疗类别（{@code adm_encounter_diagnosis.med_type}，{@link com.openhis.health.domain.share.enums.YbMedType#getCode()}）。
     */
    private String medType;

    /**
     * 诊断集规范化键（{@code sorted(unique(parse(encounter_diagnosis_ids))).join(",")}）；
     * 同方判定用「有交集即可」（见 {@code EncounterDiagnosisIdsSupport#compatibleForSamePrescription}），
     * 不再作为硬分区 equals；空绑定时为空串。
     */
    private String diagnosisSetKey;

    /**
     * 药品性质码（目录 {@code pharmacology_category_code}，术语 {@code medicine_properties}：
     * 普通 / 麻醉 / 毒性 / 精一 / 精二等）。
     *
     * <p><b>禁止</b>写入门诊拆分属性 {@code part_attribute_enum}（{@code OrderCalcRuleEnum} 拆零规则），
     * 二者码值均含数字串 {@code 2}/{@code 4}，串用会把「整包不拆」误判为麻/精一。</p>
     */
    private String pharmacologyCategoryCode;

    /**
     * 药品目录分类码（{@code medication_category_code}）：
     * {@link com.openhis.health.domain.share.constants.DailySettlementSharedConstants#MEDICATION_CATEGORY_PATENT_CHINESE_CODE}
     * 中成药、
     * {@link com.openhis.health.domain.share.constants.DailySettlementSharedConstants#MEDICATION_CATEGORY_WESTERN_CODE}
     * 西药等；普通药分方时按此走西药/中成药轨道。
     */
    private String medicationCategoryCode;

    /** 是否中药饮片方。 */
    private Boolean tcmFlag;

    /** 输液成组号；同组必须落入同一处方。 */
    private Long groupId;

    /** 签发 FIFO 解析出的主发放库位。 */
    private Long pharmacyLocationId;

    /** 本行账单金额合计（{@code ChargeItem.totalPrice}）。 */
    private BigDecimal chargeTotalPrice;
}
