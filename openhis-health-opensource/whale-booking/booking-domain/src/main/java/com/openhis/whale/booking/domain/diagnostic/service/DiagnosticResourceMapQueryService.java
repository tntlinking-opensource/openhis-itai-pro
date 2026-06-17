package com.openhis.whale.booking.domain.diagnostic.service;

import com.openhis.whale.booking.domain.diagnostic.entity.DiagnosticResourceMap;
import com.openhis.whale.booking.domain.diagnostic.repository.DiagnosticResourceMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiagnosticResourceMapQueryService {

    private final DiagnosticResourceMapRepository repository;

    public Page<DiagnosticResourceMap> pageByOrganization(Long organizationId, Pageable pageable) {
        Specification<DiagnosticResourceMap> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (organizationId != null) {
                p.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            }
            return p;
        };
        return repository.findAll(spec, pageable);
    }

    public DiagnosticResourceMap loadById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 按机构与诊疗项目解析启用中的资源映射。
     */
    public Optional<DiagnosticResourceMap> findActiveByActivityDefinitionId(Long organizationId, Long activityDefinitionId) {
        if (organizationId == null || activityDefinitionId == null) {
            return Optional.empty();
        }
        Specification<DiagnosticResourceMap> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            p.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            p.getExpressions().add(cb.equal(root.get("activityDefinitionId"), activityDefinitionId));
            p.getExpressions().add(cb.equal(root.get("activeFlag"), true));
            return p;
        };
        List<DiagnosticResourceMap> rows = repository.findAll(spec);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }
}
