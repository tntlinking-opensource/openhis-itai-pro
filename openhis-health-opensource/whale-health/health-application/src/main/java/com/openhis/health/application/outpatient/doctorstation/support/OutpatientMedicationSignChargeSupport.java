/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.outpatient.doctorstation.support;

import com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.module.common.assembler.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门诊医生站药品签发后 FIFO 发放与分段挂费编排（IF-DOR-15 / IF-DOR-37 药线共用）。
 *
 * <p>西药 Tab 与中医 Tab 在「sign → void 暂存费 → FIFO 发放 → 分段 ChargeItem 草稿 → PLANNED」须同构；
 * 差异仅处方号来源（全就诊分方 vs prescriptionGroupKey），本 Support 不含分方逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientMedicationSignChargeSupport {

    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolveSupport clinicalCatalogPriceResolveSupport;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final DoctorStationAdviceMapper doctorStationAdviceMapper;

    /**
     * 单条药品医嘱签发后的 FIFO 发放与 PLANNED 费用编排（与 IF-DOR-15 药线逐步对齐）。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param accountId   自费账户
     * @param orgId       开立机构
     * @param operatorId  操作人
     * @param medReqId    药品申请主键（已 sign）
     * @throws BusinessException FIFO 分段与数量不一致、定价缺失或发放失败
     */
    public void replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medReqId,
        MedicationRequestFifoSignContext fifoCtx
    ) {
        // Cmd：作废仍锚定在医嘱请求上的暂存费用，避免签发后双挂
        chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
            encounterId, TableNameConstants.MED_MEDICATION_REQUEST, List.of(medReqId));
        // Pre：按模板与库存 FIFO 切分发放数量
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments =
            doctorStationAdviceFifoAllocationAssembler.allocateMedicationFifo(
                orgId, fifoCtx.getEncounterId(), fifoCtx.getMedicationId(), fifoCtx.getCatalogCategoryCode(),
                fifoCtx.getMethodCode(), fifoCtx.getUnitCode(), fifoCtx.getQuantity());
        ReplaceFifoMedicationDispenseDraftsCommand fifoCmd = new ReplaceFifoMedicationDispenseDraftsCommand();
        fifoCmd.setOperatorId(operatorId);
        fifoCmd.setMedReqId(fifoCtx.getMedReqId());
        fifoCmd.setMedicationId(fifoCtx.getMedicationId());
        fifoCmd.setEncounterId(fifoCtx.getEncounterId());
        fifoCmd.setPatientId(fifoCtx.getPatientId());
        fifoCmd.setUnitCode(fifoCtx.getUnitCode());
        fifoCmd.setDispenseEnum(MedicationDispenseType.OUTPATIENT);
        fifoCmd.setSegments(segments);
        // Cmd：替换为签发后 FIFO 发放草稿分段
        List<Long> dispenseIds = medicationDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoCmd);
        List<BigDecimal> segmentQuantities = new ArrayList<>();
        for (ReplaceFifoMedicationDispenseDraftsCommand.Segment segment : segments) {
            segmentQuantities.add(segment.getQuantity());
        }
        // Cmd：按发放分段重建费用草稿（收银仅见 PLANNED，须先落 DRAFT 再升位）
        createDispenseSegmentChargeDrafts(
            encounterId, patientId, accountId, orgId, operatorId,
            fifoCtx.getMedReqId(),
            TableNameConstants.MED_MEDICATION_REQUEST,
            TableNameConstants.MED_MEDICATION_DEFINITION,
            fifoCtx.getMedicationId(),
            TableNameConstants.MED_MEDICATION_DISPENSE,
            dispenseIds,
            segmentQuantities,
            fifoCtx.getUnitCode());
        // Cmd：草稿费用升为 PLANNED，供门诊收银预结算
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
            encounterId, TableNameConstants.MED_MEDICATION_DISPENSE, dispenseIds);
    }

    /**
     * 按 FIFO 发放分段批量生成费用草稿。
     *
     * @param encounterId         就诊主键
     * @param patientId           患者主键
     * @param accountId           账户主键
     * @param orgId               机构主键
     * @param operatorId          操作人
     * @param requestId           医嘱申请主键
     * @param requestServiceTable 医嘱请求表名
     * @param productDefinitionTable 产品定义表
     * @param productDefinitionId 产品定义主键
     * @param dispenseTable       发放表名
     * @param dispenseIds         发放分段 id 列表
     * @param segmentQuantities   各分段数量
     * @param quantityUnit        计价单位
     */
    public void createDispenseSegmentChargeDrafts(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        List<Long> dispenseIds,
        List<BigDecimal> segmentQuantities,
        String quantityUnit
    ) {
        if (dispenseIds == null || segmentQuantities == null || dispenseIds.size() != segmentQuantities.size()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "发放分段与费用草稿数量不一致");
        }
        if (dispenseIds.isEmpty()) {
            return;
        }
        // Pre：签发挂费须命中批号售价定价行
        ChargeItemDefinition priceLine = firstPriceLine(productDefinitionTable, productDefinitionId);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置符合开立规则的收费定价（药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再签发医嘱。");
        }
        ProductDefinitionDisplayDto productRow = loadSingleProduct(productDefinitionTable, productDefinitionId);
        List<CreateChargeItemCommand.Item> draftItems = new ArrayList<>();
        // Asm：每一发放分段一行 ChargeItem
        for (int i = 0; i < dispenseIds.size(); i++) {
            draftItems.add(doctorStationAdviceMapper.toCreateChargeItemDraftItemForFifoDispenseSegment(
                encounterId,
                patientId,
                accountId,
                orgId,
                requestId,
                requestServiceTable,
                productDefinitionTable,
                productDefinitionId,
                dispenseTable,
                dispenseIds.get(i),
                priceLine,
                segmentQuantities.get(i),
                quantityUnit,
                productRow));
        }
        CreateChargeItemCommand batchCmd = new CreateChargeItemCommand();
        batchCmd.setOperatorId(operatorId);
        batchCmd.setItems(draftItems);
        chargeItemDomainService.createDraftChargeItemFromOrder(batchCmd);
    }

    private ChargeItemDefinition firstPriceLine(String instanceTable, Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(instanceTable);
        priceQuery.setInstanceIds(List.of(instanceId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        return clinicalCatalogPriceResolveSupport.selectFirstPriceLineForChargeDraft(instanceTable, priceLines);
    }

    private ProductDefinitionDisplayDto loadSingleProduct(String table, Long id) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(table);
        lookup.setProductDefinitionId(id);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "未找到药品产品定义行");
        }
        return row;
    }
}
