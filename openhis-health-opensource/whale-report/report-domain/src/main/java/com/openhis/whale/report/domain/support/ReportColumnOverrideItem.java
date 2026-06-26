package com.openhis.whale.report.domain.support;

/**
 * 列 override JSON 单项（存于 {@code rpt_column_override.columns_json}）。
 */
public class ReportColumnOverrideItem {

    /** 列字段名，须与默认列 field 一致。 */
    private String field;
    /** 是否可见；false 时 queryPage 过滤该列。 */
    private Boolean visible;
    /** 排序序号，越小越靠前。 */
    private Integer sortOrder;
    /** 列宽（像素，可选）。 */
    private Integer width;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }
}
