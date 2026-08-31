/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.product.service.ProductDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.einvoice.boss.bridge.issue.support.BossQuantityUnitDisplayResolver;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从 health {@code adm_charge_item} 加载开票明细快照，并解析博思内部收费编码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EinvoiceChargeItemContextLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final BossQuantityUnitDisplayResolver quantityUnitDisplayResolver;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 按收费项主键列表加载明细行快照。
     *
     * @param chargeItemIds 来自支付的 charge_item_ids 解析结果
     * @param registrationFee 是否挂号票（影响 internalCode 映射为 REG_FEE）
     * @return 博思报文可用的明细快照列表
     */
    public List<BossChargeLineSnapshot> loadByIds(List<Long> chargeItemIds, boolean registrationFee) {
        List<BossChargeLineSnapshot> lines = new ArrayList<>();
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return lines;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> rows = chargeItemQueryService.loadChargeItemsByIds(query);
        List<Long> definitionIds = rows.stream()
                .map(ChargeItem::getDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ChargeItemDefinition> definitionMap =
                chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        ServiceProductAnchors serviceAnchors = loadServiceProductAnchors(rows);
        Map<String, ProductDefinitionDisplayDto> productMap = loadProductMap(rows, definitionMap, serviceAnchors);
        // 票面单位须为中文 display；quantity_unit 存术语 code（如 6/branch/bottle）
        Map<String, String> unitLabelByCode = quantityUnitDisplayResolver.warmLabelMap();
        for (ChargeItem row : rows) {
            BossChargeLineSnapshot snapshot = new BossChargeLineSnapshot();
            snapshot.setChargeItemId(row.getId());
            snapshot.setDefinitionId(row.getDefinitionId());
            ChargeItemDefinition definition = row.getDefinitionId() != null
                    ? definitionMap.get(row.getDefinitionId()) : null;
            snapshot.setInternalCode(resolveInternalCode(registrationFee, row, definition));
            ProductDefinitionDisplayDto product = resolveProduct(row, definition, productMap, serviceAnchors);
            snapshot.setItemName(resolveItemName(definition, product));
            applyProductGrade(snapshot, product);
            snapshot.setAmount(row.getTotalPrice());
            snapshot.setQuantity(row.getQuantityValue());
            snapshot.setUnit(resolveLineUnit(row, product, unitLabelByCode));
            snapshot.setUnitPrice(row.getUnitPrice());
            lines.add(snapshot);
        }
        return lines;
    }

    /**
     * 写入医保目录编码与甲/乙/丙（自费票同样需要展开，不按票头医保类型跳过）。
     */
    private static void applyProductGrade(BossChargeLineSnapshot snapshot, ProductDefinitionDisplayDto product) {
        if (product == null) {
            return;
        }
        snapshot.setItemCode(firstNonBlank(product.getYbNo(), product.getProductBusNo()));
        snapshot.setChrgitmLv(product.getChrgitmLv());
        BossChrgitmLvMapping.Grade grade = BossChrgitmLvMapping.resolve(product.getChrgitmLv());
        snapshot.setListMedicalCareType(grade.medicalCareType());
        snapshot.setListRemark(firstNonBlank(product.getChrgitmLvText(), grade.remark()));
    }

    /**
     * 具体项目名：产品目录名优先，其次价目 {@code chargeName}（对齐 areahis listDetail.name）。
     *
     * <p>价目名若与统计大类同名（如「西药费」）且产品名为空，仍返回价目名由调用方处理；
     * 正常药品价目 {@code chargeName} 为药名。</p>
     */
    private String resolveItemName(ChargeItemDefinition definition, ProductDefinitionDisplayDto product) {
        if (product != null) {
            String productName = firstNonBlank(product.getProductName(), product.getMerchandiseName());
            if (StringUtils.hasText(productName)) {
                return productName.trim();
            }
        }
        if (definition != null && StringUtils.hasText(definition.getChargeName())) {
            return definition.getChargeName().trim();
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private Map<String, ProductDefinitionDisplayDto> loadProductMap(
            List<ChargeItem> rows,
            Map<Long, ChargeItemDefinition> definitionMap,
            ServiceProductAnchors serviceAnchors) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem row : rows) {
            ChargeItemDefinition definition = row.getDefinitionId() != null && definitionMap != null
                    ? definitionMap.get(row.getDefinitionId()) : null;
            for (ProductDefinitionBatchLookup lookup : buildProductLookups(row, definition, serviceAnchors)) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (key == null || !dedupeKeys.add(key)) {
                    continue;
                }
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, ProductDefinitionDisplayDto> loaded =
                    productDefinitionQueryService.batchLoadByKeys(lookups);
            return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (Exception ex) {
            log.warn("Batch load product definitions failed, size={}, fallback single lookups", lookups.size(), ex);
            Map<String, ProductDefinitionDisplayDto> fallback = new HashMap<>();
            for (ProductDefinitionBatchLookup lookup : lookups) {
                try {
                    Map<String, ProductDefinitionDisplayDto> one =
                            productDefinitionQueryService.batchLoadByKeys(List.of(lookup));
                    if (one != null) {
                        fallback.putAll(one);
                    }
                } catch (Exception singleEx) {
                    log.warn(
                            "Single product lookup failed, table={}, id={}",
                            lookup.getProductTableName(),
                            lookup.getProductDefinitionId(),
                            singleEx);
                }
            }
            return fallback;
        }
    }

    private ProductDefinitionDisplayDto resolveProduct(
            ChargeItem row,
            ChargeItemDefinition definition,
            Map<String, ProductDefinitionDisplayDto> productMap,
            ServiceProductAnchors serviceAnchors) {
        List<ProductDefinitionBatchLookup> lookups = buildProductLookups(row, definition, serviceAnchors);
        if (lookups.isEmpty()) {
            return null;
        }
        for (ProductDefinitionBatchLookup lookup : lookups) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
            if (key == null) {
                continue;
            }
            ProductDefinitionDisplayDto hit = productMap != null ? productMap.get(key) : null;
            if (hit != null) {
                return hit;
            }
        }
        // 批量 Map 漏载时按 lookup 单条回源，避免整票丢失项目名/甲乙丙
        for (ProductDefinitionBatchLookup lookup : lookups) {
            try {
                Map<String, ProductDefinitionDisplayDto> one =
                        productDefinitionQueryService.batchLoadByKeys(List.of(lookup));
                if (one == null || one.isEmpty()) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                ProductDefinitionDisplayDto hit = key != null ? one.get(key) : null;
                if (hit != null) {
                    if (productMap != null) {
                        productMap.put(key, hit);
                    }
                    return hit;
                }
            } catch (Exception ex) {
                log.warn(
                        "Fallback product lookup failed, table={}, id={}",
                        lookup.getProductTableName(),
                        lookup.getProductDefinitionId(),
                        ex);
            }
        }
        log.warn(
                "Einvoice product not resolved, chargeItemId={}, definitionId={}, lookups={}",
                row != null ? row.getId() : null,
                definition != null ? definition.getId() : null,
                lookups.size());
        return null;
    }

    /**
     * 收费行 → 产品 lookup：product_* → 医嘱/发放锚点 → 价目 instance_* → 挂号 healthcare_service。
     *
     * <p>自费西药价目 {@code chargeName} 常为空，且 instance 可能指向统计大类；
     * 必须靠目录主键或医嘱/发放锚点展开药名/甲乙丙/单位。</p>
     */
    private static List<ProductDefinitionBatchLookup> buildProductLookups(
            ChargeItem chargeItem,
            ChargeItemDefinition definition,
            ServiceProductAnchors serviceAnchors) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>(4);
        if (chargeItem != null
                && StringUtils.hasText(chargeItem.getProductTable())
                && chargeItem.getProductId() != null) {
            lookups.add(lookupOf(chargeItem.getProductTable(), chargeItem.getProductId()));
        }
        addServiceAnchorLookups(lookups, chargeItem, serviceAnchors);
        if (definition != null
                && StringUtils.hasText(definition.getInstanceTable())
                && definition.getInstanceId() != null) {
            lookups.add(lookupOf(definition.getInstanceTable(), definition.getInstanceId()));
        }
        if (chargeItem != null
                && StringUtils.hasText(chargeItem.getServiceTable())
                && chargeItem.getServiceId() != null
                && TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(chargeItem.getServiceTable().trim())) {
            lookups.add(lookupOf(TableNameConstants.ADM_HEALTHCARE_SERVICE, chargeItem.getServiceId()));
        }
        return lookups;
    }

    private static void addServiceAnchorLookups(
            List<ProductDefinitionBatchLookup> lookups,
            ChargeItem chargeItem,
            ServiceProductAnchors serviceAnchors) {
        if (chargeItem == null || serviceAnchors == null) {
            return;
        }
        Long medicationId = resolveMedicationId(chargeItem, serviceAnchors);
        if (medicationId != null) {
            lookups.add(lookupOf(TableNameConstants.MED_MEDICATION_DEFINITION, medicationId));
        }
        if (isTable(chargeItem.getServiceTable(), TableNameConstants.WKF_DEVICE_REQUEST)
                && chargeItem.getServiceId() != null) {
            Long deviceDefId = serviceAnchors.deviceByRequestId.get(chargeItem.getServiceId());
            if (deviceDefId != null) {
                lookups.add(lookupOf(TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId));
            }
        }
        if (isTable(chargeItem.getServiceTable(), TableNameConstants.WKF_SERVICE_REQUEST)
                && chargeItem.getServiceId() != null) {
            Long activityId = serviceAnchors.activityByRequestId.get(chargeItem.getServiceId());
            if (activityId != null) {
                lookups.add(lookupOf(TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId));
            }
        }
    }

    private static Long resolveMedicationId(ChargeItem chargeItem, ServiceProductAnchors serviceAnchors) {
        if (isTable(chargeItem.getServiceTable(), TableNameConstants.MED_MEDICATION_REQUEST)
                && chargeItem.getServiceId() != null) {
            Long fromRequest = serviceAnchors.medicationByRequestId.get(chargeItem.getServiceId());
            if (fromRequest != null) {
                return fromRequest;
            }
        }
        if (isTable(chargeItem.getServiceTable(), TableNameConstants.MED_MEDICATION_DISPENSE)
                && chargeItem.getServiceId() != null) {
            Long fromServiceDispense = serviceAnchors.medicationByDispenseId.get(chargeItem.getServiceId());
            if (fromServiceDispense != null) {
                return fromServiceDispense;
            }
        }
        if (isTable(chargeItem.getDispenseTable(), TableNameConstants.MED_MEDICATION_DISPENSE)
                && chargeItem.getDispenseId() != null) {
            return serviceAnchors.medicationByDispenseId.get(chargeItem.getDispenseId());
        }
        return null;
    }

    /**
     * 票面单位：收费项术语码优先；自费药品常落成「次」或缺单位，回落到目录门诊/包装单位。
     */
    private String resolveLineUnit(
            ChargeItem row,
            ProductDefinitionDisplayDto product,
            Map<String, String> unitLabelByCode) {
        String persisted = row != null ? row.getQuantityUnit() : null;
        String displayed = quantityUnitDisplayResolver.toDisplay(persisted, unitLabelByCode);
        if (!needsProductUnitFallback(displayed, persisted, product)) {
            return displayed;
        }
        for (String code : productUnitCodes(product)) {
            String fromProduct = quantityUnitDisplayResolver.toDisplay(code, unitLabelByCode);
            if (StringUtils.hasText(fromProduct) && !isGenericTimeUnit(fromProduct, code)) {
                return fromProduct;
            }
        }
        return displayed;
    }

    private static boolean needsProductUnitFallback(
            String displayed, String persisted, ProductDefinitionDisplayDto product) {
        if (product == null || !isMedicationProduct(product)) {
            return false;
        }
        if (!StringUtils.hasText(displayed)) {
            return true;
        }
        return isGenericTimeUnit(displayed, persisted);
    }

    private static boolean isMedicationProduct(ProductDefinitionDisplayDto product) {
        return product != null
                && TableNameConstants.MED_MEDICATION_DEFINITION.equals(
                        product.getProductTableName() == null ? null : product.getProductTableName().trim());
    }

    private static boolean isGenericTimeUnit(String displayed, String code) {
        return ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT.equals(displayed)
                || ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT.equals(code);
    }

    private static List<String> productUnitCodes(ProductDefinitionDisplayDto product) {
        List<String> codes = new ArrayList<>(6);
        addIfHasText(codes, product.getDefEncounterUnitCode());
        addIfHasText(codes, product.getPackageUnitCode());
        addIfHasText(codes, product.getSplitUnitCode());
        addIfHasText(codes, product.getStockUnitCode());
        addIfHasText(codes, product.getDoseUnitCode());
        addIfHasText(codes, product.getPurchaseUnitCode());
        return codes;
    }

    private static void addIfHasText(List<String> codes, String code) {
        if (StringUtils.hasText(code)) {
            codes.add(code.trim());
        }
    }

    private ServiceProductAnchors loadServiceProductAnchors(List<ChargeItem> rows) {
        LinkedHashSet<Long> medicationRequestIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationDispenseIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceRequestIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceRequestIds = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (row == null) {
                continue;
            }
            if (isTable(row.getServiceTable(), TableNameConstants.MED_MEDICATION_REQUEST)
                    && row.getServiceId() != null) {
                medicationRequestIds.add(row.getServiceId());
            }
            if (isTable(row.getServiceTable(), TableNameConstants.MED_MEDICATION_DISPENSE)
                    && row.getServiceId() != null) {
                medicationDispenseIds.add(row.getServiceId());
            }
            if (isTable(row.getDispenseTable(), TableNameConstants.MED_MEDICATION_DISPENSE)
                    && row.getDispenseId() != null) {
                medicationDispenseIds.add(row.getDispenseId());
            }
            if (isTable(row.getServiceTable(), TableNameConstants.WKF_DEVICE_REQUEST)
                    && row.getServiceId() != null) {
                deviceRequestIds.add(row.getServiceId());
            }
            if (isTable(row.getServiceTable(), TableNameConstants.WKF_SERVICE_REQUEST)
                    && row.getServiceId() != null) {
                serviceRequestIds.add(row.getServiceId());
            }
        }
        Map<Long, Long> medicationByRequestId = new HashMap<>();
        if (!medicationRequestIds.isEmpty()) {
            for (MedicationRequest request : medicationRequestQueryService.listByIds(medicationRequestIds)) {
                if (request != null && request.getId() != null && request.getMedicationId() != null) {
                    medicationByRequestId.put(request.getId(), request.getMedicationId());
                }
            }
        }
        Map<Long, Long> medicationByDispenseId = new HashMap<>();
        if (!medicationDispenseIds.isEmpty()) {
            for (MedicationDispense dispense
                    : medicationDispenseQueryService.listMedicationDispensesByIds(medicationDispenseIds)) {
                if (dispense != null && dispense.getId() != null && dispense.getMedicationId() != null) {
                    medicationByDispenseId.put(dispense.getId(), dispense.getMedicationId());
                }
            }
        }
        Map<Long, Long> deviceByRequestId = new HashMap<>();
        if (!deviceRequestIds.isEmpty()) {
            Map<Long, DeviceRequestListModel> deviceMap =
                    deviceRequestQueryService.mapListModelsByIds(deviceRequestIds);
            if (deviceMap != null) {
                for (Map.Entry<Long, DeviceRequestListModel> entry : deviceMap.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().getDeviceDefId() != null) {
                        deviceByRequestId.put(entry.getKey(), entry.getValue().getDeviceDefId());
                    }
                }
            }
        }
        Map<Long, Long> activityByRequestId = new HashMap<>();
        if (!serviceRequestIds.isEmpty()) {
            Map<Long, ServiceRequestListModel> serviceMap =
                    serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
            if (serviceMap != null) {
                for (Map.Entry<Long, ServiceRequestListModel> entry : serviceMap.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().getActivityId() != null) {
                        activityByRequestId.put(entry.getKey(), entry.getValue().getActivityId());
                    }
                }
            }
        }
        return new ServiceProductAnchors(
                medicationByRequestId, medicationByDispenseId, deviceByRequestId, activityByRequestId);
    }

    private static boolean isTable(String tableName, String expected) {
        return StringUtils.hasText(tableName) && expected.equals(tableName.trim());
    }

    private record ServiceProductAnchors(
            Map<Long, Long> medicationByRequestId,
            Map<Long, Long> medicationByDispenseId,
            Map<Long, Long> deviceByRequestId,
            Map<Long, Long> activityByRequestId) {
    }

    private static ProductDefinitionBatchLookup lookupOf(String tableName, Long definitionId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(tableName.trim());
        lookup.setProductDefinitionId(definitionId);
        return lookup;
    }

    /**
     * 解析 health 内部收费编码，供 {@code einv_charge_contrast} 对照为博思 chargeCode。
     *
     * @param registrationFee 挂号票固定 {@code REG_FEE}
     * @param row 收费项行
     * @param definition 收费项定义（可空）
     * @return internalCode，如 {@code FIN_xxx} 或 {@code DEF_{definitionId}}
     */
    private String resolveInternalCode(boolean registrationFee, ChargeItem row, ChargeItemDefinition definition) {
        if (registrationFee) {
            return "REG_FEE";
        }
        if (definition != null && StringUtils.hasText(definition.getTypeCode())) {
            return "FIN_" + definition.getTypeCode().trim();
        }
        return "DEF_" + row.getDefinitionId();
    }

    /**
     * 是否挂号票据类型。
     *
     * @param invoiceTypeCode health 票据类型码
     * @return REG 类型为 true
     */
    public boolean isRegistrationInvoice(String invoiceTypeCode) {
        return AccountTypeCode.REG.getCode().equals(invoiceTypeCode);
    }

    /**
     * 是否门诊票据类型。
     *
     * @param invoiceTypeCode health 票据类型码
     * @return 门诊类型为 true
     */
    public boolean isOutpatientInvoice(String invoiceTypeCode) {
        return OutpatientChargeConstants.INVOICE_TYPE_OUTPATIENT.equals(invoiceTypeCode);
    }
}
