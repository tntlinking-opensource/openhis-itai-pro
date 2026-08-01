/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.mapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisApplyNoResolveResult;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisApplyNoServiceRequestResolver;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisLabJdbcClient;
import com.openhis.whale.crb.bridge.lab.pathb.YunlisLabOrderRow;
import com.openhis.whale.crb.bridge.push.context.CrbMappingContext;
import com.openhis.whale.crb.bridge.push.context.CrbPatientReadSupport;
import com.openhis.whale.crb.bridge.push.mapper.support.AbstractSourceIdEmrPayloadMapper;
import com.openhis.whale.crb.bridge.push.support.CrbIdFormatter;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.dict.queries.CrbCodeMapQueryService;
import com.openhis.whale.crb.domain.dict.queries.model.NationalDeptResolveModel;
import com.openhis.whale.crb.gateway.scheme.push.share.CrbEmrResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.ACTIVITY_TYPE_INPATIENT_CODE;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.ACTIVITY_TYPE_INPATIENT_NAME;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.ACTIVITY_TYPE_OUTPATIENT_CODE;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.ACTIVITY_TYPE_OUTPATIENT_NAME;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.DEFAULT_OPERATOR_ID;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.LAB_SOURCE_EXTERNAL;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.LAB_TYPE_INPATIENT;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.LAB_TYPE_OUTPATIENT;

/**
 * emrExLab 字段映射（Path B：yunlis {@code L_LABORDER_ID} = sourceId）。
 */
@Slf4j
@Component
public class EmrExLabMapper extends AbstractSourceIdEmrPayloadMapper {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CrbProperties crbProperties;
    private final ObjectProvider<YunlisLabJdbcClient> yunlisLabJdbcClient;
    private final YunlisApplyNoServiceRequestResolver applyNoResolver;
    private final CrbPatientReadSupport patientReadSupport;
    private final EncounterQueryService encounterQueryService;
    private final CrbCodeMapQueryService codeMapQueryService;

    public EmrExLabMapper(
            CrbProperties crbProperties,
            ObjectProvider<YunlisLabJdbcClient> yunlisLabJdbcClient,
            YunlisApplyNoServiceRequestResolver applyNoResolver,
            CrbPatientReadSupport patientReadSupport,
            EncounterQueryService encounterQueryService,
            CrbCodeMapQueryService codeMapQueryService) {
        this.crbProperties = crbProperties;
        this.yunlisLabJdbcClient = yunlisLabJdbcClient;
        this.applyNoResolver = applyNoResolver;
        this.patientReadSupport = patientReadSupport;
        this.encounterQueryService = encounterQueryService;
        this.codeMapQueryService = codeMapQueryService;
    }

    @Override
    public String resource() {
        return CrbEmrResource.EMR_EX_LAB;
    }

    @Override
    protected String formatBusinessKey(Long sourceId, CrbMappingContext ctx) {
        return CrbIdFormatter.formatLabId(ctx.getOrgCode(), sourceId);
    }

    @Override
    public Instant operationTime(Long sourceId, CrbMappingContext ctx) {
        if (!isExternalPathB()) {
            return Instant.now();
        }
        YunlisLabJdbcClient client = yunlisLabJdbcClient.getIfAvailable();
        if (client == null) {
            return Instant.now();
        }
        Optional<YunlisLabOrderRow> orderOpt = client.findLabOrderById(sourceId);
        if (orderOpt.isEmpty() || orderOpt.get().getCheckTime() == null) {
            return Instant.now();
        }
        return orderOpt.get().getCheckTime().atZone(ZONE).toInstant();
    }

    @Override
    public ObjectNode toPayload(Long sourceId, CrbMappingContext ctx) {
        if (!isExternalPathB()) {
            return skeletonPayload(sourceId, ctx);
        }
        YunlisLabJdbcClient client = yunlisLabJdbcClient.getIfAvailable();
        if (client == null) {
            log.warn("emrExLab Path B: yunlis client unavailable, sourceId={}", sourceId);
            return null;
        }
        Optional<YunlisLabOrderRow> orderOpt = client.findLabOrderById(sourceId);
        if (orderOpt.isEmpty()) {
            log.warn("emrExLab Path B: laborder missing, sourceId={}", sourceId);
            return null;
        }
        return buildExternalPayload(orderOpt.get(), ctx);
    }

    private ObjectNode buildExternalPayload(YunlisLabOrderRow order, CrbMappingContext ctx) {
        ObjectNode node = newPayloadNode();
        String exLabId = CrbIdFormatter.formatLabId(ctx.getOrgCode(), order.getLabOrderId());
        node.put("id", exLabId);

        String applyNo = firstNonBlank(order.getBarcode(), order.getApplyNo());
        Optional<YunlisApplyNoResolveResult> resolved = applyNoResolver.resolve(applyNo);
        Long patientId = null;
        Long encounterId = null;
        if (resolved.isPresent()) {
            patientId = resolved.get().getPatientId();
            encounterId = resolved.get().getEncounterId();
        }

        putPatientFields(node, patientId, order);
        putActivityType(node, order.getType());
        putSerialNumber(node, encounterId, order);
        putIfPresent(node, "wardNo", order.getWardName());
        putIfPresent(node, "wardName", order.getWardName());
        putIfPresent(node, "bedNo", order.getBed());
        putIfPresent(node, "applicationFormNo", firstNonBlank(order.getBarcode(), order.getApplyNo()));

        putDeptPair(
                node,
                "applyDeptCode",
                "applyDeptName",
                ctx.getTenantId(),
                order.getApplyDeptCode(),
                order.getApplyDeptName());
        putIfPresent(node, "applyOrgCode", ctx.getOrgCode());
        putIfPresent(node, "applyOrgName", ctx.getOrgName());
        // 申请医师 User 对照未接：有账号则原样写入并 WARN
        if (trimToNull(order.getRequester()) != null) {
            node.put("applyPhysicianId", order.getRequester());
            log.debug("emrExLab applyPhysicianId uses LIS REQUESTER={}", order.getRequester());
        }

        putIfPresent(node, "specimenCategoryCode", order.getSpecimen());
        putIfPresent(node, "specimenCategoryName", firstNonBlank(order.getSpecimenName(), order.getSpecimen()));
        putIfPresent(node, "specimenNo", order.getSampleNo());
        putDate(node, "specimenSamplingDate", firstTime(order.getSamplingTime(), order.getReceiveTime()));
        putDate(node, "specimenReceivingDate", order.getReceiveTime());
        if (trimToNull(order.getExecutor()) != null) {
            node.put("examinationPhysicianId", order.getExecutor());
        }
        putDate(node, "examinationDate", firstTime(order.getExecuteTime(), order.getCheckTime()));
        putIfPresent(node, "examinationReportNo", order.getSampleNo());
        putIfPresent(node, "examinationNotes", order.getRemark());
        putDate(node, "examinationReportDate", order.getCheckTime());
        if (trimToNull(order.getAuditor()) != null) {
            node.put("examinationReportId", order.getAuditor());
        }

        putDeptPair(
                node,
                "deptCode",
                "deptName",
                ctx.getTenantId(),
                order.getExecDeptCode(),
                order.getLabDepartment());

        Instant opTime = operationTime(order.getLabOrderId(), ctx);
        String operatorId = firstNonBlank(order.getAuditor(), ctx.getOperatorId(), DEFAULT_OPERATOR_ID);
        CrbMappingContext opCtx = new CrbMappingContext()
                .setOrgCode(ctx.getOrgCode())
                .setOrgName(ctx.getOrgName())
                .setTenantId(ctx.getTenantId())
                .setOperatorId(operatorId);
        putOperationMeta(node, opCtx, opTime);
        return node;
    }

    private void putPatientFields(ObjectNode node, Long patientId, YunlisLabOrderRow order) {
        if (patientId != null) {
            node.put("patientId", CrbIdFormatter.formatPatientId(patientId));
            Optional<Patient> patientOpt = patientReadSupport.loadById(patientId);
            if (patientOpt.isPresent()) {
                Patient patient = patientOpt.get();
                putIfPresent(node, "patientName", patient.getName());
                putIfPresent(node, "idCard", patient.getIdCard());
                return;
            }
        }
        putIfPresent(node, "patientName", order.getPatientName());
        putIfPresent(node, "idCard", order.getIdCard());
        log.warn("emrExLab patientId unresolved, labOrderId={}", order.getLabOrderId());
    }

    private void putSerialNumber(ObjectNode node, Long encounterId, YunlisLabOrderRow order) {
        if (encounterId != null) {
            Optional<Encounter> encounterOpt = encounterQueryService.findOptionalEncounterEntityById(encounterId);
            if (encounterOpt.isPresent() && trimToNull(encounterOpt.get().getBusNo()) != null) {
                node.put("serialNumber", encounterOpt.get().getBusNo());
                return;
            }
        }
        String fallback = firstNonBlank(order.getPatientBh(), order.getPatientId(), order.getSampleNo());
        putIfPresent(node, "serialNumber", fallback);
    }

    private void putActivityType(ObjectNode node, String lisType) {
        String type = trimToNull(lisType);
        if (LAB_TYPE_INPATIENT.equals(type)) {
            node.put("activityTypeCode", ACTIVITY_TYPE_INPATIENT_CODE);
            node.put("activityTypeName", ACTIVITY_TYPE_INPATIENT_NAME);
            return;
        }
        if (LAB_TYPE_OUTPATIENT.equals(type)) {
            node.put("activityTypeCode", ACTIVITY_TYPE_OUTPATIENT_CODE);
            node.put("activityTypeName", ACTIVITY_TYPE_OUTPATIENT_NAME);
            return;
        }
        // 其它 TYPE（如手动登记）按门诊占位，避免缺必填
        node.put("activityTypeCode", ACTIVITY_TYPE_OUTPATIENT_CODE);
        node.put("activityTypeName", ACTIVITY_TYPE_OUTPATIENT_NAME);
    }

    private void putDeptPair(
            ObjectNode node,
            String codeKey,
            String nameKey,
            Long tenantId,
            String locationBusNo,
            String fallbackName) {
        String busNo = trimToNull(locationBusNo);
        if (busNo != null && tenantId != null) {
            NationalDeptResolveModel resolved = codeMapQueryService.resolveNationalDept(tenantId, busNo);
            if (resolved != null && Boolean.TRUE.equals(resolved.getFound())) {
                putIfPresent(node, codeKey, resolved.getTargetDeptCode());
                putIfPresent(node, nameKey, firstNonBlank(resolved.getTargetDeptName(), fallbackName));
                return;
            }
        }
        putIfPresent(node, codeKey, busNo);
        putIfPresent(node, nameKey, fallbackName);
    }

    private ObjectNode skeletonPayload(Long sourceId, CrbMappingContext ctx) {
        ObjectNode node = newPayloadNode();
        node.put("id", formatBusinessKey(sourceId, ctx));
        putOperationMeta(node, ctx, Instant.now());
        return node;
    }

    private boolean isExternalPathB() {
        String source = trimToNull(crbProperties.getLab().getSource());
        return LAB_SOURCE_EXTERNAL.equals(source);
    }

    private static void putIfPresent(ObjectNode node, String key, String value) {
        String v = trimToNull(value);
        if (v != null) {
            node.put(key, v);
        }
    }

    private static void putDate(ObjectNode node, String key, LocalDateTime time) {
        if (time == null) {
            return;
        }
        node.put(key, DT_FMT.format(time));
    }

    private static LocalDateTime firstTime(LocalDateTime first, LocalDateTime second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }
}
