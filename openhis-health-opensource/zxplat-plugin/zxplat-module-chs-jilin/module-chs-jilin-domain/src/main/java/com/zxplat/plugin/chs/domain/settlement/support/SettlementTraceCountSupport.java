/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.zxplat.plugin.chs.domain.share.constants.ChsDetailAuditConstants;

/**
 * 2207/2304 追溯码数量汇总（DD §4.3）。
 *
 * <p>药品计入 {@code minpacunt_drug_trac_cnt}：与是否已扫无关；按收费单位与贴码单位两分支
 * {@code floor} 计完整最小包装个数。耗材计入 {@code mcs_trac_cnt}。</p>
 */
public final class SettlementTraceCountSupport {

    public static final String TABLE_MEDICATION = "med_medication_definition";
    public static final String TABLE_DEVICE = "adm_device_definition";

    private SettlementTraceCountSupport() {
    }

    /**
     * 统计药品应上传最小包装追溯码条数（DD §4.3）。
     *
     * <p>分支 A：收费单位已是贴码单位 → {@code floor(数量)}。
     * 分支 B：收费单位为拆零单位（与贴码异阶）→ {@code floor(拆零量 ÷ trace_unit_ratio)}。
     * {@code traceUnitRatio} 优先主表快照，否则药典。</p>
     *
     * @param productTableName 产品来源表名
     * @param quantity         收费数量（与 chargeUnitCode 同阶）
     * @param chargeUnitCode   收费/数量单位
     * @param traceUnitCode    贴码单位；可空
     * @param traceUnitRatio   1 贴码单位对应拆零量；分支 B 必填且 &gt; 0
     * @return 条数；非药品或无法计时 0
     */
    public static int countDrugTracUnits(
        String productTableName,
        BigDecimal quantity,
        String chargeUnitCode,
        String traceUnitCode,
        BigDecimal traceUnitRatio
    ) {
        // 非药品：不计入 minpacunt_drug_trac_cnt
        if (!isMedication(productTableName)) {
            return 0;
        }
        return countMinPackageTraceUnits(quantity, chargeUnitCode, traceUnitCode, traceUnitRatio);
    }

    /**
     * 兼容旧调用：无单位信息时按 {@code floor(|qty|)}（视为已是包装个数口径）。
     *
     * @param productTableName 产品来源表名
     * @param quantity         业务数量
     * @return 追溯码单位数；非药品返回 0
     * @deprecated 请改用带单位/比例的重载以符合 DD §4.3 分支 B
     */
    @Deprecated
    public static int countDrugTracUnits(String productTableName, BigDecimal quantity) {
        // 非药品：不计入药品追溯条数
        if (!isMedication(productTableName)) {
            return 0;
        }
        return floorAbs(quantity);
    }

    /**
     * 统计耗材追溯码单位数。
     *
     * @param productTableName 产品来源表名
     * @param quantity         业务数量
     * @return 追溯码单位数；非耗材返回 0
     */
    public static int countMcsTracUnits(String productTableName, BigDecimal quantity) {
        // 耗材表：按数量 floor 计入 mcs_trac_cnt
        if (isDevice(productTableName)) {
            return floorAbs(quantity);
        }
        return 0;
    }

    /**
     * 判断产品来源是否为药品定义表。
     *
     * @param productTableName 产品来源表名
     * @return 是否为药品
     */
    public static boolean isMedication(String productTableName) {
        return TABLE_MEDICATION.equalsIgnoreCase(trimToEmpty(productTableName));
    }

    /**
     * 判断产品来源是否为耗材定义表。
     *
     * @param productTableName 产品来源表名
     * @return 是否为耗材
     */
    public static boolean isDevice(String productTableName) {
        return TABLE_DEVICE.equalsIgnoreCase(trimToEmpty(productTableName));
    }

    /**
     * 费用上传失败提示用的目录类别。
     *
     * <p>药品、耗材按产品表区分；其余（诊疗、收费项目）归为诊疗，便于收费员定位未对码项。</p>
     *
     * @param productTableName 产品来源表名
     * @return 药品、耗材或诊疗
     */
    public static String catalogKindLabel(String productTableName) {
        // 药典行：提示写成药品
        if (isMedication(productTableName)) {
            return "药品";
        }
        // 耗材行：平台常只回截断后的目录码，须标明是耗材
        if (isDevice(productTableName)) {
            return "耗材";
        }
        return "诊疗";
    }

    /**
     * 组装平台 {@code med_list_codg}（按非高值耗材截断）。
     *
     * <p>未传高值标志时按低值口径：耗材截前
     * {@link ChsDetailAuditConstants#DEVICE_MED_LIST_CODG_MAX_LEN} 位。
     * 高值须走 {@link #toPlatformMedListCodg(String, String, Boolean)}。</p>
     *
     * @param productTableName 产品来源表名
     * @param ybNo             院内医保编码（可未 trim）
     * @return 平台 med_list_codg；空入参原样返回
     */
    public static String toPlatformMedListCodg(String productTableName, String ybNo) {
        // 两参入口：无法判定高值，按低值截断
        return toPlatformMedListCodg(productTableName, ybNo, Boolean.FALSE);
    }

    /**
     * 组装平台 {@code med_list_codg}。
     *
     * <p>药品/诊疗保持完整国家码。耗材：高值（{@code hvcmFlag=true}）传完整 {@code yb_no}
     *（国家目录按完整码对照）；低值/未标高值截前
     * {@link ChsDetailAuditConstants#DEVICE_MED_LIST_CODG_MAX_LEN} 位。
     * 完整码同时由调用方写入 {@code medins_list_codg}。</p>
     *
     * @param productTableName 产品来源表名
     * @param ybNo             院内医保编码（可未 trim）
     * @param hvcmFlag         高值耗材标志；仅耗材生效，{@code true} 不截断
     * @return 平台 med_list_codg；空入参原样返回
     */
    public static String toPlatformMedListCodg(String productTableName, String ybNo, Boolean hvcmFlag) {
        // 空码：由上游 INS-011 等门禁处理
        if (isEmpty(ybNo)) {
            return ybNo;
        }
        String trimmed = ybNo.trim();
        // 非耗材：保持完整国家码
        if (!isDevice(productTableName)) {
            return trimmed;
        }
        // 高值耗材：国家目录按完整码对照，med_list_codg 不截断
        if (Boolean.TRUE.equals(hvcmFlag)) {
            return trimmed;
        }
        // 低值未超长：整码即可
        if (trimmed.length() <= ChsDetailAuditConstants.DEVICE_MED_LIST_CODG_MAX_LEN) {
            return trimmed;
        }
        // 低值超长：仅 med_list_codg 截前 15 位
        return trimmed.substring(0, ChsDetailAuditConstants.DEVICE_MED_LIST_CODG_MAX_LEN);
    }

    /**
     * DD §4.3 两分支：贴码同阶 floor(qty)；异阶 floor(qty÷ratio)（此时 qty 视为拆零量）。
     *
     * @param quantity       收费数量
     * @param chargeUnitCode 收费单位
     * @param traceUnitCode  贴码单位
     * @param traceUnitRatio 贴码→拆零比
     * @return 最小包装个数
     */
    public static int countMinPackageTraceUnits(
        BigDecimal quantity,
        String chargeUnitCode,
        String traceUnitCode,
        BigDecimal traceUnitRatio
    ) {
        // 数量为空或非正：无可计最小包装个数
        if (quantity == null || quantity.signum() <= 0) {
            return 0;
        }
        BigDecimal absQty = quantity.abs();
        String chargeUnit = trimToEmpty(chargeUnitCode);
        String traceUnit = trimToEmpty(traceUnitCode);
        // 分支 A：收费单位已是贴码单位 → floor(数量)
        if (!isEmpty(chargeUnit) && !isEmpty(traceUnit) && chargeUnit.equals(traceUnit)) {
            return floorAbs(absQty);
        }
        // 分支 B：异阶拆零 → floor(拆零量 ÷ ratio)
        if (traceUnitRatio != null && traceUnitRatio.compareTo(BigDecimal.ZERO) > 0) {
            return absQty.divide(traceUnitRatio, 0, RoundingMode.FLOOR).intValue();
        }
        // 缺比例：无法按拆零计完整包装，计 0（禁止再 ceil）
        if (!isEmpty(chargeUnit) && !isEmpty(traceUnit) && !chargeUnit.equals(traceUnit)) {
            return 0;
        }
        // 单位皆空：保守按包装个数 floor
        return floorAbs(absQty);
    }

    /**
     * 数量向下取整为非负整数个数。
     *
     * @param quantity 业务数量
     * @return floor(|qty|)；非正为 0
     */
    private static int floorAbs(BigDecimal quantity) {
        // 数量为空或非正：计 0 个
        if (quantity == null || quantity.signum() <= 0) {
            return 0;
        }
        return quantity.abs().setScale(0, RoundingMode.FLOOR).intValue();
    }
}
