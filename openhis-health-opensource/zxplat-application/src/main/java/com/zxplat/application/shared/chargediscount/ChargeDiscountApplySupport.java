/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.chargediscount;

import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.command.ApplySettleDiscountLinesCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.custom.chargediscountconfig.queries.DiscountProductResolveItem;
import com.zxplat.domain.custom.chargediscountconfig.queries.model.ChargeDiscountConfigQueryModel;
import com.zxplat.domain.custom.chargediscountconfig.service.ChargeDiscountConfigQueryService;
import com.zxplat.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.ChargeDiscountConfigConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.domain.share.enums.SettleDiscountSource;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * <p>硬口径：未配置有效折扣（{@link ChargeDiscountConfigQueryService#isDiscountFeatureEnabled()} 为 false）时，
 * 所有方法对现有金额路径<strong>零侵入</strong>（{@link ChargeDiscountBatchResult#isApplied()} = false）。
 * 显隐只认折扣配置表（ACTIVE 未删行）。</p>
 *
 * <p>打折门禁（预结算/正式结算/列表试算同一套）：
 * <strong>仅</strong>「自费 + 款员已选折扣率 + 命中可打折配置」才走打折；
 * 未选折扣率、医保行、未命中配置 → 一律 {@code applied=false}/原价，零侵入、不抛折扣校验。</p>
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
     * 是否已配置有效折扣内容（收银/报表显隐真源）。
     *
     * @return true 表示存在 ≥1 条未删 ACTIVE 折扣配置；配置本身即开关
     */
    public boolean isDiscountFeatureEnabled() {
        return chargeDiscountConfigQueryService.isDiscountFeatureEnabled();
    }

    /**
     * 未配置有效折扣时禁止携带折扣率（BR-OPC-027 / BR-IP-STL-020）。
     *
     * @param cashierDiscountRate 款员所选率；可空
     */
    public void assertCashierRateAllowedWhenDiscountConfigured(BigDecimal cashierDiscountRate) {
        // 未传折扣率：无论是否已配折扣均放行（零侵入）
        if (cashierDiscountRate == null) {
            return;
        }
        // 未配置有效折扣却传了折扣率 → 拒绝，避免前端绕过
        if (!this.isDiscountFeatureEnabled()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                "未配置收费折扣，不能提交折扣率");
        }
        this.assertRateInOpenInterval(cashierDiscountRate, "款员折扣率");
    }

    /**
     * 试算本批折扣；未配置有效折扣或无有效打折时返回 {@code applied=false}。
     *
     * <p>门禁：未选折扣率 → 直接零侵入；医保行永不参与；
     * 仅自费且命中配置的行，在款员已选率时才折价。</p>
     *
     * @param chargeItems         拟结收费行
     * @param cashierDiscountRate 款员所选率；空=不打折（与正常结算相同）
     * @return 试算结果
     */
    public ChargeDiscountBatchResult resolveBatch(
        List<ChargeItem> chargeItems,
        BigDecimal cashierDiscountRate
    ) {
        ChargeDiscountBatchResult empty = new ChargeDiscountBatchResult();
        empty.setApplied(false);
        // 未选折扣率：与正常结算相同，不走任何打折逻辑/校验
        if (cashierDiscountRate == null) {
            return empty;
        }
        // 未配置有效折扣：等价于功能未加
        if (!this.isDiscountFeatureEnabled()) {
            return empty;
        }
        // 空批：无打折
        if (chargeItems == null || chargeItems.isEmpty()) {
            return empty;
        }
        Map<String, ChargeDiscountConfigQueryModel> hitByProductKey =
            this.mapDiscountConfigsForChargeItems(chargeItems);

        // Pre：筛出「自费 + 命中配置」行；医保行永不进集合（选了折扣也不校验、不折）
        List<ChargeItem> discountableLines = new ArrayList<>();
        boolean batchHasSelfPay = false;
        for (ChargeItem chargeItem : chargeItems) {
            // 缺产品锚点：无法命中配置，跳过
            if (chargeItem == null || StringUtils.isEmpty(chargeItem.getProductTable())
                || chargeItem.getProductId() == null) {
                continue;
            }
            // 医保等非自费：本行与折扣无关，直接跳过
            if (!this.isSelfPayAccountForPreview(chargeItem)) {
                continue;
            }
            batchHasSelfPay = true;
            String key = this.buildProductKey(chargeItem.getProductTable(), chargeItem.getProductId());
            // 自费且命中可打折配置：纳入本批打折集合
            if (hitByProductKey.containsKey(key)) {
                discountableLines.add(chargeItem);
            }
        }
        // Gate：无可折行时——纯医保零侵入放行；自费批却无可折项则拒款员率（BR-OPC-028）
        if (discountableLines.isEmpty()) {
            // 批内有自费却无一命中配置：款员选了折却无处可落
            if (batchHasSelfPay) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "本批没有可打折项目，不能提交折扣率");
            }
            // 纯医保：选不选折扣都与打折无关
            return empty;
        }

        // Gate：可折行须挂个人现金自费户（BR-OPC-028 / BR-IP-STL-021）
        this.assertAllSelfPayAccounts(discountableLines);

        // Pre：解析每行有效率并校验同批一致（BR-OPC-030）
        BigDecimal unifiedRate = null;
        String unifiedSource = null;
        Map<Long, ChargeDiscountConfigQueryModel> configByChargeItemId = new HashMap<>();
        for (ChargeItem chargeItem : discountableLines) {
            String key = this.buildProductKey(chargeItem.getProductTable(), chargeItem.getProductId());
            ChargeDiscountConfigQueryModel config = hitByProductKey.get(key);
            configByChargeItemId.put(chargeItem.getId(), config);
            BigDecimal effectiveRate = this.resolveEffectiveDiscountRate(config, cashierDiscountRate);
            String source = this.resolveEffectiveDiscountSource(config, effectiveRate);
            // 有效率空：无法落折后价（防御；款员已选率时不应出现）
            if (effectiveRate == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "本批含款员定率项目，请先选择折扣率");
            }
            // 首行定本批统一率
            if (unifiedRate == null) {
                unifiedRate = effectiveRate;
                unifiedSource = source;
            } else if (unifiedRate.compareTo(effectiveRate) != 0) {
                // 同批可折行有效率不一致：须拆单，禁止混折
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_RULE_CONFLICT,
                    "本批可打折项目有效折扣率不一致，请拆单结算");
            }
            // 统一来源尚未写入时补写（与首行同源）
            if (unifiedSource == null) {
                unifiedSource = source;
            }
        }

        // Asm：按行装配折后应收；未进可折集合的行（含医保）原价入账
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
            // 未进可折集合：原价入账（医保/未命中）
            if (config == null) {
                BigDecimal settleOriginal = MonetaryAmountSupport.scaleSettle(original);
                line.setDiscountedReceivable(settleOriginal);
                line.setDiscountGap(BigDecimal.ZERO);
                line.setDiscounted(false);
                result.getLineByChargeItemId().put(chargeItem.getId(), line);
                receivableTotal = receivableTotal.add(settleOriginal);
                continue;
            }
            BigDecimal effectiveRate = this.resolveEffectiveDiscountRate(config, cashierDiscountRate);
            String source = this.resolveEffectiveDiscountSource(config, effectiveRate);
            // 有效率空：回落原价（防御）
            if (effectiveRate == null) {
                BigDecimal settleOriginal = MonetaryAmountSupport.scaleSettle(original);
                line.setDiscountedReceivable(settleOriginal);
                line.setDiscountGap(BigDecimal.ZERO);
                line.setDiscounted(false);
                result.getLineByChargeItemId().put(chargeItem.getId(), line);
                receivableTotal = receivableTotal.add(settleOriginal);
                continue;
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
     * 收银台列表折后价试算（只读，不写库）。
     *
     * <p>门禁与 {@link #resolveBatch} 相同：未选折扣率/医保/未命中 → 折后=原价。
     * 列表不因「自费无可折却传了折扣率」拒绝整批，混医保行仍可展示。</p>
     *
     * @param chargeItems         拟展示收费行
     * @param cashierDiscountRate 款员所选率；可空
     * @return 行级折后价；未配置有效折扣时全部原价
     */
    public ChargeDiscountBatchResult previewBatch(
        List<ChargeItem> chargeItems,
        BigDecimal cashierDiscountRate
    ) {
        this.assertCashierRateAllowedWhenDiscountConfigured(cashierDiscountRate);
        ChargeDiscountBatchResult empty = new ChargeDiscountBatchResult();
        empty.setApplied(false);
        // 未配置有效折扣：列表折后价等于原价
        if (!this.isDiscountFeatureEnabled()) {
            return this.buildOriginalPricePreview(chargeItems, empty);
        }
        // 空批：无行可展示
        if (chargeItems == null || chargeItems.isEmpty()) {
            return empty;
        }
        Map<String, ChargeDiscountConfigQueryModel> hitByProductKey =
            this.mapDiscountConfigsForChargeItems(chargeItems);
        ChargeDiscountBatchResult result = new ChargeDiscountBatchResult();
        result.setApplied(false);
        BigDecimal receivableTotal = BigDecimal.ZERO;
        BigDecimal gapTotal = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            // 空行无法装配折后价，跳过以免 NPE
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            ChargeDiscountLineResult line = this.buildPreviewLine(chargeItem, hitByProductKey, cashierDiscountRate);
            result.getLineByChargeItemId().put(chargeItem.getId(), line);
            receivableTotal = receivableTotal.add(line.getDiscountedReceivable());
            gapTotal = gapTotal.add(line.getDiscountGap());
            // 任一行实际打折则本批标记已应用，供优惠合计展示
            if (line.isDiscounted()) {
                result.setApplied(true);
                result.getDiscountedLines().add(line);
                if (result.getEffectiveRate() == null) {
                    result.setEffectiveRate(line.getEffectiveRate());
                }
            }
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
     * <p>{@code fin_payment_rec_detail.account_id} 非空，须挂本批结算账户（通常与现金实付行同一账户）。</p>
     *
     * @param batchResult 试算结果
     * @param accountId   旁证挂账账户；有优惠时不能空
     * @param accountCode 账户编码，可空
     * @param resultEnum  结果态，可空（领域默认草稿）
     * @return 旁证行；无优惠时 null
     */
    public PaymentDetailItemCommand buildDiscountRecDetail(
        ChargeDiscountBatchResult batchResult,
        Long accountId,
        String accountCode,
        String resultEnum) {
        // 未应用或无优惠：不旁证
        if (batchResult == null || !batchResult.isApplied()) {
            return null;
        }
        BigDecimal gap = batchResult.getDiscountGapTotal();
        if (gap == null || gap.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (accountId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_APPLY_INVALID_ARGUMENT,
                "折扣旁证明细缺少结算账户，无法落支付从表");
        }
        PaymentDetailItemCommand line = new PaymentDetailItemCommand();
        line.setPayEnum(PaymentChannel.DISCOUNT.getCode());
        line.setAccountId(accountId);
        if (!StringUtils.isEmpty(accountCode)) {
            line.setAccountCode(accountCode.trim());
        }
        line.setAmount(MonetaryAmountSupport.scaleSettle(gap));
        line.setChargeAmount(MonetaryAmountSupport.scaleSettle(gap));
        if (!StringUtils.isEmpty(resultEnum)) {
            line.setResultEnum(resultEnum.trim());
        }
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
     * 解析本批收费行可命中的折扣配置（单项优先于大类，与结算试算同一 QueryService）。
     *
     * @param chargeItems 收费行
     * @return productTable#productId → 有效配置；未命中不含键
     */
    private Map<String, ChargeDiscountConfigQueryModel> mapDiscountConfigsForChargeItems(
        List<ChargeItem> chargeItems
    ) {
        Map<String, String> categoryByProductKey = this.mapCategoryCodesForChargeItems(chargeItems);
        List<DiscountProductResolveItem> resolveItems = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            // 缺产品锚点：无法构造命中键
            if (chargeItem == null || StringUtils.isEmpty(chargeItem.getProductTable())
                || chargeItem.getProductId() == null) {
                continue;
            }
            // 挂号锚点等非三大目录不参与打折命中
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
        return chargeDiscountConfigQueryService.mapDiscountConfigsForProducts(resolveItems);
    }

    /**
     * 未配置有效折扣时把每行折后价填成原价，供列表列展示。
     *
     * @param chargeItems 收费行
     * @param result      空结果容器
     * @return 填好原价的结果
     */
    private ChargeDiscountBatchResult buildOriginalPricePreview(
        List<ChargeItem> chargeItems,
        ChargeDiscountBatchResult result
    ) {
        // 空批：无可展示行
        if (chargeItems == null || chargeItems.isEmpty()) {
            return result;
        }
        BigDecimal receivableTotal = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            // 缺主键：无法回填折后价列
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            ChargeDiscountLineResult line = new ChargeDiscountLineResult();
            line.setChargeItemId(chargeItem.getId());
            BigDecimal original = this.resolveOriginalAmount(chargeItem);
            BigDecimal settleOriginal = MonetaryAmountSupport.scaleSettle(original);
            line.setOriginalAmount(original);
            line.setDiscountedReceivable(settleOriginal);
            line.setDiscountGap(BigDecimal.ZERO);
            line.setDiscounted(false);
            result.getLineByChargeItemId().put(chargeItem.getId(), line);
            receivableTotal = receivableTotal.add(settleOriginal);
        }
        result.setDiscountedReceivableTotal(MonetaryAmountSupport.scaleSettle(receivableTotal));
        result.setDiscountGapTotal(BigDecimal.ZERO);
        return result;
    }

    /**
     * 按与结算相同的命中/费率公式计算单行折后价；不可打折时折后=原价。
     *
     * @param chargeItem          收费行
     * @param hitByProductKey     产品键命中配置
     * @param cashierDiscountRate 款员所选率；可空
     * @return 行试算
     */
    private ChargeDiscountLineResult buildPreviewLine(
        ChargeItem chargeItem,
        Map<String, ChargeDiscountConfigQueryModel> hitByProductKey,
        BigDecimal cashierDiscountRate
    ) {
        ChargeDiscountLineResult line = new ChargeDiscountLineResult();
        line.setChargeItemId(chargeItem.getId());
        BigDecimal original = this.resolveOriginalAmount(chargeItem);
        line.setOriginalAmount(original);
        BigDecimal settleOriginal = MonetaryAmountSupport.scaleSettle(original);
        // 未选折扣率：列表折后=原价（与正常结算一致）
        if (cashierDiscountRate == null) {
            line.setDiscountedReceivable(settleOriginal);
            line.setDiscountGap(BigDecimal.ZERO);
            line.setDiscounted(false);
            return line;
        }
        // 缺产品锚点：无法命中配置，按原价展示
        if (StringUtils.isEmpty(chargeItem.getProductTable()) || chargeItem.getProductId() == null) {
            line.setDiscountedReceivable(settleOriginal);
            line.setDiscountGap(BigDecimal.ZERO);
            line.setDiscounted(false);
            return line;
        }
        String key = this.buildProductKey(chargeItem.getProductTable(), chargeItem.getProductId());
        ChargeDiscountConfigQueryModel config = hitByProductKey.get(key);
        // 未配置可打折：折后价=原价
        if (config == null) {
            line.setDiscountedReceivable(settleOriginal);
            line.setDiscountGap(BigDecimal.ZERO);
            line.setDiscounted(false);
            return line;
        }
        // 医保等非自费：不打折
        if (!this.isSelfPayAccountForPreview(chargeItem)) {
            line.setDiscountedReceivable(settleOriginal);
            line.setDiscountGap(BigDecimal.ZERO);
            line.setDiscounted(false);
            return line;
        }
        BigDecimal effectiveRate = this.resolveEffectiveDiscountRate(config, cashierDiscountRate);
        String source = this.resolveEffectiveDiscountSource(config, effectiveRate);
        // 有效率空：按原价
        if (effectiveRate == null) {
            line.setDiscountedReceivable(settleOriginal);
            line.setDiscountGap(BigDecimal.ZERO);
            line.setDiscounted(false);
            return line;
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
        return line;
    }

    /**
     * 列表试算用的自费判定：缺账户视为不可打折（不抛错）。
     *
     * @param chargeItem 收费行
     * @return 挂个人现金自费户时 true
     */
    private boolean isSelfPayAccountForPreview(ChargeItem chargeItem) {
        if (chargeItem.getAccountId() == null) {
            return false;
        }
        return accountQueryService.isSelfPay(chargeItem.getAccountId());
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
     * 解析本行有效折扣率（仅在已通过「自费+已选率+命中配置」门禁后调用）。
     *
     * <p>配置率 ∈ (0,1) 为固定优惠，优先于款员所选率；
     * 空率或 1（10 折）视为款员定率，回落 {@code cashierDiscountRate}。</p>
     *
     * @param config              命中的折扣配置；调用方保证非空
     * @param cashierDiscountRate 款员所选率；门禁层已保证非空时调用本方法
     * @return 有效率；无法定率时 null
     */
    private BigDecimal resolveEffectiveDiscountRate(
        ChargeDiscountConfigQueryModel config,
        BigDecimal cashierDiscountRate
    ) {
        BigDecimal configured = config.getDiscountRate();
        // 配置了真正小于 1 的固定优惠：收银下拉不得覆盖（BR 固定率优先）
        if (this.hasPreferentialFixedRate(configured)) {
            return configured;
        }
        // 空率或 10 折（率为 1）：配置页不定折，须用款员所选率才能算出优惠
        return cashierDiscountRate;
    }

    /**
     * 根据有效率来源写结算快照：用了配置固定优惠则为 CONFIG，否则 CASHIER。
     *
     * @param config        命中配置
     * @param effectiveRate {@link #resolveEffectiveDiscountRate} 的结果；可空
     * @return 来源 code；无法打折时仍回 CASHIER（调用方不会落快照）
     */
    private String resolveEffectiveDiscountSource(
        ChargeDiscountConfigQueryModel config,
        BigDecimal effectiveRate
    ) {
        // 有效率与配置固定优惠一致：快照记 CONFIG
        if (this.hasPreferentialFixedRate(config.getDiscountRate())
            && effectiveRate != null
            && effectiveRate.compareTo(config.getDiscountRate()) == 0) {
            return SettleDiscountSource.CONFIG.getCode();
        }
        // 其余（款员下拉、10 折回落款员率）：快照记 CASHIER
        return SettleDiscountSource.CASHIER.getCode();
    }

    /**
     * 是否配置了真正优惠的固定率（开区间 (0,1)，不含 10 折）。
     *
     * @param configuredRate 配置列；可空
     * @return true 表示应按配置率折
     */
    private boolean hasPreferentialFixedRate(BigDecimal configuredRate) {
        // 空率=款员定，不是固定优惠
        if (configuredRate == null) {
            return false;
        }
        // 非正数非法，不按固定优惠采用
        if (configuredRate.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        // 等于 1 即 10 折/原价，不算「优惠」；须小于 1 才挡住款员率
        return configuredRate.compareTo(BigDecimal.ONE) < 0;
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
