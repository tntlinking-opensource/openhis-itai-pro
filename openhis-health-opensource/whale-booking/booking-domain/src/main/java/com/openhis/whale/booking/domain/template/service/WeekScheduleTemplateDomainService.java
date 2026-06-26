package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateItemRepository;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateRepository;
import com.openhis.whale.booking.domain.template.constants.BookingTemplateCodeConstants;
import com.openhis.whale.booking.domain.template.support.BookingTemplateCodeSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

/**
 * TW 周排班模板写侧。
 *
 * <p>保存时全量替换明细；同科室仅允许一条默认周模板。</p>
 */
@Service
@RequiredArgsConstructor
public class WeekScheduleTemplateDomainService {

    private final WeekScheduleTemplateRepository weekScheduleTemplateRepository;
    private final WeekScheduleTemplateItemRepository weekScheduleTemplateItemRepository;
    private final TemplateQueryService templateQueryService;

    /**
     * 保存周模板头 + 全量替换明细。
     *
     * @param header 周模板头
     * @param items 周内日排班映射明细
     * @param operatorId 操作人
     * @return 落库后的周模板头
     * @throws BusinessException 编码重复、默认模板无科室或明细非法时
     */
    public WeekScheduleTemplate saveWeekScheduleTemplate(
        WeekScheduleTemplate header, List<WeekScheduleTemplateItem> items, Long operatorId) {
        this.applyWeekTemplateCode(header);
        this.assertTemplateName(header.getTemplateName());
        this.assertUniqueCode(header);
        // 默认周模板须绑定科室，否则无法解析适用范围
        if (Boolean.TRUE.equals(header.getIsDefault()) && header.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "默认周模板须指定科室");
        }
        LocalDateTime now = LocalDateTime.now();
        if (header.getId() == null) {
            header.setCreatedAt(now);
            header.setCreatedBy(operatorId);
            header.setIsDeleted(Boolean.FALSE);
        }
        if (header.getEnabled() == null) {
            header.setEnabled(Boolean.TRUE);
        }
        if (header.getFallbackBookable() == null) {
            header.setFallbackBookable(Boolean.FALSE);
        }
        if (header.getIsDefault() == null) {
            header.setIsDefault(Boolean.FALSE);
        }
        header.setUpdatedAt(now);
        header.setUpdatedBy(operatorId);

        // 设为默认时清除同科室其他默认标记
        if (Boolean.TRUE.equals(header.getIsDefault())) {
            this.clearOtherDefaults(header.getOrganizationId(), header.getId());
        }

        WeekScheduleTemplate saved = weekScheduleTemplateRepository.save(header);
        this.replaceItems(saved.getId(), items, operatorId, now);
        return saved;
    }

    /**
     * 软删周模板头及其全部有效明细。
     *
     * @param id 周模板主键
     * @param operatorId 操作人
     */
    public void deleteWeekScheduleTemplate(Long id, Long operatorId) {
        weekScheduleTemplateRepository.findById(id).ifPresent(header -> {
            LocalDateTime now = LocalDateTime.now();
            header.setIsDeleted(Boolean.TRUE);
            header.setDeletedAt(now);
            header.setDeletedBy(operatorId);
            weekScheduleTemplateRepository.save(header);
            for (WeekScheduleTemplateItem item : weekScheduleTemplateItemRepository
                .findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(id)) {
                item.setIsDeleted(Boolean.TRUE);
                item.setDeletedAt(now);
                item.setDeletedBy(operatorId);
                weekScheduleTemplateItemRepository.save(item);
            }
        });
    }

    /**
     * 全量替换周模板明细：先软删旧行，再插入新行并校验 weekday 与 T2 引用。
     *
     * @param weekTemplateId 周模板头主键
     * @param items          新明细列表（可为空表示清空）
     * @param operatorId     操作人
     * @param now            统一审计时间戳
     * @throws BusinessException weekday 非法或 T2 不存在时
     */
    private void replaceItems(Long weekTemplateId, List<WeekScheduleTemplateItem> items, Long operatorId, LocalDateTime now) {
        for (WeekScheduleTemplateItem existing : weekScheduleTemplateItemRepository
            .findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(weekTemplateId)) {
            existing.setIsDeleted(Boolean.TRUE);
            existing.setDeletedAt(now);
            existing.setDeletedBy(operatorId);
            weekScheduleTemplateItemRepository.save(existing);
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        int index = 0;
        for (WeekScheduleTemplateItem item : items) {
            if (item.getWeekday() == null || item.getWeekday() < 1 || item.getWeekday() > 7) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "weekday 须为 1～7");
            }
            if (item.getDayScheduleTemplateId() == null) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "dayScheduleTemplateId 不能为空");
            }
            templateQueryService.loadScheduleTemplateById(item.getDayScheduleTemplateId());
            item.setId(null);
            item.setWeekScheduleTemplateId(weekTemplateId);
            if (item.getSortNo() == null) {
                item.setSortNo(index);
            }
            if (item.getEnabled() == null) {
                item.setEnabled(Boolean.TRUE);
            }
            item.setCreatedAt(now);
            item.setCreatedBy(operatorId);
            item.setIsDeleted(Boolean.FALSE);
            item.setUpdatedAt(now);
            item.setUpdatedBy(operatorId);
            weekScheduleTemplateItemRepository.save(item);
            index++;
        }
    }

    /**
     * 同科室仅保留一条默认周模板：清除除 {@code keepId} 外其他记录的默认标记。
     *
     * @param organizationId 科室主键
     * @param keepId         本次设为默认的周模板主键（新建时可为 {@code null}）
     */
    private void clearOtherDefaults(Long organizationId, Long keepId) {
        for (WeekScheduleTemplate other : weekScheduleTemplateRepository
            .findByOrganizationIdAndIsDefaultTrueAndIsDeletedFalse(organizationId)) {
            if (keepId != null && keepId.equals(other.getId())) {
                continue;
            }
            other.setIsDefault(Boolean.FALSE);
            weekScheduleTemplateRepository.save(other);
        }
    }

    /**
     * 解析 TW 待持久化编码：显式传入 / 更新沿用 / 新建自动生成。
     *
     * @param header 待保存的周模板头（含 id 与 templateCode）
     */
    private void applyWeekTemplateCode(WeekScheduleTemplate header) {
        String existingCode = null;
        if (header.getId() != null) {
            // 更新场景：读取库中编码以便未传时沿用
            existingCode = weekScheduleTemplateRepository.findById(header.getId())
                .map(WeekScheduleTemplate::getTemplateCode)
                .orElse(null);
        }
        header.setTemplateCode(BookingTemplateCodeSupport.resolvePersistedCode(
            header.getTemplateCode(),
            existingCode,
            BookingTemplateCodeConstants.PREFIX_WEEK_SCHEDULE));
    }

    /**
     * 周模板名称非空校验。
     *
     * @param name 模板名称
     * @throws BusinessException 名称为空时
     */
    private void assertTemplateName(String name) {
        if (isEmpty(name)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "templateName 不能为空");
        }
    }

    /**
     * 同科室 TW 编码唯一性校验。
     *
     * @param header 待保存的周模板头
     * @throws BusinessException 与同科室其他周模板编码冲突时
     */
    private void assertUniqueCode(WeekScheduleTemplate header) {
        Optional<WeekScheduleTemplate> existing = weekScheduleTemplateRepository
            .findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
                header.getOrganizationId(), header.getTemplateCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(header.getId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板编码已存在");
        }
    }
}
