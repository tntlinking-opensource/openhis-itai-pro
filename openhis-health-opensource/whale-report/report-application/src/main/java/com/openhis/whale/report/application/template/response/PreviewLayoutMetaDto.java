package com.openhis.whale.report.application.template.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 预览 UI 用排版元数据（不含 layoutJson 全文；guideImage 仅设计态叠图，不进 PDF）。
 */
@Getter
@Setter
public class PreviewLayoutMetaDto {

    /** 排版模式 flow / overlay */
    private String layoutMode;
    /** 纸型：A4、A5 或 roll 时的「80mm」 */
    private String pageSize;
    /** 方向 portrait / landscape */
    private String orientation;
    /** 设计器叠图引导图 URL */
    private String guideImageUrl;
}
