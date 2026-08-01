/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.support;

import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementPaymentChargeLineDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementPaymentItemDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementPaymentPayMethodDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementPaymentItemDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.share.constants.OutpatientDailySettlementConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.financial.DailySettlementRefundPaymentRules;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日结交账 payment 明细 enrichment（项目行、支付方式、行净额；BR-OP-DCL-015 / BR-OP-DCL-026）。
 *
 * <p>门诊/住院日结 Mapper 共用；读侧经 ChargeItem 与 PaymentRecDetail QueryService（B.6.4）。
 * 行 {@code netAmount}：有收费明细时按带符号 charge 行额代数和回写（医保全个账头表
 * {@code tendered}/{@code display}=0 时仍展示收费正/退费负）；无明细（预交金等）保留领域预览净额。</p>
 */
@Component
@RequiredArgsConstructor
public class DailySettlementPaymentDetailLoader {

    /** 收费项批量读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 定价定义读侧（项目名称、fin_type）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 支付渠道 code → 展示名。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** 产品目录批量读侧（项目名称回落，与收银小票口径一致）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 按 payment 读模型批量加载 charge_item（优先 payment.chargeItemIds）。
     *
     * @param paymentModels 展示窗内 payment 读模型
     * @return paymentId → 收费项列表
     */
    public Map<Long, List<ChargeItem>> loadChargeItemsByPaymentModels(
        Collection<PaymentReconciliationQueryModel> paymentModels) {
        Map<Long, List<ChargeItem>> result = new LinkedHashMap<>();
        if (paymentModels == null || paymentModels.isEmpty()) {
            return result;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null || paymentModel.getId() == null) {
                continue;
            }
            List<Long> chargeItemIds = this.parseChargeItemIds(paymentModel.getChargeItemIds());
            List<ChargeItem> chargeItems = List.of();
            if (!chargeItemIds.isEmpty()) {
                ChargeItemQueryCommand command = new ChargeItemQueryCommand();
                command.setIds(chargeItemIds);
                List<ChargeItem> loaded = this.chargeItemQueryService.loadChargeItemsByIds(command);
                if (loaded != null && !loaded.isEmpty()) {
                    chargeItems = loaded;
                }
            }
            result.put(paymentModel.getId(), chargeItems);
        }
        return result;
    }

    /**
     * 为候选 payment 行填充 chargeLines、payMethods，并回写带符号行净额。
     *
     * @param items                   候选 payment DTO
     * @param chargeItemsByPaymentId  paymentId → 收费项
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     */
    public void enrichOutpatientPaymentItems(
        List<DailySettlementPaymentItemDto> items,
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, ChargeItemDefinition> definitionById = this.loadDefinitionMap(chargeItemsByPaymentId);
        Map<String, ProductDefinitionDisplayDto> productMap =
            this.loadProductMap(this.collectChargeItems(chargeItemsByPaymentId));
        for (DailySettlementPaymentItemDto item : items) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            boolean reverse = this.isReversePayment(item);
            List<ChargeItem> chargeItems = List.of();
            if (chargeItemsByPaymentId != null) {
                List<ChargeItem> loaded = chargeItemsByPaymentId.get(item.getPaymentId());
                if (loaded != null) {
                    chargeItems = loaded;
                }
            }
            item.setChargeLines(this.buildChargeLines(chargeItems, definitionById, productMap, reverse));
            List<PaymentRecDetailQueryModel> recDetails = List.of();
            if (recDetailsByPaymentId != null) {
                List<PaymentRecDetailQueryModel> loaded = recDetailsByPaymentId.get(item.getPaymentId());
                if (loaded != null) {
                    recDetails = loaded;
                }
            }
            item.setPayMethods(this.buildPayMethods(
                recDetails,
                reverse,
                item.getHasInsurance(),
                item.getKindEnum()));
            // 有收费明细：行净额改按带符号账单额，避免医保全个账两行均为 0
            this.applySignedBillNetAmount(item);
        }
    }

    /**
     * 为住院候选 payment 行填充 chargeLines、payMethods，并回写带符号行净额。
     *
     * @param items                   候选 payment DTO
     * @param chargeItemsByPaymentId  paymentId → 收费项
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     */
    public void enrichInpatientPaymentItems(
        List<InpatientDailySettlementPaymentItemDto> items,
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, ChargeItemDefinition> definitionById = this.loadDefinitionMap(chargeItemsByPaymentId);
        Map<String, ProductDefinitionDisplayDto> productMap =
            this.loadProductMap(this.collectChargeItems(chargeItemsByPaymentId));
        for (InpatientDailySettlementPaymentItemDto item : items) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            boolean reverse = this.isReverseInpatientPayment(item);
            List<ChargeItem> chargeItems = List.of();
            if (chargeItemsByPaymentId != null) {
                List<ChargeItem> loaded = chargeItemsByPaymentId.get(item.getPaymentId());
                if (loaded != null) {
                    chargeItems = loaded;
                }
            }
            item.setChargeLines(this.buildChargeLines(chargeItems, definitionById, productMap, reverse));
            List<PaymentRecDetailQueryModel> recDetails = List.of();
            if (recDetailsByPaymentId != null) {
                List<PaymentRecDetailQueryModel> loaded = recDetailsByPaymentId.get(item.getPaymentId());
                if (loaded != null) {
                    recDetails = loaded;
                }
            }
            item.setPayMethods(this.buildPayMethods(
                recDetails,
                reverse,
                item.getHasInsurance(),
                item.getKindEnum()));
            // 有收费明细：行净额改按带符号账单额，避免医保全个账两行均为 0
            this.applySignedBillNetAmount(item);
        }
    }

    /**
     * 门诊行净额回写：有 chargeLines 时取带符号行额代数和（结算 2 位）；无明细保留原 netAmount。
     *
     * @param item 门诊交账候选行
     */
    private void applySignedBillNetAmount(DailySettlementPaymentItemDto item) {
        if (item == null) {
            return;
        }
        BigDecimal signedBillNet = this.sumSignedChargeLineAmounts(item.getChargeLines());
        if (signedBillNet == null) {
            // 无收费明细（预交金等）：保留领域 tendered−returned 带符号净额
            return;
        }
        item.setNetAmount(signedBillNet);
    }

    /**
     * 住院行净额回写：有 chargeLines 时取带符号行额代数和（结算 2 位）；无明细保留原 netAmount。
     *
     * @param item 住院交账候选行
     */
    private void applySignedBillNetAmount(InpatientDailySettlementPaymentItemDto item) {
        if (item == null) {
            return;
        }
        BigDecimal signedBillNet = this.sumSignedChargeLineAmounts(item.getChargeLines());
        if (signedBillNet == null) {
            // 无收费明细（预交金等）：保留领域 tendered−returned 带符号净额
            return;
        }
        item.setNetAmount(signedBillNet);
    }

    /**
     * 对已带符号的收费项目行求代数和（结算刻度）；无行时返回 null 表示不覆盖。
     *
     * @param chargeLines 弹窗项目行（逆向已取负）
     * @return 结算 2 位净额；无有效行时 null
     */
    private BigDecimal sumSignedChargeLineAmounts(List<DailySettlementPaymentChargeLineDto> chargeLines) {
        if (chargeLines == null || chargeLines.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasLine = false;
        for (DailySettlementPaymentChargeLineDto line : chargeLines) {
            if (line == null) {
                continue;
            }
            hasLine = true;
            BigDecimal lineAmount = line.getLineAmount();
            if (lineAmount == null) {
                continue;
            }
            total = total.add(lineAmount);
        }
        if (!hasLine) {
            return null;
        }
        return MonetaryAmountSupport.scaleDisplay(total);
    }

    /**
     * 解析 payment.chargeItemIds 逗号分隔串。
     *
     * @param rawChargeItemIds 原始 id 串
     * @return id 列表
     */
    private List<Long> parseChargeItemIds(String rawChargeItemIds) {
        if (StringUtils.isEmpty(rawChargeItemIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String segment : rawChargeItemIds.split(",")) {
            String trimmed = StringUtils.trimToNull(segment);
            if (trimmed == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法片段
            }
        }
        return ids;
    }

    /**
     * 批量加载 charge_item 关联的定义（项目名称、fin_type）。
     *
     * @param chargeItemsByPaymentId paymentId → 收费项
     * @return definitionId → 定义
     */
    private Map<Long, ChargeItemDefinition> loadDefinitionMap(Map<Long, List<ChargeItem>> chargeItemsByPaymentId) {
        Set<Long> definitionIds = new LinkedHashSet<>();
        if (chargeItemsByPaymentId != null) {
            for (List<ChargeItem> chargeItems : chargeItemsByPaymentId.values()) {
                if (chargeItems == null) {
                    continue;
                }
                for (ChargeItem chargeItem : chargeItems) {
                    if (chargeItem != null && chargeItem.getDefinitionId() != null) {
                        definitionIds.add(chargeItem.getDefinitionId());
                    }
                }
            }
        }
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        return this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    /**
     * 汇总 payment 批量下的全部 charge_item（供产品目录批量加载）。
     *
     * @param chargeItemsByPaymentId paymentId → 收费项
     * @return 收费项列表
     */
    private List<ChargeItem> collectChargeItems(Map<Long, List<ChargeItem>> chargeItemsByPaymentId) {
        List<ChargeItem> allChargeItems = new ArrayList<>();
        if (chargeItemsByPaymentId == null || chargeItemsByPaymentId.isEmpty()) {
            return allChargeItems;
        }
        for (List<ChargeItem> chargeItems : chargeItemsByPaymentId.values()) {
            if (chargeItems == null || chargeItems.isEmpty()) {
                continue;
            }
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null) {
                    allChargeItems.add(chargeItem);
                }
            }
        }
        return allChargeItems;
    }

    /**
     * 批量加载收费项关联产品目录（去重 composite key）。
     *
     * @param chargeItems 收费项列表
     * @return compositeKey → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductMap(List<ChargeItem> chargeItems) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (chargeItems == null) {
            return Map.of();
        }
        for (ChargeItem chargeItem : chargeItems) {
            ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                lookup.getProductTableName(),
                lookup.getProductDefinitionId());
            if (compositeKey == null || !dedupeKeys.add(compositeKey)) {
                continue;
            }
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 从批量产品 Map 解析单条收费项对应目录展示。
     *
     * @param chargeItem 收费项
     * @param productMap 预加载产品 Map
     * @return 产品展示 DTO；无关联时 null
     */
    private ProductDefinitionDisplayDto resolveProductForChargeItem(
        ChargeItem chargeItem,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        if (productMap == null || productMap.isEmpty() || chargeItem == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
        if (lookup == null) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(),
            lookup.getProductDefinitionId());
        if (key == null) {
            return null;
        }
        return productMap.get(key);
    }

    /**
     * 与门诊收银待收费列表口径一致：优先目录 product_name，其次定价 charge_name。
     *
     * @param chargeItem     收费项
     * @param definition     价目定义
     * @param productMap     产品目录索引
     * @return 项目名称；无法解析时 null
     */
    private String resolveChargeLineDisplayName(
        ChargeItem chargeItem,
        ChargeItemDefinition definition,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(chargeItem, productMap);
        if (product != null && StringUtils.isNotEmpty(product.getProductName())) {
            return product.getProductName().trim();
        }
        if (definition != null && StringUtils.isNotEmpty(definition.getChargeName())) {
            return definition.getChargeName().trim();
        }
        return null;
    }

    /**
     * 收费项 → 弹窗项目行。
     *
     * @param chargeItems     收费项列表
     * @param definitionById  定义索引
     * @param productMap      产品目录索引
     * @param reverse         是否逆向 payment
     * @return 项目行列表
     */
    private List<DailySettlementPaymentChargeLineDto> buildChargeLines(
        List<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionById,
        Map<String, ProductDefinitionDisplayDto> productMap,
        boolean reverse) {
        List<DailySettlementPaymentChargeLineDto> lines = new ArrayList<>();
        if (chargeItems == null) {
            return lines;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            DailySettlementPaymentChargeLineDto line = new DailySettlementPaymentChargeLineDto();
            ChargeItemDefinition definition = null;
            if (chargeItem.getDefinitionId() != null && definitionById != null) {
                definition = definitionById.get(chargeItem.getDefinitionId());
            }
            line.setItemName(this.resolveChargeLineDisplayName(chargeItem, definition, productMap));
            line.setUnitPrice(chargeItem.getUnitPrice());
            line.setQuantity(chargeItem.getQuantityValue());
            BigDecimal lineAmount = chargeItem.getTotalPrice();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            if (reverse) {
                lineAmount = lineAmount.abs().negate();
            }
            line.setLineAmount(lineAmount);
            if (definition != null && StringUtils.isNotEmpty(definition.getTypeCode())) {
                line.setFinTypeCode(definition.getTypeCode().trim());
            }
            lines.add(line);
        }
        return lines;
    }

    /**
     * 支付子明细 → 弹窗支付方式行（自费/医保 + 渠道；门诊/住院日结交账弹窗共用）。
     *
     * @param recDetails      支付子明细
     * @param reverse         是否逆向 payment
     * @param hasInsurance    是否医保费用性质（payment 级，仅作 CASHIER 回落参考）
     * @param kindEnum        业务 kind code
     * @return 支付方式行（同名渠道合并金额）
     */
    private List<DailySettlementPaymentPayMethodDto> buildPayMethods(
        List<PaymentRecDetailQueryModel> recDetails,
        boolean reverse,
        Boolean hasInsurance,
        String kindEnum) {
        Map<String, DailySettlementPaymentPayMethodDto> mergedByDisplayName = new LinkedHashMap<>();
        if (recDetails == null) {
            return List.of();
        }
        for (PaymentRecDetailQueryModel detail : recDetails) {
            if (detail == null) {
                continue;
            }
            BigDecimal amount = this.resolveDetailNetAmount(detail);
            if (reverse) {
                amount = amount.abs().negate();
            }
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String payEnum = detail.getPayEnum();
            if (payEnum != null) {
                payEnum = payEnum.trim();
            }
            String displayName = this.resolveHandoverPayMethodDisplayName(payEnum, hasInsurance, kindEnum);
            if (StringUtils.isEmpty(displayName)) {
                continue;
            }
            DailySettlementPaymentPayMethodDto existing = mergedByDisplayName.get(displayName);
            if (existing == null) {
                DailySettlementPaymentPayMethodDto method = new DailySettlementPaymentPayMethodDto();
                method.setPayMethodCode(payEnum);
                method.setPayMethodName(displayName);
                method.setAmount(amount);
                mergedByDisplayName.put(displayName, method);
                continue;
            }
            BigDecimal existingAmount = existing.getAmount();
            if (existingAmount == null) {
                existingAmount = BigDecimal.ZERO;
            }
            existing.setAmount(existingAmount.add(amount));
        }
        return new ArrayList<>(mergedByDisplayName.values());
    }

    /**
     * 交账弹窗支付方式展示：前缀取 payment 合同费用性质（{@code hasInsurance}），后缀取渠道短名。
     *
     * <p>现金/微信等自付渠道在医保合同时须展示「医保·渠道」，禁止按渠道类型误标「自费」（BR-OP-DCL-015）。</p>
     *
     * @param payEnum      持久化 pay_enum
     * @param hasInsurance 是否医保费用性质（来自 payment.contract_no → fin_contract）
     * @param kindEnum     业务 kind
     * @return 展示名；无法解析时 null
     */
    private String resolveHandoverPayMethodDisplayName(
        String payEnum,
        Boolean hasInsurance,
        String kindEnum) {
        if (StringUtils.isEmpty(payEnum)) {
            return null;
        }
        PaymentChannel channel = PaymentChannel.fromCode(payEnum);
        if (PaymentChannel.CASHIER.equals(channel)) {
            return this.resolveCashierHandoverPayMethodDisplayName(hasInsurance, kindEnum);
        }
        String expenseLabel = OutpatientDailySettlementConstants.EXPENSE_SELF_PAY_LABEL;
        // 前缀一律跟合同费用性质，不跟渠道是否统筹/个账
        if (hasInsurance != null && hasInsurance) {
            expenseLabel = OutpatientDailySettlementConstants.EXPENSE_INSURANCE_LABEL;
        }
        String channelLabel = this.resolveHandoverChannelShortLabel(channel, payEnum);
        if (StringUtils.isEmpty(channelLabel)) {
            return null;
        }
        return expenseLabel
            + OutpatientDailySettlementConstants.PAY_METHOD_LABEL_SEPARATOR
            + channelLabel;
    }

    /**
     * 内部 CASHIER 占位：不落「收银台」，按费用性质回落为现金渠道展示。
     *
     * @param hasInsurance 是否医保费用性质
     * @param kindEnum     业务 kind（预留；当前与渠道展示无关）
     * @return 展示名
     */
    private String resolveCashierHandoverPayMethodDisplayName(Boolean hasInsurance, String kindEnum) {
        String expenseLabel = OutpatientDailySettlementConstants.EXPENSE_SELF_PAY_LABEL;
        if (hasInsurance != null && hasInsurance) {
            expenseLabel = OutpatientDailySettlementConstants.EXPENSE_INSURANCE_LABEL;
        }
        return expenseLabel
            + OutpatientDailySettlementConstants.PAY_METHOD_LABEL_SEPARATOR
            + PaymentChannel.CASH.getDisplay();
    }

    /**
     * 渠道短名（医保统筹/个账去前缀，其余用枚举展示名）。
     *
     * @param channel 支付渠道枚举
     * @param payEnum 原始 code
     * @return 渠道短名
     */
    private String resolveHandoverChannelShortLabel(PaymentChannel channel, String payEnum) {
        if (PaymentChannel.INSURANCE_FUND.equals(channel)) {
            return OutpatientDailySettlementConstants.PAY_METHOD_INSURANCE_FUND_SHORT;
        }
        if (PaymentChannel.INSURANCE_ACCT.equals(channel)) {
            return OutpatientDailySettlementConstants.PAY_METHOD_INSURANCE_ACCT_SHORT;
        }
        if (channel != null && StringUtils.isNotEmpty(channel.getDisplay())) {
            return channel.getDisplay().trim();
        }
        String fallback = this.paymentChannelDisplayAssembler.resolvePayChannelDisplay(payEnum, null);
        if (StringUtils.isEmpty(fallback)) {
            return payEnum;
        }
        return fallback.trim();
    }

    /**
     * 子明细净额：实收减已退。
     *
     * @param detail 自付子明细
     * @return 行净额
     */
    private BigDecimal resolveDetailNetAmount(PaymentRecDetailQueryModel detail) {
        BigDecimal amount = detail.getAmount();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal returnAmount = detail.getReturnAmount();
        if (returnAmount == null) {
            returnAmount = BigDecimal.ZERO;
        }
        return amount.subtract(returnAmount);
    }

    /**
     * 是否逆向退费 payment（门诊候选行）。
     *
     * @param item 候选 payment 行
     * @return 是否逆向
     */
    private boolean isReversePayment(DailySettlementPaymentItemDto item) {
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            item.getPaymentEnum(),
            item.getRelationId(),
            item.getStatusEnum());
    }

    /**
     * 是否逆向退费 payment（住院候选行）。
     *
     * @param item 候选 payment 行
     * @return 是否逆向
     */
    private boolean isReverseInpatientPayment(InpatientDailySettlementPaymentItemDto item) {
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            item.getPaymentEnum(),
            item.getRelationId(),
            item.getStatusEnum());
    }
}
