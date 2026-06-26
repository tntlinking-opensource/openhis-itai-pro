package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布前冲突预览响应（BR-EXT-005/006）。
 */
@Getter
@Setter
public class PreviewPublishScheduleConflictsResponse {

    /** 是否存在阻断或警告级冲突。 */
    private Boolean hasConflict;

    /** 冲突明细列表。 */
    private List<SchedulePublishConflictDto> conflicts = new ArrayList<>();
}
