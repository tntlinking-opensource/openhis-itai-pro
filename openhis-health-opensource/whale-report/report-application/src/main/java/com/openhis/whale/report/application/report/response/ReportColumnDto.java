package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** C 类报表列定义（字段绑定、展示与列定制）。 */
@Data
public class ReportColumnDto {

    /** 行 Map / SQL 列字段名，与 Loader 或 ResultSet 标签一致。 */
    private String field;
    /** 表头展示名。 */
    private String label;
    /** 对齐：left / center / right。 */
    private String align;
    /** 列宽（像素，可选）。 */
    private Integer width;
    /** 列定制：是否可见（仅 override API 使用）。 */
    private Boolean visible;
}
