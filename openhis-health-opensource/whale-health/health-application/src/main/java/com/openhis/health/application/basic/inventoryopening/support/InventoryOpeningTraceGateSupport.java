/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.support;

import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import com.openhis.health.application.shared.tracenumber.TraceNumberRecordOrchestrator;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

    /**
 * 库存期初追溯与行唯一闸门（AS-BAS-INV-001 · BR-BAS-INV-OPN-007/008/018）。
 *
 * <p>扫码可选：无码形成无码批；有码仅校验<strong>单内码不重复</strong>。
 * <strong>不做</strong>「码个数 = ceil(拆零/每码拆零)」硬 Gate（与进货区分；DD：期初不因追溯码卡流程）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryOpeningTraceGateSupport {

    /** 西药分类码（MedicationCategory）。 */
    public static final String CATEGORY_WESTERN = "western";
    /** 中成药分类码。 */
    public static final String CATEGORY_CHINESE_PATENT = "chinese-patent";
    /** 中草药分类码（不强制追溯）。 */
    public static final String CATEGORY_CHINESE_HERBAL = "chinese-herbal";

    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;

    /**
     * 校验可写明细：行唯一、效期、药库进价、强制码与码数、单内码不重复。
     *
     * @param lines              明细（可空）
     * @param openingTypeCode    三类 OPENING 之一
     * @param productsByKey      产品复合键 → 读模型
     * @param requireAtLeastOne  为 true 时拒绝空明细（提交/导入）
     */
    public void assertWritableLines(
        List<InventoryOpeningLineDto> lines,
        String openingTypeCode,
        Map<String, ProductDefinitionDisplayDto> productsByKey,
        boolean requireAtLeastOne
    ) {
        if (lines == null || lines.isEmpty()) {
            if (requireAtLeastOne) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_019, "请至少录入一行明细后再提交");
            }
            return;
        }
        Set<String> lineKeys = new HashSet<>();
        Set<String> allTraceNos = new HashSet<>();
        for (InventoryOpeningLineDto line : lines) {
            if (line == null) {
                continue;
            }
            this.assertLineBasics(line, openingTypeCode);
            String uniqueKey = this.buildLineUniqueKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (lineKeys.contains(uniqueKey)) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002,
                    "同单禁止重复品批：" + uniqueKey);
            }
            lineKeys.add(uniqueKey);
            // productsByKey 保留入参兼容调用方预加载；码数硬 Gate 已取消
            this.assertTraceRulesForWritableLine(line, allTraceNos);
        }
    }

    /**
     * 同意路径：品批唯一、有码单内唯一、供应商齐；<strong>不</strong>做码数硬 Gate。
     *
     * @param lines         AGREE 前 APPROVAL 行
     * @param productsByKey 产品读模型（保留兼容；本闸门不再按分类强制有码）
     */
    public void assertAgreedLinesTrace(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productsByKey
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_007, "同意明细不能为空");
        }
        Set<String> lineKeys = new HashSet<>();
        Set<String> allTraceNos = new HashSet<>();
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            String uniqueKey = this.buildLineUniqueKey(line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (lineKeys.contains(uniqueKey)) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_008, "同单品批重复");
            }
            lineKeys.add(uniqueKey);
            String traceRaw = line.getTraceNo();
            boolean hasTrace = StringUtils.isNotEmpty(traceRaw) && this.traceNumberRecordOrchestrator.hasTraceNos(traceRaw);
            // 期初允许不扫码；有码仅单内唯一，不做个数硬 Gate
            if (hasTrace) {
                this.assertTraceCodesUniqueInOrder(traceRaw, allTraceNos);
            }
            // 裁决 1A：同意前供应商须齐（行上）
            if (line.getSupplierId() == null) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "供应商必填");
            }
        }
    }

    /**
     * 是否「建议」扫码品种：药品表且分类为 western / chinese-patent；耗材/非药品表否。
     *
     * <p>仅作提示/策略参考；<strong>不</strong>据此硬拒无码期初行（DD：期初扫码可选）。
     * 药品表若产品或分类缺失则 fail-closed 抛 {@code BAS_INV_OPN_007}（无法判定分类时禁止静默当草药）。</p>
     *
     * @param itemTable 物品表
     * @param product   产品读模型
     * @return 建议扫码时 true
     */
    public boolean isTraceForced(String itemTable, ProductDefinitionDisplayDto product) {
        if (StringUtils.isEmpty(itemTable)) {
            return false;
        }
        // 耗材表不强制
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable.trim())) {
            return false;
        }
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable.trim())) {
            return false;
        }
        // 药品表必须能读到分类：加载失败时禁止「当成不强制」放行
        if (product == null || StringUtils.isEmpty(product.getCategoryCode())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_007,
                "药品分类未加载，无法校验强制追溯规则");
        }
        String cat = product.getCategoryCode().trim();
        if (CATEGORY_WESTERN.equals(cat)) {
            return true;
        }
        if (CATEGORY_CHINESE_PATENT.equals(cat)) {
            return true;
        }
        return false;
    }

    /**
     * 校验单行基础字段：品批、效期、数量、单位；药库进价与供应商硬必填。
     *
     * @param line             可写行
     * @param openingTypeCode  三类 OPENING 之一
     */
    private void assertLineBasics(InventoryOpeningLineDto line, String openingTypeCode) {
        // 品规身份
        if (StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "明细物品不能为空");
        }
        // 批号
        if (StringUtils.isEmpty(line.getLotNumber())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "批号不能为空");
        }
        // 效期（有明细行时必填）
        if (line.getEndTime() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "效期不能为空");
        }
        // 数量须为正拆零量
        if (line.getItemQuantity() == null || line.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "数量须大于 0");
        }
        // 单位
        if (StringUtils.isEmpty(line.getUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "单位不能为空");
        }
        // 药库期初进价硬必填
        if (com.openhis.health.domain.share.enums.SupplyType.CABINET_OPENING.getCode().equals(openingTypeCode)) {
            if (line.getPrice() == null) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "药库期初进价必填");
            }
        }
        // 裁决 1A：供应商硬必填（库存域不变量）
        if (line.getSupplierId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "供应商必填");
        }
    }

    /**
     * 可写行追溯规则：有码仅单内唯一；无码放行（DD：期初不因码数卡流程）。
     *
     * @param line         可写行
     * @param allTraceNos  单内已出现码集合（跨行去重）
     */
    private void assertTraceRulesForWritableLine(
        InventoryOpeningLineDto line,
        Set<String> allTraceNos
    ) {
        boolean hasTrace = StringUtils.isNotEmpty(line.getTraceNo())
            && this.traceNumberRecordOrchestrator.hasTraceNos(line.getTraceNo());
        // 无码：无码批，不拦
        if (!hasTrace) {
            return;
        }
        this.assertTraceCodesUniqueInOrder(line.getTraceNo(), allTraceNos);
    }

    /**
     * 展开追溯码串并校验单内不重复（跨行累计）。
     *
     * <p>不做码数与拆零量对齐；半扫/多扫由写侧前填分摊消化，不在此抛业务异常。</p>
     *
     * @param traceRaw     逗号串
     * @param allTraceNos  单内累计码集合
     */
    private void assertTraceCodesUniqueInOrder(String traceRaw, Set<String> allTraceNos) {
        List<String> codes = this.traceNumberRecordOrchestrator.splitTraceNos(traceRaw);
        // 空串：无码可验
        if (codes.isEmpty()) {
            return;
        }
        for (String code : codes) {
            // 单内已出现：冲突硬拒（数据完整，非「扫不够」类）
            if (allTraceNos.contains(code)) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_008, "追溯码冲突：" + code);
            }
            allTraceNos.add(code);
        }
    }

    /**
     * 组装单内品批唯一键 {@code itemTable|itemId|lotNumber}。
     *
     * @param itemTable  物品表
     * @param itemId     物品主键
     * @param lotNumber  批号
     * @return 唯一键
     */
    private String buildLineUniqueKey(String itemTable, Long itemId, String lotNumber) {
        String table = "";
        if (itemTable != null) {
            table = itemTable.trim();
        }
        String lot = "";
        if (lotNumber != null) {
            lot = lotNumber.trim();
        }
        return table + "|" + itemId + "|" + lot;
    }
}
