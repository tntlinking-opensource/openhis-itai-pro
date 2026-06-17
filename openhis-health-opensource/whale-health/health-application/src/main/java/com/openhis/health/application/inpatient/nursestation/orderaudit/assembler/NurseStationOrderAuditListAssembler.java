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

package com.openhis.health.application.inpatient.nursestation.orderaudit.assembler;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditRowDto;
import com.openhis.health.module.querysupport.order.NurseStationOrderViewListAssembler;
import com.openhis.health.module.querysupport.order.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.module.querysupport.order.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.module.querysupport.order.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站医嘱校对列表装配（IF-IP-AUD-01 Asm，BR-IP-AUD-005/006）。
 *
 * <p>在 OrderInfo 骨架上叠加药/诊明细与展示名；行展示委托
 * {@link NurseStationOrderViewListAssembler}；不返回余额/费别（BR-IP-AUD-007）。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderAuditListAssembler {

    /** 医嘱查看列表行展示内核。 */
    private final NurseStationOrderViewListAssembler orderViewListAssembler;

    /**
     * OrderInfo 骨架 + 域明细 → 按就诊分组且组内已排序的列表。
     *
     * @param skeleton       过滤后的读模型行
     * @param medByEncounter 就诊 → 药品明细列表
     * @param svcByEncounter 就诊 → 诊疗明细列表
     * @return 患者分组列表（BR-IP-AUD-005）
     */
    public List<NurseStationOrderAuditPatientGroupDto> assemblePatientGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        NurseStationOrderViewListDetailIndex index =
            orderViewListAssembler.indexEncounterDetails(skeleton, medByEncounter, svcByEncounter);
        NurseStationOrderViewDisplayContext displayContext =
            orderViewListAssembler.prepareDisplayContext(index);
        Map<Long, List<NurseStationOrderAuditRowDto>> rowsByEncounter = new LinkedHashMap<>();
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            NurseStationOrderAuditRowDto line = this.mapOrderRow(order, index, displayContext);
            if (line == null) {
                continue;
            }
            List<NurseStationOrderAuditRowDto> bucket = rowsByEncounter.get(order.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByEncounter.put(order.getEncounterId(), bucket);
            }
            bucket.add(line);
        }
        List<NurseStationOrderAuditPatientGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NurseStationOrderAuditRowDto>> entry : rowsByEncounter.entrySet()) {
            List<NurseStationOrderAuditRowDto> sorted = this.sortRows(entry.getValue());
            NurseStationOrderAuditPatientGroupDto group = new NurseStationOrderAuditPatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(sorted);
            groups.add(group);
        }
        return groups;
    }

    /**
     * 将单条 OrderInfo 映射为校对列表行。
     *
     * @return 列表行；非药/诊类型返回 null
     */
    private NurseStationOrderAuditRowDto mapOrderRow(
        InpatientOrderListSkeleton order,
        NurseStationOrderViewListDetailIndex index,
        NurseStationOrderViewDisplayContext displayContext) {
        Long requestId = orderViewListAssembler.resolveRequestId(order);
        NurseStationOrderViewDisplayCore core;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildMedicationDisplayCore(
                order, index.getMedDetailById().get(requestId), displayContext);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildServiceDisplayCore(
                order, index.getSvcDetailById().get(requestId), displayContext);
        } else {
            return null;
        }
        if (core == null) {
            return null;
        }
        NurseStationOrderAuditRowDto line = new NurseStationOrderAuditRowDto();
        this.applyViewCoreToAuditRow(core, line);
        return line;
    }

    /**
     * 医嘱查看展示内核 → 校对列表行 DTO。
     */
    private void applyViewCoreToAuditRow(NurseStationOrderViewDisplayCore core, NurseStationOrderAuditRowDto line) {
        line.setListRowKey(core.getListRowKey());
        line.setRequestId(core.getRequestId());
        line.setOrderType(core.getOrderType());
        line.setEncounterId(core.getEncounterId());
        line.setPatientId(core.getPatientId());
        line.setStatusEnum(core.getStatusEnum());
        line.setStatusDisplay(core.getStatusDisplay());
        line.setTherapyEnum(core.getTherapyEnum());
        line.setTherapyDisplay(core.getTherapyDisplay());
        line.setCategoryEnum(core.getCategoryEnum());
        line.setAdviceDisplayName(core.getAdviceDisplayName());
        line.setSpecificationText(core.getSpecificationText());
        line.setDosageDisplay(core.getDosageDisplay());
        line.setUsageDisplay(core.getUsageDisplay());
        line.setFrequencyDisplay(core.getFrequencyDisplay());
        line.setAuthoredTime(core.getAuthoredTime());
        line.setStopTime(core.getStopTime());
        line.setRequesterName(core.getRequesterName());
        line.setPerformerLocationDisplay(core.getPerformerLocationDisplay());
        line.setGroupId(core.getGroupId());
        line.setGroupNo(core.getGroupNo());
        line.setSortNumber(core.getSortNumber());
    }

    /**
     * 组内排序（BR-IP-AUD-005）：状态 → 组号/排序号升序，开立时间降序。
     *
     * @param rows 未排序行
     * @return 排序后列表
     */
    private List<NurseStationOrderAuditRowDto> sortRows(List<NurseStationOrderAuditRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<NurseStationOrderAuditRowDto> copy = new ArrayList<>(rows);
        copy.sort(Comparator
            .comparing(NurseStationOrderAuditRowDto::getStatusEnum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(NurseStationOrderAuditRowDto::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(NurseStationOrderAuditRowDto::getGroupNo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(NurseStationOrderAuditRowDto::getAuthoredTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return copy;
    }
}
