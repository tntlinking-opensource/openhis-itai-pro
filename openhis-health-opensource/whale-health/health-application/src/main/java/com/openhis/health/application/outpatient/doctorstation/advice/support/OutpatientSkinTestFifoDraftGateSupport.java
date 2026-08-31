/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.SaveMedicationAdviceDraftLinePayload;
import com.openhis.health.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductTableNameHelper;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IF-DOR-14 暂存门控：门诊皮试药 FIFO 单批优先，无一单批够量且多批合计够量时须医生确认（BR-ORD-034）。
 */
@Component
@RequiredArgsConstructor
public class OutpatientSkinTestFifoDraftGateSupport {

    /** FIFO 试算与 EX-ORD-309 抛出。 */
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    /** 药品目录读模型（categoryCode 等）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 暂存批次写库前：校验皮试药是否须确认跨批。
     *
     * @param orgId                   开立机构
     * @param encounterId             就诊主键
     * @param lines                   本批 CREATE/UPDATE/DELETE 行
     * @param confirmCrossLotSkinTest 医生已在弹窗确认可跨批
     */
    public void assertDraftBatchSkinTestFifoGate(
        Long orgId,
        Long encounterId,
        List<DoctorStationAdviceDraftLineRequest> lines,
        boolean confirmCrossLotSkinTest
    ) {
        // 已确认：本批暂存不再重复提示
        if (confirmCrossLotSkinTest) {
            return;
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (DoctorStationAdviceDraftLineRequest line : lines) {
            if (line == null) {
                continue;
            }
            String op = trimToEmpty(line.getLineOp()).toUpperCase(Locale.ROOT);
            if (!DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)
                && !DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                continue;
            }
            String productTable = ProductTableNameHelper.normalize(line.getProductTableName());
            if (!ProductTableNameHelper.isMedication(productTable)) {
                continue;
            }
            SaveMedicationAdviceDraftLinePayload payload = line.getMedicationPayload();
            if (payload == null || !Boolean.TRUE.equals(payload.getSkinTestFlag())) {
                continue;
            }
            if (payload.getMedicationDefinitionId() == null) {
                continue;
            }
            BigDecimal quantity = payload.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ProductDefinitionDisplayDto productRow = this.loadMedicationProductRow(payload.getMedicationDefinitionId());
            String categoryCode = null;
            if (productRow != null) {
                categoryCode = productRow.getCategoryCode();
            }
            doctorStationAdviceFifoAllocationAssembler.assertOutpatientSkinTestCrossLotConfirmedOrThrow(
                orgId,
                encounterId,
                payload.getMedicationDefinitionId(),
                categoryCode,
                payload.getMethodCode(),
                payload.getUnitCode(),
                quantity,
                false);
        }
    }

    /**
     * 按药品定义主键加载读模型行。
     *
     * @param medicationDefinitionId 药品定义主键
     * @return 读模型；未命中时返回 null（门控由 FIFO 层抛业务异常）
     */
    private ProductDefinitionDisplayDto loadMedicationProductRow(Long medicationDefinitionId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(medicationDefinitionId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        return map.get(ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationDefinitionId));
    }
}
