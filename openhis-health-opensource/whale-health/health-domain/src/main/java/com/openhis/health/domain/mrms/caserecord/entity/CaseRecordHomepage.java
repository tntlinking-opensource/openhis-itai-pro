/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.entity;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.health.domain.share.enums.FieldFillSource;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.openhis.health.domain.share.utils.HomepageRegionCodesTextUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页实体（{@code mrms_case_record_homepage}），一次住院对应一条有效首页。
 *
 * <p>标量字段对齐 4401；{@code field_sources_json} 记录 manual/auto 来源；归档后 {@code frozen=true}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_case_record_homepage")
public class CaseRecordHomepage extends BaseAuditedEntity {

    /** 所属病案主档 {@code mrms_case_record.id}，一次住院一条首页。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 首页版本号，每次重大保存递增。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 归档后冻结标记，true 时禁止编辑。 */
    @Column(name = "frozen", nullable = false)
    private Boolean frozen;

    /** 4401 首页流水号，首次保存时生成。 */
    @Column(name = "ipt_medcas_hmpg_sn", length = 30)
    private String iptMedcasHmpgSn;

    /** 字段来源 JSON，键=列名，值=manual/auto/mixed。 */
    @Column(name = "field_sources_json", columnDefinition = "TEXT")
    private String fieldSourcesJson;

    /** 患者姓名（4401 psn_name）。 */
    @Column(name = "psn_name", length = 100)
    private String psnName;

    /** 性别字典码（4401 gend，GB/T 2261.1）。 */
    @Column(name = "gend_code", length = 16)
    private String gendCode;

    /** 出生日期（4401 brdy）。 */
    @Column(name = "brdy")
    private LocalDate brdy;

    /** 国籍字典码（4401 ntly）。 */
    @Column(name = "ntly_code", length = 16)
    private String ntlyCode;

    /** 民族字典码（4401 naty，GB/T 3304）。 */
    @Column(name = "naty_code", length = 16)
    private String natyCode;

    /** 证件号码（4401 certno）。 */
    @Column(name = "certno", length = 32)
    private String certno;

    /** 职业字典码（4401 prfs，GB/T 6565）。 */
    @Column(name = "prfs_code", length = 16)
    private String prfsCode;

    /** 婚姻状况字典码（4401 mrg_stas，GB/T 2261.2）。 */
    @Column(name = "mrg_stas_code", length = 16)
    private String mrgStasCode;

    /** 现住址（4401 curr_addr）。 */
    @Column(name = "curr_addr", length = 500)
    private String currAddr;

    /** 联系电话（4401 psn_tel）。 */
    @Column(name = "psn_tel", length = 32)
    private String psnTel;

    /** 联系人姓名（4401 coner_name）。 */
    @Column(name = "coner_name", length = 100)
    private String conerName;

    /** 联系人电话（4401 coner_tel）。 */
    @Column(name = "coner_tel", length = 32)
    private String conerTel;

    /** 联系人关系字典码（4401 coner_rlts）。 */
    @Column(name = "coner_rlts_code", length = 16)
    private String conerRltsCode;

    /** 联系人地址（4401 coner_addr）。 */
    @Column(name = "coner_addr", length = 500)
    private String conerAddr;

    /** 治疗类别字典码（4401 trt_type）。 */
    @Column(name = "trt_type_code", length = 16)
    private String trtTypeCode;

    /**
     * 实施临床路径（中医病案首页国标；1中医 2西医 3否；老系统 inpath）。
     *
     * <p>列对全租户存在；仅中医院院点编辑/打印开启。</p>
     */
    @Column(name = "clinical_path_code", length = 8)
    private String clinicalPathCode;

    /**
     * 使用医疗机构中药制剂（1是 2否；老系统 usetczj）。
     */
    @Column(name = "tcm_prep_code", length = 8)
    private String tcmPrepCode;

    /**
     * 使用中医诊疗设备（1是 2否；老系统 usetceq）。
     */
    @Column(name = "tcm_device_code", length = 8)
    private String tcmDeviceCode;

    /**
     * 使用中医诊疗技术（1是 2否；老系统 usetczl）。
     */
    @Column(name = "tcm_tech_code", length = 8)
    private String tcmTechCode;

    /**
     * 辨证施护（1是 2否；老系统 isbzsf）。
     */
    @Column(name = "syndrome_nurse_code", length = 8)
    private String syndromeNurseCode;

    /** 特级护理天数（4401 spga_nurscare_days）。 */
    @Column(name = "spga_nurscare_days")
    private Integer spgaNurscareDays;

    /** 一级护理天数（4401 lv1_nurscare_days）。 */
    @Column(name = "lv1_nurscare_days")
    private Integer lv1NurscareDays;

    /** 二级护理天数（4401 scd_nurscare_days）。 */
    @Column(name = "scd_nurscare_days")
    private Integer scdNurscareDays;

    /** 三级护理天数（4401 lv3_nurscare_days）。 */
    @Column(name = "lv3_nurscare_days")
    private Integer lv3NurscareDays;

    /** 门急诊西医诊断名称（4401 otp_wm_dise）。 */
    @Column(name = "otp_wm_dise", length = 200)
    private String otpWmDise;

    /** 门急诊西医诊断编码（4401 wm_dise_code）。 */
    @Column(name = "wm_dise_code", length = 32)
    private String wmDiseCode;

    /** 门急诊中医诊断名称（4401 otp_tcm_dise）。 */
    @Column(name = "otp_tcm_dise", length = 200)
    private String otpTcmDise;

    /** 门急诊中医诊断编码（4401 tcm_dise_code）。 */
    @Column(name = "tcm_dise_code", length = 32)
    private String tcmDiseCode;

    /** 入院前颅脑损伤昏迷时长（天/时/分文本，4401 brn_damg_bfadm_coma_dura）。 */
    @Column(name = "brn_damg_bfadm_coma_dura", length = 32)
    private String brnDamgBfadmComaDura;

    /** 入院后颅脑损伤昏迷时长（天/时/分文本，4401 brn_damg_afadm_coma_dura）。 */
    @Column(name = "brn_damg_afadm_coma_dura", length = 32)
    private String brnDamgAfadmComaDura;

    /** 呼吸机使用时长（天/时/分文本，4401 vent_used_dura）。 */
    @Column(name = "vent_used_dura", length = 32)
    private String ventUsedDura;

    /** 住院号（adm_encounter.bus_no 冗余）。 */
    @Column(name = "ipt_no", length = 64)
    private String iptNo;

    /** 入院途径字典码（4401 adm_way，提交校验必填）。 */
    @Column(name = "adm_way_code", length = 16)
    private String admWayCode;

    /** 入院科别编码（4401/4101A：科室医保编码 yb_no，非院内 bus_no）。 */
    @Column(name = "adm_caty_code", length = 32)
    private String admCatyCode;

    /** 入院科别名称（展示名；优先科室医保名称）。 */
    @Column(name = "adm_caty_name", length = 100)
    private String admCatyName;

    /** 入院时间（4401 adm_time）。 */
    @Column(name = "adm_time")
    private LocalDateTime admTime;

    /** 出院科别编码（4401/4101A dscg_caty：科室医保编码 yb_no）。 */
    @Column(name = "dscg_caty_code", length = 32)
    private String dscgCatyCode;

    /** 出院科别名称（展示名；优先科室医保名称）。 */
    @Column(name = "dscg_caty_name", length = 100)
    private String dscgCatyName;

    /** 出院时间（4401 dscg_time）。 */
    @Column(name = "dscg_time")
    private LocalDateTime dscgTime;

    /** 住院天数（4401 ipt_days）。 */
    @Column(name = "ipt_days")
    private Integer iptDays;

    /** 转科路径摘要（科室 A→B→C）。 */
    @Column(name = "transfer_dept_summary", length = 500)
    private String transferDeptSummary;

    /** 离院方式字典码（4401 dscg_way，提交校验必填）。 */
    @Column(name = "dscg_way_code", length = 16)
    private String dscgWayCode;

    /** 科主任姓名（4401 dept_director）。 */
    @Column(name = "dept_director_name", length = 50)
    private String deptDirectorName;

    /** 主任/副主任医师姓名（4401 chfdr）。 */
    @Column(name = "chfdr_name", length = 50)
    private String chfdrName;

    /** 主治医师姓名（4401 atddr）。 */
    @Column(name = "atddr_name", length = 50)
    private String atddrName;

    /** 主治医师 {@code adm_practitioner.id}（自动取值）。 */
    @Column(name = "atddr_practitioner_id")
    private Long atddrPractitionerId;

    /** 住院医师姓名（4401 ipt_dr）。 */
    @Column(name = "ipt_dr_name", length = 50)
    private String iptDrName;

    /** 责任护士姓名（4401 resp_nurs）。 */
    @Column(name = "resp_nurs_name", length = 50)
    private String respNursName;

    /** 医疗付费方式（原 extension.payWayCode）。 */
    @Column(name = "pay_way_code", length = 16)
    private String payWayCode;

    /** 健康卡号（原 extension.healthCardNo）。 */
    @Column(name = "health_card_no", length = 64)
    private String healthCardNo;

    /** 年龄岁（原 extension.ageYear）。 */
    @Column(name = "age_year")
    private Integer ageYear;

    /** 不足 1 周岁月龄（原 extension.ageMonth）。 */
    @Column(name = "age_month")
    private Integer ageMonth;

    /** 出生地省市区 codes 斜杠拼接文本（如 220000/220100/220102）。 */
    @Column(name = "birplc_codes", columnDefinition = "TEXT")
    private String birplcCodes;

    /** 出生地中文地名（连续文本，供卫统 CSD / HQMS A22；与 codes 同步由前端写入）。 */
    @Column(name = "birplc_name", length = 200)
    private String birplcName;

    /** 籍贯省市 codes 斜杠拼接文本（如 220000/220100）。 */
    @Column(name = "napl_codes", columnDefinition = "TEXT")
    private String naplCodes;

    /** 籍贯中文地名（连续文本，供卫统 GG；HQMS A23C 另走 RC036）。 */
    @Column(name = "napl_name", length = 200)
    private String naplName;

    /** 现住址省市区 codes 斜杠拼接文本。 */
    @Column(name = "curr_addr_codes", columnDefinition = "TEXT")
    private String currAddrCodes;

    /** 现住址详细（原 extension.currAddrDetail）。 */
    @Column(name = "curr_addr_detail", length = 500)
    private String currAddrDetail;

    /** 现住址邮编（原 extension.currAddrPoscode）。 */
    @Column(name = "curr_addr_poscode", length = 16)
    private String currAddrPoscode;

    /** 户口地址省市区 codes 斜杠拼接文本。 */
    @Column(name = "resd_addr_codes", columnDefinition = "TEXT")
    private String resdAddrCodes;

    /** 户口详细地址（原 extension.resdAddrDetail）。 */
    @Column(name = "resd_addr_detail", length = 500)
    private String resdAddrDetail;

    /** 户口邮编（原 extension.resdAddrPoscode）。 */
    @Column(name = "resd_addr_poscode", length = 16)
    private String resdAddrPoscode;

    /** 工作单位及地址（原 extension.emprAddr）。 */
    @Column(name = "empr_addr", length = 500)
    private String emprAddr;

    /** 单位电话（原 extension.emprTel）。 */
    @Column(name = "empr_tel", length = 32)
    private String emprTel;

    /** 单位邮编（原 extension.emprPoscode）。 */
    @Column(name = "empr_poscode", length = 16)
    private String emprPoscode;

    /** 入院病区（原 extension.admWard）。 */
    @Column(name = "adm_ward", length = 100)
    private String admWard;

    /** 入院床位（原 extension.admBed）。 */
    @Column(name = "adm_bed", length = 32)
    private String admBed;

    /** 出院病区（原 extension.dscgWard）。 */
    @Column(name = "dscg_ward", length = 100)
    private String dscgWard;

    /** 出院床位（原 extension.dscgBed）。 */
    @Column(name = "dscg_bed", length = 32)
    private String dscgBed;

    /** 药物过敏（原 extension.drugAllergyCode）。 */
    @Column(name = "drug_allergy_code", length = 8)
    private String drugAllergyCode;

    /** 过敏药物（原 extension.allergyDrugName）。 */
    @Column(name = "allergy_drug_name", length = 200)
    private String allergyDrugName;

    /** 病理号（原 extension.pathologyNo）。 */
    @Column(name = "pathology_no", length = 64)
    private String pathologyNo;

    /** 主要诊断治愈好转（原 extension.mainDiagCureCode）。 */
    @Column(name = "main_diag_cure_code", length = 8)
    private String mainDiagCureCode;

    /** 抢救次数（原 extension.rescueCount）。 */
    @Column(name = "rescue_count")
    private Integer rescueCount;

    /** 抢救成功次数（原 extension.rescueSuccessCount）。 */
    @Column(name = "rescue_success_count")
    private Integer rescueSuccessCount;

    /**
     * 临床路径管理（原 extension.clinicPathManageCode；异于中医 {@link #clinicalPathCode}）。
     */
    @Column(name = "clinic_path_manage_code", length = 8)
    private String clinicPathManageCode;

    /** 诊断符合门诊与出院（原 extension.diagMatchOpDscg）。 */
    @Column(name = "diag_match_op_dscg", length = 8)
    private String diagMatchOpDscg;

    /** 诊断符合入院与出院（原 extension.diagMatchAdmDscg）。 */
    @Column(name = "diag_match_adm_dscg", length = 8)
    private String diagMatchAdmDscg;

    /** 诊断符合术前与术后（原 extension.diagMatchPrePost）。 */
    @Column(name = "diag_match_pre_post", length = 8)
    private String diagMatchPrePost;

    /** 诊断符合临床与病理（原 extension.diagMatchClinicPath）。 */
    @Column(name = "diag_match_clinic_path", length = 8)
    private String diagMatchClinicPath;

    /** 诊断符合放射与病理（原 extension.diagMatchRadioPath）。 */
    @Column(name = "diag_match_radio_path", length = 8)
    private String diagMatchRadioPath;

    /** 死亡患者尸检（原 extension.autopsyCode）。 */
    @Column(name = "autopsy_code", length = 8)
    private String autopsyCode;

    /** 血型 ABO（原 extension.aboCode）。 */
    @Column(name = "abo_code", length = 8)
    private String aboCode;

    /** Rh（原 extension.rhCode）。 */
    @Column(name = "rh_code", length = 8)
    private String rhCode;

    /** 进修医师（原 extension.trainDrName）。 */
    @Column(name = "train_dr_name", length = 50)
    private String trainDrName;

    /** 实习医师（原 extension.intnDrName）。 */
    @Column(name = "intn_dr_name", length = 50)
    private String intnDrName;

    /** 编码员（原 extension.codrName）。 */
    @Column(name = "codr_name", length = 50)
    private String codrName;

    /** 病案质量（原 extension.medcasQltCode）。 */
    @Column(name = "medcas_qlt_code", length = 8)
    private String medcasQltCode;

    /** 质控日期（原 extension.qltctrlDate）。 */
    @Column(name = "qltctrl_date")
    private LocalDate qltctrlDate;

    /** 质控医师（原 extension.qltctrlDrName）。 */
    @Column(name = "qltctrl_dr_name", length = 50)
    private String qltctrlDrName;

    /** 质控护士（原 extension.qltctrlNursName）。 */
    @Column(name = "qltctrl_nurs_name", length = 50)
    private String qltctrlNursName;

    /** 拟接收医院（原 extension.acpMedinsName）。 */
    @Column(name = "acp_medins_name", length = 200)
    private String acpMedinsName;

    /** 拟接收社区（原 extension.acpCommunityName）。 */
    @Column(name = "acp_community_name", length = 200)
    private String acpCommunityName;

    /** 31 天再住院计划（原 extension.rinp31Flag）。 */
    @Column(name = "rinp31_flag", length = 8)
    private String rinp31Flag;

    /** 再住院目的（原 extension.rinp31Purpose）。 */
    @Column(name = "rinp31_purpose", length = 500)
    private String rinp31Purpose;

    /**
     * 医疗费总额快照（4401 {@code medfee_sumamt}）。
     *
     * <p>仅「重新统计」或首次空快照补种时写入；打开页/保存其它章不按目录现查覆盖。</p>
     */
    @Column(name = "medfee_sumamt", precision = 16, scale = 2)
    private BigDecimal medfeeSumamt;

    /**
     * 自付金额快照（4401 {@code selfpay_amt}）。
     *
     * <p>写口径同 {@link #medfeeSumamt}。</p>
     */
    @Column(name = "selfpay_amt", precision = 16, scale = 2)
    private BigDecimal selfpayAmt;

    /**
     * 费用分项快照 JSON（4401 分项键→金额；如 {@code wm_fee}）。
     *
     * <p>写口径同 {@link #medfeeSumamt}；与 {@link com.openhis.health.domain.share.constants.MrmsConstants#FEE_LIVE_QUERY_KEYS}
     * 分项键对齐。</p>
     */
    @Column(name = "fee_items_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String feeItemsJson;

    /**
     * 初始化空首页。
     */
    public void initEmpty(Long caseRecordId, Long tenantId, Long operatorId) {
        this.caseRecordId = caseRecordId;
        this.setTenantId(tenantId);
        this.versionNo = 1;
        this.frozen = false;
        this.iptMedcasHmpgSn = "HMPG" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setIsDeleted(false);
    }

    /**
     * 断言首页未冻结可编辑。
     */
    public void assertEditable() {
        // 归档冻结后禁止编辑
        if (Boolean.TRUE.equals(frozen)) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_FROZEN, "首页已冻结不可编辑");
        }
    }

    /**
     * 递增版本并更新审计字段。
     *
     * @param operatorId 操作人
     */
    public void bumpVersion(Long operatorId) {
        this.versionNo = this.versionNo + 1;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 编辑审核通过后设定业务版本（保持 frozen=true）。
     *
     * @param toVersion 目标版本号
     * @param operatorId 操作人
     */
    public void applyApprovedVersion(Integer toVersion, Long operatorId) {
        if (toVersion == null || toVersion < 1) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_VERSION_CONFLICT, "目标版本无效");
        }
        this.versionNo = toVersion;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 归档冻结首页。
     *
     * @param operatorId 操作人
     */
    public void freezeHomepage(Long operatorId) {
        this.frozen = true;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 解档恢复首页可编辑。
     *
     * <p>业务口径已取消解档入口；方法保留供存量调用方编译，新业务禁止调用。</p>
     *
     * @param operatorId 操作人
     * @deprecated 解档取消，归档后改走编辑申请
     */
    @Deprecated
    public void unfreezeHomepage(Long operatorId) {
        this.frozen = false;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 应用医生手工保存的标量字段，并标记字段来源为 manual。
     *
     * @param command 保存命令（仅非 null 字段生效；可清空码字段传空白）
     * @param sources 字段来源 Map
     */
    public void applyManualFields(HomepageSaveCommand command, Map<String, String> sources) {
        if (command == null) {
            return;
        }
        markManualField(command.getPsnName(), "psn_name", sources, v -> this.psnName = v);
        markManualField(command.getGendCode(), "gend_code", sources, v -> this.gendCode = v);
        if (command.getBrdy() != null) {
            this.brdy = command.getBrdy();
            sources.put("brdy", FieldFillSource.MANUAL.getCode());
        }
        markManualField(command.getNtlyCode(), "ntly_code", sources, v -> this.ntlyCode = v);
        markManualField(command.getNatyCode(), "naty_code", sources, v -> this.natyCode = v);
        markManualField(command.getCertno(), "certno", sources, v -> this.certno = v);
        markManualField(command.getPrfsCode(), "prfs_code", sources, v -> this.prfsCode = v);
        markManualField(command.getMrgStasCode(), "mrg_stas_code", sources, v -> this.mrgStasCode = v);
        markManualField(command.getCurrAddr(), "curr_addr", sources, v -> this.currAddr = v);
        markManualField(command.getPsnTel(), "psn_tel", sources, v -> this.psnTel = v);
        markManualField(command.getConerName(), "coner_name", sources, v -> this.conerName = v);
        markManualField(command.getConerTel(), "coner_tel", sources, v -> this.conerTel = v);
        markManualField(command.getConerRltsCode(), "coner_rlts_code", sources, v -> this.conerRltsCode = v);
        markManualField(command.getConerAddr(), "coner_addr", sources, v -> this.conerAddr = v);
        markManualField(command.getTrtTypeCode(), "trt_type_code", sources, v -> this.trtTypeCode = v);
        // 中医信息五字段：空白字串清空列并去掉 manual 锁，便于院点改选
        applyClearableManualCode(command.getClinicalPathCode(), "clinical_path_code", sources, v -> this.clinicalPathCode = v);
        applyClearableManualCode(command.getTcmPrepCode(), "tcm_prep_code", sources, v -> this.tcmPrepCode = v);
        applyClearableManualCode(command.getTcmDeviceCode(), "tcm_device_code", sources, v -> this.tcmDeviceCode = v);
        applyClearableManualCode(command.getTcmTechCode(), "tcm_tech_code", sources, v -> this.tcmTechCode = v);
        applyClearableManualCode(
                command.getSyndromeNurseCode(), "syndrome_nurse_code", sources, v -> this.syndromeNurseCode = v);
        if (command.getSpgaNurscareDays() != null) {
            this.spgaNurscareDays = command.getSpgaNurscareDays();
            sources.put("spga_nurscare_days", FieldFillSource.MANUAL.getCode());
        }
        if (command.getLv1NurscareDays() != null) {
            this.lv1NurscareDays = command.getLv1NurscareDays();
            sources.put("lv1_nurscare_days", FieldFillSource.MANUAL.getCode());
        }
        if (command.getScdNurscareDays() != null) {
            this.scdNurscareDays = command.getScdNurscareDays();
            sources.put("scd_nurscare_days", FieldFillSource.MANUAL.getCode());
        }
        if (command.getLv3NurscareDays() != null) {
            this.lv3NurscareDays = command.getLv3NurscareDays();
            sources.put("lv3_nurscare_days", FieldFillSource.MANUAL.getCode());
        }
        markManualField(command.getOtpWmDise(), "otp_wm_dise", sources, v -> this.otpWmDise = v);
        markManualField(command.getWmDiseCode(), "wm_dise_code", sources, v -> this.wmDiseCode = v);
        markManualField(command.getOtpTcmDise(), "otp_tcm_dise", sources, v -> this.otpTcmDise = v);
        markManualField(command.getTcmDiseCode(), "tcm_dise_code", sources, v -> this.tcmDiseCode = v);
        markManualField(
                command.getBrnDamgBfadmComaDura(),
                "brn_damg_bfadm_coma_dura",
                sources,
                v -> this.brnDamgBfadmComaDura = v);
        markManualField(
                command.getBrnDamgAfadmComaDura(),
                "brn_damg_afadm_coma_dura",
                sources,
                v -> this.brnDamgAfadmComaDura = v);
        markManualField(command.getVentUsedDura(), "vent_used_dura", sources, v -> this.ventUsedDura = v);
        markManualField(command.getIptNo(), "ipt_no", sources, v -> this.iptNo = v);
        // 入院途径：空白不落 manual 空锁，避免打开页无法从就诊 in_way 回填
        if (command.getAdmWayCode() != null) {
            if (command.getAdmWayCode().isBlank()) {
                this.admWayCode = null;
                sources.remove("adm_way_code");
            } else {
                markManualField(command.getAdmWayCode(), "adm_way_code", sources, v -> this.admWayCode = v);
            }
        }
        markManualField(command.getAdmCatyCode(), "adm_caty_code", sources, v -> this.admCatyCode = v);
        markManualField(command.getAdmCatyName(), "adm_caty_name", sources, v -> this.admCatyName = v);
        if (command.getAdmTime() != null) {
            this.admTime = command.getAdmTime();
            sources.put("adm_time", FieldFillSource.MANUAL.getCode());
        }
        markManualField(command.getDscgCatyCode(), "dscg_caty_code", sources, v -> this.dscgCatyCode = v);
        markManualField(command.getDscgCatyName(), "dscg_caty_name", sources, v -> this.dscgCatyName = v);
        if (command.getDscgTime() != null) {
            this.dscgTime = command.getDscgTime();
            sources.put("dscg_time", FieldFillSource.MANUAL.getCode());
        }
        if (command.getIptDays() != null) {
            this.iptDays = command.getIptDays();
            sources.put("ipt_days", FieldFillSource.MANUAL.getCode());
        }
        markManualField(
                command.getTransferDeptSummary(),
                "transfer_dept_summary",
                sources,
                v -> this.transferDeptSummary = v);
        markManualField(command.getDscgWayCode(), "dscg_way_code", sources, v -> this.dscgWayCode = v);
        markManualField(command.getDeptDirectorName(), "dept_director_name", sources, v -> this.deptDirectorName = v);
        markManualField(command.getChfdrName(), "chfdr_name", sources, v -> this.chfdrName = v);
        markManualField(command.getAtddrName(), "atddr_name", sources, v -> this.atddrName = v);
        markManualField(command.getIptDrName(), "ipt_dr_name", sources, v -> this.iptDrName = v);
        markManualField(command.getRespNursName(), "resp_nurs_name", sources, v -> this.respNursName = v);
        // V20 升列：可清空码/文本与 Integer/LocalDate；*Codes 列表落斜杠拼接字符串
        applyClearableManualCode(command.getPayWayCode(), "pay_way_code", sources, v -> this.payWayCode = v);
        applyClearableManualCode(command.getHealthCardNo(), "health_card_no", sources, v -> this.healthCardNo = v);
        if (command.getAgeYear() != null) {
            this.ageYear = command.getAgeYear();
            sources.put("age_year", FieldFillSource.MANUAL.getCode());
        }
        if (command.getAgeMonth() != null) {
            this.ageMonth = command.getAgeMonth();
            sources.put("age_month", FieldFillSource.MANUAL.getCode());
        }
        applyManualCodesList(command.getBirplcCodes(), "birplc_codes", sources, v -> this.birplcCodes = v);
        applyClearableManualCode(command.getBirplcName(), "birplc_name", sources, v -> this.birplcName = v);
        applyManualCodesList(command.getNaplCodes(), "napl_codes", sources, v -> this.naplCodes = v);
        applyClearableManualCode(command.getNaplName(), "napl_name", sources, v -> this.naplName = v);
        applyManualCodesList(
                command.getCurrAddrCodes(), "curr_addr_codes", sources, v -> this.currAddrCodes = v);
        applyClearableManualCode(
                command.getCurrAddrDetail(), "curr_addr_detail", sources, v -> this.currAddrDetail = v);
        applyClearableManualCode(
                command.getCurrAddrPoscode(), "curr_addr_poscode", sources, v -> this.currAddrPoscode = v);
        applyManualCodesList(
                command.getResdAddrCodes(), "resd_addr_codes", sources, v -> this.resdAddrCodes = v);
        applyClearableManualCode(
                command.getResdAddrDetail(), "resd_addr_detail", sources, v -> this.resdAddrDetail = v);
        applyClearableManualCode(
                command.getResdAddrPoscode(), "resd_addr_poscode", sources, v -> this.resdAddrPoscode = v);
        applyClearableManualCode(command.getEmprAddr(), "empr_addr", sources, v -> this.emprAddr = v);
        applyClearableManualCode(command.getEmprTel(), "empr_tel", sources, v -> this.emprTel = v);
        applyClearableManualCode(command.getEmprPoscode(), "empr_poscode", sources, v -> this.emprPoscode = v);
        applyClearableManualCode(command.getAdmWard(), "adm_ward", sources, v -> this.admWard = v);
        applyClearableManualCode(command.getAdmBed(), "adm_bed", sources, v -> this.admBed = v);
        applyClearableManualCode(command.getDscgWard(), "dscg_ward", sources, v -> this.dscgWard = v);
        applyClearableManualCode(command.getDscgBed(), "dscg_bed", sources, v -> this.dscgBed = v);
        applyClearableManualCode(
                command.getDrugAllergyCode(), "drug_allergy_code", sources, v -> this.drugAllergyCode = v);
        applyClearableManualCode(
                command.getAllergyDrugName(), "allergy_drug_name", sources, v -> this.allergyDrugName = v);
        applyClearableManualCode(command.getPathologyNo(), "pathology_no", sources, v -> this.pathologyNo = v);
        applyClearableManualCode(
                command.getMainDiagCureCode(), "main_diag_cure_code", sources, v -> this.mainDiagCureCode = v);
        if (command.getRescueCount() != null) {
            this.rescueCount = command.getRescueCount();
            sources.put("rescue_count", FieldFillSource.MANUAL.getCode());
        }
        if (command.getRescueSuccessCount() != null) {
            this.rescueSuccessCount = command.getRescueSuccessCount();
            sources.put("rescue_success_count", FieldFillSource.MANUAL.getCode());
        }
        applyClearableManualCode(
                command.getClinicPathManageCode(),
                "clinic_path_manage_code",
                sources,
                v -> this.clinicPathManageCode = v);
        applyClearableManualCode(
                command.getDiagMatchOpDscg(), "diag_match_op_dscg", sources, v -> this.diagMatchOpDscg = v);
        applyClearableManualCode(
                command.getDiagMatchAdmDscg(), "diag_match_adm_dscg", sources, v -> this.diagMatchAdmDscg = v);
        applyClearableManualCode(
                command.getDiagMatchPrePost(), "diag_match_pre_post", sources, v -> this.diagMatchPrePost = v);
        applyClearableManualCode(
                command.getDiagMatchClinicPath(),
                "diag_match_clinic_path",
                sources,
                v -> this.diagMatchClinicPath = v);
        applyClearableManualCode(
                command.getDiagMatchRadioPath(), "diag_match_radio_path", sources, v -> this.diagMatchRadioPath = v);
        applyClearableManualCode(command.getAutopsyCode(), "autopsy_code", sources, v -> this.autopsyCode = v);
        applyClearableManualCode(command.getAboCode(), "abo_code", sources, v -> this.aboCode = v);
        applyClearableManualCode(command.getRhCode(), "rh_code", sources, v -> this.rhCode = v);
        applyClearableManualCode(command.getTrainDrName(), "train_dr_name", sources, v -> this.trainDrName = v);
        applyClearableManualCode(command.getIntnDrName(), "intn_dr_name", sources, v -> this.intnDrName = v);
        applyClearableManualCode(command.getCodrName(), "codr_name", sources, v -> this.codrName = v);
        applyClearableManualCode(command.getMedcasQltCode(), "medcas_qlt_code", sources, v -> this.medcasQltCode = v);
        if (command.getQltctrlDate() != null) {
            this.qltctrlDate = command.getQltctrlDate();
            sources.put("qltctrl_date", FieldFillSource.MANUAL.getCode());
        }
        applyClearableManualCode(command.getQltctrlDrName(), "qltctrl_dr_name", sources, v -> this.qltctrlDrName = v);
        applyClearableManualCode(
                command.getQltctrlNursName(), "qltctrl_nurs_name", sources, v -> this.qltctrlNursName = v);
        applyClearableManualCode(command.getAcpMedinsName(), "acp_medins_name", sources, v -> this.acpMedinsName = v);
        applyClearableManualCode(
                command.getAcpCommunityName(), "acp_community_name", sources, v -> this.acpCommunityName = v);
        applyClearableManualCode(command.getRinp31Flag(), "rinp31_flag", sources, v -> this.rinp31Flag = v);
        applyClearableManualCode(command.getRinp31Purpose(), "rinp31_purpose", sources, v -> this.rinp31Purpose = v);
    }

    /**
     * 手工录入字段：非 null 值写入实体并标记 {@link FieldFillSource#MANUAL} 来源。
     *
     * @param value      入参值；null 时跳过
     * @param sourceKey  字段来源 map 键
     * @param sources    字段来源追踪表
     * @param setter     实体字段写入器
     */
    private void markManualField(
            String value,
            String sourceKey,
            Map<String, String> sources,
            java.util.function.Consumer<String> setter) {
        if (value == null) {
            return;
        }
        setter.accept(value);
        sources.put(sourceKey, FieldFillSource.MANUAL.getCode());
    }

    /**
     * 可清空码/文本手工写入：null 跳过；空白则清空列并移除来源锁。
     *
     * @param value     入参码值
     * @param sourceKey field_sources 键（蛇形列名）
     * @param sources   字段来源表
     * @param setter    列写入器
     */
    private void applyClearableManualCode(
            String value,
            String sourceKey,
            Map<String, String> sources,
            java.util.function.Consumer<String> setter) {
        if (value == null) {
            return;
        }
        // 空白表示用户清空，避免空串锁死自动回填位
        if (value.isBlank()) {
            setter.accept(null);
            sources.remove(sourceKey);
            return;
        }
        markManualField(value, sourceKey, sources, setter);
    }

    /**
     * 省市区 codes 列表手工写入：null 跳过；空列表清空列；否则落斜杠拼接字符串并标 manual。
     *
     * @param codes     入参列表
     * @param sourceKey field_sources 键
     * @param sources   字段来源表
     * @param setter    列写入器（纯字符串）
     */
    private void applyManualCodesList(
            List<String> codes,
            String sourceKey,
            Map<String, String> sources,
            java.util.function.Consumer<String> setter) {
        if (codes == null) {
            return;
        }
        // 空数组表示用户清空省市区
        if (codes.isEmpty()) {
            setter.accept(null);
            sources.remove(sourceKey);
            return;
        }
        String text = HomepageRegionCodesTextUtil.stringListToText(codes);
        setter.accept(text);
        sources.put(sourceKey, FieldFillSource.MANUAL.getCode());
    }

    /**
     * 校验首页是否满足提交条件（不含主档状态，由 {@link CaseRecord} 负责）。
     *
     * @param diagnoses 当前有效诊断行
     */
    public void assertReadyToSubmit(List<HomepageDiagnosis> diagnoses) {
        List<String> errors = listSubmitValidationErrors(diagnoses);
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    MrmsErrorCodes.HOMEPAGE_SUBMIT_VALIDATION,
                    String.join("；", errors));
        }
    }

    /**
     * 收集首页提交校验错误（质控规则复用，不抛异常）。
     *
     * @param diagnoses 当前有效诊断行
     * @return 错误描述列表；无问题时为空
     */
    public List<String> listSubmitValidationErrors(List<HomepageDiagnosis> diagnoses) {
        List<String> errors = new ArrayList<>();
        // 冻结首页不可提交
        if (Boolean.TRUE.equals(frozen)) {
            errors.add("首页已冻结不可提交");
        }
        // Wave1 必填标量（4401 提交前置）
        validateRequired(errors, "姓名", psnName);
        validateRequired(errors, "性别", gendCode);
        if (brdy == null) {
            errors.add("出生日期不能为空");
        }
        validateRequired(errors, "证件号", certno);
        validateRequired(errors, "住院号", iptNo);
        validateRequired(errors, "入院途径", admWayCode);
        validateRequired(errors, "入院科别", admCatyCode);
        if (admTime == null) {
            errors.add("入院时间不能为空");
        }
        validateRequired(errors, "出院科别", dscgCatyCode);
        if (dscgTime == null) {
            errors.add("出院时间不能为空");
        }
        if (iptDays == null) {
            errors.add("住院天数不能为空");
        }
        validateRequired(errors, "离院方式", dscgWayCode);
        validateRequired(errors, "主任/副主任医师", chfdrName);
        validateRequired(errors, "主治医师", atddrName);
        validateRequired(errors, "住院医师", iptDrName);
        // 出入院时间逻辑校验
        if (admTime != null && dscgTime != null && dscgTime.isBefore(admTime)) {
            errors.add("出院时间不能早于入院时间");
        }
        validateDischargeMainDiagnosis(errors, diagnoses);
        return errors;
    }

    /**
     * 出院主诊断校验：须存在编码非空的 DISCHARGE_MAIN，且 maindiag_flag=1 至多一条。
     *
     * @param errors    错误收集列表
     * @param diagnoses 当前有效诊断行
     */
    private void validateDischargeMainDiagnosis(List<String> errors, List<HomepageDiagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            errors.add("出院主诊断编码不能为空");
            return;
        }
        boolean hasMainDischarge = false;
        int mainDischargeCount = 0;
        // 出院主诊断须唯一且编码非空
        for (HomepageDiagnosis row : diagnoses) {
            if (!HomepageDiagType.DISCHARGE_MAIN.getCode().equals(row.getDiagTypeCode())) {
                continue;
            }
            if (trimToNull(row.getDiagCode()) != null) {
                hasMainDischarge = true;
            }
            if ("1".equals(row.getMaindiagFlag())) {
                mainDischargeCount = mainDischargeCount + 1;
            }
        }
        if (!hasMainDischarge) {
            errors.add("出院主诊断编码不能为空");
        }
        if (mainDischargeCount > 1) {
            errors.add("出院主诊断只能有一条");
        }
    }

    /**
     * 4401 必填标量非空校验：trim 后为空则追加「{label}不能为空」。
     *
     * @param errors 错误收集列表
     * @param label  字段中文名
     * @param value  待校验值
     */
    private void validateRequired(List<String> errors, String label, String value) {
        if (trimToNull(value) == null) {
            errors.add(label + "不能为空");
        }
    }
}
