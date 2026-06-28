package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.repository.ScheduleTemplateRepository;
import com.openhis.whale.booking.domain.template.repository.SlotProvisionTemplateRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 排班模板读侧查询（T1/T2）。
 */
@Service
@RequiredArgsConstructor
public class TemplateQueryService {

    private final SlotProvisionTemplateRepository slotProvisionTemplateRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;

    /**
     * 分页查询 T1 放号时段模板。
     */
    public Page<SlotProvisionTemplate> pageSlotProvisionTemplates(
        Long organizationId, String keyword, Pageable pageable) {
        return slotProvisionTemplateRepository.findAll(
            this.buildSlotProvisionSpec(organizationId, keyword), pageable);
    }

    /**
     * 按主键加载 T1 模板。
     */
    public SlotProvisionTemplate loadSlotProvisionTemplateById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        var optional = slotProvisionTemplateRepository.findById(id);
        var row = optional.orElse(null);
        if (row != null && Boolean.TRUE.equals(row.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_NOT_FOUND, "放号时段模板已删除，请刷新后重新选择");
        }
        return optional.orElseThrow(() -> new BusinessException(
            ErrorCodeConstants.SCHEDULE_NOT_FOUND, "放号时段模板不存在"));
    }

    /**
     * 分页查询 T2 日程模板。
     */
    public Page<ScheduleTemplate> pageScheduleTemplates(
        Long organizationId, String keyword, Pageable pageable) {
        return scheduleTemplateRepository.findAll(
            this.buildScheduleTemplateSpec(organizationId, keyword), pageable);
    }

    /**
     * 按主键加载 T2 模板。
     */
    public ScheduleTemplate loadScheduleTemplateById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        return scheduleTemplateRepository.findById(id).filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "日程模板不存在"));
    }

    /**
     * 科室下启用的 T1 下拉（生成/编辑 T2 时）。
     */
    public List<SlotProvisionTemplate> listEnabledSlotProvisionTemplates(Long organizationId) {
        Specification<SlotProvisionTemplate> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isTrue(root.get("enabled")));
            if (organizationId != null) {
                jakarta.persistence.criteria.Predicate orgMatch = cb.equal(root.get("organizationId"), organizationId);
                jakarta.persistence.criteria.Predicate global = cb.isNull(root.get("organizationId"));
                predicates.add(cb.or(orgMatch, global));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return slotProvisionTemplateRepository.findAll(spec);
    }

    private Specification<SlotProvisionTemplate> buildSlotProvisionSpec(Long organizationId, String keyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("templateCode")), like),
                    cb.like(cb.lower(root.get("templateName")), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<ScheduleTemplate> buildScheduleTemplateSpec(Long organizationId, String keyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("templateCode")), like),
                    cb.like(cb.lower(root.get("templateName")), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
