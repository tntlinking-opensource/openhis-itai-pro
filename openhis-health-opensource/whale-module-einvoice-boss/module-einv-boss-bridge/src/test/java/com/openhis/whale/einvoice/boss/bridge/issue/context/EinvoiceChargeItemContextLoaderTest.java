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
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.einvoice.boss.bridge.issue.support.BossQuantityUnitDisplayResolver;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EinvoiceChargeItemContextLoaderTest {

    @Mock
    private ChargeItemQueryService chargeItemQueryService;
    @Mock
    private ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    @Mock
    private ProductDefinitionQueryService productDefinitionQueryService;
    @Mock
    private BossQuantityUnitDisplayResolver quantityUnitDisplayResolver;
    @Mock
    private MedicationRequestQueryService medicationRequestQueryService;
    @Mock
    private MedicationDispenseQueryService medicationDispenseQueryService;
    @Mock
    private DeviceRequestQueryService deviceRequestQueryService;
    @Mock
    private ServiceRequestQueryService serviceRequestQueryService;

    @InjectMocks
    private EinvoiceChargeItemContextLoader loader;

    @Test
    void loadByIds_selfPayWesternMedicine_expandsNameAndGradeViaDefinitionInstance() {
        ChargeItem row = new ChargeItem();
        row.setId(101L);
        row.setDefinitionId(201L);
        row.setTotalPrice(new BigDecimal("5.55"));
        row.setQuantityValue(BigDecimal.ONE);
        row.setUnitPrice(new BigDecimal("5.55"));
        // 自费西药偶发未挂 product_*，仅价目 instance 指向药品目录
        row.setProductTable(null);
        row.setProductId(null);

        ChargeItemDefinition definition = new ChargeItemDefinition();
        definition.setId(201L);
        definition.setTypeCode("1001");
        definition.setChargeName(null);
        definition.setInstanceTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        definition.setInstanceId(301L);

        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        product.setProductDefinitionId(301L);
        product.setProductName("硝苯地平控释片");
        product.setYbNo("XC08CAX066A011020104394");
        product.setChrgitmLv(2);

        when(chargeItemQueryService.loadChargeItemsByIds(any(ChargeItemQueryCommand.class)))
                .thenReturn(List.of(row));
        when(chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(List.of(201L)))
                .thenReturn(Map.of(201L, definition));
        when(quantityUnitDisplayResolver.warmLabelMap()).thenReturn(Map.of());
        when(quantityUnitDisplayResolver.toDisplay(any(), any())).thenReturn("盒");
        when(productDefinitionQueryService.batchLoadByKeys(anyList())).thenAnswer(invocation -> {
            List<ProductDefinitionBatchLookup> lookups = invocation.getArgument(0);
            Map<String, ProductDefinitionDisplayDto> map = new java.util.HashMap<>();
            for (ProductDefinitionBatchLookup lookup : lookups) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (key != null && Long.valueOf(301L).equals(lookup.getProductDefinitionId())) {
                    map.put(key, product);
                }
            }
            return map;
        });

        List<BossChargeLineSnapshot> lines = loader.loadByIds(List.of(101L), false);

        assertEquals(1, lines.size());
        BossChargeLineSnapshot line = lines.get(0);
        assertEquals("硝苯地平控释片", line.getItemName());
        assertEquals("甲类", line.getListRemark());
        assertEquals("1", line.getListMedicalCareType());
        assertEquals("XC08CAX066A011020104394", line.getItemCode());

        ArgumentCaptor<List<ProductDefinitionBatchLookup>> captor = ArgumentCaptor.forClass(List.class);
        verify(productDefinitionQueryService).batchLoadByKeys(captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(1, captor.getValue().size());
        assertEquals(TableNameConstants.MED_MEDICATION_DEFINITION, captor.getValue().get(0).getProductTableName());
        assertEquals(301L, captor.getValue().get(0).getProductDefinitionId());
    }

    @Test
    void loadByIds_selfPay_prefersMedicationRequestOverGenericInstance() {
        ChargeItem row = new ChargeItem();
        row.setId(101L);
        row.setDefinitionId(201L);
        row.setTotalPrice(new BigDecimal("20.29"));
        row.setQuantityValue(new BigDecimal("2"));
        row.setUnitPrice(new BigDecimal("10.145"));
        row.setQuantityUnit("次");
        row.setProductTable(null);
        row.setProductId(null);
        row.setServiceTable(TableNameConstants.MED_MEDICATION_REQUEST);
        row.setServiceId(401L);

        ChargeItemDefinition definition = new ChargeItemDefinition();
        definition.setId(201L);
        definition.setTypeCode("1002");
        definition.setChargeName("中成药费");
        definition.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        definition.setInstanceId(999L);

        MedicationRequest request = new MedicationRequest();
        request.setId(401L);
        request.setMedicationId(301L);

        ProductDefinitionDisplayDto product = new ProductDefinitionDisplayDto();
        product.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        product.setProductDefinitionId(301L);
        product.setProductName("藿香正气胶囊");
        product.setYbNo("ZC08AAA001");
        product.setChrgitmLv(2);
        product.setPackageUnitCode("box");
        product.setDefEncounterUnitCode("box");

        when(chargeItemQueryService.loadChargeItemsByIds(any(ChargeItemQueryCommand.class)))
                .thenReturn(List.of(row));
        when(chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(List.of(201L)))
                .thenReturn(Map.of(201L, definition));
        when(medicationRequestQueryService.listByIds(any())).thenReturn(List.of(request));
        when(quantityUnitDisplayResolver.warmLabelMap()).thenReturn(Map.of());
        when(quantityUnitDisplayResolver.toDisplay(any(), any())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if ("box".equals(code)) {
                return "盒";
            }
            return code;
        });
        when(productDefinitionQueryService.batchLoadByKeys(anyList())).thenAnswer(invocation -> {
            List<ProductDefinitionBatchLookup> lookups = invocation.getArgument(0);
            Map<String, ProductDefinitionDisplayDto> map = new java.util.HashMap<>();
            for (ProductDefinitionBatchLookup lookup : lookups) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (key != null && Long.valueOf(301L).equals(lookup.getProductDefinitionId())) {
                    map.put(key, product);
                }
            }
            return map;
        });

        List<BossChargeLineSnapshot> lines = loader.loadByIds(List.of(101L), false);

        assertEquals(1, lines.size());
        BossChargeLineSnapshot line = lines.get(0);
        assertEquals("藿香正气胶囊", line.getItemName());
        assertEquals("甲类", line.getListRemark());
        assertEquals("1", line.getListMedicalCareType());
        assertEquals("盒", line.getUnit());
        assertEquals("ZC08AAA001", line.getItemCode());
    }
}
