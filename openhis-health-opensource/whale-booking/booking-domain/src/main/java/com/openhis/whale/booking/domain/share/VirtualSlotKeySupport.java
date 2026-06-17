package com.openhis.whale.booking.domain.share;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 周模板 fallback 虚拟号源键编解码（2.25 lazy 物化）。
 *
 * <p>格式：{@code WFB|{orgId}|{yyyy-MM-dd}|{dayTemplateId}|{HH:mm}|{HH:mm}}</p>
 */
public final class VirtualSlotKeySupport {

    private static final String PREFIX = "WFB";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private VirtualSlotKeySupport() {
    }

    /**
     * 编码虚拟号源键。
     */
    public static String encode(
        Long organizationId, LocalDate date, Long dayScheduleTemplateId,
        LocalDateTime startTime, LocalDateTime endTime) {
        if (organizationId == null || date == null || dayScheduleTemplateId == null
            || startTime == null || endTime == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "virtualSlotKey 参数不完整");
        }
        return PREFIX + "|" + organizationId + "|" + date + "|" + dayScheduleTemplateId + "|"
            + startTime.format(TIME_FMT) + "|" + endTime.format(TIME_FMT);
    }

    /**
     * 解码虚拟号源键。
     */
    public static VirtualSlotKeyParts decode(String virtualSlotKey) {
        if (virtualSlotKey == null || virtualSlotKey.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "virtualSlotKey 不能为空");
        }
        String[] parts = virtualSlotKey.split("\\|");
        if (parts.length != 6 || !PREFIX.equals(parts[0])) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "virtualSlotKey 格式非法");
        }
        try {
            VirtualSlotKeyParts result = new VirtualSlotKeyParts();
            result.setOrganizationId(Long.parseLong(parts[1]));
            result.setDate(LocalDate.parse(parts[2]));
            result.setDayScheduleTemplateId(Long.parseLong(parts[3]));
            LocalTime start = LocalTime.parse(parts[4], TIME_FMT);
            LocalTime end = LocalTime.parse(parts[5], TIME_FMT);
            result.setStartTime(result.getDate().atTime(start));
            result.setEndTime(result.getDate().atTime(end));
            return result;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "virtualSlotKey 解析失败");
        }
    }

    /**
     * 虚拟号源键组成部分。
     */
    @lombok.Getter
    @lombok.Setter
    public static class VirtualSlotKeyParts {
        private Long organizationId;
        private LocalDate date;
        private Long dayScheduleTemplateId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
}
