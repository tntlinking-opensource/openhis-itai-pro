/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.support;

import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 出院校对费用时间门禁（AS-IP-NUR-003 **BR-IP-AUD-017**）。
 *
 * <p>校对出院医嘱（开嘱 ACTIVE）前：扫描本就诊<strong>净生效</strong>收费明细（冲销留痕成对轧差后净额 &gt; 0）；
 * 比对轴与费用明细同轴——客户可见业务时间（Procedure.recorded / 发放展示 / 滚费 occurrence）。
 * 已整行冲销（正向+负向净额为 0）不参与门禁；任一净生效费用业务时间严格晚于计划出院 → 失败并列项目。</p>
 */
@Component
@RequiredArgsConstructor
public class DischargeAuditChargeTimeGate {

    /** 提示中最多展开的费用行数，超出以「等共 N 项」收尾，避免消息过长。 */
    private static final int MAX_DETAIL_LINES_IN_MESSAGE = 20;

    /** 费用时间展示格式。 */
    private static final DateTimeFormatter FEE_TIME_DISPLAY =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 收费明细读侧（按就诊批量拉费用）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 净剩 Procedure.recorded（客户可见轴，与费用明细同口径）。 */
    private final ProcedureQueryService procedureQueryService;

    /** 耗材主发放展示时刻（护士划价客户可见轴）。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 产品展示名批量解析（费用行提示用）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 断言本就诊无<strong>净生效</strong>费用业务时间晚于计划出院时间；有则抛 **EX-IP-AUD-205**。
     *
     * <p>冲销留痕按 {@code refund_id} 轧差：负向行与净额≤0 的正向行不参与比对。</p>
     *
     * @param encounterId          住院就诊主键
     * @param plannedDischargeTime 计划出院时间（待校出院嘱 {@code occurrence_start_time}）
     * @throws BusinessException 存在晚于出院时间的净生效费用时
     */
    public void assertNoChargesAfterPlannedDischarge(
        Long encounterId,
        LocalDateTime plannedDischargeTime) {
        // Gate：缺就诊或出院时间则无法比对，由上游编排保证；此处直接放行避免误拦非出院路径
        if (encounterId == null || plannedDischargeTime == null) {
            return;
        }
        // Pre：装载本就诊有效费用（排除反结链上已被替代的历史 SETTLED）
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        cmd.setStatusEnumIn(this.dischargeAuditChargeStatusCodes());
        cmd.setExcludeSupersededSettledLines(Boolean.TRUE);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        // Pre：冲销负向行按 refund_id 累加，供正向行轧差净额（整行冲销净额≤0 不拦）
        Map<Long, BigDecimal> offsetTotalBySourceId = this.mapOffsetTotalBySourceId(chargeItems);
        // Pre：批量解析客户可见业务时间（与 AS-IP-NUR-006 费用明细同轴）
        CustomerVisibleTimeMaps timeMaps = this.loadCustomerVisibleTimeMaps(encounterId, chargeItems);
        List<AfterDischargeFeeLine> afterDischarge = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            // 空行跳过，避免 NPE
            if (chargeItem == null) {
                continue;
            }
            // Gate：负向冲销行本身不作为「费用」拦出院校对
            if (this.isOffsetChargeLine(chargeItem)) {
                continue;
            }
            // Gate：已被冲销轧到净额≤0 的正向行视为未生效，不拦
            if (!this.hasPositiveNetCharge(chargeItem, offsetTotalBySourceId)) {
                continue;
            }
            Optional<LocalDateTime> feeTimeOpt =
                this.resolveCustomerVisibleFeeTime(chargeItem, timeMaps);
            // Pre：无法解析客户轴时回落 occurrence，避免漏拦
            LocalDateTime feeTime = feeTimeOpt.orElse(null);
            if (feeTime == null) {
                feeTime = chargeItem.getOccurrenceTime();
            }
            // 仍无时刻则无法比对，跳过该行
            if (feeTime == null) {
                continue;
            }
            // 严格晚于：等于出院时刻允许（记进含出）
            if (!feeTime.isAfter(plannedDischargeTime)) {
                continue;
            }
            afterDischarge.add(new AfterDischargeFeeLine(chargeItem, feeTime));
        }
        // Gate：无越界费用则通过
        if (afterDischarge.isEmpty()) {
            return;
        }
        // Asm：按费用业务时间升序拼提示，便于护士按时间改价/改出院
        afterDischarge.sort(Comparator.comparing(AfterDischargeFeeLine::feeTime));
        String message = this.buildAfterDischargeMessage(plannedDischargeTime, afterDischarge);
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_AUD_205,
            message);
    }

    /**
     * 出院校对费用扫描状态白名单（待收费/待结算/已结算/退款中）。
     *
     * <p>不含 {@code REFUNDED}：已退历史行不参与出院校对时间门禁。</p>
     *
     * @return 状态 code 列表
     */
    private List<String> dischargeAuditChargeStatusCodes() {
        return List.of(
            ChargeItemStatus.PLANNED.getCode(),
            ChargeItemStatus.PENDING_SETTLEMENT.getCode(),
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDING.getCode());
    }

    /**
     * 按被冲销源主键累加负向冲销行金额（{@code refund_id} → Σ 负向 {@code total_price}）。
     *
     * @param chargeItems 本就诊有效费用
     * @return 源收费主键 → 冲销金额合计（通常为负）
     */
    private Map<Long, BigDecimal> mapOffsetTotalBySourceId(List<ChargeItem> chargeItems) {
        Map<Long, BigDecimal> offsetTotalBySourceId = new HashMap<>();
        // Gate：空列表直接返回，避免无意义遍历
        if (chargeItems == null || chargeItems.isEmpty()) {
            return offsetTotalBySourceId;
        }
        for (ChargeItem chargeItem : chargeItems) {
            // 仅累加负向冲销行
            if (chargeItem == null || !this.isOffsetChargeLine(chargeItem)) {
                continue;
            }
            Long sourceId = chargeItem.getRefundId();
            // 无链回源行则无法轧差，跳过（仍靠负向行自身被 isOffset 剔除）
            if (sourceId == null) {
                continue;
            }
            BigDecimal lineTotal = chargeItem.getTotalPrice();
            // 缺金额按 0，避免 NPE 打断整批校对
            if (lineTotal == null) {
                lineTotal = BigDecimal.ZERO;
            }
            BigDecimal existing = offsetTotalBySourceId.get(sourceId);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            offsetTotalBySourceId.put(sourceId, existing.add(lineTotal));
        }
        return offsetTotalBySourceId;
    }

    /**
     * 是否冲销/负向留痕行（数量或金额为负；与费用明细「护士冲销」识别一致，不以仅有 refund_id 判定——
     * 反结再生行亦带 {@code refund_id} 但金额为正，须参与净生效门禁）。
     *
     * @param chargeItem 收费明细
     * @return 负向冲销行时 true
     */
    private boolean isOffsetChargeLine(ChargeItem chargeItem) {
        // 空行非冲销
        if (chargeItem == null) {
            return false;
        }
        BigDecimal quantityValue = chargeItem.getQuantityValue();
        // 数量为负 → 冲销留痕
        if (quantityValue != null && quantityValue.signum() < 0) {
            return true;
        }
        BigDecimal totalPrice = chargeItem.getTotalPrice();
        // 金额为负 → 冲销留痕（与数量判定正交，防缺数量行）
        if (totalPrice != null && totalPrice.signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 正向行轧差冲销后是否仍有正净额（净生效费用）。
     *
     * @param chargeItem             候选正向行
     * @param offsetTotalBySourceId  源 id → 冲销金额合计
     * @return 净额 &gt; 0 时 true
     */
    private boolean hasPositiveNetCharge(
        ChargeItem chargeItem,
        Map<Long, BigDecimal> offsetTotalBySourceId) {
        // 无主键无法关联冲销，视为非净生效
        if (chargeItem == null || chargeItem.getId() == null) {
            return false;
        }
        BigDecimal ownTotal = chargeItem.getTotalPrice();
        if (ownTotal == null) {
            ownTotal = BigDecimal.ZERO;
        }
        BigDecimal offsetTotal = BigDecimal.ZERO;
        if (offsetTotalBySourceId != null) {
            BigDecimal mapped = offsetTotalBySourceId.get(chargeItem.getId());
            // 无冲销映射则净额=自身金额
            if (mapped != null) {
                offsetTotal = mapped;
            }
        }
        // 净额 = 正向 + 负向冲销；≤0 视为已冲销干净
        return ownTotal.add(offsetTotal).compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 预取本批费用解析客户可见轴所需的 Procedure.recorded / 发放展示 Map。
     *
     * @param encounterId 就诊主键
     * @param chargeItems 本就诊有效费用
     * @return 三组时间 Map
     */
    private CustomerVisibleTimeMaps loadCustomerVisibleTimeMaps(
        Long encounterId,
        List<ChargeItem> chargeItems) {
        Map<Long, LocalDateTime> netProcedureRecordedById =
            procedureQueryService.mapNetCompletedRecordedTimeByEncounterId(encounterId);
        Set<Long> nursePricingServiceReqIds = new HashSet<>();
        Set<Long> nursePricingDeviceReqIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            // 无服务锚点则无法按 Request 取展示轴
            if (chargeItem == null || chargeItem.getServiceId() == null) {
                continue;
            }
            // 仅护士/医技划价需要按 Request 回查展示轴
            if (!GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
                && !GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())) {
                continue;
            }
            // 诊疗划价 → ServiceRequest 主键集
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                nursePricingServiceReqIds.add(chargeItem.getServiceId());
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                // 耗材划价 → DeviceRequest 主键集
                nursePricingDeviceReqIds.add(chargeItem.getServiceId());
            }
        }
        Map<Long, LocalDateTime> nursePricingRecordedByServiceReqId =
            procedureQueryService.mapNetCompletedRecordedTimeByServiceRequestIds(
                List.of(encounterId), nursePricingServiceReqIds);
        Map<Long, LocalDateTime> deviceDisplayTimeByReqId =
            deviceDispenseQueryService.mapPrimaryDisplayTimeByDeviceReqIds(nursePricingDeviceReqIds);
        return new CustomerVisibleTimeMaps(
            netProcedureRecordedById,
            nursePricingRecordedByServiceReqId,
            deviceDisplayTimeByReqId);
    }

    /**
     * 解析单行客户可见费用时间（对齐费用明细 {@code resolveCustomerVisibleBusinessTime}）。
     *
     * @param chargeItem 收费明细
     * @param timeMaps   预取 Map
     * @return 客户可见时刻；无法解析时 empty（调用方再回落 occurrence）
     */
    private Optional<LocalDateTime> resolveCustomerVisibleFeeTime(
        ChargeItem chargeItem,
        CustomerVisibleTimeMaps timeMaps) {
        // 缺实体或预取 Map 无法解析
        if (chargeItem == null || timeMaps == null) {
            return Optional.empty();
        }
        // 滚费：客户轴 = occurrence（落账业务日时刻）
        if (GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            // 滚费缺 occurrence 则无法比对
            if (chargeItem.getOccurrenceTime() == null) {
                return Optional.empty();
            }
            return Optional.of(chargeItem.getOccurrenceTime());
        }
        // 护士/医技划价耗材：客户轴 = 主发放展示时刻
        if ((GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
                || GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum()))
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
            // 无 DeviceRequest 锚点则无发放展示轴
            if (chargeItem.getServiceId() == null) {
                return Optional.empty();
            }
            LocalDateTime dispenseDisplay =
                timeMaps.deviceDisplayTimeByReqId().get(chargeItem.getServiceId());
            // 发放单未回写展示时刻时交由调用方回落 occurrence
            if (dispenseDisplay == null) {
                return Optional.empty();
            }
            return Optional.of(dispenseDisplay);
        }
        // 优先本行挂接 Procedure 的净剩 recorded（医嘱执行/诊疗划价）
        Long procedureId = chargeItem.getProcedureId();
        if (procedureId != null) {
            LocalDateTime recordedTime = timeMaps.netProcedureRecordedById().get(procedureId);
            if (recordedTime != null) {
                return Optional.of(recordedTime);
            }
            // 医嘱执行缺 recorded 时回落 occurrence，避免有账却漏检
            if (GenerateSource.ORDER_EXECUTE.getCode().equals(chargeItem.getGenerateSourceEnum())
                && chargeItem.getOccurrenceTime() != null) {
                return Optional.of(chargeItem.getOccurrenceTime());
            }
            // 已对冲 Procedure 或无 recorded：不对用户轴硬回落
            return Optional.empty();
        }
        // 护士划价诊疗缺 procedure_id：按 ServiceRequest 取净剩 recorded
        if (GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
            && chargeItem.getServiceId() != null) {
            LocalDateTime recordedByRequest =
                timeMaps.nursePricingRecordedByServiceReqId().get(chargeItem.getServiceId());
            if (recordedByRequest != null) {
                return Optional.of(recordedByRequest);
            }
        }
        // 其它来源无法解析客户轴
        return Optional.empty();
    }

    /**
     * 组装「费用晚于出院」用户提示文案（含项目名与客户可见费用时间）。
     *
     * @param plannedDischargeTime 计划出院时间
     * @param afterDischarge       已排序的越界费用行
     * @return 非空提示文案
     */
    private String buildAfterDischargeMessage(
        LocalDateTime plannedDischargeTime,
        List<AfterDischargeFeeLine> afterDischarge) {
        List<ChargeItem> chargeItems = new ArrayList<>();
        for (AfterDischargeFeeLine line : afterDischarge) {
            chargeItems.add(line.chargeItem());
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = this.loadProductDisplayByCharges(chargeItems);
        String dischargeDisplay = plannedDischargeTime.format(FEE_TIME_DISPLAY);
        StringBuilder body = new StringBuilder();
        int shown = 0;
        for (AfterDischargeFeeLine line : afterDischarge) {
            if (shown >= MAX_DETAIL_LINES_IN_MESSAGE) {
                break;
            }
            if (shown > 0) {
                body.append('；');
            }
            String itemName = this.resolveChargeDisplayName(line.chargeItem(), productByKey);
            String timeText = line.feeTime().format(FEE_TIME_DISPLAY);
            body.append(itemName).append('（').append(timeText).append('）');
            shown = shown + 1;
        }
        int total = afterDischarge.size();
        // 超出展开上限时补「等共 N 项」
        if (total > MAX_DETAIL_LINES_IN_MESSAGE) {
            body.append("等共").append(total).append("项");
        }
        return "校对失败：以下费用发生时间晚于出院时间（"
            + dischargeDisplay
            + "）："
            + body;
    }

    /**
     * 批量装载越界费用行对应产品展示名。
     *
     * @param chargeItems 越界费用行
     * @return 复合键 → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductDisplayByCharges(List<ChargeItem> chargeItems) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupeKeys = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            String productTable = chargeItem.getProductTable();
            Long productId = chargeItem.getProductId();
            if (productId == null || StringUtils.isEmpty(productTable)) {
                continue;
            }
            String dedupeKey = ProductDefinitionKeyUtil.buildCompositeKey(productTable.trim(), productId);
            if (dedupeKey == null) {
                continue;
            }
            if (!dedupeKeys.add(dedupeKey)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(productTable.trim());
            lookup.setProductDefinitionId(productId);
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 解析单行费用展示名（优先产品通用名，回落「未命名费用」）。
     *
     * @param chargeItem   费用行
     * @param productByKey 产品展示 Map
     * @return 非空展示名
     */
    private String resolveChargeDisplayName(
        ChargeItem chargeItem,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        // 空行无法解析品名
        if (chargeItem == null) {
            return "未命名费用";
        }
        String productTable = chargeItem.getProductTable();
        Long productId = chargeItem.getProductId();
        // 缺产品锚点时用占位名，仍能提示费用时间
        if (productId == null || StringUtils.isEmpty(productTable)) {
            return "未命名费用";
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(productTable.trim(), productId);
        if (key == null || productByKey == null) {
            return "未命名费用";
        }
        ProductDefinitionDisplayDto product = productByKey.get(key);
        // 产品目录未命中（停用/删档）
        if (product == null) {
            return "未命名费用";
        }
        String productName = product.getProductName();
        if (StringUtils.isEmpty(productName)) {
            return "未命名费用";
        }
        return productName.trim();
    }

    /**
     * 客户可见时间预取结果（Procedure.recorded / 划价 Request recorded / 耗材发放展示）。
     *
     * @param netProcedureRecordedById           就诊净剩 ProcedureId → recorded
     * @param nursePricingRecordedByServiceReqId 护士划价 ServiceRequestId → recorded
     * @param deviceDisplayTimeByReqId           DeviceRequestId → 发放展示
     */
    private record CustomerVisibleTimeMaps(
        Map<Long, LocalDateTime> netProcedureRecordedById,
        Map<Long, LocalDateTime> nursePricingRecordedByServiceReqId,
        Map<Long, LocalDateTime> deviceDisplayTimeByReqId) {
    }

    /**
     * 越界费用行（实体 + 已解析的客户可见费用时间）。
     *
     * @param chargeItem 收费明细
     * @param feeTime    用于比对与提示的费用业务时间
     */
    private record AfterDischargeFeeLine(ChargeItem chargeItem, LocalDateTime feeTime) {
    }
}
