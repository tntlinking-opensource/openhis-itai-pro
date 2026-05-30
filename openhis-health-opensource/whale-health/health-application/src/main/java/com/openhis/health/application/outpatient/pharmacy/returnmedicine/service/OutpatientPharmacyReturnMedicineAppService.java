/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.service;

import com.openhis.health.application.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.assembler.OutpatientReturnMedicinePatientQueueAssembler;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.mapper.OutpatientPharmacyReturnMedicineMapper;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.ConfirmMedicineReturnRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.GetReturnMedicineDetailRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.LoadReturnMedicineInitRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.PageReturnPatientRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.ReturnMedicineItemRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.ConfirmMedicineReturnResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.LoadReturnMedicineInitResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.PageReturnPatientResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.ReturnMedicineDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.domain.medication.medicationdispense.command.DeclinePendingRefundMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.RefundDispensedMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.application.querysupport.pharmacy.PharmacyEncounterQueueLoader;
import com.openhis.health.application.querysupport.pharmacy.ReturnMedicineDetailLoader;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊药房退药工作站编排：初始化、患者队列、明细与确认/拒绝退药。
 */
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyReturnMedicineAppService {

    private final ICurrentUser currentUser;

    private final CurrentOperatorResolver currentOperatorResolver;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ReturnMedicineDetailLoader returnMedicineDetailLoader;
    private final PharmacyEncounterQueueLoader pharmacyEncounterQueueLoader;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final OutpatientPharmacyReturnMedicineMapper returnMedicineMapper;
    private final OutpatientReturnMedicinePatientQueueAssembler outpatientReturnMedicinePatientQueueAssembler;

    /**
     * 加载退药初始化元数据（科室与退药状态筛选项）。
     *
     * @param request 请求体（可空壳）
     * @return 初始化数据
     * @throws BusinessException {@code EX-PHA-002-101}：加载失败
     */
    public LoadReturnMedicineInitResponse loadReturnMedicineInit(LoadReturnMedicineInitRequest request) {
        try {
            if (request == null) {
                request = new LoadReturnMedicineInitRequest();
            }
            List<Organization> departments = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
            LoadReturnMedicineInitResponse resp = new LoadReturnMedicineInitResponse();
            resp.setDepartmentOptions(this.returnMedicineMapper.mapToDepartmentOptions(departments));
            resp.setRefundStatusOptions(this.returnMedicineMapper.mapToRefundStatusOptions());
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药元数据加载失败：" + ex.getMessage());
        }
    }

    /**
     * 分页查询退药患者队列（仅含药品发放事实的就诊）。
     *
     * @param request 分页与过滤条件
     * @return 分页结果
     * @throws BusinessException {@code EX-PHA-002-101}：查询失败；分页参数非法
     */
    public PageReturnPatientResponse pageReturnPatientQueue(PageReturnPatientRequest request) {
        try {
            if (request == null) {
                request = new PageReturnPatientRequest();
            }
            this.validatePageReturnPatientRequest(request);
            PageReturnPatientQuery query = this.returnMedicineMapper.toPageReturnPatientQuery(request);
            List<Long> pharmacyLocationIds = this.managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
            if (!pharmacyLocationIds.isEmpty()) {
                query.setLocationIds(pharmacyLocationIds);
            }
            Page<Long> encounterIdPage =
                this.pharmacyEncounterQueueLoader.pageEncounterIdsForOutpatientPharmacyReturn(query);
            List<Long> encounterIds = encounterIdPage.getContent();
            List<OutpatientEncounterContextQueryModel> infos =
                this.encounterContextFacade.listOutpatientContextsByEncounterIdsOrdered(encounterIds);
            Map<Long, String> refundCodeByEncounter =
                this.medicationDispenseQueryService.mapAggregatedRefundStatusCodeByEncounterIds(encounterIds);
            List<ReturnMedicinePatientQueueRow> rows =
                this.outpatientReturnMedicinePatientQueueAssembler.buildRows(infos, refundCodeByEncounter);
            PageReturnPatientResponse response = new PageReturnPatientResponse();
            return PagedResultBuilder.build(
                response,
                this.returnMedicineMapper.mapToReturnPatientRows(rows),
                encounterIdPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药队列查询失败：" + ex.getMessage());
        }
    }

    /**
     * 查询指定就诊的药品退药明细。
     *
     * @param request 就诊与过滤条件
     * @return 明细列表
     * @throws BusinessException {@code EX-PHA-002-101}：就诊不存在或查询失败
     */
    public ReturnMedicineDetailResponse getReturnMedicineDetail(GetReturnMedicineDetailRequest request) {
        try {
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "encounterId 不能为空");
            }
            this.encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
            ReturnMedicineDetailQuery query = this.returnMedicineMapper.toReturnMedicineDetailQuery(request);
            List<ReturnMedicineDetailReadRow> rows = this.returnMedicineDetailLoader.listReturnMedicineDetails(query);
        // 按发放单汇总收费金额，展示口径与门诊发药 slip 明细一致。
            this.applyChargeTotalPriceByDispenseId(request.getEncounterId(), rows);
            ReturnMedicineDetailResponse resp = new ReturnMedicineDetailResponse();
            resp.setItems(this.returnMedicineMapper.mapToReturnMedicineDetailRows(rows));
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药明细查询失败：" + ex.getMessage());
        }
    }

    /**
     * 确认退药或拒绝退药（仅药品发放单）。
     *
     * @param request 操作类型、原因与退药项列表
     * @return 成功响应
     * @throws BusinessException 参数非法、状态不允许、库存定位失败等
     */
    public ConfirmMedicineReturnResponse confirmMedicineReturn(ConfirmMedicineReturnRequest request) {
        this.validateConfirmMedicineReturnRequest(request);
        Long operatorId = this.requireOperatorUserId();
        Long practitionerId = this.resolvePractitionerIdForAudit(operatorId);
        boolean confirm =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_CONFIRM.equalsIgnoreCase(
                request.getConfirmType().trim());
        boolean decline =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE.equalsIgnoreCase(
                request.getConfirmType().trim());
        if (!confirm && !decline) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED, "confirmType 只能是 CONFIRM 或 DECLINE");
        }
        List<RefundDispensedMedicationCommand.Item> medRefundItems = new ArrayList<>();
        List<DeclinePendingRefundMedicationDispenseCommand.Item> medDeclineItems = new ArrayList<>();
        LinkedHashSet<Long> medRequestIds = new LinkedHashSet<>();
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
        Map<Long, BigDecimal> inventoryIncreaseMerged = new HashMap<>();

        for (ReturnMedicineItemRequest item : request.getItems()) {
            if (item == null) {
                continue;
            }
            String table = StringUtils.defaultString(item.getTableName()).trim();
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(table)) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "本页仅支持药品退药");
            }
            MedicationDispense md = this.medicationDispenseQueryService.mustGetMedicationDispense(item.getDispenseId());
            this.assertPendingRefundDispense(md.getStatusEnum(), item.getDispenseId());
            if (confirm) {
                RefundDispensedMedicationCommand.Item it = new RefundDispensedMedicationCommand.Item();
                it.setId(md.getId());
                it.setRefundQuantity(this.resolveRefundQuantity(md));
                it.setOperatorId(operatorId);
                it.setPractitionerId(practitionerId);
                it.setReasonText(request.getReason());
                medRefundItems.add(it);
                this.collectMedicationTraceAndInventory(md, traceItems, inventoryIncreaseMerged);
            } else {
                DeclinePendingRefundMedicationDispenseCommand.Item it = new DeclinePendingRefundMedicationDispenseCommand.Item();
                it.setId(md.getId());
                it.setDeclineReasonText(request.getReason());
                it.setPractitionerId(practitionerId);
                medDeclineItems.add(it);
            }
            medRequestIds.add(item.getRequestId());
        }

        if (confirm) {
            if (!medRefundItems.isEmpty()) {
                RefundDispensedMedicationCommand cmd = new RefundDispensedMedicationCommand();
                cmd.setOperatorId(operatorId);
                cmd.setItems(medRefundItems);
                this.medicationDispenseDomainService.refundDispensedMedication(cmd);
            }
        } else {
            if (!medDeclineItems.isEmpty()) {
                DeclinePendingRefundMedicationDispenseCommand cmd = new DeclinePendingRefundMedicationDispenseCommand();
                cmd.setOperatorId(operatorId);
                cmd.setItems(medDeclineItems);
                this.medicationDispenseDomainService.declinePendingRefundMedicationDispense(cmd);
            }
        }

        for (Long requestId : medRequestIds) {
            if (requestId == null) {
                continue;
            }
            this.medicationRequestDomainService.completeMedicationRequestAfterRefund(requestId, operatorId);
        }

        if (confirm) {
            if (!traceItems.isEmpty()) {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                this.traceNumberRecordDomainService.appendTraceRecords(traceCmd);
            }
            if (!inventoryIncreaseMerged.isEmpty()) {
                IncreaseQuantityCommand inc = new IncreaseQuantityCommand();
                inc.setOperatorId(operatorId);
                List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> incItems = new ArrayList<>();
                for (Map.Entry<Long, BigDecimal> e : inventoryIncreaseMerged.entrySet()) {
                    IncreaseQuantityCommand.IncreaseQuantityItemCommand it = new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
                    it.setId(e.getKey());
                    it.setIncreaseQty(e.getValue());
                    incItems.add(it);
                }
                inc.setItems(incItems);
                this.inventoryItemDomainService.increaseQuantities(inc);
            }
        }

        ConfirmMedicineReturnResponse resp = new ConfirmMedicineReturnResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setFailedItems(new ArrayList<>());
        if (confirm) {
            resp.setMessage(MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE);
        } else {
            resp.setMessage(MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_DECLINE_SUCCESS_MESSAGE);
        }
        return resp;
    }

    private void validatePageReturnPatientRequest(PageReturnPatientRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "pageNo 必须大于等于 1");
        }
        if (request.getPageSize() == null) {
            request.setPageSize(10);
        }
        if (request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "pageSize 非法");
        }
        if (request.getPageSize() > 100) {
            request.setPageSize(100);
        }
    }

    private void validateConfirmMedicineReturnRequest(ConfirmMedicineReturnRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "请求不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "请选择要退药的药品");
        }
        if (StringUtils.isBlank(request.getConfirmType())) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "confirmType 不能为空");
        }
        boolean decline =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE.equalsIgnoreCase(
                request.getConfirmType().trim());
        if (decline && StringUtils.isBlank(request.getReason())) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_302_DECLINE_REASON_REQUIRED, "拒绝退药须填写原因");
        }
    }

    private Long requireOperatorUserId() {
        return currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED,
            "用户未登录");
    }

    private Long resolvePractitionerIdForAudit(Long operatorId) {
        Long pid = currentUser.getCurrentPractitionerId();
        if (pid != null) {
            return pid;
        }
        return operatorId;
    }

    private void assertPendingRefundDispense(DispenseStatus status, Long dispenseId) {
        if (status == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED, "发放单状态缺失");
        }
        if (DispenseStatus.REFUNDED.equals(status) || DispenseStatus.DECLINED.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_202_DUPLICATE_OR_CLOSED, "该药品已退药或已拒绝，请勿重复操作");
        }
        if (!DispenseStatus.PENDING_REFUND.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED,
                "当前状态不允许退药，请刷新后重试：" + dispenseId);
        }
    }

    private BigDecimal resolveRefundQuantity(MedicationDispense md) {
        BigDecimal q = md.getDispenseQuantity();
        if (q == null) {
            q = md.getQuantity();
        }
        if (q == null) {
            return BigDecimal.ZERO;
        }
        return q.abs();
    }

    private void collectMedicationTraceAndInventory(
        MedicationDispense md,
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems,
        Map<Long, BigDecimal> inventoryIncreaseMerged
    ) {
        if (StringUtils.isNotBlank(md.getTraceNo())) {
            for (String part : md.getTraceNo().split(GeneralSharedConstants.COMMA)) {
                String traceNo = part.trim();
                if (traceNo.isEmpty()) {
                    continue;
                }
                AppendTraceRecordsCommand.AppendTraceRecordItemCommand it =
                    new AppendTraceRecordsCommand.AppendTraceRecordItemCommand();
                it.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                it.setItemId(md.getMedicationId());
                it.setLocationTypeEnum(LocationForm.PHARMACY.getCode());
                it.setLocationId(md.getLocationId());
                it.setLocationStoreId(null);
                it.setLotNumber(md.getLotNumber());
                it.setTraceNo(traceNo);
                it.setStatusEnum(TraceDirection.IN.getCode());
                it.setUnitCode(md.getUnitCode());
                it.setOperationType(TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode());
                it.setBusNo(md.getBusNo());
                traceItems.add(it);
            }
        }
        if (StringUtils.isBlank(md.getLotNumber())) {
            return;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(md.getMedicationId());
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        lookups.add(lookup);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionBatchLoader.loadAsMap(lookups);
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, md.getMedicationId());
        ProductDefinitionDisplayDto product = null;
        if (key != null) {
            product = productByKey.get(key);
        }
        if (product == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_203_INVENTORY_MISSING, "药品定义缺失，无法换算退库数量");
        }
        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            ProductDefinitionDisplaySupport.toUnitConversionSource(product), md.getUnitCode(), this.resolveRefundQuantity(md));
        if (splitQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_203_INVENTORY_MISSING, "退库数量非法，无法回补库存");
        }
        Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForMedicationDispenseBatch(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            md.getMedicationId(),
            md.getLocationId(),
            md.getLotNumber());
        BigDecimal existed = inventoryIncreaseMerged.get(inventoryItemId);
        if (existed == null) {
            inventoryIncreaseMerged.put(inventoryItemId, splitQty);
        } else {
            inventoryIncreaseMerged.put(inventoryItemId, existed.add(splitQty));
        }
    }

    /**
     * 为退药明细行回填总价：按就诊 + 发放单 id 批量查收费明细并汇总（与 {@link com.openhis.health.application.outpatient.pharmacy.dispense.service.OutpatientPharmacyDispenseAppService} 同源）。
     *
     * @param encounterId 就诊主键
     * @param rows        读侧明细行；无匹配收费时保持原值（默认 0）
     */
    private void applyChargeTotalPriceByDispenseId(Long encounterId, List<ReturnMedicineDetailReadRow> rows) {
        if (encounterId == null || rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> pricingDispenseIds = new ArrayList<>();
        Map<Long, Long> pricingDispenseIdByRowDispenseId = new HashMap<>();
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null || row.getDispenseId() == null) {
                continue;
            }
            Long pricingDispenseId = row.getChargeAnchorDispenseId();
            if (pricingDispenseId == null) {
                pricingDispenseId = row.getDispenseId();
            }
            pricingDispenseIds.add(pricingDispenseId);
            pricingDispenseIdByRowDispenseId.put(row.getDispenseId(), pricingDispenseId);
        }
        if (pricingDispenseIds.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, pricingDispenseIds);
        Map<Long, BigDecimal> unitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> totalPriceByDispenseId = new HashMap<>();
        mergeChargePricingFromChargeItems(chargeRows, unitPriceByDispenseId, totalPriceByDispenseId);
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null || row.getDispenseId() == null) {
                continue;
            }
            Long pricingDispenseId = pricingDispenseIdByRowDispenseId.get(row.getDispenseId());
            if (pricingDispenseId == null) {
                continue;
            }
            BigDecimal total = totalPriceByDispenseId.get(pricingDispenseId);
            if (total != null) {
                // 退药页展示应退金额绝对值（与应退数量口径一致）
                row.setTotalPrice(total.abs());
            }
        }
    }

    /**
     * 按发放单聚合收费明细的总价（与门诊发药 {@code mergeChargePricingFromChargeItems} 一致）。
     *
     * @param chargeRows        收费行
     * @param unitPriceOut      输出：dispenseId→单价（退药页未用，占位与发药签名一致）
     * @param totalPriceOut     输出：dispenseId→金额合计
     */
    private static void mergeChargePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null) {
                continue;
            }
            if (chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existedTotal = totalByDispense.get(dispenseKey);
                if (existedTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existedTotal.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existedQty = qtyByDispense.get(dispenseKey);
                if (existedQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existedQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0 && unitPriceOut != null) {
                unitPriceOut.put(dispenseKey, totalVal.divide(qtyVal, 4, RoundingMode.HALF_UP));
            }
        }
    }
}
