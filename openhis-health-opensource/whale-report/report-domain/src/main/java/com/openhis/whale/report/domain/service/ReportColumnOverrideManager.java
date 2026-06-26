package com.openhis.whale.report.domain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.domain.entity.ReportColumnOverride;
import com.openhis.whale.report.domain.repository.ReportColumnOverrideRepository;
import com.openhis.whale.report.domain.support.ReportColumnOverrideItem;
import org.springframework.stereotype.Service;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C 类报表列定制：用户 override 优先于机构 override，再与默认列 merge。
 *
 * <h2>Merge 规则</h2>
 * <ol>
 *   <li>存在 {@code user_id} 行 → 用用户 columns_json</li>
 *   <li>否则存在 {@code organization_id} 且 user_id 为空 → 用机构 columns_json</li>
 *   <li>否则 → 默认列（definition / Loader 返回）</li>
 * </ol>
 * <p>只改 visible、sortOrder、width；field/label 仍以默认列为准，防止 SQL 绑定错位。</p>
 *
 * @see #resolveColumnsForSettings 列设置 UI（含隐藏列）
 * @see #applyOverrides queryPage/export（过滤隐藏列并排序）
 */
@Service
public class ReportColumnOverrideManager {

    private final ReportColumnOverrideRepository repository;
    private final ObjectMapper objectMapper;

    public ReportColumnOverrideManager(ReportColumnOverrideRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 查用户级列 override。
     *
     * @param tenantId   租户主键
     * @param reportCode 报表编码
     * @param userId     用户主键
     * @return 用户 override 行，参数无效时 empty
     */
    public Optional<ReportColumnOverride> findUserOverride(Long tenantId, String reportCode, Long userId) {
        if (tenantId == null || userId == null || !StringUtils.isNotEmpty(reportCode)) {
            // 缺租户/用户/编码时不查库
            return Optional.empty();
        }
        return repository.findFirstByTenantIdAndReportCodeAndUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                tenantId, reportCode.trim(), userId);
    }

    /**
     * 查机构级列 override（user_id 为空）。
     *
     * @param tenantId       租户主键
     * @param reportCode     报表编码
     * @param organizationId 机构主键
     * @return 机构 override 行，参数无效时 empty
     */
    public Optional<ReportColumnOverride> findOrganizationOverride(
            Long tenantId, String reportCode, Long organizationId) {
        if (tenantId == null || organizationId == null || !StringUtils.isNotEmpty(reportCode)) {
            return Optional.empty();
        }
        return repository.findFirstByTenantIdAndReportCodeAndOrganizationIdAndUserIdIsNullAndIsDeletedFalseOrderByUpdatedAtDesc(
                tenantId, reportCode.trim(), organizationId);
    }

    /**
     * 保存或更新用户级列 override。
     *
     * @param tenantId   租户主键
     * @param reportCode 报表编码
     * @param userId     用户主键
     * @param items      列配置项（field/visible/sortOrder/width）
     * @return 持久化后的 override 行
     */
    public ReportColumnOverride saveUserOverride(
            Long tenantId, String reportCode, Long userId, List<ReportColumnOverrideItem> items) {
        if (tenantId == null || userId == null || !StringUtils.isNotEmpty(reportCode)) {
            throw new ValidationException("租户、用户与报表编码不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new ValidationException("列配置不能为空");
        }
        String code = reportCode.trim();
        Optional<ReportColumnOverride> existing = findUserOverride(tenantId, code, userId);
        ReportColumnOverride row;
        if (existing.isPresent()) {
            // 更新已有用户 override
            row = existing.get();
        } else {
            // 首次保存：插入新行
            row = new ReportColumnOverride();
            row.setTenantId(tenantId);
            row.setReportCode(code);
            row.setUserId(userId);
            row.setIsDeleted(false);
        }
        row.setColumnsJson(serializeItems(items));
        return repository.save(row);
    }

    /**
     * 软删用户级 override，恢复默认列。
     *
     * @param tenantId   租户主键
     * @param reportCode 报表编码
     * @param userId     用户主键
     */
    public void resetUserOverride(Long tenantId, String reportCode, Long userId) {
        findUserOverride(tenantId, reportCode, userId).ifPresent(row -> {
            row.setIsDeleted(true);
            repository.save(row);
        });
    }

    /**
     * 列设置 UI：返回全部默认列并附带 visible / sortOrder（含隐藏列）。
     *
     * @param defaultColumns  默认列（来自 definition 或 Loader）
     * @param tenantId        租户主键
     * @param reportCode      报表编码
     * @param userId          当前用户，可为 null
     * @param organizationId  当前机构，可为 null
     * @return 含 visible 的列设置视图，按 sortOrder 排序
     */
    public List<ColumnSettingView> resolveColumnsForSettings(
            List<ColumnView> defaultColumns,
            Long tenantId,
            String reportCode,
            Long userId,
            Long organizationId) {
        if (defaultColumns == null || defaultColumns.isEmpty()) {
            return List.of();
        }
        List<ReportColumnOverrideItem> overrideItems = resolveOverrideItems(tenantId, reportCode, userId, organizationId);
        Map<String, ReportColumnOverrideItem> index = new LinkedHashMap<>();
        for (ReportColumnOverrideItem item : overrideItems) {
            if (item != null && StringUtils.isNotEmpty(item.getField())) {
                index.put(item.getField().trim(), item);
            }
        }
        List<ColumnSettingEntry> entries = new ArrayList<>();
        for (int i = 0; i < defaultColumns.size(); i += 1) {
            ColumnView column = defaultColumns.get(i);
            if (column == null || !StringUtils.isNotEmpty(column.field())) {
                continue;
            }
            ReportColumnOverrideItem item = index.get(column.field().trim());
            boolean visible = item == null || !Boolean.FALSE.equals(item.getVisible());
            int sortOrder = 10_000 + i;
            if (item != null && item.getSortOrder() != null) {
                sortOrder = item.getSortOrder();
            }
            Integer width = column.width();
            if (item != null && item.getWidth() != null) {
                width = item.getWidth();
            }
            entries.add(new ColumnSettingEntry(
                    new ColumnView(column.field(), column.label(), column.align(), width), visible, sortOrder));
        }
        entries.sort(Comparator.comparingInt(ColumnSettingEntry::sortOrder));
        List<ColumnSettingView> views = new ArrayList<>();
        for (ColumnSettingEntry entry : entries) {
            views.add(new ColumnSettingView(
                    entry.column().field(),
                    entry.column().label(),
                    entry.column().align(),
                    entry.column().width(),
                    entry.visible()));
        }
        return views;
    }

    /**
     * 将 override 应用到默认列：过滤不可见列并按 sortOrder 排序。
     *
     * @param defaultColumns  默认列
     * @param tenantId        租户主键
     * @param reportCode      报表编码
     * @param userId          当前用户，可为 null
     * @param organizationId  当前机构，可为 null
     * @return merge 后可见列（queryPage/export 使用）
     */
    public List<ColumnView> applyOverrides(
            List<ColumnView> defaultColumns,
            Long tenantId,
            String reportCode,
            Long userId,
            Long organizationId) {
        if (defaultColumns == null || defaultColumns.isEmpty()) {
            return List.of();
        }
        List<ReportColumnOverrideItem> overrideItems = resolveOverrideItems(tenantId, reportCode, userId, organizationId);
        if (overrideItems.isEmpty()) {
            // 无 override 时原样返回默认列
            return new ArrayList<>(defaultColumns);
        }
        Map<String, ReportColumnOverrideItem> index = new LinkedHashMap<>();
        for (ReportColumnOverrideItem item : overrideItems) {
            if (item != null && StringUtils.isNotEmpty(item.getField())) {
                index.put(item.getField().trim(), item);
            }
        }
        List<ColumnView> merged = new ArrayList<>();
        for (ColumnView column : defaultColumns) {
            if (column == null || !StringUtils.isNotEmpty(column.field())) {
                continue;
            }
            ReportColumnOverrideItem item = index.get(column.field().trim());
            if (item != null && Boolean.FALSE.equals(item.getVisible())) {
                // 用户隐藏列：queryPage 不展示
                continue;
            }
            ColumnView adjusted = column;
            if (item != null && item.getWidth() != null) {
                adjusted = new ColumnView(column.field(), column.label(), column.align(), item.getWidth());
            }
            merged.add(adjusted);
        }
        merged.sort(Comparator.comparingInt(col -> sortOrderFor(col, index)));
        return merged;
    }

    /**
     * 解析生效的 override 项（用户优先于机构）。
     *
     * @param tenantId       租户主键
     * @param reportCode     报表编码
     * @param userId         用户主键，可为 null
     * @param organizationId 机构主键，可为 null
     * @return 生效的列配置项；无 override 时空列表
     */
    public List<ReportColumnOverrideItem> resolveOverrideItems(
            Long tenantId, String reportCode, Long userId, Long organizationId) {
        Optional<ReportColumnOverride> userRow = findUserOverride(tenantId, reportCode, userId);
        if (userRow.isPresent()) {
            // 用户级 override 优先
            return parseItems(userRow.get().getColumnsJson());
        }
        Optional<ReportColumnOverride> orgRow = findOrganizationOverride(tenantId, reportCode, organizationId);
        if (orgRow.isPresent()) {
            // 无用户配置时回退机构级
            return parseItems(orgRow.get().getColumnsJson());
        }
        return List.of();
    }

    private static int sortOrderFor(ColumnView column, Map<String, ReportColumnOverrideItem> index) {
        ReportColumnOverrideItem item = index.get(column.field().trim());
        if (item != null && item.getSortOrder() != null) {
            return item.getSortOrder();
        }
        return Integer.MAX_VALUE;
    }

    private List<ReportColumnOverrideItem> parseItems(String json) {
        if (!StringUtils.isNotEmpty(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json.trim(), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new ValidationException("列配置 JSON 解析失败");
        }
    }

    private String serializeItems(List<ReportColumnOverrideItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception ex) {
            throw new ValidationException("列配置 JSON 序列化失败");
        }
    }

    /** 列 merge 用的轻量视图（避免 domain 依赖 application DTO）。 */
    public record ColumnView(
            /** 列字段名，与 SQL/Port 绑定一致。 */
            String field,
            /** 列展示标题。 */
            String label,
            /** 对齐方式。 */
            String align,
            /** 列宽像素，可为 null。 */
            Integer width) {
    }

    /** 列设置弹窗：含 visible，不过滤隐藏列。 */
    public record ColumnSettingView(
            /** 列字段名。 */
            String field,
            /** 列展示标题。 */
            String label,
            /** 对齐方式。 */
            String align,
            /** 列宽像素。 */
            Integer width,
            /** 是否在 queryPage 中展示。 */
            boolean visible) {
    }

    private record ColumnSettingEntry(ColumnView column, boolean visible, int sortOrder) {
    }
}
