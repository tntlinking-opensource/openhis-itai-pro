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

package com.openhis.health.application.emergency.doctorstation.advice.orchestration;

import com.openhis.health.application.emergency.doctorstation.advice.constants.EmergencyDoctorStationAdviceConstants;
import com.openhis.health.application.emergency.doctorstation.advice.request.PageEmergencyAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * 急诊医嘱目录分页编排：按 {@code productKind} 路由药品/耗材/诊疗域目录查询。
 */
@Component
@RequiredArgsConstructor
public class EmgAdviceCatalogQueryOrchestrator {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final EmgAdviceCatalogRowAssembler emgAdviceCatalogRowAssembler;

    public Page<DoctorStationAdviceCatalogRowDto> pageEmergencyAdviceCatalog(PageEmergencyAdviceCatalogRequest request) {
        String kind = StringUtils.upperCase(StringUtils.trimToEmpty(request.getProductKind()));
        int pageIndex = request.springPageIndex();
        int pageSize = request.getBoundedPageSize();
        PageRequest pageable = PageRequest.of(pageIndex, pageSize);
        if (EmergencyDoctorStationAdviceConstants.PRODUCT_KIND_MEDICATION.equals(kind)) {
            Specification<MedicationDefinition> spec = buildMedicationCatalogSpec(request);
            Page<MedicationDefinitionPageQueryModel> page = medicationDefinitionQueryService.pageMedicationCatalog(spec, pageable);
            List<DoctorStationAdviceCatalogRowDto> rows = emgAdviceCatalogRowAssembler.assembleMedicationPage(request, page);
            return new PageImpl<>(rows, pageable, page.getTotalElements());
        }
        if (EmergencyDoctorStationAdviceConstants.PRODUCT_KIND_DEVICE.equals(kind)) {
            Page<DeviceDefinition> page = deviceDefinitionQueryService.pageDeviceDefinition(
                pageIndex,
                pageSize,
                request.getKeyword(),
                request.getCategoryCode(),
                null);
            List<DoctorStationAdviceCatalogRowDto> rows = emgAdviceCatalogRowAssembler.assembleDevicePage(request, page);
            return new PageImpl<>(rows, pageable, page.getTotalElements());
        }
        if (EmergencyDoctorStationAdviceConstants.PRODUCT_KIND_ACTIVITY.equals(kind)) {
            ActivityDefinitionPageQuery q = new ActivityDefinitionPageQuery();
            q.setKeyword(request.getKeyword());
            q.setCategoryCode(request.getCategoryCode());
            q.setStatusEnum(PublicationStatus.ACTIVE.getCode());
            Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(q, pageable);
            List<DoctorStationAdviceCatalogRowDto> rows = emgAdviceCatalogRowAssembler.assembleActivityPage(request, page);
            return new PageImpl<>(rows, pageable, page.getTotalElements());
        }
        throw new BusinessException(com.openhis.health.domain.share.constants.ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "不支持的 productKind: " + request.getProductKind());
    }

    private Specification<MedicationDefinition> buildMedicationCatalogSpec(PageEmergencyAdviceCatalogRequest request) {
        return (root, query, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            ands.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            ands.add(cb.equal(root.get(FieldNameConstants.ACTIVE_FLAG), Boolean.TRUE));
            String kw = StringUtils.trimToEmpty(request.getKeyword());
            if (!kw.isEmpty()) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(kw);
                ands.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc)
                ));
            }
            String cc = StringUtils.trimToEmpty(request.getCategoryCode());
            if (!cc.isEmpty()) {
                ands.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), cc));
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
    }
}
