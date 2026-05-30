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

package com.openhis.health.domain.workflow.appointment.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.workflow.appointment.entity.Appointment;
import com.openhis.health.domain.workflow.appointment.queries.model.AppointmentBatchModel;
import com.openhis.health.domain.workflow.appointment.queries.model.AppointmentQueryModel;
import com.openhis.health.domain.workflow.appointment.queries.model.AppointmentQueuePageModel;
import com.openhis.health.domain.workflow.appointment.queries.model.PatientAppointmentModel;
import com.openhis.health.domain.workflow.appointment.repository.AppointmentRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 预约与候诊读侧查询服务。
 *
 * <p>约束：只读查询，不写库；默认过滤 {@code isDeleted=false}。</p>
 */
@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;

    /**
     * 按就诊查询有效预约摘要；无记录时返回 null。
     *
     * @param encounterId 就诊标识
     * @return 预约模型或 null
     */
    public AppointmentQueryModel findByEncounterId(Long encounterId) {
        Appointment appointment = appointmentRepository.findByEncounterId(encounterId).orElse(null);
        // 就诊尚未产生预约事实时返回空，由上层决定是否引导挂号/预约
        if (appointment == null) {
            return null;
        }
        AppointmentQueryModel model = new AppointmentQueryModel();
        model.setId(appointment.getId());
        model.setAppointmentKind(appointment.getAppointmentKind());
        model.setDisplayOrder(appointment.getDisplayOrder());
        model.setStatusView("ACTIVE");
        return model;
    }

    /**
     * 按机构与诊室分页查询候诊队列。
     *
     * @param organizationId 机构标识
     * @param locationId 诊室/目的地库位标识
     * @param pageable 分页
     * @return 分页队列行
     * @throws BusinessException 机构或诊室未传
     */
    public Page<AppointmentQueuePageModel> findQueueByOrganizationAndLocation(
        Long organizationId, Long locationId, Pageable pageable) {
        // 队列必须绑定到具体机构与诊室，否则无法界定候诊资源池
        if (organizationId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "查询队列时 organizationId/locationId 不能为空");
        }
        return appointmentRepository.findByOrganizationIdAndDestinationLocationId(
            organizationId, locationId, pageable).map(this::toQueuePageModel);
    }

    /**
     * 批量按就诊查询预约摘要，保持入参顺序中有预约的就诊才输出一行。
     *
     * @param encounterIds 就诊 id 列表
     * @return 批量模型列表
     */
    public List<AppointmentBatchModel> findByEncounterIdIn(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Appointment> appointments = appointmentRepository.findByEncounterIdIn(encounterIds);
        Map<Long, Appointment> byEncounterId = new HashMap<>();
        for (Appointment appointment : appointments) {
            // 同一就诊多预约时保留首次命中，与入参顺序无关的稳定去重
            if (appointment.getEncounterId() != null && !byEncounterId.containsKey(appointment.getEncounterId())) {
                byEncounterId.put(appointment.getEncounterId(), appointment);
            }
        }

        List<AppointmentBatchModel> models = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            Appointment appointment = byEncounterId.get(encounterId);
            // 该就诊无预约记录则跳过，不造占位行
            if (appointment == null) {
                continue;
            }
            AppointmentBatchModel model = new AppointmentBatchModel();
            model.setEncounterId(encounterId);
            model.setAppointmentId(appointment.getId());
            model.setQueueStatus("ACTIVE");
            models.add(model);
        }
        return models;
    }

    /**
     * 按患者查询其名下预约时段列表（复诊、随访场景）。
     *
     * @param patientId 患者标识
     * @return 预约列表
     * @throws BusinessException patientId 为空
     */
    public List<PatientAppointmentModel> findByPatientId(Long patientId) {
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "按患者查询时 patientId 不能为空");
        }
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        List<PatientAppointmentModel> models = new ArrayList<>();
        for (Appointment appointment : appointments) {
            PatientAppointmentModel model = new PatientAppointmentModel();
            model.setId(appointment.getId());
            model.setPatientId(appointment.getPatientId());
            model.setSlotId(appointment.getSlotId());
            model.setStartInstant(appointment.getStartInstant());
            models.add(model);
        }
        return models;
    }

    /**
     * 候诊队列行映射：暴露分诊级别与排序等叫号所需字段。
     *
     * @param appointment 预约实体
     * @return 队列分页行模型
     */
    private AppointmentQueuePageModel toQueuePageModel(Appointment appointment) {
        AppointmentQueuePageModel model = new AppointmentQueuePageModel();
        model.setId(appointment.getId());
        model.setEncounterId(appointment.getEncounterId());
        model.setTriageLevel(appointment.getTriageLevel());
        model.setDisplayOrder(appointment.getDisplayOrder());
        return model;
    }
}

