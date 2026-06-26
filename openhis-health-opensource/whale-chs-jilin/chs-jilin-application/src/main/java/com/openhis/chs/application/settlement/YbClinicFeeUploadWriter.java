/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.settlement;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.outpatient.support.ChsFeeUploadResultSupport;
import com.openhis.chs.domain.outpatient.dto.OpSettleChargeLine;
import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import com.openhis.chs.domain.outpatient.repository.YbClinicFeeUploadRepository;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.support.FeeUploadResultApplier;
import com.openhis.chs.domain.settlement.support.FeeUploadResultIndexer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 2204 费用上传行落库。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbClinicFeeUploadWriter {

    private final YbClinicFeeUploadRepository clinicFeeUploadRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 持久化 2204 上传行及平台分割结果。
     */
    public void persistFrom2204(
        Long encounterId,
        String mdtrtId,
        String chrgBchno,
        List<OpSettleChargeLine> lines,
        ResultBody body2204,
        Long tenantId) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, JSONObject> resultBySn = FeeUploadResultIndexer.indexByFeedetlSn(body2204);
        LocalDateTime now = LocalDateTime.now();
        for (OpSettleChargeLine line : lines) {
            if (line == null || line.getChargeItemId() == null || StringUtils.isBlank(line.getFeedetlSn())) {
                continue;
            }
            JSONObject platformRow = resultBySn.get(line.getFeedetlSn().trim());
            YbClinicFeeUpload row = clinicFeeUploadRepository
                .findFirstByChargeItemIdAndChrgBchnoAndTenantIdAndIsDeletedFalse(
                    line.getChargeItemId(), chrgBchno, tenantId)
                .orElseGet(YbClinicFeeUpload::new);
            if (row.getId() == null) {
                row.setCreatedAt(now);
            }
            row.setEncounterId(encounterId);
            row.setChargeItemId(line.getChargeItemId());
            row.setFeedetlSn(line.getFeedetlSn().trim());
            row.setMdtrtId(mdtrtId);
            row.setChrgBchno(chrgBchno);
            row.setDetItemFeeSumamt(line.getDetItemFeeSumamt());
            chargeBatchLifecycle.markClinicFeeUploaded(row);
            FeeUploadResultApplier.applyToClinicRow(row, platformRow);
            row.setUploadedAt(now);
            row.setTenantId(tenantId);
            clinicFeeUploadRepository.save(row);
        }
    }

    public void linkPreSettle(String chrgBchno, Long preSettleId, Long tenantId) {
        if (StringUtils.isBlank(chrgBchno) || preSettleId == null) {
            return;
        }
        List<YbClinicFeeUpload> rows = clinicFeeUploadRepository
            .findAllByChrgBchnoAndTenantIdAndIsDeletedFalse(chrgBchno.trim(), tenantId);
        for (YbClinicFeeUpload row : rows) {
            row.setPreSettleId(preSettleId);
            clinicFeeUploadRepository.save(row);
        }
    }

    public void markSettledByChrgBchno(String chrgBchno, Long tenantId) {
        if (StringUtils.isBlank(chrgBchno)) {
            return;
        }
        List<YbClinicFeeUpload> rows = clinicFeeUploadRepository
            .findAllByChrgBchnoAndUploadStatusAndTenantIdAndIsDeletedFalse(
                chrgBchno.trim(), com.openhis.chs.domain.settlement.YbFeeUploadStatus.UPLOADED, tenantId);
        for (YbClinicFeeUpload row : rows) {
            chargeBatchLifecycle.markClinicFeeSettled(row);
            clinicFeeUploadRepository.save(row);
        }
    }

    public void markRevokedByChrgBchno(String chrgBchno, Long tenantId) {
        markRevokedByChrgBchno(chrgBchno, tenantId, false);
    }

    public void markSettleRevokedByChrgBchno(String chrgBchno, Long tenantId) {
        markRevokedByChrgBchno(chrgBchno, tenantId, true);
    }

    private void markRevokedByChrgBchno(String chrgBchno, Long tenantId, boolean includeSettled) {
        if (StringUtils.isBlank(chrgBchno)) {
            return;
        }
        List<YbClinicFeeUpload> rows = clinicFeeUploadRepository
            .findAllByChrgBchnoAndTenantIdAndIsDeletedFalse(chrgBchno.trim(), tenantId);
        for (YbClinicFeeUpload row : rows) {
            if (includeSettled) {
                chargeBatchLifecycle.markClinicFeeSettleRevoked(row);
            } else {
                chargeBatchLifecycle.markClinicFeeRevoked(row);
            }
            clinicFeeUploadRepository.save(row);
        }
    }
}
