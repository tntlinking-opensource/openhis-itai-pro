package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.dto.BedLocationSummaryDto;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;

import java.util.List;
import java.util.Optional;

/**
 * 位置主数据读 Port（诊室等）。
 */
public interface BookingLocationPort {

    /**
     * 按科室列出启用中的物理位置。
     *
     * @param organizationId 科室主键
     * @return 位置下拉选项
     */
    List<MasterDataOption> listActiveLocationsByOrganization(Long organizationId);

    /**
     * 按机构列出启用中的病区。
     *
     * @param organizationId 机构主键
     * @return 病区下拉选项
     */
    List<MasterDataOption> listWardsByOrganization(Long organizationId);

    /**
     * 列出病区下启用态床位（含运营状态）。
     *
     * @param wardLocationId 病区 location 主键
     * @return 床位摘要列表
     */
    List<BedLocationSummaryDto> listBedsUnderWard(Long wardLocationId);

    /**
     * 由床位 location 向上解析所属病区。
     *
     * @param bedLocationId 床位 location 主键
     * @return 病区 location 主键；无法解析时 empty
     */
    Optional<Long> resolveWardLocationIdByBedLocationId(Long bedLocationId);
}
