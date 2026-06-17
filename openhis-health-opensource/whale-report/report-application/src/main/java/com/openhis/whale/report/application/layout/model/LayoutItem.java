package com.openhis.whale.report.application.layout.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class LayoutItem {

    private String elementCode;
    private Boolean visible = true;
    private String labelOverride;
    private List<String> hiddenColumns;
    /** 表格列 field → left/center/right，覆盖元素定义 column_defs 与默认对齐 */
    private Map<String, String> columnAligns;
    private List<String> hiddenItems;
    /** kvRow 每行键值列数，默认 1（纵向）；热敏窄纸可设 1，宽纸可设 2+ */
    private Integer columns;
    private LayoutStyle style;
    private LayoutBox box;
    private LayoutBoxDelta boxDelta;
}
