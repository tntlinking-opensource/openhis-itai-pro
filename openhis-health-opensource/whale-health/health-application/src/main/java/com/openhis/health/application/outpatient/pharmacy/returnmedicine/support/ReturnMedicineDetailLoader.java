/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.support;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊退药明细读组装：待退/已退发放行 + 负向/原药嘱 + 药品定义 + 开立医生（AS-OP-PHA-002 IF-PHA-02-03）。
 *
 * <p>退药链路<strong>不</strong>关联原正向发药单：批号/追溯码取自待退行（创建待退时已复制）；
 * 原药嘱经负向请求的 {@code refund_medicine_id} 解析；应退金额由 App 层按 {@code service_id} 汇总。</p>
 */
@Component
@RequiredArgsConstructor
public class ReturnMedicineDetailLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 查询退药明细行列表。
     *
     * @param query 就诊主键与可选退药状态过滤
     * @return 明细行；就诊为空时返回空列表
     */
    public List<ReturnMedicineDetailReadRow> listReturnMedicineDetails(ReturnMedicineDetailQuery query) {
        if (query == null || query.getEncounterId() == null) {
            return new ArrayList<>();
        }
        // Pre：按就诊 + 可选退药状态加载门诊待退/已退发放事实
        List<MedicationDispense> filtered = this.medicationDispenseQueryService.listOutpatientReturnMedicationDispensesForDetail(
            query.getEncounterId(),
            query.getRefundStatus(),
            query.getLocationIds());
        if (filtered.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        Set<Long> originMedReqIds = new LinkedHashSet<>();
        for (MedicationDispense md : filtered) {
            if (md.getMedReqId() != null) {
                medReqIds.add(md.getMedReqId());
            }
        }
        // Pre：批量加载负向药嘱，并收集 refund_medicine_id 指向的原药嘱主键
        Map<Long, MedicationRequest> medReqById = this.medicationRequestQueryService.mapEntitiesByIds(medReqIds);
        for (MedicationRequest req : medReqById.values()) {
            if (req == null) {
                continue;
            }
            Long originId = req.getRefundMedicineId();
            if (originId != null) {
                originMedReqIds.add(originId);
            }
        }
        Map<Long, MedicationRequest> originMedReqById = this.medicationRequestQueryService.mapEntitiesByIds(originMedReqIds);
        Set<Long> practitionerIds = new HashSet<>();
        for (MedicationRequest req : medReqById.values()) {
            this.collectRequesterId(practitionerIds, req);
        }
        for (MedicationRequest req : originMedReqById.values()) {
            this.collectRequesterId(practitionerIds, req);
        }
        Map<Long, Practitioner> practitionerById = this.loadPractitioners(practitionerIds);
        Map<Long, MedicationDefinition> medicationById = this.loadMedicationDefinitions(filtered);
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        List<ReturnMedicineDetailReadRow> rows = new ArrayList<>();
        for (MedicationDispense md : filtered) {
            ReturnMedicineDetailReadRow row = new ReturnMedicineDetailReadRow();
            row.setDispenseId(md.getId());
            row.setRequestId(md.getMedReqId());
            MedicationRequest neg = medReqById.get(md.getMedReqId());
            Long refundOriginId = null;
            if (neg != null) {
                refundOriginId = neg.getRefundMedicineId();
            }
            if (refundOriginId == null) {
                refundOriginId = md.getMedReqId();
            }
            row.setRefundOriginRequestId(refundOriginId);
            MedicationRequest originReq = originMedReqById.get(refundOriginId);
            row.setItemId(md.getMedicationId());
            row.setItemName(this.resolveMedicationName(medicationById, md.getMedicationId()));
            row.setLotNumber(this.resolveLotNumber(md));
            row.setQuantity(this.absReturnMedicineQuantity(md.getDispenseQuantity(), md.getQuantity()));
            String unitCode = StringUtils.defaultString(md.getUnitCode());
            row.setUnitCode(unitCode);
            row.setUnitCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, unitCode));
            row.setTotalPrice(BigDecimal.ZERO);
            row.setDoctorName(this.resolveDoctorName(practitionerById, neg, originReq));
            DispenseStatus st = md.getStatusEnum();
            if (st != null) {
                row.setRefundEnum(st.getCode());
                row.setRefundEnumText(st.getDisplay());
            } else {
                row.setRefundEnum(GeneralSharedConstants.EMPTY_STRING);
                row.setRefundEnumText(GeneralSharedConstants.EMPTY_STRING);
            }
            if (neg != null) {
                row.setReqStatus(neg.getStatusEnum());
                RequestStatus rs = EnumUtil.fromCode(RequestStatus.class, neg.getStatusEnum());
                if (rs != null) {
                    row.setReqStatusText(rs.getDisplay());
                } else {
                    row.setReqStatusText(StringUtils.defaultString(neg.getStatusEnum()));
                }
            } else {
                row.setReqStatus(GeneralSharedConstants.EMPTY_STRING);
                row.setReqStatusText(GeneralSharedConstants.EMPTY_STRING);
            }
            row.setServiceTable(TableNameConstants.MED_MEDICATION_REQUEST);
            row.setTraceNo(this.resolveTraceNo(md));
            rows.add(row);
        }
        return rows;
    }

    /** 收集开立医生主键。 */
    private void collectRequesterId(Set<Long> practitionerIds, MedicationRequest req) {
        if (req != null && req.getRequesterId() != null) {
            practitionerIds.add(req.getRequesterId());
        }
    }

    /** 批号：取自待退/已退发放行。 */
    private String resolveLotNumber(MedicationDispense current) {
        if (current != null && StringUtils.isNotEmpty(current.getLotNumber())) {
            return current.getLotNumber().trim();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 追溯码：规则同 {@link #resolveLotNumber}。 */
    private String resolveTraceNo(MedicationDispense current) {
        if (current != null && StringUtils.isNotEmpty(current.getTraceNo())) {
            return current.getTraceNo().trim();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 开立医生展示名：负向药嘱 requester 优先，否则原药嘱。 */
    private String resolveDoctorName(
        Map<Long, Practitioner> practitionerById,
        MedicationRequest refundReq,
        MedicationRequest originReq) {
        Long requesterId = null;
        if (refundReq != null && refundReq.getRequesterId() != null) {
            requesterId = refundReq.getRequesterId();
        } else if (originReq != null) {
            requesterId = originReq.getRequesterId();
        }
        return this.resolvePractitionerName(practitionerById, requesterId);
    }

    /** 退药数量展示绝对值。 */
    private BigDecimal absReturnMedicineQuantity(BigDecimal dispenseQuantity, BigDecimal quantity) {
        BigDecimal base = dispenseQuantity;
        if (base == null) {
            base = quantity;
        }
        if (base == null) {
            return BigDecimal.ZERO;
        }
        return base.abs();
    }

    /** 批量加载医务人员。 */
    private Map<Long, Practitioner> loadPractitioners(Set<Long> ids) {
        Map<Long, Practitioner> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (Practitioner row : this.practitionerQueryService.findAllByIds(ids)) {
            if (row != null && row.getId() != null) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /** 批量加载药品定义。 */
    private Map<Long, MedicationDefinition> loadMedicationDefinitions(List<MedicationDispense> dispenses) {
        Set<Long> ids = new LinkedHashSet<>();
        for (MedicationDispense md : dispenses) {
            if (md != null && md.getMedicationId() != null) {
                ids.add(md.getMedicationId());
            }
        }
        Map<Long, MedicationDefinition> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (MedicationDefinition med : this.medicationDefinitionQueryService.findAllByIds(ids)) {
            if (med != null && med.getId() != null) {
                map.put(med.getId(), med);
            }
        }
        return map;
    }

    /** 药品名称回落。 */
    private String resolveMedicationName(Map<Long, MedicationDefinition> medicationById, Long medicationId) {
        if (medicationId == null || medicationById == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        MedicationDefinition med = medicationById.get(medicationId);
        if (med != null && StringUtils.isNotEmpty(med.getName())) {
            return med.getName();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 医生姓名回落。 */
    private String resolvePractitionerName(Map<Long, Practitioner> practitionerById, Long practitionerId) {
        if (practitionerId == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        Practitioner p = practitionerById.get(practitionerId);
        if (p != null && StringUtils.isNotEmpty(p.getName())) {
            return p.getName();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }
}
