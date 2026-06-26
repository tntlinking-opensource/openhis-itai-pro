package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.domain.service.ReportColumnOverrideManager;
import com.openhis.whale.report.domain.support.ReportColumnOverrideItem;
import org.springframework.stereotype.Component;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 列 DTO 与 {@link ReportColumnOverrideManager} 视图之间的转换。
 *
 * <ul>
 *   <li>{@link #toColumnDtos} — 列设置弹窗，保留 visible 与隐藏列</li>
 *   <li>{@link #toVisibleColumnDtos} — queryPage/export 出参，仅可见列</li>
 * </ul>
 */
@Component
public class ReportColumnMergeSupport {

    /**
     * 列 DTO → 领域 ColumnView（不含 visible）。
     *
     * @param columns 查询结果或默认列
     * @return 领域层列视图列表
     */
    public List<ReportColumnOverrideManager.ColumnView> toColumnViews(List<ReportColumnDto> columns) {
        List<ReportColumnOverrideManager.ColumnView> views = new ArrayList<>();
        if (columns == null) {
            return views;
        }
        for (ReportColumnDto column : columns) {
            if (column == null || !StringUtils.isNotEmpty(column.getField())) {
                continue;
            }
            views.add(new ReportColumnOverrideManager.ColumnView(
                    column.getField().trim(),
                    column.getLabel(),
                    column.getAlign(),
                    column.getWidth()));
        }
        return views;
    }

    /**
     * 列设置视图 → DTO（含 visible 与隐藏列）。
     *
     * @param views 列设置弹窗视图
     * @return 含 visible 的列 DTO 列表
     */
    public List<ReportColumnDto> toColumnDtos(List<ReportColumnOverrideManager.ColumnSettingView> views) {
        List<ReportColumnDto> columns = new ArrayList<>();
        if (views == null) {
            return columns;
        }
        for (ReportColumnOverrideManager.ColumnSettingView view : views) {
            if (view == null || !StringUtils.isNotEmpty(view.field())) {
                continue;
            }
            ReportColumnDto dto = new ReportColumnDto();
            dto.setField(view.field());
            dto.setLabel(view.label());
            dto.setAlign(view.align());
            dto.setWidth(view.width());
            dto.setVisible(view.visible());
            columns.add(dto);
        }
        return columns;
    }

    /**
     * 查询出参列 DTO（仅可见列，不含 visible 字段）。
     *
     * @param views merge 后的可见列视图
     * @return queryPage/export 出参列
     */
    public List<ReportColumnDto> toVisibleColumnDtos(List<ReportColumnOverrideManager.ColumnView> views) {
        List<ReportColumnDto> columns = new ArrayList<>();
        if (views == null) {
            return columns;
        }
        for (ReportColumnOverrideManager.ColumnView view : views) {
            if (view == null || !StringUtils.isNotEmpty(view.field())) {
                continue;
            }
            ReportColumnDto dto = new ReportColumnDto();
            dto.setField(view.field());
            dto.setLabel(view.label());
            dto.setAlign(view.align());
            dto.setWidth(view.width());
            columns.add(dto);
        }
        return columns;
    }

    /**
     * 保存列定制：DTO → 持久化 JSON 项（补默认 visible 与 sortOrder）。
     *
     * @param columns 前端提交的列配置
     * @return 可序列化到 columns_json 的项列表
     */
    public List<ReportColumnOverrideItem> toOverrideItems(List<ReportColumnDto> columns) {
        List<ReportColumnOverrideItem> items = new ArrayList<>();
        if (columns == null) {
            return items;
        }
        int order = 0;
        for (ReportColumnDto column : columns) {
            if (column == null || !StringUtils.isNotEmpty(column.getField())) {
                continue;
            }
            ReportColumnOverrideItem item = new ReportColumnOverrideItem();
            item.setField(column.getField().trim());
            if (column.getVisible() != null) {
                item.setVisible(column.getVisible());
            } else {
                // 未传 visible 时默认展示
                item.setVisible(Boolean.TRUE);
            }
            item.setSortOrder(order);
            item.setWidth(column.getWidth());
            order = order + 1;
            items.add(item);
        }
        return items;
    }
}
