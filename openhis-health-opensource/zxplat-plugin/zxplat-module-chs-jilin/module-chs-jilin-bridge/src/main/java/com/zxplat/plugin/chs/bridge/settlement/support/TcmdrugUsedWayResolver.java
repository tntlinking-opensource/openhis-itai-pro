/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.share.enums.TcmdrugUsedWay;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.constants.DailySettlementSharedConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * HIS 中草药处方味数 → 医保 {@code tcmdrug_used_way}（单向映射，仅 bridge）。
 *
 * <p>同一 {@code prescription_no} 下中草药去重后的味数：大于 5 传复方 {@code 1}，
 * 小于等于 5 传单方 {@code 2}。西药/中成药/诊疗不计入味数，也不填该字段。</p>
 */
public final class TcmdrugUsedWayResolver {

    /**
     * 复方门槛：味数大于本值传复方，否则单方。
     */
    public static final int COMPOUND_FLAVOR_THRESHOLD = 5;

    private TcmdrugUsedWayResolver() {
    }

    /**
     * 待判定的费用上传行（与 2204/2301 明细一一对应）。
     *
     * @param prescriptionNo 院内处方号；空则本批次无号中草药并为一组
     * @param flavorKey      一味药的去重键（药品定义 id 或医保码）
     * @param herbal         是否中草药饮片
     */
    public record HerbLine(String prescriptionNo, String flavorKey, boolean herbal) {
    }

    /**
     * 由药典分类判定是否中草药（{@code medication_category_code=4}）。
     *
     * @param medication 药品定义；可空
     * @return 中草药时 {@code true}
     */
    public static boolean isHerbalMedication(MedicationDefinition medication) {
        // 无药典行：不能当中草药计入味数
        if (medication == null) {
            return false;
        }
        return isHerbalCategory(medication.getCategoryCode());
    }

    /**
     * 药品目录分类码是否为中草药。
     *
     * @param categoryCode {@code medication_category_code}
     * @return 码值为中草药时 {@code true}
     */
    public static boolean isHerbalCategory(String categoryCode) {
        // 分类码空：西药/诊疗等本字段不参与味数
        if (isEmpty(categoryCode)) {
            return false;
        }
        return DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE.equals(categoryCode.trim());
    }

    /**
     * 组装一行味数判定输入（非中草药仍保留占位，便于与上传行对齐）。
     *
     * @param prescriptionNo       院内处方号
     * @param productDefinitionId  药品定义主键
     * @param ybNo                 国家医保编码（定义 id 空时回落）
     * @param chargeItemId         收费项主键（前两者都空时回落，避免丢行）
     * @param herbal               是否中草药
     * @return 味数判定行
     */
    public static HerbLine of(
            String prescriptionNo,
            Long productDefinitionId,
            String ybNo,
            Long chargeItemId,
            boolean herbal) {
        return new HerbLine(prescriptionNo, flavorKey(productDefinitionId, ybNo, chargeItemId), herbal);
    }

    /**
     * 按处方汇总中草药味数，得到 {@code tcmdrug_used_way} code。
     *
     * @param lines 本批上传行对应的味数输入
     * @return 处方键 → 平台 code；无中草药的处方不出现
     */
    public static Map<String, String> resolveCodeByPrescription(Collection<HerbLine> lines) {
        Map<String, String> empty = Map.of();
        // Gate：无费用行则无需判定复方/单方
        if (lines == null || lines.isEmpty()) {
            return empty;
        }
        Map<String, Set<String>> flavorsByRx = new LinkedHashMap<>();
        for (HerbLine line : lines) {
            // 非中草药：不计入味数
            if (line == null || !line.herbal()) {
                continue;
            }
            // 无去重键：无法计为独立一味
            if (isEmpty(line.flavorKey())) {
                continue;
            }
            String rxKey = rxKey(line.prescriptionNo());
            Set<String> flavors = flavorsByRx.get(rxKey);
            // 该处方尚未登记味集合
            if (flavors == null) {
                flavors = new LinkedHashSet<>();
                flavorsByRx.put(rxKey, flavors);
            }
            flavors.add(line.flavorKey());
        }
        Map<String, String> wayByRx = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : flavorsByRx.entrySet()) {
            wayByRx.put(entry.getKey(), codeForFlavorCount(entry.getValue().size()));
        }
        return wayByRx;
    }

    /**
     * 中草药行取已解析的平台码；非中草药返回 {@code null}（报文不传该字段）。
     *
     * @param line    与上传行对齐的味数输入
     * @param wayByRx {@link #resolveCodeByPrescription(Collection)} 的结果
     * @return 复方/单方 code；非中草药为 {@code null}
     */
    public static String codeOrNull(HerbLine line, Map<String, String> wayByRx) {
        // 非中草药：报文不传 tcmdrug_used_way
        if (line == null || !line.herbal()) {
            return null;
        }
        // 本批无中草药处方结果
        if (wayByRx == null || wayByRx.isEmpty()) {
            return null;
        }
        return wayByRx.get(rxKey(line.prescriptionNo()));
    }

    /**
     * 味数 → 平台码：大于门槛为复方，否则单方。
     *
     * @param distinctHerbCount 同一处方去重后的中草药味数
     * @return {@link TcmdrugUsedWay#getCode()}
     */
    public static String codeForFlavorCount(int distinctHerbCount) {
        // 味数大于 5：复方
        if (distinctHerbCount > COMPOUND_FLAVOR_THRESHOLD) {
            return TcmdrugUsedWay.COMPOUND.getCode();
        }
        return TcmdrugUsedWay.SINGLE.getCode();
    }

    /**
     * 处方分组键：空处方号并入本批无号组，避免每味各算单方。
     *
     * @param prescriptionNo 院内处方号
     * @return 非空分组键
     */
    public static String rxKey(String prescriptionNo) {
        // 无处方号：本批无号中草药并为一张方
        if (isEmpty(prescriptionNo)) {
            return "";
        }
        return prescriptionNo.trim();
    }

    /**
     * 一味药去重键：优先药品定义 id，其次医保码，再次收费项 id。
     *
     * @param productDefinitionId 药品定义主键
     * @param ybNo                国家医保编码
     * @param chargeItemId        收费项主键
     * @return 去重键；三者皆空则为空串
     */
    private static String flavorKey(Long productDefinitionId, String ybNo, Long chargeItemId) {
        // 优先药品定义 id（同一味多行只计一次）
        if (productDefinitionId != null) {
            return "id:" + productDefinitionId;
        }
        // 无定义 id：用医保编码去重
        if (isNotEmpty(ybNo)) {
            return "yb:" + ybNo.trim();
        }
        // 再缺编码：退回收费项 id，避免该行从味数中消失
        if (chargeItemId != null) {
            return "ci:" + chargeItemId;
        }
        return "";
    }
}
