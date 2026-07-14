/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.openhis.whale.crb.bridge.push.context.CrbPatientReadSupport;
import com.openhis.whale.crb.bridge.push.mapper.support.AbstractSourceIdEmrPayloadMapper;
import com.openhis.whale.crb.bridge.push.support.CrbIdFormatter;
import com.openhis.whale.crb.gateway.scheme.push.assembler.EmrPatientInfoPayloadAssembler;
import com.openhis.whale.crb.gateway.scheme.push.assembler.PushOperationMetaAssembler;
import com.openhis.whale.crb.gateway.scheme.push.model.EmrPatientInfoPayload;
import com.openhis.whale.crb.gateway.scheme.push.model.PushAssemblyContext;
import com.openhis.whale.crb.gateway.scheme.push.model.PushOperationMeta;
import com.openhis.whale.crb.gateway.scheme.push.share.CrbEmrResource;
import com.openhis.whale.crb.gateway.scheme.push.share.PushSchemeJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/**
 * emrPatientInfo 字段映射（P10-C/D 参考实现：gateway scheme assembler）。
 */
@Component
@RequiredArgsConstructor
public class EmrPatientInfoMapper extends AbstractSourceIdEmrPayloadMapper {

    private final CrbPatientReadSupport patientReadSupport;

    @Override
    public String resource() {
        return CrbEmrResource.EMR_PATIENT_INFO;
    }

    @Override
    protected String formatBusinessKey(Long sourceId, CrbMappingContext ctx) {
        return CrbIdFormatter.formatPatientId(sourceId);
    }

    @Override
    public ObjectNode toPayload(Long sourceId, CrbMappingContext ctx) {
        Patient patient = patientReadSupport.loadById(sourceId).orElse(null);
        if (patient == null || patient.getName() == null || patient.getName().isBlank()) {
            return null;
        }
        Instant operationTime = resolveOperationTime(patient);
        PushAssemblyContext assemblyContext = new PushAssemblyContext(
                ctx.getOrgCode(), ctx.getOrgName(), ctx.getOperatorId());
        PushOperationMeta meta = PushOperationMetaAssembler.assemble(assemblyContext, operationTime);
        EmrPatientInfoPayload payload = EmrPatientInfoPayloadAssembler.assemble(
                formatBusinessKey(sourceId, ctx),
                patient.getName(),
                patient.getIdCard(),
                patient.getGenderEnum() != null ? String.valueOf(patient.getGenderEnum()) : null,
                patient.getBirthDate() != null ? patient.getBirthDate().toString() : null,
                patient.getPhone(),
                patient.getWorkCompany(),
                patient.getNationalityCode(),
                meta);
        return PushSchemeJsonSupport.toObjectNode(payload);
    }

    private static Instant resolveOperationTime(Patient patient) {
        if (patient.getUpdatedAt() != null) {
            return patient.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant();
        }
        if (patient.getCreatedAt() != null) {
            return patient.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
        }
        return Instant.now();
    }
}
