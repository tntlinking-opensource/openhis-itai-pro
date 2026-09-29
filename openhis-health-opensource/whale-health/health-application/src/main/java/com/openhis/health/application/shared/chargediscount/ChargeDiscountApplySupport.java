/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.chargediscount;

import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.ApplySettleDiscountLinesCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.custom.chargediscountconfig.queries.DiscountProductResolveItem;
import com.openhis.health.domain.custom.chargediscountconfig.queries.model.ChargeDiscountConfigQueryModel;
import com.openhis.health.domain.custom.chargediscountconfig.service.ChargeDiscountConfigQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ChargeDiscountConfigConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.SettleDiscountSource;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收费折扣应用编排（DM-CUS-016 / AS-OP-FIN-002 / AS-IP-FIN-003）。
 *
 * <p>硬口径：{@link ChargeDiscountConfigQueryService#isDiscountFeatureEnabled()} 为 false 时，
 * 所有方法对现有金额路径<strong>零侵入</strong>（{@link ChargeDiscountBatchResult#isApplied()} = false）。</p>
 *
 * <p>落点：{@code application.shared.chargediscount}（跨 OP/IP；禁止依赖 feature Request/Response）。</p>
 */
@Component
@RequiredArgsConstructor
public class ChargeDiscountApplySupport {

    private final ChargeDiscountConfigQueryService chargeDiscountConfigQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final AccountQueryService accountQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 本院是否启用打折能力（收银/报表门控真源）。
     *
     * @return true 表示存在 ACTIVE 配置
     */
    public boolean isDiscountFeatureEnabled() {
        return chargeDiscountConfigQueryService.isDiscountFeatureEnabled();
    }

    /**
     * 门控关闭时禁止携带折扣率（BR-OPC-027 / BR-IP-STL-020）。
     *
     * @param cashierDiscountRate 款员所选率；可空
     */
    public void assertCashierRateAllowedByFeatureGate(BigDecimal cashierDiscountRate) {
        // 未传折扣率：无论门控开闭均放行（零侵入）
        if (cashierDiscountRate == null) {
            return;
        }
        // 门控关闭却传了折扣率 → 拒绝，避免前端绕过
        if (!this.isDiscountFeatureEnabled()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                "本院未启用收费折扣，不能提交折扣率");
        }
        this.assertRateInOpenInterval(cashierDiscountRate, "款员折扣率");
    }

    /**
     * 试算本批折扣；门控关闭或无有效打折时返回 {@code applied=false}。
     *
     * @param chargeItems         拟结收费行
     * @param cashierDiscountRate 款员所选率；可空（仅固定率命中时可空）
     * @return 试算结果
     */
    public ChargeDiscountBatchResult resolveBatch(
        List<ChargeItem> chargeItems,
        BigDecimal cashierDiscountRate
    ) {
        ChargeDiscountBatchResult empty = new ChargeDiscountBatchResult();
        empty.setApplied(false);
        // 门控关闭：等价于功能未加
        if (!this.isDiscountFeatureEnabled()) {
            return empty;
        }
        // 空批：无打折
        if (chargeItems == null || chargeItems.isEmpty()) {
            return empty;
        }
        // 未提交折扣率且后续发现需要款员率时再拒绝；先解析命中
        Map<String, String> categoryByProductKey = this.mapCategoryCodesForChargeItems(chargeItems);
        List<DiscountProductResolveItem> resolveItems = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || StringUtils.isEmpty(chargeItem.getProductTable())
                || chargeItem.getProductId() == null) {
                continue;
            }
            // 挂号锚点等非三大目录不参与
            if (!ChargeDiscountConfigConstants.ALLOWED_PRODUCT_TABLES.contains(chargeItem.getProductTable())) {
                continue;
            }
            DiscountProductResolveItem item = new DiscountProductResolveItem();
            item.setProductTable(chargeItem.getProductTable());
            item.setProductId(chargeItem.getProductId());
            item.setCategoryCode(categoryByProductKey.get(
                this.buildProductKey(chargeItem.getProductTable(), chargeItem.getProductId())));
            resolveItems.add(item);
        }
        Map<String, ChargeDiscountConfigQueryModel> hitByProductKey =
            chargeDiscountConfigQueryService.mapDiscountConfigsForProducts(resolveItems);

        List<ChargeItem> hitLines = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || StringUtils.isEmpty(chargeItem.getProductTable())
                || chargeItem.getProductId() == null) {
                continue;
            }
            String key = this.buildProductKey(chargeItem.getProductTable(), chargeItem.getProductId());
            if (hitByProductKey.containsKey(key)) {
                hitLines.add(chargeItem);
            }
        }
        // 无任何命中：不得提交折扣率；未传率则零侵入
        if (hitLines.isEmpty()) {
            if (cashierDiscountRate != null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "本批没有可打折项目，不能提交折扣率");
            }
            return empty;
        }

        // 纯自费才可打折（BR-OPC-028 / BR-IP-STL-021）
        this.assertAllSelfPayAccounts(hitLines);

        // 解析每行有效率并校验同批一致（BR-OPC-030）
        BigDecimal unifiedRate = null;
        String unifiedSource = null;
        Map<Long, ChargeDiscountConfigQueryModel> configByChargeItemId = new HashMap<>();
        for (ChargeItem chargeItem : hitLines) {
            String key = this.buildProductKey(chargeItem.getProductTable(), chargeItem.getProductId());
            ChargeDiscountConfigQueryModel config = hitByProductKey.get(key);
            configByChargeItemId.put(chargeItem.getId(), config);
            BigDecimal effectiveRate;
            String source;
            // 配置有固定率 → 用配置率
            if (config.getDiscountRate() != null) {
                effectiveRate = config.getDiscountRate();
                source = SettleDiscountSource.CONFIG.getCode();
            } else {
                // 款员定率行：必须有本笔所选率
                if (cashierDiscountRate == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                        "本批含款员定率项目，请先选择折扣率");
                }
                effectiveRate = cashierDiscountRate;
                source = SettleDiscountSource.CASHIER.getCode();
            }
            // 费率冲突拆单
            if (unifiedRate == null) {
                unifiedRate = effectiveRate;
                unifiedSource = source;
            } else if (unifiedRate.compareTo(effectiveRate) != 0) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "本批可打折项目有效折扣率不一致，请拆单结算");
            }
            // 同批来源以实际行为准：若同时出现 CONFIG 与 CASHIER 但费率相同，快照按各行来源写
            if (unifiedSource == null) {
                unifiedSource = source;
            }
        }

        ChargeDiscountBatchResult result = new ChargeDiscountBatchResult();
        result.setApplied(true);
        result.setEffectiveRate(unifiedRate);
        BigDecimal receivableTotal = BigDecimal.ZERO;
        BigDecimal gapTotal = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            ChargeDiscountLineResult line = new ChargeDiscountLineResult();
            line.setChargeItemId(chargeItem.getId());
            BigDecimal original = this.resolveOriginalAmount(chargeItem);
            line.setOriginalAmount(original);
            ChargeDiscountConfigQueryModel config = configByChargeItemId.get(chargeItem.getId());
            // 未命中：原价入账，不参与打折
            if (config == null) {
                BigDecimal settleOriginal = MonetaryAmountSupport.scaleSettle(original);
                line.setDiscountedReceivable(settleOriginal);
                line.setDiscountGap(BigDecimal.ZERO);
                line.setDiscounted(false);
                result.getLineByChargeItemId().put(chargeItem.getId(), line);
                receivableTotal = receivableTotal.add(settleOriginal);
                continue;
            }
            BigDecimal effectiveRate;
            String source;
            if (config.getDiscountRate() != null) {
                effectiveRate = config.getDiscountRate();
                source = SettleDiscountSource.CONFIG.getCode();
            } else {
                effectiveRate = cashierDiscountRate;
                source = SettleDiscountSource.CASHIER.getCode();
            }
            BigDecimal discounted = MonetaryAmountSupport.scaleSettle(original.multiply(effectiveRate));
            BigDecimal gap = MonetaryAmountSupport.scaleSettle(original.subtract(discounted));
            // 优惠额不为负（极端舍入）
            if (gap.compareTo(BigDecimal.ZERO) < 0) {
                gap = BigDecimal.ZERO;
            }
            line.setDiscountedReceivable(discounted);
            line.setDiscountGap(gap);
            line.setEffectiveRate(effectiveRate);
            line.setSettleDiscountSourceEnum(source);
            line.setDiscounted(true);
            result.getLineByChargeItemId().put(chargeItem.getId(), line);
            result.getDiscountedLines().add(line);
            receivableTotal = receivableTotal.add(discounted);
            gapTotal = gapTotal.add(gap);
        }
        result.setDiscountedReceivableTotal(MonetaryAmountSupport.scaleSettle(receivableTotal));
        result.setDiscountGapTotal(MonetaryAmountSupport.scaleSettle(gapTotal));
        return result;
    }

    /**
     * 将试算结果持久化为 ChargeItem 折扣快照（仅 applied 且有打折行时写库）。
     *
     * @param batchResult 试算结果
     */
    public void persistLineDiscounts(ChargeDiscountBatchResult batchResult) {
        // 未应用：不写库
        if (batchResult == null || !batchResult.isApplied()) {
            return;
        }
        if (batchResult.getDiscountedLines() == null || batchResult.getDiscountedLines().isEmpty()) {
            return;
        }
        ApplySettleDiscountLinesCommand command = new ApplySettleDiscountLinesCommand();
        List<ApplySettleDiscountLinesCommand.Item> items = new ArrayList<>();
        for (ChargeDiscountLineResult line : batchResult.getDiscountedLines()) {
            // 仅持久化实际打折行
            if (line == null || !line.isDiscounted()) {
                continue;
            }
            ApplySettleDiscountLinesCommand.Item item = new ApplySettleDiscountLinesCommand.Item();
            item.setChargeItemId(line.getChargeItemId());
            item.setDiscountedReceivable(line.getDiscountedReceivable());
            item.setDiscountGap(line.getDiscountGap());
            item.setSettleDiscountRate(line.getEffectiveRate());
            item.setSettleDiscountSourceEnum(line.getSettleDiscountSourceEnum());
            items.add(item);
        }
        command.setItems(items);
        // Cmd：领域写快照
        chargeItemDomainService.applySettleDiscountLines(command);
    }

    /**
     * 构造 DISCOUNT 旁证明细（优惠合计&gt;0 时）；不计入实付勾稽分子。
     *
     * @param batchResult 试算结果
     * @return 旁证行；无优惠时 null
     */
    public PaymentDetailItemCommand buildDiscountRecDetail(ChargeDiscountBatchResult batchResult) {
        // 未应用或无优惠：不旁证
        if (batchResult == null || !batchResult.isApplied()) {
            return null;
        }
        BigDecimal gap = batchResult.getDiscountGapTotal();
        if (gap == null || gap.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        PaymentDetailItemCommand line = new PaymentDetailItemCommand();
        line.setPayEnum(PaymentChannel.DISCOUNT.getCode());
        line.setAmount(MonetaryAmountSupport.scaleSettle(gap));
        line.setChargeAmount(MonetaryAmountSupport.scaleSettle(gap));
        return line;
    }

    /**
     * 解析行原价：优先 totalPrice，其次 baseAmount。
     *
     * @param chargeItem 收费行
     * @return 原价（非 null）
     */
    public BigDecimal resolveOriginalAmount(ChargeItem chargeItem) {
        if (chargeItem.getTotalPrice() != null) {
            return chargeItem.getTotalPrice();
        }
        if (chargeItem.getBaseAmount() != null) {
            return chargeItem.getBaseAmount();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 解析行应收：优先非空 discount_amount，否则 totalPrice（与费用明细应收口径一致）。
     *
     * @param chargeItem 收费行
     * @return 应收
     */
    public BigDecimal resolveReceivableAmount(ChargeItem chargeItem) {
        if (chargeItem.getDiscountAmount() != null) {
            return chargeItem.getDiscountAmount();
        }
        return this.resolveOriginalAmount(chargeItem);
    }

    /**
     * 合计应收（折后或原价）。
     *
     * @param chargeItems 收费行
     * @return 结算 2 位合计
     */
    public BigDecimal sumReceivableAmount(List<ChargeItem> chargeItems) {
        BigDecimal total = BigDecimal.ZERO;
        if (chargeItems == null) {
            return MonetaryAmountSupport.scaleSettle(total);
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            total = total.add(this.resolveReceivableAmount(chargeItem));
        }
        return MonetaryAmountSupport.scaleSettle(total);
    }

    /**
     * 断言命中行全部挂个人现金自费户。
     *
     * @param hitLines 命中可打折行
     */
    private void assertAllSelfPayAccounts(List<ChargeItem> hitLines) {
        for (ChargeItem chargeItem : hitLines) {
            // 缺账户无法判定自费
            if (chargeItem.getAccountId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "收费项缺少账户，不能打折");
            }
            // 非个人现金户禁止打折
            if (!accountQueryService.isSelfPay(chargeItem.getAccountId())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "仅纯自费勾选项目可打折");
            }
        }
    }

    /**
     * 批量解析收费行产品大类码（药/诊/材）。
     *
     * @param chargeItems 收费行
     * @return productTable#productId → categoryCode
     */
    private Map<String, String> mapCategoryCodesForChargeItems(List<ChargeItem> chargeItems) {
        Map<String, String> result = new HashMap<>();
        Set<Long> medIds = new HashSet<>();
        Set<Long> actIds = new HashSet<>();
        Set<Long> deviceIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getProductId() == null
                || StringUtils.isEmpty(chargeItem.getProductTable())) {
                continue;
            }
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(chargeItem.getProductTable())) {
                medIds.add(chargeItem.getProductId());
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(chargeItem.getProductTable())) {
                actIds.add(chargeItem.getProductId());
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(chargeItem.getProductTable())) {
                deviceIds.add(chargeItem.getProductId());
            }
        }
        if (!medIds.isEmpty()) {
            List<MedicationDefinition> meds = medicationDefinitionQueryService.findAllByIds(medIds);
            for (MedicationDefinition med : meds) {
                if (med != null && med.getId() != null && !StringUtils.isEmpty(med.getCategoryCode())) {
                    result.put(
                        this.buildProductKey(TableNameConstants.MED_MEDICATION_DEFINITION, med.getId()),
                        med.getCategoryCode().trim());
                }
            }
        }
        if (!actIds.isEmpty()) {
            List<ActivityDefinition> acts = activityDefinitionQueryService.findAllByIds(actIds);
            for (ActivityDefinition act : acts) {
                if (act != null && act.getId() != null && !StringUtils.isEmpty(act.getCategoryCode())) {
                    result.put(
                        this.buildProductKey(TableNameConstants.WKF_ACTIVITY_DEFINITION, act.getId()),
                        act.getCategoryCode().trim());
                }
            }
        }
        if (!deviceIds.isEmpty()) {
            List<DeviceDefinition> devices = deviceDefinitionQueryService.getAllDeviceDefinitions(deviceIds);
            for (DeviceDefinition device : devices) {
                if (device != null && device.getId() != null && !StringUtils.isEmpty(device.getCategoryCode())) {
                    result.put(
                        this.buildProductKey(TableNameConstants.ADM_DEVICE_DEFINITION, device.getId()),
                        device.getCategoryCode().trim());
                }
            }
        }
        return result;
    }

    /**
     * 构造产品键。
     *
     * @param productTable 表名
     * @param productId    产品 id
     * @return table#id
     */
    private String buildProductKey(String productTable, Long productId) {
        return productTable + ChargeDiscountConfigConstants.PRODUCT_KEY_SEPARATOR + productId;
    }

    /**
     * 断言折扣率 ∈ (0,1]。
     *
     * @param rate  费率
     * @param label 文案前缀
     */
    private void assertRateInOpenInterval(BigDecimal rate, String label) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_INVALID_ARGUMENT,
                label + "须属于 (0,1]");
        }
    }
}
