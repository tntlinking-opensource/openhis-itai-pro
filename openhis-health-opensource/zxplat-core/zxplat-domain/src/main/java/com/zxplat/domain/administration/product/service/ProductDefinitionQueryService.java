/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.product.service;

import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 产品定义批量只读查询（L1：四表路由 + 展示 DTO / 目录编码）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDefinitionQueryService {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /**
     * 批量加载产品展示 DTO（复合键 → {@link ProductDefinitionDisplayDto}）。
     *
     * <p>术语展示列由应用层 {@code ProductDefinitionDisplayTerminologyAssembler} 回填。</p>
     *
     * @param lookups 表名 + 定义主键；空或无效项跳过
     * @return 复合键 Map；入参空时返回空 Map
     */
    public Map<String, ProductDefinitionDisplayDto> batchLoadByKeys(List<ProductDefinitionBatchLookup> lookups) {
        Map<String, ProductDefinitionDisplayDto> result = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : groupIdsByTable(lookups).entrySet()) {
            loadDisplayRows(entry.getKey(), new ArrayList<>(entry.getValue()), result);
        }
        return result;
    }

    public Map<String, ProductDefinitionCatalogQueryModel> batchLoadAsMap(List<ProductDefinitionBatchLookup> lookups) {
        Map<String, ProductDefinitionCatalogQueryModel> result = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : groupIdsByTable(lookups).entrySet()) {
            loadCatalogRows(entry.getKey(), new ArrayList<>(entry.getValue()), result);
        }
        return result;
    }

    private static Map<String, Set<Long>> groupIdsByTable(List<ProductDefinitionBatchLookup> lookups) {
        Map<String, Set<Long>> idsByTable = new LinkedHashMap<>();
        if (lookups == null || lookups.isEmpty()) {
            return idsByTable;
        }
        for (ProductDefinitionBatchLookup lookup : lookups) {
            if (lookup == null || lookup.getProductDefinitionId() == null
                || StringUtils.isEmpty(lookup.getProductTableName())) {
                continue;
            }
            String table = lookup.getProductTableName().trim();
            idsByTable.computeIfAbsent(table, key -> new LinkedHashSet<>()).add(lookup.getProductDefinitionId());
        }
        return idsByTable;
    }

    private void loadDisplayRows(String tableName, List<Long> idList, Map<String, ProductDefinitionDisplayDto> result) {
        try {
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(tableName)) {
                for (MedicationDefinition med : medicationDefinitionQueryService.findAllByIds(idList)) {
                    putDisplay(result, ProductDefinitionDisplaySupport.fromMedication(med));
                }
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(tableName)) {
                for (ActivityDefinition act : activityDefinitionQueryService.findAllByIds(idList)) {
                    putDisplay(result, ProductDefinitionDisplaySupport.fromActivity(act));
                }
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(tableName)) {
                for (DeviceDefinition dev : deviceDefinitionQueryService.getAllDeviceDefinitions(idList)) {
                    putDisplay(result, ProductDefinitionDisplaySupport.fromDevice(dev));
                }
            } else if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(tableName)) {
                Map<Long, HealthcareService> healthcareById =
                    healthcareServiceQueryService.loadHealthcareServiceMapByIds(idList);
                for (HealthcareService healthcareService : healthcareById.values()) {
                    putDisplay(result, ProductDefinitionDisplaySupport.fromHealthcareService(healthcareService));
                }
            }
        } catch (RuntimeException ex) {
            // 单表失败不阻断其他表（如耗材异常不应丢掉同票诊疗/药品的甲乙丙与项目名）
            log.warn("batchLoadByKeys failed for table={}, ids={}", tableName, idList.size(), ex);
        }
    }

    private void loadCatalogRows(String tableName, List<Long> idList, Map<String, ProductDefinitionCatalogQueryModel> result) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(tableName)) {
            for (MedicationDefinition med : medicationDefinitionQueryService.findAllByIds(idList)) {
                putMedication(result, med);
            }
        } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(tableName)) {
            for (ActivityDefinition act : activityDefinitionQueryService.findAllByIds(idList)) {
                putActivity(result, act);
            }
        } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(tableName)) {
            for (DeviceDefinition dev : deviceDefinitionQueryService.getAllDeviceDefinitions(idList)) {
                putDevice(result, dev);
            }
        } else if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(tableName)) {
            Map<Long, HealthcareService> healthcareById =
                healthcareServiceQueryService.loadHealthcareServiceMapByIds(idList);
            for (HealthcareService healthcareService : healthcareById.values()) {
                putHealthcareService(result, healthcareService);
            }
        }
    }

    private static void putDisplay(Map<String, ProductDefinitionDisplayDto> result, ProductDefinitionDisplayDto dto) {
        if (dto == null) {
            return;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getProductTableName(), dto.getProductDefinitionId());
        if (key != null) {
            result.put(key, dto);
        }
    }

    private static void putMedication(Map<String, ProductDefinitionCatalogQueryModel> result, MedicationDefinition med) {
        if (med == null || med.getId() == null) {
            return;
        }
        ProductDefinitionCatalogQueryModel dto = new ProductDefinitionCatalogQueryModel();
        dto.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        dto.setProductDefinitionId(med.getId());
        dto.setProductBusNo(med.getBusNo());
        dto.setProductName(med.getName());
        dto.setYbNo(med.getYbNo());
        put(result, dto);
    }

    private static void putActivity(Map<String, ProductDefinitionCatalogQueryModel> result, ActivityDefinition act) {
        if (act == null || act.getId() == null) {
            return;
        }
        ProductDefinitionCatalogQueryModel dto = new ProductDefinitionCatalogQueryModel();
        dto.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        dto.setProductDefinitionId(act.getId());
        dto.setProductBusNo(act.getBusNo());
        dto.setProductName(act.getName());
        dto.setYbNo(act.getYbNo());
        put(result, dto);
    }

    private static void putDevice(
            Map<String, ProductDefinitionCatalogQueryModel> result,
            com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition dev) {
        if (dev == null || dev.getId() == null) {
            return;
        }
        ProductDefinitionCatalogQueryModel dto = new ProductDefinitionCatalogQueryModel();
        dto.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
        dto.setProductDefinitionId(dev.getId());
        dto.setProductBusNo(dev.getBusNo());
        dto.setProductName(dev.getName());
        dto.setYbNo(dev.getYbNo());
        // 高值标志：2204/2301/3301 决定是否截断 med_list_codg
        dto.setHvcmFlag(dev.getHvcmFlag());
        put(result, dto);
    }

    private static void putHealthcareService(
            Map<String, ProductDefinitionCatalogQueryModel> result,
            HealthcareService healthcareService) {
        if (healthcareService == null || healthcareService.getId() == null) {
            return;
        }
        ProductDefinitionCatalogQueryModel dto = new ProductDefinitionCatalogQueryModel();
        dto.setProductTableName(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        dto.setProductDefinitionId(healthcareService.getId());
        dto.setProductBusNo(healthcareService.getBusNo());
        dto.setProductName(healthcareService.getName());
        dto.setYbNo(healthcareService.getYbNo());
        put(result, dto);
    }

    private static void put(Map<String, ProductDefinitionCatalogQueryModel> result, ProductDefinitionCatalogQueryModel dto) {
        String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getProductTableName(), dto.getProductDefinitionId());
        if (key != null) {
            result.put(key, dto);
        }
    }
}
