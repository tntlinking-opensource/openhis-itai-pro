/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.prescription.support;

import com.alibaba.fastjson2.JSON;
import com.openhis.elep.domain.gateway.ElepOpCode;
import com.openhis.elep.domain.prescription.entity.YbElepUploadLog;
import com.openhis.elep.domain.prescription.repository.YbElepUploadLogRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 写入 {@code yb_elep_upload_log}。 */
@Component
@RequiredArgsConstructor
public class ElepUploadLogWriter {

    private final YbElepUploadLogRepository uploadLogRepository;

    public void write(
            Long prescriptionId,
            ElepOpCode opCode,
            Object request,
            Object response,
            boolean success,
            String errorMessage,
            Long operatorId) {
        YbElepUploadLog log = new YbElepUploadLog();
        log.setPrescriptionId(prescriptionId);
        log.setOpCode(opCode.getCode());
        log.setRequestJson(maskSecrets(JSON.toJSONString(request)));
        log.setResponseJson(JSON.toJSONString(response));
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setTenantId(TenantUtils.getCurrentTenantId());
        log.setCreatedAt(LocalDateTime.now());
        log.setCreatedBy(operatorId);
        log.setIsDeleted(false);
        uploadLogRepository.save(log);
    }

    private static String maskSecrets(String json) {
        if (json == null) {
            return null;
        }
        return json
                .replaceAll("\"prePrvKey\"\\s*:\\s*\"[^\"]*\"", "\"prePrvKey\":\"***\"")
                .replaceAll("\"pre_prv_key\"\\s*:\\s*\"[^\"]*\"", "\"pre_prv_key\":\"***\"");
    }
}
