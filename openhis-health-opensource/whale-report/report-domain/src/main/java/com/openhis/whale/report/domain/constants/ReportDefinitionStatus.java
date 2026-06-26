package com.openhis.whale.report.domain.constants;

/**
 * C 类报表定义发布状态（{@code rpt_definition.status}，String code）。
 */
public final class ReportDefinitionStatus {

    /** 草稿：设计器可编辑，queryPage/export 默认拒绝（designPreview 除外）。 */
    public static final String DRAFT = "draft";

    /** 已发布：允许 queryPage/export。 */
    public static final String PUBLISHED = "published";

    private ReportDefinitionStatus() {
    }
}
