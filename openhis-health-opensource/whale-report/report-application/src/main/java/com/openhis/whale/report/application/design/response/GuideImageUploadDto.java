package com.openhis.whale.report.application.design.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 套打定位纸上传结果：{@link #url} 为站点相对路径，写入 layoutJson.page.overlay.guideImageUrl。
 */
@Getter
@Setter
public class GuideImageUploadDto {

    /** 如 /uploads/report-guides/2026/06/13/abc.png */
    private String url;
}
