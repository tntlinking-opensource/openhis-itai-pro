/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.assembler;

import com.openhis.health.application.outpatient.financial.feedetail.assembler.OutpatientFeeDetailAssembler;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.ChargeItemOrderingDoctorFromRequestSupport;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 住院费用明细报表就诊块装配（AS-RPT-009；金额列委托 {@link OutpatientFeeDetailAssembler}）。
 */
@Component
@RequiredArgsConstructor
public class InpatientFeeDetailReportBlockAssembler {

    /** 就诊挂号科室、busNo 读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者姓名读侧。 */
    private final PatientQueryService patientQueryService;

    /** 科室名称读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 开单医生/收费员读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 开单医生自三类 Request 批量解析。 */
    private final ChargeItemOrderingDoctorFromRequestSupport chargeItemOrderingDoctorFromRequestSupport;

    /** 执行人：有 procedure 时取主执行人，否则回落账单 performer_id（临嘱/长嘱同口径）。 */
    private final ProcedureQueryService procedureQueryService;

    /** 产品定义批量读侧。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 号源类型单条回落读侧。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** 术语单位译文。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 定价行 fin_type 解析。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 历史进价解析（BR-PRICE-SRC-001）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装换算比与拆零展示单价（BR-MON-PKG-001）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 金额列装配（与门诊费用明细同构）。 */
    private final OutpatientFeeDetailAssembler outpatientFeeDetailAssembler;

    /** 费用性质 + medType 报表上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * 将域层就诊块批量 enrichment 并装配为 DTO（默认不按 definition 合并行）。
     *
     * @param domainBlocks 域层就诊块
     * @param query        同批查询（含 payment 上下文）
     * @return 就诊块 DTO 列表
     */
    public List<OutpatientFeeDetailEncounterBlockDto> assembleEncounterBlockDtos(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks,
        OutpatientFeeDetailPageQuery query) {
        return this.assembleEncounterBlockDtos(domainBlocks, query, false);
    }

    /**
     * 将域层就诊块批量 enrichment 并装配为 DTO。
     *
     * <p>当 {@code mergeByDefinition=true} 时，仅在本批每个就诊块装配完行 DTO 后，按
     * {@code ChargeItem.definitionId + quantityUnit} 合并同块内行；{@code definitionId} 为空的行各自按
     * {@code chargeItemId} 分桶，避免误并。数量按收退符号净合计。页脚/全量 summary 不走本合并。</p>
     *
     * @param domainBlocks      域层就诊块（本页或导出扫描的一批）
     * @param query             同批查询（含 payment 上下文）
     * @param mergeByDefinition 是否按收费项目定义合并块内行
     * @return 就诊块 DTO 列表
     */
    public List<OutpatientFeeDetailEncounterBlockDto> assembleEncounterBlockDtos(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks,
        OutpatientFeeDetailPageQuery query,
        boolean mergeByDefinition) {
        if (domainBlocks == null || domainBlocks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> encounterIdsForContext = new LinkedHashSet<>();
        for (OutpatientFeeDetailEncounterBlockQueryModel domainBlock : domainBlocks) {
            if (domainBlock != null && domainBlock.getEncounterId() != null) {
                encounterIdsForContext.add(domainBlock.getEncounterId());
            }
        }
        Map<Long, PatientSettlementContextRow> contextByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIdsForContext);
        FeeDetailEnrichmentBundle bundle = this.loadEnrichmentBundle(domainBlocks);
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        List<OutpatientFeeDetailEncounterBlockDto> result = new ArrayList<>(domainBlocks.size());
        for (OutpatientFeeDetailEncounterBlockQueryModel domainBlock : domainBlocks) {
            if (domainBlock == null || domainBlock.getEncounterId() == null) {
                continue;
            }
            OutpatientFeeDetailEncounterBlockDto blockDto = new OutpatientFeeDetailEncounterBlockDto();
            blockDto.setEncounterId(domainBlock.getEncounterId());
            Encounter encounter = bundle.encounterById.get(domainBlock.getEncounterId());
            if (encounter != null) {
                blockDto.setBusNo(encounter.getBusNo());
                if (encounter.getStartTime() != null) {
                    blockDto.setEncounterDate(
                        encounter.getStartTime().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
                }
                if (encounter.getOrganizationId() != null) {
                    blockDto.setRegistrationDepartmentName(
                        bundle.organizationNameById.get(encounter.getOrganizationId()));
                }
            }
            Long patientId = domainBlock.getPatientId();
            if (patientId == null && encounter != null) {
                patientId = encounter.getPatientId();
            }
            if (patientId != null) {
                Patient patient = bundle.patientById.get(patientId);
                if (patient != null) {
                    blockDto.setPatientName(patient.getName());
                }
            }
            // Pre：医疗类别仍按就诊主诊；费用性质由行级账单账户装配
            PatientSettlementContextRow settlementContext = contextByEncounterId.get(domainBlock.getEncounterId());
            if (settlementContext != null) {
                blockDto.setMedType(settlementContext.getMedType());
                blockDto.setMedTypeDisplay(settlementContext.getMedTypeDisplay());
            }
            List<OutpatientFeeDetailLineDto> lineDtos = new ArrayList<>();
            // Pre：chargeItemId → 合并锚点（definitionId + 数量单位），供块内 BY_DEFINITION 分桶
            Map<Long, FeeDetailDefinitionMergeAnchor> mergeAnchorByChargeItemId = new HashMap<>();
            if (domainBlock.getLines() != null) {
                for (OutpatientFeeDetailChargeRowQueryModel row : domainBlock.getLines()) {
                    OutpatientFeeDetailLineDto lineDto = this.assembleSingleLine(row, bundle, unitLabelByCode);
                    if (lineDto != null) {
                        lineDtos.add(lineDto);
                    }
                    // 记录本行合并锚点；缺 chargeItemId 时无法参与同品规分桶
                    if (row != null && row.getChargeItem() != null && row.getChargeItem().getId() != null) {
                        ChargeItem chargeItem = row.getChargeItem();
                        FeeDetailDefinitionMergeAnchor anchor = new FeeDetailDefinitionMergeAnchor();
                        anchor.definitionId = chargeItem.getDefinitionId();
                        anchor.quantityUnit = chargeItem.getQuantityUnit();
                        mergeAnchorByChargeItemId.put(chargeItem.getId(), anchor);
                    }
                }
            }
            // Asm：同就诊块内按 definitionId+单位 净合并（仅当前批块，禁止全窗物化）
            if (mergeByDefinition && !lineDtos.isEmpty()) {
                lineDtos = this.mergeLinesByDefinition(lineDtos, mergeAnchorByChargeItemId);
            }
            if (!lineDtos.isEmpty()) {
                OutpatientFeeDetailLineDto firstLine = lineDtos.get(0);
                blockDto.setFeeNatureContractNo(firstLine.getFeeNatureContractNo());
                blockDto.setFeeNatureDisplay(firstLine.getFeeNatureDisplay());
            }
            // Asm：开立科室取块内首条账单 requesting_org_id
            if (domainBlock.getLines() != null && !domainBlock.getLines().isEmpty()) {
                for (OutpatientFeeDetailChargeRowQueryModel row : domainBlock.getLines()) {
                    if (row == null || row.getChargeItem() == null) {
                        continue;
                    }
                    Long requestingOrgId = row.getChargeItem().getRequestingOrgId();
                    if (requestingOrgId != null) {
                        blockDto.setRequestingOrgName(bundle.organizationNameById.get(requestingOrgId));
                        break;
                    }
                }
            }
            blockDto.setLines(lineDtos);
            // Asm：块小计在合并后重算，与展示行一致
            blockDto.setSubtotal(this.outpatientFeeDetailAssembler.assembleEncounterSubtotal(lineDtos));
            result.add(blockDto);
        }
        return result;
    }

    /**
     * 同就诊块内按 {@code ChargeItem.definitionId + quantityUnit} 合并明细行。
     *
     * <p>有 definitionId 的行按「定义+数量单位」分桶并净加总金额/数量；definitionId 为空时以
     * chargeItemId 单独成桶，避免不同无定义行被误并。事件字段（收费行/支付/结算时刻/开单人/执行人/收费人）
     * 在合并后清空。单位入键：禁止混单位数量代数加总。</p>
     *
     * @param lineDtos                    已装配的块内行
     * @param mergeAnchorByChargeItemId   chargeItemId → definitionId+单位
     * @return 合并后的行列表（保持首次出现顺序）
     */
    private List<OutpatientFeeDetailLineDto> mergeLinesByDefinition(
        List<OutpatientFeeDetailLineDto> lineDtos,
        Map<Long, FeeDetailDefinitionMergeAnchor> mergeAnchorByChargeItemId) {
        if (lineDtos == null || lineDtos.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, List<OutpatientFeeDetailLineDto>> buckets = new LinkedHashMap<>();
        for (OutpatientFeeDetailLineDto lineDto : lineDtos) {
            // 空行跳过，避免 NPE 污染分桶
            if (lineDto == null) {
                continue;
            }
            String bucketKey = this.resolveDefinitionMergeBucketKey(lineDto, mergeAnchorByChargeItemId);
            List<OutpatientFeeDetailLineDto> bucket = buckets.get(bucketKey);
            // 新桶：保持 Encounter 块内首次出现顺序
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets.put(bucketKey, bucket);
            }
            bucket.add(lineDto);
        }
        List<OutpatientFeeDetailLineDto> merged = new ArrayList<>(buckets.size());
        for (List<OutpatientFeeDetailLineDto> bucket : buckets.values()) {
            // 单行桶无需合并，原样保留事件字段
            if (bucket.size() == 1) {
                merged.add(bucket.get(0));
                continue;
            }
            merged.add(this.mergeDefinitionBucket(bucket));
        }
        return merged;
    }

    /**
     * 解析块内合并分桶键：有 definitionId 用 {@code D:{id}|U:{unit}}；否则用 {@code C:{chargeItemId}}。
     *
     * @param lineDto                   已装配行
     * @param mergeAnchorByChargeItemId chargeItemId → 合并锚点
     * @return 分桶键
     */
    private String resolveDefinitionMergeBucketKey(
        OutpatientFeeDetailLineDto lineDto,
        Map<Long, FeeDetailDefinitionMergeAnchor> mergeAnchorByChargeItemId) {
        Long chargeItemId = lineDto.getChargeItemId();
        FeeDetailDefinitionMergeAnchor anchor = null;
        // 有 chargeItemId 时取域层 definitionId + 数量单位
        if (chargeItemId != null && mergeAnchorByChargeItemId != null) {
            anchor = mergeAnchorByChargeItemId.get(chargeItemId);
        }
        // 有定义：同 definition+单位 可并；无定义：按收费行各自成桶
        if (anchor != null && anchor.definitionId != null) {
            String unitPart = anchor.quantityUnit;
            if (unitPart == null) {
                unitPart = "\u0000";
            }
            return "D:" + anchor.definitionId + "|U:" + unitPart;
        }
        if (chargeItemId != null) {
            return "C:" + chargeItemId;
        }
        // 缺主键的脏行各自隔离，避免互相吞并
        return "N:" + System.identityHashCode(lineDto);
    }

    /**
     * 将同一 definition 桶内多行合并为一行：金额代数加总后 {@code scaleCalc}；
     * 展示/品规取首样本；事件字段清空；方向/费用性质/单位仅全同保留；单价仅唯一非空值时保留。
     *
     * @param bucket 同桶多行（size≥2）
     * @return 合并后的展示行
     */
    private OutpatientFeeDetailLineDto mergeDefinitionBucket(List<OutpatientFeeDetailLineDto> bucket) {
        OutpatientFeeDetailLineDto first = bucket.get(0);
        OutpatientFeeDetailLineDto merged = new OutpatientFeeDetailLineDto();
        // Asm：品规/大类/医保展示取首样本（同 definition 预期一致）
        merged.setFeeCategoryCode(first.getFeeCategoryCode());
        merged.setFeeCategoryDisplay(first.getFeeCategoryDisplay());
        merged.setSubCategoryCode(first.getSubCategoryCode());
        merged.setSubCategoryDisplay(first.getSubCategoryDisplay());
        merged.setProductName(first.getProductName());
        merged.setProductCode(first.getProductCode());
        merged.setSpecification(first.getSpecification());
        merged.setInsuranceLevelDisplay(first.getInsuranceLevelDisplay());
        merged.setDiscountRateDisplay(first.getDiscountRateDisplay());
        // Asm：事件字段清空——合并后不再对应单一收费行/支付/人员
        merged.setChargeItemId(null);
        merged.setPaymentId(null);
        merged.setBillDate(null);
        merged.setOrderingDoctorId(null);
        merged.setOrderingDoctorName(null);
        merged.setPerformerId(null);
        merged.setPerformerName(null);
        merged.setCashierId(null);
        merged.setCashierName(null);
        // Asm：收退方向全同保留，否则置空（收退混桶不再标方向）
        merged.setTradeDirection(this.resolveUnanimousTradeDirection(bucket));
        // Asm：费用性质全同保留，否则置空
        merged.setFeeNatureContractNo(this.resolveUnanimousFeeNatureContractNo(bucket));
        merged.setFeeNatureDisplay(this.resolveUnanimousFeeNatureDisplay(bucket));
        // Asm：数量单位全同保留，否则置空
        merged.setQuantityUnitDisplay(this.resolveUnanimousQuantityUnitDisplay(bucket));
        // Asm：单价仅唯一非空值时保留，禁止合计÷数量反推
        merged.setSaleUnitPrice(this.resolveSingleDistinctUnitPrice(bucket, true));
        merged.setPurchaseUnitPrice(this.resolveSingleDistinctUnitPrice(bucket, false));
        // Asm：数量净合计（退费行取负，对齐汇总 Tab addSignedQuantity）；金额列已带符号直接加总
        BigDecimal quantitySum = BigDecimal.ZERO;
        BigDecimal baseSum = BigDecimal.ZERO;
        BigDecimal discountSum = BigDecimal.ZERO;
        BigDecimal receivableSum = BigDecimal.ZERO;
        BigDecimal discountedSum = BigDecimal.ZERO;
        BigDecimal purchaseSum = BigDecimal.ZERO;
        BigDecimal saleSum = BigDecimal.ZERO;
        BigDecimal actualReceivedSum = BigDecimal.ZERO;
        BigDecimal insuranceSum = BigDecimal.ZERO;
        BigDecimal selfPaySum = BigDecimal.ZERO;
        for (OutpatientFeeDetailLineDto line : bucket) {
            quantitySum = quantitySum.add(this.resolveSignedQuantityForMerge(line));
            baseSum = baseSum.add(MonetaryAmountSupport.nullToZero(line.getBaseAmount()));
            discountSum = discountSum.add(MonetaryAmountSupport.nullToZero(line.getDiscountAmount()));
            receivableSum = receivableSum.add(MonetaryAmountSupport.nullToZero(line.getReceivableAmount()));
            discountedSum = discountedSum.add(MonetaryAmountSupport.nullToZero(line.getDiscountedAmount()));
            purchaseSum = purchaseSum.add(MonetaryAmountSupport.nullToZero(line.getPurchaseAmount()));
            saleSum = saleSum.add(MonetaryAmountSupport.nullToZero(line.getSaleAmount()));
            actualReceivedSum = actualReceivedSum.add(MonetaryAmountSupport.nullToZero(line.getActualReceivedAmount()));
            insuranceSum = insuranceSum.add(MonetaryAmountSupport.nullToZero(line.getInsuranceAmount()));
            selfPaySum = selfPaySum.add(MonetaryAmountSupport.nullToZero(line.getSelfPayAmount()));
        }
        merged.setQuantityValue(MonetaryAmountSupport.scaleCalc(quantitySum));
        merged.setBaseAmount(MonetaryAmountSupport.scaleCalc(baseSum));
        merged.setDiscountAmount(MonetaryAmountSupport.scaleCalc(discountSum));
        merged.setReceivableAmount(MonetaryAmountSupport.scaleCalc(receivableSum));
        merged.setDiscountedAmount(MonetaryAmountSupport.scaleCalc(discountedSum));
        merged.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(purchaseSum));
        merged.setSaleAmount(MonetaryAmountSupport.scaleCalc(saleSum));
        merged.setActualReceivedAmount(MonetaryAmountSupport.scaleCalc(actualReceivedSum));
        merged.setInsuranceAmount(MonetaryAmountSupport.scaleCalc(insuranceSum));
        merged.setSelfPayAmount(MonetaryAmountSupport.scaleCalc(selfPaySum));
        return merged;
    }

    /**
     * 桶内收退方向：全同则返回该值，否则 null。
     *
     * @param bucket 同桶行
     * @return 一致的 tradeDirection 或 null
     */
    private String resolveUnanimousTradeDirection(List<OutpatientFeeDetailLineDto> bucket) {
        String first = bucket.get(0).getTradeDirection();
        for (int i = 1; i < bucket.size(); i++) {
            // 任一不一致则不再标收退方向
            if (!Objects.equals(first, bucket.get(i).getTradeDirection())) {
                return null;
            }
        }
        return first;
    }

    /**
     * 桶内费用性质合同号：全同则保留，否则 null。
     *
     * @param bucket 同桶行
     * @return 一致的 feeNatureContractNo 或 null
     */
    private String resolveUnanimousFeeNatureContractNo(List<OutpatientFeeDetailLineDto> bucket) {
        String first = bucket.get(0).getFeeNatureContractNo();
        for (int i = 1; i < bucket.size(); i++) {
            // 混性质桶清空合同号，避免误导对账
            if (!Objects.equals(first, bucket.get(i).getFeeNatureContractNo())) {
                return null;
            }
        }
        return first;
    }

    /**
     * 桶内费用性质展示名：全同则保留，否则 null。
     *
     * @param bucket 同桶行
     * @return 一致的 feeNatureDisplay 或 null
     */
    private String resolveUnanimousFeeNatureDisplay(List<OutpatientFeeDetailLineDto> bucket) {
        String first = bucket.get(0).getFeeNatureDisplay();
        for (int i = 1; i < bucket.size(); i++) {
            // 混性质桶清空展示名
            if (!Objects.equals(first, bucket.get(i).getFeeNatureDisplay())) {
                return null;
            }
        }
        return first;
    }

    /**
     * 桶内数量单位展示：全同则保留，否则 null。
     *
     * @param bucket 同桶行
     * @return 一致的 quantityUnitDisplay 或 null
     */
    private String resolveUnanimousQuantityUnitDisplay(List<OutpatientFeeDetailLineDto> bucket) {
        String first = bucket.get(0).getQuantityUnitDisplay();
        for (int i = 1; i < bucket.size(); i++) {
            // 单位不一致时清空，禁止混单位后仍显示单一单位
            if (!Objects.equals(first, bucket.get(i).getQuantityUnitDisplay())) {
                return null;
            }
        }
        return first;
    }

    /**
     * 合并用符号数量：落库 quantity 为正；退费行（tradeDirection=REFUND）取负，与汇总 Tab 净数量同口径。
     *
     * @param line 已装配明细行
     * @return 带符号数量（计算刻度，未再 scale）
     */
    private BigDecimal resolveSignedQuantityForMerge(OutpatientFeeDetailLineDto line) {
        BigDecimal quantity = MonetaryAmountSupport.nullToZero(line.getQuantityValue());
        // 退费行冲减净数量
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(line.getTradeDirection())) {
            return quantity.negate();
        }
        return quantity;
    }

    /**
     * 桶内单价：仅当非空值集合按绝对值恰好一个时保留该绝对值；否则 null（禁止合计÷数量）。
     *
     * <p>退费行单价为负展示，合并净合计时按绝对值判同价，避免「+21 与 -21」被误判为异价清空。</p>
     *
     * @param bucket        同桶行
     * @param saleUnitPrice true=售价单价；false=进价单价
     * @return 唯一非空单价绝对值或 null
     */
    private BigDecimal resolveSingleDistinctUnitPrice(
        List<OutpatientFeeDetailLineDto> bucket,
        boolean saleUnitPrice) {
        BigDecimal uniqueAbs = null;
        for (OutpatientFeeDetailLineDto line : bucket) {
            BigDecimal candidate;
            // 售价/进价单价分列取数
            if (saleUnitPrice) {
                candidate = line.getSaleUnitPrice();
            } else {
                candidate = line.getPurchaseUnitPrice();
            }
            // 空单价不参与「唯一非空」判定
            if (candidate == null) {
                continue;
            }
            BigDecimal absCandidate = candidate.abs();
            // 首次非空：记为候选
            if (uniqueAbs == null) {
                uniqueAbs = absCandidate;
                continue;
            }
            // 与已有候选按绝对值不等 → 异价，清空
            if (uniqueAbs.compareTo(absCandidate) != 0) {
                return null;
            }
        }
        return uniqueAbs;
    }

    /**
     * 全量命中行合计（Assembler 权威）。
     *
     * @param allLines 全量明细行
     * @return 金额 summary
     */
    public OutpatientFeeDetailAmountSummaryDto assembleGlobalSummary(List<OutpatientFeeDetailLineDto> allLines) {
        return this.outpatientFeeDetailAssembler.assembleEncounterSubtotal(allLines);
    }

    /**
     * 由域层候选行轻量装配全量金额 summary，禁止为页脚全量 enrichment。
     *
     * <p>明细分页页脚不跑就诊块展示 enrich；进价合计由命中 scope 批量历史进价加总
     * （与门诊 {@code OutpatientFeeDetailAppService#buildGlobalSummary} 同构）。</p>
     *
     * @param rows 全量命中候选行
     * @return 合计 DTO（结算刻度 2 位）
     */
    public OutpatientFeeDetailAmountSummaryDto assembleGlobalSummaryFromChargeRows(
        List<OutpatientFeeDetailChargeRowQueryModel> rows) {
        // Asm：售价/实收/医保轻量加总；进价 Map 空（本方法非明细分页页脚主路径）
        OutpatientFeeDetailAmountSummaryDto summary =
            this.outpatientFeeDetailAssembler.assembleGlobalSummaryFromChargeRows(rows, Map.of());
        summary.setPurchaseAmountTotal(null);
        return summary;
    }

    /**
     * 明细分页页脚：售价 + 进价合计（与门诊同构）。
     *
     * @param saleAmountTotal     域层轻量加总后的售价合计
     * @param purchaseAmountTotal 命中 scope 历史进价合计
     * @return 页脚 summary
     */
    public OutpatientFeeDetailAmountSummaryDto assembleDetailPageFooterSaleSummary(
        BigDecimal saleAmountTotal,
        BigDecimal purchaseAmountTotal) {
        return this.outpatientFeeDetailAssembler.assembleDetailPageFooterSaleSummary(
            saleAmountTotal, purchaseAmountTotal);
    }

    /**
     * 装配单条明细行。
     *
     * @param row             域层候选行
     * @param bundle          enrichment 上下文
     * @param unitLabelByCode 单位码 → 展示名
     * @return 明细行 DTO；缺 chargeItem 时 null
     */
    private OutpatientFeeDetailLineDto assembleSingleLine(
        OutpatientFeeDetailChargeRowQueryModel row,
        FeeDetailEnrichmentBundle bundle,
        Map<String, String> unitLabelByCode) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        Long chargeItemId = row.getChargeItem().getId();
        OutpatientFeeDetailPaymentContextQueryModel paymentCtx = row.getPaymentContext();
        ProductDefinitionDisplayDto product = this.resolveProduct(row, bundle);
        if (product == null) {
            throw new IllegalArgumentException("missing product definition for charge item " + chargeItemId);
        }
        String finTypeCode = null;
        String finTypeDisplay = null;
        if (chargeItemId != null && bundle.finTypeCodeByChargeItemId != null) {
            finTypeCode = bundle.finTypeCodeByChargeItemId.get(chargeItemId);
        }
        if (StringUtils.isNotEmpty(finTypeCode) && bundle.finTypeLabelByCode != null) {
            finTypeDisplay = bundle.finTypeLabelByCode.get(finTypeCode.trim());
        }
        if (StringUtils.isEmpty(finTypeDisplay) && StringUtils.isNotEmpty(finTypeCode)) {
            finTypeDisplay = finTypeCode.trim();
        }
        OutpatientFeeDetailLineDto lineDto =
            this.outpatientFeeDetailAssembler.assembleLineAmounts(
                row, paymentCtx, product, finTypeCode, finTypeDisplay);
        if (lineDto == null) {
            return null;
        }
        String unitCode = row.getChargeItem().getQuantityUnit();
        if (StringUtils.isNotEmpty(unitCode)) {
            String unitDisplay = unitLabelByCode.get(unitCode.trim());
            if (StringUtils.isNotEmpty(unitDisplay)) {
                lineDto.setQuantityUnitDisplay(unitDisplay);
            } else {
                lineDto.setQuantityUnitDisplay(unitCode.trim());
            }
        }
        Long orderingDoctorId = null;
        if (chargeItemId != null && bundle.orderingDoctorByChargeItemId != null) {
            orderingDoctorId = bundle.orderingDoctorByChargeItemId.get(chargeItemId);
        }
        if (orderingDoctorId != null) {
            lineDto.setOrderingDoctorId(orderingDoctorId);
            lineDto.setOrderingDoctorName(bundle.practitionerNameById.get(orderingDoctorId));
        }
        // Asm：执行人 — 临嘱/长嘱同口径：有执行记录优先 Procedure 主执行人，否则账单 performer_id
        Long performerId = null;
        if (chargeItemId != null && bundle.performerByChargeItemId != null) {
            performerId = bundle.performerByChargeItemId.get(chargeItemId);
        }
        if (performerId != null) {
            lineDto.setPerformerId(performerId);
            lineDto.setPerformerName(bundle.practitionerNameById.get(performerId));
        }
        if (lineDto.getCashierId() != null) {
            lineDto.setCashierName(bundle.practitionerNameById.get(lineDto.getCashierId()));
        }
        // Asm：费用性质跟收费行挂账账户
        Long accountId = row.getChargeItem().getAccountId();
        if (accountId != null && bundle.feeNatureByAccountId != null) {
            PatientSettlementContextRow feeNature = bundle.feeNatureByAccountId.get(accountId);
            if (feeNature != null) {
                lineDto.setFeeNatureContractNo(feeNature.getFeeNatureContractNo());
                lineDto.setFeeNatureDisplay(feeNature.getFeeNatureDisplay());
            }
        }
        // Asm：回填历史进价（与售价同号；诊疗无进价留空）
        this.applyPurchasePrices(lineDto, row.getChargeItem(), bundle);
        return lineDto;
    }

    /**
     * 将批量进价回填到明细行（退费行与售价同号；诊疗无进价则留空）。
     *
     * <p>单价与收费数量单位对齐（包装操作单位展示包装价；拆零操作单位才折展示拆零价）；行额为包装锚合计（已在 bundle 中算好）。</p>
     *
     * @param lineDto      已装配售价/实收的明细行
     * @param chargeItem   收费明细实体
     * @param bundle       enrichment 上下文
     */
    private void applyPurchasePrices(
        OutpatientFeeDetailLineDto lineDto,
        ChargeItem chargeItem,
        FeeDetailEnrichmentBundle bundle) {
        BigDecimal signMultiplier = BigDecimal.ONE;
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(lineDto.getTradeDirection())) {
            signMultiplier = new BigDecimal("-1");
        }
        Long chargeItemId = null;
        // 有账单实体时取主键，供进价 Map 回填
        if (chargeItem != null) {
            chargeItemId = chargeItem.getId();
        }
        BigDecimal purchaseUnit = null;
        BigDecimal purchaseAmount = null;
        if (chargeItemId != null) {
            purchaseUnit = bundle.purchaseUnitByChargeItemId.get(chargeItemId);
            purchaseAmount = bundle.purchaseAmountByChargeItemId.get(chargeItemId);
        }
        // 诊疗/挂号等无进价：不编 0，前端展示为空
        if (purchaseUnit == null && purchaseAmount == null) {
            lineDto.setPurchaseUnitPrice(null);
            lineDto.setPurchaseAmount(null);
            return;
        }
        BigDecimal ratio = null;
        if (chargeItemId != null && bundle.packageUnitRatioByChargeItemId != null) {
            ratio = bundle.packageUnitRatioByChargeItemId.get(chargeItemId);
        }
        ProductDefinitionDisplayDto product = null;
        if (chargeItem != null && bundle.productByCompositeKey != null) {
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null
                && lookup.getProductTableName() != null
                && lookup.getProductDefinitionId() != null) {
                String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (compositeKey != null) {
                    product = bundle.productByCompositeKey.get(compositeKey);
                }
            }
        }
        BigDecimal displayUnit = this.chargeItemPackageUnitRatioSupport.toChargeAlignedPurchaseDisplayUnitPrice(
            purchaseUnit, ratio, chargeItem, product);
        if (displayUnit != null) {
            lineDto.setPurchaseUnitPrice(
                MonetaryAmountSupport.applySignAtCalcScale(displayUnit, signMultiplier));
        } else {
            lineDto.setPurchaseUnitPrice(null);
        }
        if (purchaseAmount != null) {
            lineDto.setPurchaseAmount(
                MonetaryAmountSupport.applySignAtCalcScale(purchaseAmount, signMultiplier));
        } else {
            lineDto.setPurchaseAmount(null);
        }
    }

    /**
     * 批量加载就诊块 enrichment 上下文。
     *
     * @param domainBlocks 当前批域层就诊块
     * @return enrichment 上下文
     */
    private FeeDetailEnrichmentBundle loadEnrichmentBundle(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks) {
        FeeDetailEnrichmentBundle bundle = new FeeDetailEnrichmentBundle();
        List<ChargeItem> chargeItemsForFinType = new ArrayList<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        LinkedHashSet<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (OutpatientFeeDetailEncounterBlockQueryModel block : domainBlocks) {
            if (block == null) {
                continue;
            }
            if (block.getEncounterId() != null) {
                encounterIds.add(block.getEncounterId());
            }
            if (block.getPatientId() != null) {
                patientIds.add(block.getPatientId());
            }
            if (block.getLines() == null) {
                continue;
            }
            for (OutpatientFeeDetailChargeRowQueryModel row : block.getLines()) {
                if (row == null || row.getChargeItem() == null) {
                    continue;
                }
                chargeItemsForFinType.add(row.getChargeItem());
                if (row.getChargeItem().getAccountId() != null) {
                    accountIds.add(row.getChargeItem().getAccountId());
                }
                if (row.getChargeItem().getPatientId() != null) {
                    patientIds.add(row.getChargeItem().getPatientId());
                }
                // 开立科室名：纳入 organization 批量解析
                if (row.getChargeItem().getRequestingOrgId() != null) {
                    bundle.organizationIds.add(row.getChargeItem().getRequestingOrgId());
                }
                // 收费人姓名：仅已结 enterer_id（结算写入；合成上下文，不查 Payment）
                if (row.getChargeItem().getEntererId() != null) {
                    String statusCode = row.getChargeItem().getStatusEnum();
                    boolean settledOrRefunded =
                        ChargeItemStatus.SETTLED.getCode().equals(statusCode)
                            || ChargeItemStatus.REFUNDED.getCode().equals(statusCode);
                    if (settledOrRefunded) {
                        practitionerIds.add(row.getChargeItem().getEntererId());
                    }
                }
                if (row.getPaymentContext() != null && row.getPaymentContext().getCashierId() != null) {
                    practitionerIds.add(row.getPaymentContext().getCashierId());
                }
                ProductDefinitionBatchLookup lookup =
                    ChargeItemProductLookupHelper.buildLookupFromChargeItem(row.getChargeItem());
                if (lookup != null) {
                    productLookups.add(lookup);
                }
            }
        }
        // Pre：开单医生仅医生开立 Request.requester_id（划价/滚费不回填）
        bundle.orderingDoctorByChargeItemId =
            this.chargeItemOrderingDoctorFromRequestSupport.mapOrderingDoctorIdByChargeItemId(chargeItemsForFinType);
        for (Long orderingDoctorId : bundle.orderingDoctorByChargeItemId.values()) {
            if (orderingDoctorId != null) {
                practitionerIds.add(orderingDoctorId);
            }
        }
        // Pre：执行人 — 临嘱/长嘱同口径：procedure 主执行人优先，否则 performer_id
        bundle.performerByChargeItemId = this.mapPerformerIdByChargeItemId(chargeItemsForFinType);
        for (Long performerId : bundle.performerByChargeItemId.values()) {
            if (performerId != null) {
                practitionerIds.add(performerId);
            }
        }
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        for (Encounter encounter : encounters) {
            if (encounter != null && encounter.getId() != null) {
                bundle.encounterById.put(encounter.getId(), encounter);
                if (encounter.getPatientId() != null) {
                    patientIds.add(encounter.getPatientId());
                }
                if (encounter.getOrganizationId() != null) {
                    bundle.organizationIds.add(encounter.getOrganizationId());
                }
            }
        }
        bundle.patientById = this.patientQueryService.loadPatientEntityMapByIds(patientIds);
        bundle.organizationNameById =
            this.organizationQueryService.findOrganizationNamesByIds(bundle.organizationIds);
        bundle.practitionerNameById = this.loadPractitionerNameMap(practitionerIds);
        List<ProductDefinitionBatchLookup> lookupList = new ArrayList<>(productLookups);
        bundle.productByCompositeKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookupList);
        bundle.finTypeCodeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItemsForFinType);
        bundle.finTypeLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        // Pre：历史进价批量解析（禁当前 ACTIVE 目录价）；单价一次、行额按包装锚，禁止双倍定价 IO
        bundle.purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(
                chargeItemsForFinType);
        bundle.packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                chargeItemsForFinType, bundle.productByCompositeKey);
        // 必须传 product Map：包装操作单位禁止走「假定拆零量」兼容分支（#4M-OP）
        bundle.purchaseAmountByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItemsForFinType,
                bundle.purchaseUnitByChargeItemId,
                bundle.packageUnitRatioByChargeItemId,
                bundle.productByCompositeKey);
        // Pre：账单挂账账户 → 费用性质
        bundle.feeNatureByAccountId =
            this.reportPatientSettlementContextAssembler.mapFeeNatureContextsByAccountIds(accountIds);
        return bundle;
    }

    /**
     * 解析单行产品定义。
     *
     * @param row    域层候选行
     * @param bundle enrichment 上下文
     * @return 产品展示 DTO
     */
    private ProductDefinitionDisplayDto resolveProduct(
        OutpatientFeeDetailChargeRowQueryModel row,
        FeeDetailEnrichmentBundle bundle) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup =
            ChargeItemProductLookupHelper.buildLookupFromChargeItem(row.getChargeItem());
        if (lookup == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(),
            lookup.getProductDefinitionId());
        if (compositeKey == null) {
            return null;
        }
        ProductDefinitionDisplayDto product = bundle.productByCompositeKey.get(compositeKey);
        if (product != null) {
            return product;
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(lookup.getProductTableName())
            && lookup.getProductDefinitionId() != null) {
            Map<Long, HealthcareService> healthcareById = this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(
                List.of(lookup.getProductDefinitionId()));
            HealthcareService healthcareService = healthcareById.get(lookup.getProductDefinitionId());
            return ProductDefinitionDisplaySupport.fromHealthcareService(healthcareService);
        }
        return null;
    }

    /**
     * 批量解析执行人（临嘱/长嘱同口径）。
     *
     * <p>有 {@code procedure_id} 时取执行记录主执行人；否则回落 {@code performer_id}
     *（划价/滚费等无 Procedure 的路径）。</p>
     *
     * @param chargeItems 收费行
     * @return chargeItemId → 执行人 practitionerId
     */
    private Map<Long, Long> mapPerformerIdByChargeItemId(List<ChargeItem> chargeItems) {
        LinkedHashMap<Long, Long> result = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            if (chargeItem.getProcedureId() != null) {
                procedureIds.add(chargeItem.getProcedureId());
            }
        }
        Map<Long, Long> practitionerByProcedureId = Map.of();
        if (!procedureIds.isEmpty()) {
            practitionerByProcedureId =
                this.procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            // 有执行记录：临嘱单次 / 长嘱反复均取 Procedure 主执行人
            if (chargeItem.getProcedureId() != null) {
                Long fromProcedure = practitionerByProcedureId.get(chargeItem.getProcedureId());
                if (fromProcedure != null) {
                    result.put(chargeItem.getId(), fromProcedure);
                    continue;
                }
            }
            // 无执行记录（划价等）：回落账单 performer_id
            if (chargeItem.getPerformerId() != null) {
                result.put(chargeItem.getId(), chargeItem.getPerformerId());
            }
        }
        return result;
    }

    /**
     * 批量解析医务人员展示名。
     *
     * @param practitionerIds 医务人员/enterer 主键集合
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNameMap(Set<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        Map<Long, String> entererNames = this.practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
        for (Map.Entry<Long, String> entry : entererNames.entrySet()) {
            if (entry.getKey() != null && StringUtils.isNotEmpty(entry.getValue())) {
                nameById.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return nameById;
    }

    /**
     * 块内 BY_DEFINITION 合并锚点：definitionId + 收费数量单位。
     */
    private static final class FeeDetailDefinitionMergeAnchor {

        /** 收费项目定义主键；可空（空则按 chargeItemId 单桶）。 */
        private Long definitionId;

        /** 收费数量单位码；可空（空与有值分键）。 */
        private String quantityUnit;
    }

    /**
     * 单次查询批量 enrichment 上下文容器。
     */
    private static final class FeeDetailEnrichmentBundle {

        /** encounterId → 就诊实体。 */
        private Map<Long, Encounter> encounterById = new HashMap<>();

        /** patientId → 患者实体。 */
        private Map<Long, Patient> patientById = Map.of();

        /** 待解析名称的 organizationId 集合。 */
        private Set<Long> organizationIds = new HashSet<>();

        /** organizationId → 科室名称。 */
        private Map<Long, String> organizationNameById = Map.of();

        /** practitionerId → 姓名（开单医生 + 执行人 + 收费人）。 */
        private Map<Long, String> practitionerNameById = Map.of();

        /** chargeItemId → 开单医生（仅医生开立 Request.requester_id）。 */
        private Map<Long, Long> orderingDoctorByChargeItemId = Map.of();

        /** chargeItemId → 执行人（Procedure 主执行人优先，否则 performer_id；临嘱/长嘱同口径）。 */
        private Map<Long, Long> performerByChargeItemId = Map.of();

        /** 产品复合键 → 展示 DTO。 */
        private Map<String, ProductDefinitionDisplayDto> productByCompositeKey = Map.of();

        /** chargeItemId → fin_type_code。 */
        private Map<Long, String> finTypeCodeByChargeItemId = Map.of();

        /** fin_type_code → 展示名。 */
        private Map<String, String> finTypeLabelByCode = Map.of();

        /** chargeItemId → 进价单价（未带收退符号）。 */
        private Map<Long, BigDecimal> purchaseUnitByChargeItemId = Map.of();

        /** chargeItemId → 进价总价（未带收退符号）。 */
        private Map<Long, BigDecimal> purchaseAmountByChargeItemId = Map.of();

        /** chargeItemId → 包装→拆零倍数（行进价合计与展示单价用）。 */
        private Map<Long, BigDecimal> packageUnitRatioByChargeItemId = Map.of();

        /** accountId → 费用性质（ChargeItem.account_id 真源）。 */
        private Map<Long, PatientSettlementContextRow> feeNatureByAccountId = Map.of();
    }
}
