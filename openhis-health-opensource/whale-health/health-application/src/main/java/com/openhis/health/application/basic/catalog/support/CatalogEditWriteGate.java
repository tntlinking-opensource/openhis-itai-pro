/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.catalog.support;

import com.openhis.health.application.basic.devicecatalog.request.UpdateDeviceCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.UpdateMedicationCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.UpdateServiceCatalogRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.share.constants.CatalogEditabilityConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 三大目录写侧 immutable 字段门禁：存在业务引用时禁止修改名称/分类/单位/拆零倍数等关键字段。
 *
 * <p>入库（{@code LOCK_INVENTORY}）与开立（{@code LOCK_CLINICAL}）均锁名称/分类/业务单位/拆零；
 * 药品性质/规格/剂型/剂量单位与倍数仅在开立后锁定。锁定态仍允许改拆分属性、医保等级、病案结算类型
 * （目录变更后病案首页须点「重新统计」才按新类型覆盖费用快照）。
 * 药品/耗材编辑路径不维护定价行（见各 AppService {@code edit*}），请求侧通常不传参考价；
 * 门禁不得把「未传价格」误判为改价。诊疗编辑在非锁定态仍可改维护态价格，故保留价格比对。</p>
 */
@Component
public class CatalogEditWriteGate {

    /**
     * 写侧断言：药品目录更新不得越权修改已锁定字段。
     *
     * <p>不比对采购/销售参考价：编辑接口不落价，前端更新时也不传价格字段。
     * 不比对拆分属性、医保等级、病案结算类型：发生业务后仍允许改。
     * 临床与规格段（药品性质/规格/剂型/剂量）仅 {@code LOCK_CLINICAL} 时比对。</p>
     *
     * @param existing    库内聚合快照
     * @param pricing     实例维度定价行（财务/医保类别仍从此快照读取）
     * @param request     更新请求
     * @param editability 服务端解析的业务码
     */
    public void assertMedicationCatalogUpdateAllowed(
        MedicationDefinition existing,
        List<ChargeItemDefinition> pricing,
        UpdateMedicationCatalogRequest request,
        String editability) {
        if (existing == null || request == null) {
            return;
        }
        if (!CatalogEditabilityConstants.isLocked(editability)) {
            return;
        }
        CatalogPricingSnapshot priceSnapshot = CatalogPricingSnapshot.fromChargeLines(pricing);
        // 入库或开立：名称/分类/业务单位/拆零倍数不可改（病案结算类型不在 immutable 集合）
        this.assertStringUnchanged("通用名称", existing.getName(), request.getName());
        this.assertStringUnchanged("商品名称", existing.getMerchandiseName(), request.getMerchandiseName());
        this.assertStringUnchanged("业务编码", existing.getBusNo(), request.getBusNo());
        this.assertStringUnchanged("药品分类", existing.getCategoryCode(), request.getCategoryCode());
        this.assertStringUnchanged("财务类别", priceSnapshot.itemTypeCode, request.getTypeCode());
        this.assertStringUnchanged("医保类别", priceSnapshot.ybType, request.getYbType());
        this.assertStringUnchanged("采购单位", existing.getPurchaseUnitCode(), request.getPurchaseUnitCode());
        this.assertStringUnchanged("拆零单位", existing.getSplitUnitCode(), request.getSplitUnitCode());
        this.assertStringUnchanged("销售单位", existing.getPackageUnitCode(), request.getPackageUnitCode());
        this.assertStringUnchanged("库存单位", existing.getStockUnitCode(), request.getStockUnitCode());
        this.assertDecimalUnchanged("采购→拆零倍数", existing.getPurchaseUnitRatio(), request.getPurchaseUnitRatio());
        this.assertDecimalUnchanged("库存→拆零倍数", existing.getStockUnitRatio(), request.getStockUnitRatio());
        this.assertDecimalUnchanged("销售→拆零倍数", existing.getPackageUnitRatio(), request.getPackageUnitRatio());
        // 仅医生开立后锁定临床与规格段（入库未开立仍可改）
        if (!CatalogEditabilityConstants.LOCK_CLINICAL.equalsIgnoreCase(editability.trim())) {
            return;
        }
        this.assertStringUnchanged(
            "药品性质", existing.getPharmacologyCategoryCode(), request.getPharmacologyCategoryCode());
        this.assertStringUnchanged("规格", existing.getTotalVolume(), request.getTotalVolume());
        this.assertIntegerUnchanged("剂型", existing.getDoseFrom(), request.getDoseFrom());
        this.assertStringUnchanged("剂量单位", existing.getDoseUnitCode(), request.getDoseUnitCode());
        this.assertDecimalUnchanged("剂量→拆零倍数", existing.getDoseUnitRatio(), request.getDoseUnitRatio());
    }

    /**
     * 写侧断言：器材目录更新不得越权修改已锁定字段。
     *
     * <p>不比对采购/销售参考价：编辑接口不落价，前端更新时也不传价格字段。
     * 不比对医保等级、病案结算类型：发生业务后仍允许改；{@code chargeableFlag} 亦不在 immutable 集合。</p>
     *
     * @param existing    库内聚合快照
     * @param pricing     实例维度定价行（财务/医保类别仍从此快照读取）
     * @param request     更新请求
     * @param editability 服务端解析的业务码
     */
    public void assertDeviceCatalogUpdateAllowed(
        DeviceDefinition existing,
        List<ChargeItemDefinition> pricing,
        UpdateDeviceCatalogRequest request,
        String editability) {
        if (existing == null || request == null) {
            return;
        }
        if (!CatalogEditabilityConstants.isLocked(editability)) {
            return;
        }
        CatalogPricingSnapshot priceSnapshot = CatalogPricingSnapshot.fromChargeLines(pricing);
        String requestPurchaseUnit = this.resolveDevicePurchaseUnitCode(request);
        String requestSplitUnit = this.resolveDeviceSplitUnitCode(request);
        // 名称/分类/单位/拆零：发生业务后不可改（病案结算类型不比对）
        this.assertStringUnchanged("耗材名称", existing.getName(), request.getName());
        this.assertStringUnchanged("商品名称", existing.getMerchandiseName(), request.getMerchandiseName());
        this.assertStringUnchanged("业务编码", existing.getBusNo(), request.getBusNo());
        this.assertStringUnchanged("耗材分类", existing.getCategoryCode(), request.getCategoryCode());
        this.assertStringUnchanged("耗材类型", existing.getTypeCode(), request.getTypeCode());
        this.assertStringUnchanged("财务类别", priceSnapshot.itemTypeCode, request.getItemTypeCode());
        this.assertStringUnchanged("医保类别", priceSnapshot.ybType, request.getYbType());
        this.assertStringUnchanged("规格", existing.getTotalVolume(), request.getTotalVolume());
        this.assertStringUnchanged("采购单位", existing.getPurchaseUnitCode(), requestPurchaseUnit);
        this.assertStringUnchanged("拆零单位", existing.getSplitUnitCode(), requestSplitUnit);
        this.assertStringUnchanged("包装单位", existing.getPackageUnitCode(), request.getPackageUnitCode());
        this.assertStringUnchanged("库存单位", existing.getStockUnitCode(), request.getStockUnitCode());
        this.assertStringUnchanged("剂量单位", existing.getDoseUnitCode(), request.getDoseUnitCode());
        this.assertDecimalUnchanged("剂量→拆零倍数", existing.getDoseUnitRatio(), request.getDoseUnitRatio());
        this.assertDecimalUnchanged("采购→拆零倍数", existing.getPurchaseUnitRatio(), request.getPurchaseUnitRatio());
        this.assertDecimalUnchanged("库存→拆零倍数", existing.getStockUnitRatio(), request.getStockUnitRatio());
        this.assertDecimalUnchanged("包装→拆零倍数", existing.getPackageUnitRatio(), request.getPackageUnitRatio());
    }

    /**
     * 写侧断言：诊疗目录更新不得越权修改已锁定字段。
     *
     * <p>不比对医保等级、病案结算类型：发生业务后仍允许改。</p>
     *
     * @param existing    库内聚合快照
     * @param pricing     实例维度定价行
     * @param request     更新请求
     * @param editability 服务端解析的业务码
     */
    public void assertServiceCatalogUpdateAllowed(
        ActivityDefinition existing,
        List<ChargeItemDefinition> pricing,
        UpdateServiceCatalogRequest request,
        String editability) {
        if (existing == null || request == null) {
            return;
        }
        if (!CatalogEditabilityConstants.isLocked(editability)) {
            return;
        }
        CatalogPricingSnapshot priceSnapshot = CatalogPricingSnapshot.fromChargeLines(pricing);
        String requestTypeEnum;
        // 请求未传 typeEnum：按 null 比对，避免 NPE
        if (request.getTypeEnum() == null) {
            requestTypeEnum = null;
        } else {
            requestTypeEnum = String.valueOf(request.getTypeEnum());
        }
        // 名称/分类/单位/售价：发生业务后不可改；医保等级与病案结算类型不比对
        this.assertStringUnchanged("诊疗名称", existing.getName(), request.getName());
        this.assertStringUnchanged("业务编码", existing.getBusNo(), request.getBusNo());
        this.assertStringUnchanged("目录分类", existing.getCategoryCode(), request.getCategoryCode());
        this.assertStringUnchanged("诊疗类型", existing.getTypeEnum(), requestTypeEnum);
        this.assertStringUnchanged("销售/计价单位", existing.getPackageUnitCode(), request.getPackageUnitCode());
        this.assertStringUnchanged("财务类别", priceSnapshot.itemTypeCode, request.getItemTypeCode());
        this.assertStringUnchanged("医保类别", priceSnapshot.ybType, request.getYbType());
        // 锁定态跳过 replacePriceLines；未传价格视为不改价，勿把 null 误判为改价
        // 诊疗目录无进价，仅校验售价（SALE）
        this.assertDecimalUnchangedIfPresent("售价", priceSnapshot.retailPrice, request.getRetailPrice());
    }

    /**
     * 耗材目录修订：解析采购单位（仅读 {@code purchaseUnitCode}，无历史别名回落）。
     *
     * @param request 修订请求
     * @return 采购单位 code
     */
    private String resolveDevicePurchaseUnitCode(UpdateDeviceCatalogRequest request) {
        return request.getPurchaseUnitCode();
    }

    /**
     * 耗材目录修订：解析拆零单位（仅读 {@code splitUnitCode}，无历史别名回落）。
     *
     * @param request 修订请求
     * @return 拆零单位 code
     */
    private String resolveDeviceSplitUnitCode(UpdateDeviceCatalogRequest request) {
        return request.getSplitUnitCode();
    }

    /**
     * 目录写 Guard：字符串字段变更则拒绝（trim 后比较）。
     *
     * @param fieldLabel 字段中文名（异常文案）
     * @param before     变更前
     * @param after      变更后
     * @throws ValidationException 值发生变化时
     */
    private void assertStringUnchanged(String fieldLabel, String before, String after) {
        if (Objects.equals(StringUtils.trimToNull(before), StringUtils.trimToNull(after))) {
            return;
        }
        throw new ValidationException("更新目录失败：" + fieldLabel + "已发生业务引用，不允许修改");
    }

    /**
     * 目录写 Guard：整型字段变更则拒绝。
     *
     * @param fieldLabel 字段中文名（异常文案）
     * @param before     变更前
     * @param after      变更后
     * @throws ValidationException 值发生变化时
     */
    private void assertIntegerUnchanged(String fieldLabel, Integer before, Integer after) {
        if (Objects.equals(before, after)) {
            return;
        }
        throw new ValidationException("更新目录失败：" + fieldLabel + "已发生业务引用，不允许修改");
    }

    /**
     * 目录写 Guard：金额字段变更则拒绝（compareTo 语义）。
     *
     * @param fieldLabel 字段中文名（异常文案）
     * @param before     变更前
     * @param after      变更后
     * @throws ValidationException 值发生变化时
     */
    private void assertDecimalUnchanged(String fieldLabel, BigDecimal before, BigDecimal after) {
        if (this.decimalEquals(before, after)) {
            return;
        }
        throw new ValidationException("更新目录失败：" + fieldLabel + "已发生业务引用，不允许修改");
    }

    /**
     * 目录写 Guard：请求未带金额时跳过（视为不改价）；有值则与库内比对。
     *
     * @param fieldLabel 字段中文名（异常文案）
     * @param before     变更前
     * @param after      变更后；{@code null} 表示未传
     * @throws ValidationException 请求带了金额且与库内不一致时
     */
    private void assertDecimalUnchangedIfPresent(String fieldLabel, BigDecimal before, BigDecimal after) {
        if (after == null) {
            return;
        }
        this.assertDecimalUnchanged(fieldLabel, before, after);
    }

    /**
     * BigDecimal 相等比较（双 null 视为相等）。
     *
     * @param before 变更前
     * @param after  变更后
     * @return 数值相等为 true
     */
    private boolean decimalEquals(BigDecimal before, BigDecimal after) {
        if (before == null && after == null) {
            return true;
        }
        if (before == null || after == null) {
            return false;
        }
        return before.compareTo(after) == 0;
    }

    /**
     * 收费定价行快照：供目录写侧 immutable 字段比对（维护态 PURCHASE/SALE/LIMIT）。
     */
    private static final class CatalogPricingSnapshot {

        private String itemTypeCode;
        private String ybType;
        private BigDecimal purchasePrice;
        private BigDecimal retailPrice;

        /**
         * 从维护态定价行列表提取目录写 Guard 快照（PURCHASE/SALE 与财务/医保类别）。
         *
         * @param rows 收费定价行列表
         * @return 定价快照；空列表返回空快照
         */
        private static CatalogPricingSnapshot fromChargeLines(List<ChargeItemDefinition> rows) {
            CatalogPricingSnapshot snapshot = new CatalogPricingSnapshot();
            if (rows == null || rows.isEmpty()) {
                return snapshot;
            }
            for (ChargeItemDefinition row : rows) {
                if (row == null) {
                    continue;
                }
                if (snapshot.itemTypeCode == null && row.getTypeCode() != null) {
                    snapshot.itemTypeCode = row.getTypeCode();
                }
                if (snapshot.ybType == null && row.getYbType() != null) {
                    snapshot.ybType = row.getYbType();
                }
                if (PriceType.PURCHASE.getCode().equals(row.getPriceTypeCode())) {
                    snapshot.purchasePrice = row.getPriceValue();
                    continue;
                }
                if (PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                    snapshot.retailPrice = row.getPriceValue();
                }
            }
            return snapshot;
        }
    }
}
