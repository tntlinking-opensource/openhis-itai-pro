package com.openhis.whale.mrms.application;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.mrms.application.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.dto.CaseRecordSummaryDto;
import com.openhis.whale.mrms.application.request.LongValueRequest;
import com.openhis.whale.mrms.application.request.QueryCaseRecordRequest;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.repository.CaseRecordRepository;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 病案主档 AppService → /api/app/caseRecord/{methodName}
 */
@Service
@Validated
@RequiredArgsConstructor
public class CaseRecordAppService {

    private final CaseRecordManager caseRecordManager;
    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordAssembler caseRecordAssembler;

    public CaseRecordDetailDto getById(@Valid LongValueRequest request) {
        CaseRecord record = caseRecordManager.requireById(request.getValue());
        return caseRecordAssembler.toDetail(record);
    }

    public CaseRecordDetailDto getByEncounterId(@Valid LongValueRequest request) {
        CaseRecord record = caseRecordManager.findByEncounterId(request.getValue())
                .orElseThrow(() -> new EntityNotFoundException("病案主档不存在，encounterId=" + request.getValue()));
        return caseRecordAssembler.toDetail(record);
    }

    public CaseRecordDetailDto createForEncounter(@Valid LongValueRequest request) {
        CaseRecord record = caseRecordManager.createIfAbsent(request.getValue());
        return caseRecordAssembler.toDetail(record);
    }

    public PagedResult<CaseRecordSummaryDto> queryPage(@Valid QueryCaseRecordRequest request) {
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
        Long tenantId = TenantUtils.getCurrentTenantId();
        Specification<CaseRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (StringUtils.hasText(request.getStatusCode())) {
                predicates.add(cb.equal(root.get("statusCode"), request.getStatusCode()));
            }
            if (StringUtils.hasText(request.getDeptCode())) {
                predicates.add(cb.equal(root.get("deptCode"), request.getDeptCode()));
            }
            if (StringUtils.hasText(request.getKeyword())) {
                String like = "%" + request.getKeyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("patientName"), like),
                        cb.like(root.get("inpatientNo"), like),
                        cb.like(root.get("medcasno"), like)));
            }
            if (request.getDischargeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dischargeAt"),
                        request.getDischargeFrom().atStartOfDay()));
            }
            if (request.getDischargeTo() != null) {
                LocalDateTime end = request.getDischargeTo().plusDays(1).atStartOfDay().minusNanos(1);
                predicates.add(cb.lessThanOrEqualTo(root.get("dischargeAt"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<CaseRecord> page = caseRecordRepository.findAll(spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "dischargeAt", "id")));
        List<CaseRecordSummaryDto> items = page.getContent().stream().map(caseRecordAssembler::toSummary).toList();
        return PagedResult.of(items, page.getTotalElements(), pageNo, pageSize);
    }
}
