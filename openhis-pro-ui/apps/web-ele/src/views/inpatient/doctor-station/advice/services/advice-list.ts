import type {
  AdviceDrugItem,
  AdviceProductOption,
  AdviceRowItem,
  TcmAdviceListHerbLine,
} from './advice.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceRowDto,
} from '#/api/doctor-station/advice.types';
import type { InpatientDoctorStationAdviceRowDto } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';
import type { DrugGroupDisplayItem, DrugGroupKey } from '#/utils/doctor-station/drug-group-table';

import { formatDateTime } from '@zxplat/utils';

import {
  resolveAdviceStatusTagTypeFromCode,
  resolveSkinTestTagTypeFromCode,
} from '#/utils/advice/advice-status-display';
import { resolveAdviceQuantityUnitLabel } from '#/utils/doctor-station/advice-quantity-unit-display';
import { mergeConsecutiveRowsByDrugGroup } from '#/utils/doctor-station/drug-group-table';
import {
  formatLongTermMedicationDoseLine,
  formatMedicationDoseSummaryLine,
} from '#/utils/doctor-station/medication-dose-display';
import {
  buildMedicationContentTitleWithPackageSplit,
  mergePackageSplitIntoSpec,
} from '#/utils/doctor-station/package-split-ratio-display';
import { convertProductUnitPriceForDisplay } from '#/utils/doctor-station/product-unit-band-price';
import { SERVICE_CATEGORY_DISCHARGE } from '#/utils/print/constants/service-request-category-codes';
import { sumMoneyThenCalc } from '#/utils/format/money-scale';
import { buildServiceApplyFormExtensionText } from '#/utils/service-apply-display';
import { resolveServiceApplyFormNo } from '#/utils/service-apply-form-group-display';
import { TCM_DECOCTION_ACTIVITY_NAME } from '#/components/doctor-station/tcm-advice/services/tcm-prescription.service';
import { simplifyTcmHerbDoseDisplay } from '#/utils/tcm-prescription-display';

import { formatUnitPriceYuan } from './advice-format';
import { isInpatientLongTermMedicationTherapy } from './advice-row';
import {
  HERBAL_MEDICATION_CATEGORY_CODE,
  INPATIENT_ENTRUST_ACTIVITY_NAME,
  INPATIENT_ENTRUST_CATEGORY_CODE,
  INPATIENT_NURSING_LEVEL_CATEGORY_CODE,
} from './advice-tab.constants';
import { normalizeCatalogUnitBands } from './medication-unit-bands';

/** 医嘱列表映射、成组合并、目录选品、库存展示、旧行回填 */

// —— 列表 DTO → 表格行 ——

/** 列表起止时间展示到分钟（YYYY-MM-DD HH:mm） */
function formatAdviceListTimeMinute(raw: string): string {
  const full = formatDateTime(raw);
  const matched = String(full ?? '').match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2})/);
  return matched ? matched[1]! : String(full ?? '').trim();
}

function resourceTypeToTypeLabel(resourceType: string): string {
  const rt = resourceType.toUpperCase();
  if (rt === 'MEDICATION') return '药品';
  if (rt === 'SERVICE') return '诊疗';
  if (rt === 'DEVICE') return '耗材';
  return resourceType || '医嘱';
}

export function inferAdviceRowResourceType(dto: DoctorStationAdviceRowDto): string {
  const raw = String(dto.resourceType ?? '').trim();
  if (raw) return raw.toUpperCase();
  const tbl = String(dto.productTableName ?? '').toLowerCase();
  if (tbl.includes('medication')) return 'MEDICATION';
  return '';
}

/** 是否嘱托行（类目 52 或目录/展示名「嘱托」，BR-IP-ORD-028⑥） */
export function isEntrustAdviceDto(
  dto: Pick<
    DoctorStationAdviceRowDto,
    'adviceDisplayName' | 'catalogCategoryCode' | 'serviceCategoryEnum'
  >,
): boolean {
  const cat =
    String(dto.serviceCategoryEnum ?? '').trim() || String(dto.catalogCategoryCode ?? '').trim();
  if (cat === INPATIENT_ENTRUST_CATEGORY_CODE) return true;
  return String(dto.adviceDisplayName ?? '').trim() === INPATIENT_ENTRUST_ACTIVITY_NAME;
}

function buildAdviceContentTitle(dto: DoctorStationAdviceRowDto): string {
  // 嘱托：内容列优先医生录入正文，禁止只显示目录名「嘱托」
  if (isEntrustAdviceDto(dto)) {
    const entrust = String(dto.entrustContent ?? '').trim();
    if (entrust) return entrust;
    const summary = String(dto.contentSummary ?? '').trim();
    if (summary) return summary;
  }
  const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  // 规格装量：【5mg1片x100】；输液仅规格【250ml: 50g】（禁止题名外再拼 1瓶=100片）
  return buildMedicationContentTitleWithPackageSplit(
    name,
    spec,
    dto.packageSplitRatioText,
    dto.injectionFlag === true,
  );
}

function buildAdviceDosageSummaryLine(dto: DoctorStationAdviceRowDto): string {
  const longTerm = isInpatientLongTermMedicationTherapy(dto.therapyTypeCode);
  const firstDoseParts = {
    firstDose: dto.firstDose,
    firstDoseUnitCode: dto.firstDoseUnitCode,
    firstDoseUnitName: dto.firstDoseUnitName,
  };
  // 长期药：一次一片，每日三片，首日…（频次在用法行）
  if (longTerm) {
    const longTermLine = formatLongTermMedicationDoseLine({
      dosageDisplay: dto.dosageDisplay,
      dailyDoseDisplay: dto.dailyDoseDisplay,
      dose: dto.dose,
      doseUnitCode: dto.doseUnitCode,
      doseUnitName: dto.doseUnitName,
      splitDose: dto.splitDose,
      splitDoseUnitCode: dto.splitDoseUnitCode,
      splitDoseUnitName: dto.splitDoseUnitName,
      // 输液本地重拼跟计量单位（与后端 injectFlag 口径；请求侧 injectionFlag 同源回填）
      injectFlag: dto.injectionFlag === true,
      ...firstDoseParts,
    });
    if (longTermLine) return longTermLine;
  }
  return formatMedicationDoseSummaryLine({
    dosageDisplay: dto.dosageDisplay,
    dose: dto.dose,
    doseUnitCode: dto.doseUnitCode,
    doseUnitName: dto.doseUnitName,
    splitDose: dto.splitDose,
    splitDoseUnitCode: dto.splitDoseUnitCode,
    splitDoseUnitName: dto.splitDoseUnitName,
    injectFlag: dto.injectionFlag === true,
    quantity: dto.quantity,
    unitCode: dto.unitCode,
    unitName: dto.unitName,
    includeTotal: !longTerm,
    resolveTotalUnitLabel: resolveAdviceQuantityUnitLabel,
    ...firstDoseParts,
  });
}

function therapyTypeLabel(code: string): string {
  const c = code.trim().toUpperCase();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  if (c === 'DISCHARGE_MEDICATION') return '出院带药';
  return '';
}

/** 列表「类型」列长临文案：优先后端 therapyTypeText，否则按 therapyTypeCode 推导 */
export function resolveAdviceTherapyTypeText(
  row: Pick<AdviceRowItem, 'therapyTypeCode' | 'therapyTypeText'>,
): string {
  const text = String(row.therapyTypeText ?? '').trim();
  if (text) return text;
  return therapyTypeLabel(String(row.therapyTypeCode ?? ''));
}

/** 长临颜色 class（由 therapyTypeCode 判断） */
export function adviceTherapyTypeClass(code?: string): string {
  const c = String(code ?? '')
    .trim()
    .toUpperCase();
  if (c === 'LONG_TERM') return 'ip-advice-therapy-text--long';
  if (c === 'TEMPORARY') return 'ip-advice-therapy-text--temp';
  if (c === 'DISCHARGE_MEDICATION') return 'ip-advice-therapy-text--discharge';
  return '';
}

function buildAdviceMethodRateDurationLine(dto: DoctorStationAdviceRowDto): string {
  const m = String(dto.methodName ?? '').trim();
  const r = String(dto.rateName ?? '').trim();
  const speed = String(dto.speedName ?? '').trim();
  const longTerm = isInpatientLongTermMedicationTherapy(dto.therapyTypeCode);
  let dayPart = '';
  if (!longTerm) {
    const durRaw = dto.dispensePerDuration;
    if (durRaw !== null && durRaw !== undefined && String(durRaw).trim() !== '') {
      const d = Number(durRaw);
      if (Number.isFinite(d) && d > 0) dayPart = ` (${Math.trunc(d)}天)`;
    }
  }
  const parts: string[] = [];
  if (m) parts.push(m);
  if (r) parts.push(r);
  if (speed) parts.push(`滴速${speed}`);
  if (parts.length === 0) return dayPart.trim();
  return `${parts.join(', ')}${dayPart}`;
}

/** 列表 DTO 是否为护理级别（category 26） */
function isNursingLevelAdviceDto(dto: DoctorStationAdviceRowDto): boolean {
  const serviceCat = String(dto.serviceCategoryEnum ?? '').trim();
  if (serviceCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE) return true;
  const catalogCat = String(dto.catalogCategoryCode ?? '').trim();
  return catalogCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE;
}

/** 医嘱内容 extension：申请单样本/部位/备注 + 用法频次天数；出院行拼计划出院时间等 */
function buildAdviceListExtensionLine(dto: DoctorStationAdviceRowDto): string {
  // 护理级别无频次展示（BR-IP-ORD-026；列表仅展示级别名称）
  const usageLine = isNursingLevelAdviceDto(dto) ? '' : buildAdviceMethodRateDurationLine(dto);
  const oralLine = buildOralPackageRemainExtensionLine(dto);
  const techLine = buildTechExecutionProgressText(dto);
  const extras: string[] = [];
  // 出院医嘱：扩展行展示出院方式/计划出院时间/原因（BR-IP-ORD-017；不写 contentSummary）
  const dischargeParts = buildDischargeAdviceExtensionParts(dto);
  for (const part of dischargeParts) {
    extras.push(part);
  }
  // 医技 TECH 诊疗：副行「医技已执行 / 医技待执行」（BR-IP-ORD-038；同出院蓝色扩展行）
  if (techLine) {
    extras.push(techLine);
  }
  // 诊疗行：扩展行展示执行科室名（与门诊同构；TECH 行与医技进度并列，如「医技已执行；检验科」）
  if (inferAdviceRowResourceType(dto) === 'SERVICE') {
    const dept = String(dto.performLocationName ?? '').trim();
    if (dept) {
      extras.push(dept);
    }
  }
  if (usageLine) extras.push(usageLine);
  if (oralLine) extras.push(oralLine);
  return buildServiceApplyFormExtensionText(
    {
      remark: dto.remark,
      serviceRequestDetails: dto.serviceRequestDetails,
    },
    extras,
  );
}

/** 出院方式 code：死亡（与术语 out_way_code / 种子 5 对齐） */
const OUT_WAY_CODE_DEATH = '5';

/**
 * 出院医嘱列表扩展片段：出院方式、计划出院时间/死亡时间、原因（专用字段，禁止拼进 contentSummary）。
 *
 * @param dto 医嘱列表行（住院含 plannedDischargeTime 等）
 * @returns 非空片段；非出院行返回空数组
 */
function buildDischargeAdviceExtensionParts(dto: DoctorStationAdviceRowDto): string[] {
  const serviceCat = String(dto.serviceCategoryEnum ?? '').trim();
  if (serviceCat !== SERVICE_CATEGORY_DISCHARGE) {
    return [];
  }
  const ip = dto as InpatientDoctorStationAdviceRowDto;
  const parts: string[] = [];
  const outWay = String(ip.outWayName ?? '').trim();
  if (outWay) {
    parts.push(outWay);
  }
  const plannedRaw = String(ip.plannedDischargeTime ?? '').trim();
  if (plannedRaw) {
    const plannedDisplay = formatAdviceListTimeMinute(plannedRaw) || plannedRaw;
    // 死亡出院：扩展行展示「死亡时间」，与患者表 deceased_date 开立同步口径一致
    const timeLabel =
      String(ip.outWayCode ?? '').trim() === OUT_WAY_CODE_DEATH ? '死亡时间' : '出院时间';
    parts.push(`${timeLabel}：${plannedDisplay}`);
  }
  const reason = String(ip.reasonText ?? '').trim();
  if (reason) {
    parts.push(`原因：${reason}`);
  }
  return parts;
}

/**
 * 口服一盒一点剩余展示行（BR-IP-EXE-038）；未命中返回空串。
 */
function buildOralPackageRemainExtensionLine(dto: DoctorStationAdviceRowDto): string {
  const ip = dto as {
    oralPackageBatchInventoryApplied?: boolean;
    oralPackageConsumedSplitQuantity?: number | string;
    oralPackageRemainSplitQuantity?: number | string;
    oralPackageSplitUnitDisplay?: string;
    oralPackageNextClaimDate?: string;
  };
  if (ip.oralPackageBatchInventoryApplied !== true) return '';
  const unit = String(ip.oralPackageSplitUnitDisplay ?? '').trim() || '片';
  const eaten = String(ip.oralPackageConsumedSplitQuantity ?? '').trim();
  const remain = String(ip.oralPackageRemainSplitQuantity ?? '').trim();
  const next = String(ip.oralPackageNextClaimDate ?? '').trim();
  const parts: string[] = [];
  if (eaten !== '' || remain !== '') {
    parts.push(`已吃${eaten || '0'}${unit}，剩余${remain || '0'}${unit}`);
  }
  if (next) parts.push(`预计下次领盒 ${next}`);
  return parts.join('；');
}

/**
 * 医技执行进度副文案（BR-IP-ORD-038）：挂医嘱内容扩展行，风格同出院「医嘱离院；出院时间」。
 * 非 TECH 执行科诊疗（计数字段空）返回空串；执行科室名由扩展行另段拼接（performLocationName）。
 *
 * @param dto 列表行 DTO
 * @returns `医技已执行` / `医技待执行`；无医技进度时为空串
 */
function buildTechExecutionProgressText(dto: DoctorStationAdviceRowDto): string {
  const ip = dto as {
    techPlannedExecutionCount?: number | null;
    techCompletedCount?: number | null;
    techCancelledCount?: number | null;
  };
  // 非 TECH 行后端不下发计划次数
  if (ip.techPlannedExecutionCount === null || ip.techPlannedExecutionCount === undefined) {
    return '';
  }
  const completed = Number(ip.techCompletedCount ?? 0);
  const cancelled = Number(ip.techCancelledCount ?? 0);
  let net = completed - cancelled;
  // 业务：净已执行不为负
  if (!Number.isFinite(net) || net < 0) {
    net = 0;
  }
  const planned = Number(ip.techPlannedExecutionCount);
  // 计划次数有效且净执行已满 → 已执行；否则待执行
  if (Number.isFinite(planned) && planned > 0 && net >= planned) {
    return '医技已执行';
  }
  return '医技待执行';
}

function formatAdviceRowTotalPrice(dto: DoctorStationAdviceRowDto): string {
  const v = dto.lineDisplayTotalPrice;
  if (v !== null && v !== undefined && String(v).trim() !== '') {
    return formatUnitPriceYuan(v);
  }
  return formatUnitPriceYuan(dto.lineTotalAmount);
}

function normalizedListSortNumber(
  raw: DoctorStationAdviceRowDto['sortNumber'],
): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? Math.trunc(n) : undefined;
}

function skinTestDisplayText(dto: DoctorStationAdviceRowDto): string {
  const outcome = String(dto.skinTestOutcomeText ?? '').trim();
  if (outcome) return outcome;
  const t = String(dto.skinTestFlagText ?? '').trim();
  if (t) return t;
  if (dto.skinTestFlag === true) return '是';
  if (dto.skinTestFlag === false) return '否';
  return '—';
}

/** 护理级别诊疗行识别（列表置顶与弹窗门控共用） */
export function isNursingLevelAdviceRow(
  row: Pick<AdviceRowItem, 'catalogCategoryCode' | 'serviceCategoryEnum'>,
): boolean {
  const serviceCat = String(row.serviceCategoryEnum ?? '').trim();
  if (serviceCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE) return true;
  const catalogCat = String(row.catalogCategoryCode ?? '').trim();
  return catalogCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE;
}

/** 护理级别须固定展示在医嘱列表第一行（AS-IP-DOR-003 展示习惯） */
export function pinNursingLevelAdviceRowsToFirst(rows: readonly AdviceRowItem[]): AdviceRowItem[] {
  if (rows.length <= 1) return [...rows];
  const nursingRows: AdviceRowItem[] = [];
  const otherRows: AdviceRowItem[] = [];
  for (const row of rows) {
    if (isNursingLevelAdviceRow(row)) {
      nursingRows.push(row);
    } else {
      otherRows.push(row);
    }
  }
  if (nursingRows.length === 0) return [...rows];
  return [...nursingRows, ...otherRows];
}

export function mapAdviceListRowDto(dto: DoctorStationAdviceRowDto): AdviceRowItem {
  const resourceType = inferAdviceRowResourceType(dto);
  const requestId = String(dto.requestId ?? '').trim();
  const listKey = String(dto.listRowKey ?? '').trim();
  const key =
    listKey ||
    (requestId && resourceType ? `api-${resourceType}-${requestId}` : `api-row-${Date.now()}`);
  const status =
    String(dto.displayStatusEnumText ?? '').trim() ||
    String(dto.statusEnumText ?? '').trim() ||
    String(dto.displayStatusEnum ?? '').trim() ||
    String(dto.statusEnum ?? '').trim() ||
    '—';
  // 起止时间列：时间与人名分行展示（BR-IP-ORD-031）；原始值供点选改时回填
  const orderingDoctor = (dto.authoredByName || '').trim() || '—';
  const stopperName = String(dto.stopperName ?? '').trim();
  const openedRaw = String(dto.reqOpenedTime ?? '').trim();
  const startTime = openedRaw ? formatAdviceListTimeMinute(openedRaw) : '';
  const stopRaw = String(dto.stopTime ?? '').trim();
  const endTime = stopRaw ? formatAdviceListTimeMinute(stopRaw) : undefined;
  const content = buildAdviceContentTitle(dto);
  const dosageLine = buildAdviceDosageSummaryLine(dto);
  const extension = buildAdviceListExtensionLine(dto);
  // 嘱托类型列展示「嘱托」类目名（与长/临 therapy 文案并列）
  const typeLabel = isEntrustAdviceDto(dto)
    ? INPATIENT_ENTRUST_ACTIVITY_NAME
    : String(dto.catalogCategoryName ?? '').trim() || resourceTypeToTypeLabel(resourceType);

  return {
    key,
    status,
    type: typeLabel,
    content: content || String(dto.contentSummary ?? '').trim(),
    startTime,
    endTime,
    openedTimeRaw: openedRaw || undefined,
    stopTimeRaw: stopRaw || undefined,
    stopperName: stopperName || undefined,
    stopPerformerCheckId:
      String((dto as { stopPerformerCheckId?: string }).stopPerformerCheckId ?? '').trim() ||
      undefined,
    extension,
    lineTotalAmount: formatAdviceRowTotalPrice(dto),
    diagnosisSummary:
      (
        dto.conditionDefinitionName ||
        dto.boundConditionName ||
        dto.diagnosisSummary ||
        ''
      ).trim() || '—',
    orderingDoctor,
    skinTestText: skinTestDisplayText(dto),
    skinTestOutcomeCode: String(dto.skinTestOutcomeCode ?? '').trim() || undefined,
    basedOnMedicationRequestId: String(dto.basedOnMedicationRequestId ?? '').trim() || undefined,
    needAppendSkinTestFlag: Boolean(dto.needAppendSkinTestFlag),
    dosageText: dosageLine || undefined,
    oralPackageBatchInventoryApplied: Boolean(
      (dto as { oralPackageBatchInventoryApplied?: boolean }).oralPackageBatchInventoryApplied,
    ),
    oralPackageRemainText: buildOralPackageRemainExtensionLine(dto) || undefined,
    productTableName: String(dto.productTableName ?? '').trim() || undefined,
    sortNumber: normalizedListSortNumber(dto.sortNumber),
    skinTestFlag: dto.skinTestFlag,
    resourceType,
    requestId,
    groupId: String(dto.groupId ?? '').trim() || undefined,
    statusEnum: dto.displayStatusEnum ?? dto.statusEnum,
    therapyTypeCode: String(dto.therapyTypeCode ?? '').trim() || undefined,
    therapyTypeText:
      String(dto.therapyTypeText ?? '').trim() ||
      therapyTypeLabel(String(dto.therapyTypeCode ?? '')) ||
      undefined,
    entrustContent: String(dto.entrustContent ?? '').trim() || undefined,
    remark: String(dto.remark ?? '').trim() || undefined,
    rateCode: String(dto.rateCode ?? '').trim() || undefined,
    productDefinitionId: String(dto.productDefinitionId ?? '').trim() || undefined,
    adviceDisplayName: String(dto.adviceDisplayName ?? '').trim() || undefined,
    specificationText: String(dto.specificationText ?? '').trim() || undefined,
    conditionDefinitionName: String(dto.conditionDefinitionName ?? '').trim() || undefined,
    methodCode: String(dto.methodCode ?? '').trim() || undefined,
    speedCode: String(dto.speedCode ?? '').trim() || undefined,
    speedName: String(dto.speedName ?? '').trim() || undefined,
    injectionFlag:
      dto.injectionFlag === true ? true : dto.injectionFlag === false ? false : undefined,
    listDose: positiveListNumber(dto.dose),
    listDoseUnitCode: String(dto.doseUnitCode ?? '').trim() || undefined,
    listFirstDose: positiveListNumber(dto.firstDose),
    listFirstDoseUnitCode: String(dto.firstDoseUnitCode ?? '').trim() || undefined,
    listFirstDoseUnitName: String(dto.firstDoseUnitName ?? '').trim() || undefined,
    listDoseSchedule: String(dto.doseSchedule ?? '').trim() || undefined,
    listQuantitySchedule: String(dto.quantitySchedule ?? '').trim() || undefined,
    listUnitCode: String(dto.unitCode ?? '').trim() || undefined,
    listUnitName: String(dto.unitName ?? '').trim() || undefined,
    dispensePerDuration: (() => {
      const raw = dto.dispensePerDuration;
      if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
      const n = Number(raw);
      return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
    })(),
    listQuantity: (() => {
      const q = dto.quantity;
      if (q === null || q === undefined || String(q).trim() === '') return undefined;
      const n = typeof q === 'number' ? q : Number(q);
      return Number.isFinite(n) && n > 0 ? n : undefined;
    })(),
    listExecutionCount: (() => {
      const raw = dto.executionCount;
      if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
      const n = typeof raw === 'number' ? raw : Number(raw);
      return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
    })(),
    performOrgId: String(dto.performLocationId ?? '').trim() || undefined,
    performOrgName: String(dto.performLocationName ?? '').trim() || undefined,
    applyFormNo: resolveServiceApplyFormNo(dto.prescriptionNo),
    serviceRequestDetails: dto.serviceRequestDetails ?? undefined,
    serviceCategoryEnum: String(dto.serviceCategoryEnum ?? '').trim() || undefined,
    catalogCategoryCode: String(dto.catalogCategoryCode ?? '').trim() || undefined,
    priorityEnum: String(dto.priorityEnum ?? '').trim() || undefined,
    ...(dto.chrgitmLv !== null && dto.chrgitmLv !== undefined && String(dto.chrgitmLv).trim() !== ''
      ? { chrgitmLv: dto.chrgitmLv }
      : {}),
    ...(String(dto.chrgitmLvText ?? '').trim()
      ? { chrgitmLvText: String(dto.chrgitmLvText).trim() }
      : {}),
    tcmFlag: dto.tcmFlag === true ? true : undefined,
    prescriptionNo: String(dto.prescriptionNo ?? '').trim() || undefined,
    chineseHerbsDoseQuantity:
      dto.chineseHerbsDoseQuantity !== null && dto.chineseHerbsDoseQuantity !== undefined
        ? dto.chineseHerbsDoseQuantity
        : undefined,
    dosageInstructionText: String(dto.dosageInstructionText ?? '').trim() || undefined,
    dosageDisplay: String(dto.dosageDisplay ?? '').trim() || undefined,
  };
}

function positiveListNumber(raw: unknown): number | undefined {
  if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

export function mapCatalogRowToOption(row: DoctorStationAdviceCatalogRowDto): AdviceProductOption {
  const defId = String(row.productDefinitionId ?? '').trim();
  const tbl = String(row.productTableName ?? '').trim();
  const advice = String(row.adviceType ?? '')
    .trim()
    .toUpperCase();
  const categoryFallback =
    advice === 'MED' ? '药品' : advice === 'SRV' ? '诊疗' : advice === 'DEV' ? '耗材' : advice;
  const categoryLabel = String(row.categoryCodeDictText ?? '').trim() || categoryFallback;
  const id = tbl && defId ? `${tbl}:${defId}` : defId || tbl || `row-${Date.now()}`;
  const specText =
    String(row.totalVolume ?? '').trim() ||
    String(row.unitName ?? '').trim() ||
    String(row.unitCode ?? '').trim();
  const unitBands = normalizeCatalogUnitBands(row.unitBands, row.unitBandsJson);
  const catalogUnitPriceRaw = row.unitPrice;
  let catalogUnitPrice: number | undefined;
  if (
    catalogUnitPriceRaw !== null &&
    catalogUnitPriceRaw !== undefined &&
    String(catalogUnitPriceRaw).trim() !== ''
  ) {
    const cp = Number(catalogUnitPriceRaw);
    if (Number.isFinite(cp)) catalogUnitPrice = cp;
  }
  const price = convertProductUnitPriceForDisplay({
    unitPrice: row.unitPrice,
    unitBands,
    unitBandsJson: row.unitBandsJson,
  });
  const orgIdRaw = row.defaultPerformOrgId;
  const orgId =
    orgIdRaw !== null && orgIdRaw !== undefined && String(orgIdRaw).trim() !== ''
      ? String(orgIdRaw).trim()
      : undefined;
  const orgName = String(row.defaultPerformOrgName ?? '').trim() || undefined;
  const uCode = String(row.unitCode ?? '').trim() || undefined;
  const uName = String(row.unitName ?? '').trim() || undefined;

  const sortOrderRaw = row.sortOrder;
  let sortOrder: number | undefined;
  if (sortOrderRaw !== null && sortOrderRaw !== undefined && String(sortOrderRaw).trim() !== '') {
    const sn = Number(sortOrderRaw);
    if (Number.isFinite(sn)) {
      sortOrder = sn;
    }
  }

  return {
    id,
    code: row.busNo || '',
    name: row.name || '',
    ...(sortOrder !== undefined ? { sortOrder } : {}),
    // 商品名始终回填，空值由选品表展示为「—」
    merchandiseName: String(row.merchandiseName ?? '').trim(),
    category: categoryLabel,
    spec: specText,
    price,
    catalogUnitPrice,
    availableQuantity: row.availableQuantity,
    ...(String(row.availableQuantityText ?? '').trim()
      ? { availableQuantityText: String(row.availableQuantityText).trim() }
      : {}),
    // 厂家列始终回填，空值由选品表展示为「—」
    manufacturer: String(row.manufacturer ?? '').trim(),
    ...(Array.isArray(row.inventoryList) && row.inventoryList.length > 0
      ? { inventoryList: row.inventoryList }
      : {}),
    unitName: uName,
    rateCode: row.rateCode ? String(row.rateCode).trim() : undefined,
    methodCode: row.methodCode ? String(row.methodCode).trim() : undefined,
    injectFlag: row.injectFlag === true,
    productDefinitionId: defId,
    productTableName: tbl,
    categoryCode: String(row.categoryCode ?? '').trim() || undefined,
    ...(unitBands.length > 0 ? { unitBands } : {}),
    ...(orgId ? { defaultPerformOrgId: orgId } : {}),
    ...(orgName ? { defaultPerformOrgName: orgName } : {}),
    ...(uCode ? { defaultUnitCode: uCode } : {}),
    ...(uName ? { defaultUnitName: uName } : {}),
    // 住院临嘱用目录「住院临时医嘱拆分属性」；缺省回落门诊拆分属性
    ...(() => {
      const partAttr =
        String(row.thoPartAttributeEnum ?? '').trim() || String(row.partAttributeEnum ?? '').trim();
      return partAttr ? { partAttributeEnum: partAttr } : {};
    })(),
    ...(row.chrgitmLv !== null && row.chrgitmLv !== undefined && String(row.chrgitmLv).trim() !== ''
      ? { chrgitmLv: row.chrgitmLv }
      : {}),
    ...(String(row.chrgitmLvText ?? '').trim()
      ? { chrgitmLvText: String(row.chrgitmLvText).trim() }
      : {}),
    ...(String(row.dosageInstruction ?? '').trim()
      ? { dosageInstruction: String(row.dosageInstruction).trim() }
      : {}),
    ...(String(row.dosageInstructionDictText ?? '').trim()
      ? { dosageInstructionDictText: String(row.dosageInstructionDictText).trim() }
      : {}),
    ...pickPositiveCatalogDoseField(row.dose, 'dose'),
    ...pickPositiveCatalogDoseField(row.minUnit, 'minUnit'),
    ...pickPositiveCatalogDoseField(row.maxUnit, 'maxUnit'),
  };
}

/** 目录剂量字段：仅透出有限正数，避免 0/空串污染选品默认值。 */
function pickPositiveCatalogDoseField(
  raw: null | number | string | undefined,
  key: 'dose' | 'maxUnit' | 'minUnit',
): Partial<Pick<AdviceProductOption, 'dose' | 'maxUnit' | 'minUnit'>> {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return {};
  }
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return {};
  }
  return { [key]: n };
}

export function getAdviceStatusTagType(
  row: AdviceRowItem,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  return resolveAdviceStatusTagTypeFromCode(row.statusEnum);
}

/** 皮试列 Tag 类型：仅认 skinTestOutcomeCode / skinTestFlag */
export function getSkinTestDisplayTagType(
  row: Pick<AdviceRowItem, 'skinTestFlag' | 'skinTestOutcomeCode' | 'skinTestText'>,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' | null {
  return resolveSkinTestTagTypeFromCode(row.skinTestOutcomeCode, row.skinTestFlag);
}

// —— 成组合并 ——

export function flattenAdviceRowsForResourceOps(rows: AdviceRowItem[]): AdviceRowItem[] {
  const out: AdviceRowItem[] = [];
  for (const r of rows) {
    const members = r.mergedAdviceRows;
    if (members !== undefined && members.length > 0) {
      out.push(...members);
    } else {
      out.push(r);
    }
  }
  return out;
}

/** 医嘱列表开立时间排序：升序=先开在上，倒序=后开在上（默认） */
export type AdviceListTimeSortOrder = 'asc' | 'desc';

/**
 * 列表排序：按开立时间（reqOpenedTime）；
 * 成组药聚邻——倒序用组内最晚、升序用组内最早作为簇位置，组内再按各自开立时间排；
 * 皮试检查 companion 与 based_on 皮试药同簇（药在前）。
 * @param timeSortOrder 默认倒序（后开的在上）
 */
function sortDoctorStationAdviceRowsStable(
  rows: readonly DoctorStationAdviceRowDto[],
  timeSortOrder: AdviceListTimeSortOrder = 'desc',
): DoctorStationAdviceRowDto[] {
  // 成组药簇位置：倒序取组内最晚、升序取组内最早，避免同组被拆开
  const groupClusterOpenedAt = new Map<string, string>();
  // 皮试药 requestId → 开立时刻，供 companion 同簇
  const medicationOpenedAt = new Map<string, string>();
  for (const row of rows) {
    const openedAt = resolveAdviceOpenedAtSortKey(row);
    const groupKey = medicationGroupMergeKey(row);
    if (groupKey !== '' && groupKey !== null && groupKey !== undefined && openedAt !== '') {
      const existing = groupClusterOpenedAt.get(String(groupKey));
      if (existing === undefined) {
        groupClusterOpenedAt.set(String(groupKey), openedAt);
      } else if (timeSortOrder === 'asc') {
        if (openedAt.localeCompare(existing) < 0) {
          groupClusterOpenedAt.set(String(groupKey), openedAt);
        }
      } else if (openedAt.localeCompare(existing) > 0) {
        groupClusterOpenedAt.set(String(groupKey), openedAt);
      }
    }
    if (inferAdviceRowResourceType(row) === 'MEDICATION' && openedAt !== '') {
      const rid = String(row.requestId ?? '').trim();
      if (rid) {
        medicationOpenedAt.set(rid, openedAt);
      }
    }
  }

  return rows.toSorted((a, b) => {
    const aTime = resolveAdviceClusterOpenedAtSortKey(a, groupClusterOpenedAt, medicationOpenedAt);
    const bTime = resolveAdviceClusterOpenedAtSortKey(b, groupClusterOpenedAt, medicationOpenedAt);
    // 无开立时刻的草稿排后（升/倒序均靠后）
    if (aTime === '' && bTime !== '') return 1;
    if (aTime !== '' && bTime === '') return -1;
    const timeCompare =
      timeSortOrder === 'asc' ? aTime.localeCompare(bTime) : bTime.localeCompare(aTime);
    if (timeCompare !== 0) {
      return timeCompare;
    }

    const ag = medicationGroupMergeKey(a);
    const bg = medicationGroupMergeKey(b);
    const aGroupSort = ag === '' || ag === null || ag === undefined ? '\uFFFF' : String(ag);
    const bGroupSort = bg === '' || bg === null || bg === undefined ? '\uFFFF' : String(bg);
    const gcmp = aGroupSort.localeCompare(bGroupSort);
    if (gcmp !== 0) return gcmp;

    const aSkin = resolveSkinTestClusterKey(a);
    const bSkin = resolveSkinTestClusterKey(b);
    const aSkinSort = aSkin === '' ? '\uFFFF' : aSkin;
    const bSkinSort = bSkin === '' ? '\uFFFF' : bSkin;
    const scmp = aSkinSort.localeCompare(bSkinSort);
    if (scmp !== 0) return scmp;

    // 皮试药在前、companion 在后（同簇内）
    const rankCmp = resolveSkinTestCompanionRank(a) - resolveSkinTestCompanionRank(b);
    if (rankCmp !== 0) return rankCmp;

    // 同组成组药：再按各自开立时间排（与列表升/倒序一致）
    const aOpened = resolveAdviceOpenedAtSortKey(a);
    const bOpened = resolveAdviceOpenedAtSortKey(b);
    if (aOpened === '' && bOpened !== '') return 1;
    if (aOpened !== '' && bOpened === '') return -1;
    if (aOpened !== '' && bOpened !== '') {
      const memberTimeCmp =
        timeSortOrder === 'asc' ? aOpened.localeCompare(bOpened) : bOpened.localeCompare(aOpened);
      if (memberTimeCmp !== 0) return memberTimeCmp;
    }

    return String(a.requestId ?? '').localeCompare(String(b.requestId ?? ''));
  });
}

/** 单行开立时刻排序键（reqOpenedTime）。 */
function resolveAdviceOpenedAtSortKey(dto: DoctorStationAdviceRowDto): string {
  return String(dto.reqOpenedTime ?? '').trim();
}

/** 簇排序键：成组药用组内最早/最晚（由调用方预计算）；皮试 companion 用锚定药开立时刻。 */
function resolveAdviceClusterOpenedAtSortKey(
  dto: DoctorStationAdviceRowDto,
  groupClusterOpenedAt: ReadonlyMap<string, string>,
  medicationOpenedAt: ReadonlyMap<string, string>,
): string {
  const basedOnMed = String(dto.basedOnMedicationRequestId ?? '').trim();
  if (basedOnMed) {
    const linked = medicationOpenedAt.get(basedOnMed);
    if (linked !== undefined && linked !== '') {
      return linked;
    }
  }
  const groupKey = medicationGroupMergeKey(dto);
  if (groupKey !== '' && groupKey !== null && groupKey !== undefined) {
    const groupTime = groupClusterOpenedAt.get(String(groupKey));
    if (groupTime !== undefined && groupTime !== '') {
      return groupTime;
    }
  }
  return resolveAdviceOpenedAtSortKey(dto);
}

/** 皮试聚邻键：companion=basedOn；皮试药=自身；其它空。 */
function resolveSkinTestClusterKey(dto: DoctorStationAdviceRowDto): string {
  const basedOn = String(dto.basedOnMedicationRequestId ?? '').trim();
  if (basedOn) return basedOn;
  if (inferAdviceRowResourceType(dto) === 'MEDICATION' && dto.skinTestFlag === true) {
    return String(dto.requestId ?? '').trim();
  }
  return '';
}

/** 同皮试簇内次序：皮试药 0，companion 1，其它 2。 */
function resolveSkinTestCompanionRank(dto: DoctorStationAdviceRowDto): number {
  if (String(dto.basedOnMedicationRequestId ?? '').trim()) return 1;
  if (inferAdviceRowResourceType(dto) === 'MEDICATION' && dto.skinTestFlag === true) return 0;
  return 2;
}

function medicationGroupMergeKey(dto: DoctorStationAdviceRowDto): DrugGroupKey {
  if (inferAdviceRowResourceType(dto) !== 'MEDICATION') return '';
  // 中药饮片按方合并，不走西药 groupId 成组
  if (isTcmHerbAdviceDto(dto)) return '';
  const gid = String(dto.groupId ?? '').trim();
  return gid === '' ? '' : gid;
}

function doctorStationAdviceRowDtoToDrugGroupItem(
  dto: DoctorStationAdviceRowDto,
): DrugGroupDisplayItem {
  const longTerm = isInpatientLongTermMedicationTherapy(dto.therapyTypeCode);
  // 长期成组：剂量位「一次一片，每日三片，首日…」
  const longTermDoseLine = longTerm
    ? formatLongTermMedicationDoseLine({
        dosageDisplay: dto.dosageDisplay,
        dailyDoseDisplay: dto.dailyDoseDisplay,
        dose: dto.dose,
        doseUnitCode: dto.doseUnitCode,
        doseUnitName: dto.doseUnitName,
        splitDose: dto.splitDose,
        splitDoseUnitCode: dto.splitDoseUnitCode,
        splitDoseUnitName: dto.splitDoseUnitName,
        injectFlag: dto.injectionFlag === true,
        firstDose: dto.firstDose,
        firstDoseUnitCode: dto.firstDoseUnitCode,
        firstDoseUnitName: dto.firstDoseUnitName,
      })
    : '';
  const dosageDisplay = longTermDoseLine || String(dto.dosageDisplay ?? '').trim() || undefined;
  return {
    selectedDrugName: String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim(),
    // 成组括线：规格段为 5mg1片x100；输液仅规格
    selectedDrugSpec: mergePackageSplitIntoSpec(
      String(dto.specificationText ?? '').trim(),
      dto.packageSplitRatioText,
      dto.injectionFlag === true,
    ),
    pharmacy: '',
    singleDose: longTermDoseLine || (dto.dose ?? ''),
    doseSubUnit: longTermDoseLine ? '' : String(dto.doseUnitName ?? dto.doseUnitCode ?? '').trim(),
    totalAmount: longTerm ? '' : (dto.quantity ?? ''),
    totalAmountUnit: longTerm ? '' : resolveAdviceQuantityUnitLabel(dto.unitName, dto.unitCode),
    usage: String(dto.methodName ?? dto.methodCode ?? '').trim(),
    frequency: String(dto.rateName ?? dto.rateCode ?? '').trim(),
    medicationDays: longTerm ? '' : (dto.dispensePerDuration ?? ''),
    speedDisplay:
      String(dto.speedName ?? '').trim() || String(dto.speedCode ?? '').trim() || undefined,
    dosageDisplay,
    splitDose: longTermDoseLine ? undefined : (dto.splitDose ?? undefined),
    splitDoseUnitCode: longTermDoseLine
      ? undefined
      : String(dto.splitDoseUnitCode ?? '').trim() || undefined,
    splitDoseUnitName: longTermDoseLine
      ? undefined
      : String(dto.splitDoseUnitName ?? '').trim() || undefined,
    doseUnitCode: longTermDoseLine ? undefined : String(dto.doseUnitCode ?? '').trim() || undefined,
  };
}

function sumSliceLinePriceY(slice: readonly DoctorStationAdviceRowDto[]): string {
  const values: Array<number | string> = [];
  // 成组/同方：各明细行展示总价加总（6 位后再 format 到元）
  for (const d of slice) {
    const v = d.lineDisplayTotalPrice ?? d.lineTotalAmount;
    if (v !== null && v !== undefined && String(v).trim() !== '') {
      values.push(v);
    }
  }
  return formatUnitPriceYuan(sumMoneyThenCalc(values));
}

/** 成组同滴速：取组内首条非空 speed（常在加药行，溶媒首行可能为空） */
function pickGroupSpeedFromAdviceSlice(slice: readonly DoctorStationAdviceRowDto[]): {
  speedCode?: string;
  speedName?: string;
} {
  for (const dto of slice) {
    const speedName = String(dto.speedName ?? '').trim();
    const speedCode = String(dto.speedCode ?? '').trim();
    if (speedName || speedCode) {
      return {
        speedCode: speedCode || undefined,
        speedName: speedName || undefined,
      };
    }
  }
  return {};
}

/** 病案首页医嘱列表：中药方标题（与纸质/旧系统汤剂块一致） */
const TCM_LIST_PRESCRIPTION_TITLE = '中药汤剂';

/** 病案首页医嘱列表：类型列中药文案 */
const TCM_LIST_TYPE_LABEL = '中药';

/** 是否中药饮片行（认 tcmFlag / 饮片分类码 / 付数，禁止用展示名） */
function isTcmHerbAdviceDto(dto: DoctorStationAdviceRowDto): boolean {
  if (dto.tcmFlag === true) {
    return true;
  }
  if (String(dto.catalogCategoryCode ?? '').trim() === HERBAL_MEDICATION_CATEGORY_CODE) {
    return true;
  }
  return dto.chineseHerbsDoseQuantity !== null && dto.chineseHerbsDoseQuantity !== undefined;
}

/** 去掉皮试装配误挂在代煎名上的「（关联：…）」后缀，仅用于认 companion */
function stripAdviceLinkedHint(name: string): string {
  const idx = name.indexOf('（关联：');
  if (idx <= 0) {
    return name;
  }
  return name.slice(0, idx).trim();
}

/** 代煎活动展示名：认常量，去掉误挂的关联后缀 */
function decoctionCompanionDisplayName(dto: DoctorStationAdviceRowDto): string {
  const raw = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
  const stripped = stripAdviceLinkedHint(raw);
  if (stripped === TCM_DECOCTION_ACTIVITY_NAME) {
    return TCM_DECOCTION_ACTIVITY_NAME;
  }
  return stripped || TCM_DECOCTION_ACTIVITY_NAME;
}

/** 是否药品行（代煎是诊疗 SR，禁止把西药/饮片当成 companion） */
function isMedicationProductAdviceDto(dto: DoctorStationAdviceRowDto): boolean {
  if (inferAdviceRowResourceType(dto) === 'MEDICATION') {
    return true;
  }
  return String(dto.productTableName ?? '')
    .toLowerCase()
    .includes('medication');
}

/**
 * 是否代煎 companion。
 * 活动名对齐后端 DECOCTION_ACTIVITY_NAME；皮试 hint 会改展示名，故同时认 basedOn / 同方处方号。
 */
function isDecoctionCompanionDto(
  dto: DoctorStationAdviceRowDto,
  herbRequestIds?: ReadonlySet<string>,
  tcmPrescriptionNos?: ReadonlySet<string>,
): boolean {
  if (isTcmHerbAdviceDto(dto) || isMedicationProductAdviceDto(dto)) {
    return false;
  }
  const name = stripAdviceLinkedHint(
    String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim(),
  );
  if (name === TCM_DECOCTION_ACTIVITY_NAME) {
    return true;
  }
  const basedOn = String(dto.basedOnMedicationRequestId ?? '').trim();
  if (basedOn && herbRequestIds?.has(basedOn)) {
    return true;
  }
  const rx = String(dto.prescriptionNo ?? '').trim();
  return Boolean(rx && tcmPrescriptionNos?.has(rx));
}

/** 单味剂量展示：优先简化 dosageDisplay，否则 dose+单位 */
function formatTcmListHerbDose(dto: DoctorStationAdviceRowDto): string {
  const fromDisplay = simplifyTcmHerbDoseDisplay(String(dto.dosageDisplay ?? '').trim());
  if (fromDisplay) {
    return fromDisplay;
  }
  const doseRaw = dto.dose;
  if (doseRaw === null || doseRaw === undefined || String(doseRaw).trim() === '') {
    return '';
  }
  const unit =
    String(dto.doseUnitName ?? '').trim() ||
    (String(dto.doseUnitCode ?? '')
      .trim()
      .toLowerCase() === 'g'
      ? '克'
      : '');
  return unit ? `${String(doseRaw).trim()}${unit}` : String(doseRaw).trim();
}

/** 单味药名：特殊煎法进括号，如 石膏(先煎) */
function formatTcmListHerbName(dto: DoctorStationAdviceRowDto): string {
  const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
  const instruction = String(dto.dosageInstructionText ?? '').trim();
  if (name && instruction) {
    return `${name}(${instruction})`;
  }
  return name;
}

/** 同方饮片合并键：处方号优先，缺省 groupId，再回落 requestId */
function tcmPrescriptionMergeKey(dto: DoctorStationAdviceRowDto): string {
  const rx = String(dto.prescriptionNo ?? '').trim();
  if (rx) {
    return `rx:${rx}`;
  }
  const gid = String(dto.groupId ?? '').trim();
  if (gid) {
    return `gid:${gid}`;
  }
  const rid = String(dto.requestId ?? '').trim();
  return rid ? `id:${rid}` : '';
}

/** 代煎索引键：处方号 + basedOn 代表药行 */
function collectDecoctionMatchKeys(dto: DoctorStationAdviceRowDto): string[] {
  const keys: string[] = [];
  const rx = String(dto.prescriptionNo ?? '').trim();
  if (rx) {
    keys.push(`rx:${rx}`);
  }
  const basedOn = String(dto.basedOnMedicationRequestId ?? '').trim();
  if (basedOn) {
    keys.push(`basedOn:${basedOn}`);
  }
  return keys;
}

/** 从剩余代煎里取出挂到本方的 companion（处方号优先，其次 basedOn 方内饮片） */
function takeDecoctionForHerbSlice(
  slice: readonly DoctorStationAdviceRowDto[],
  decoctionByKey: Map<string, DoctorStationAdviceRowDto>,
): DoctorStationAdviceRowDto | undefined {
  const tryKeys: string[] = [];
  // 先同方号，再饮片 requestId ↔ companion.basedOn
  for (const herb of slice) {
    const rx = String(herb.prescriptionNo ?? '').trim();
    if (rx) {
      tryKeys.push(`rx:${rx}`);
    }
  }
  for (const herb of slice) {
    const rid = String(herb.requestId ?? '').trim();
    if (rid) {
      tryKeys.push(`basedOn:${rid}`);
    }
  }
  for (const key of tryKeys) {
    const hit = decoctionByKey.get(key);
    if (!hit) {
      continue;
    }
    for (const k of collectDecoctionMatchKeys(hit)) {
      if (decoctionByKey.get(k) === hit) {
        decoctionByKey.delete(k);
      }
    }
    return hit;
  }
  return undefined;
}

/** 方内任一味勾选代煎 */
function sliceHasDecoctionFlag(slice: readonly DoctorStationAdviceRowDto[]): boolean {
  for (const dto of slice) {
    if (dto.decoctionDelegatedFlag === true) {
      return true;
    }
  }
  return false;
}

/** 中药方闭合行：用法 + 付数 */
function buildTcmClosingMeta(head: DoctorStationAdviceRowDto): string {
  const parts: string[] = [];
  const method = String(head.methodName ?? '').trim();
  if (method) {
    parts.push(method);
  }
  const fuRaw = head.chineseHerbsDoseQuantity;
  if (fuRaw !== null && fuRaw !== undefined && String(fuRaw).trim() !== '') {
    parts.push(`付数:${String(fuRaw).trim()}`);
  }
  return parts.join(' ');
}

/** 代煎扩展行：活动名 + 付数副 */
function buildTcmDecoctionText(name: string, fuRaw: null | number | string | undefined): string {
  const trimmed = String(name ?? '').trim();
  if (!trimmed) {
    return '';
  }
  if (fuRaw === null || fuRaw === undefined || String(fuRaw).trim() === '') {
    return trimmed;
  }
  return `${trimmed} ${String(fuRaw).trim()}副`;
}

/** 有 companion 用其活动名；否则饮片代煎标志仍展示「代煎中药」 */
function resolveTcmDecoctionText(
  slice: readonly DoctorStationAdviceRowDto[],
  decoction: DoctorStationAdviceRowDto | undefined,
): string {
  const fuRaw = slice[0]?.chineseHerbsDoseQuantity;
  if (decoction) {
    return buildTcmDecoctionText(decoctionCompanionDisplayName(decoction), fuRaw);
  }
  if (sliceHasDecoctionFlag(slice)) {
    return buildTcmDecoctionText(TCM_DECOCTION_ACTIVITY_NAME, fuRaw);
  }
  return '';
}

/** 同方饮片 → 一张方一行 */
function buildTcmPrescriptionListRow(
  slice: readonly DoctorStationAdviceRowDto[],
  decoction: DoctorStationAdviceRowDto | undefined,
): AdviceRowItem {
  const head = slice[0];
  if (head === undefined) {
    throw new Error('buildTcmPrescriptionListRow: empty slice');
  }
  const mapped = mapAdviceListRowDto(head);
  const herbs: TcmAdviceListHerbLine[] = [];
  const memberRows: AdviceRowItem[] = [];
  // 按方内顺序拼饮片行，供竖排单元格
  for (const dto of slice) {
    memberRows.push(mapAdviceListRowDto(dto));
    herbs.push({
      name: formatTcmListHerbName(dto),
      dose: formatTcmListHerbDose(dto),
    });
  }
  const closing = buildTcmClosingMeta(head);
  const decoctionText = resolveTcmDecoctionText(slice, decoction);
  const idPart = slice
    .map((d) => String(d.requestId ?? '').trim())
    .filter(Boolean)
    .join('&');
  const rx = String(head.prescriptionNo ?? '').trim();
  // 总金额 = 同方各味饮片 + 代煎 companion（禁止只用首味）
  const amountSlice = decoction ? [...slice, decoction] : [...slice];
  return {
    ...mapped,
    key: rx ? `merged-tcm-${rx}-${idPart}` : `merged-tcm-${idPart || mapped.key}`,
    type: TCM_LIST_TYPE_LABEL,
    tcmFlag: true,
    tcmPrescriptionTitle: TCM_LIST_PRESCRIPTION_TITLE,
    tcmHerbLines: herbs,
    tcmClosingMeta: closing,
    tcmDecoctionText: decoctionText || undefined,
    extension: decoctionText || mapped.extension,
    mergedAdviceRows: memberRows,
    content: TCM_LIST_PRESCRIPTION_TITLE,
    dosageText: undefined,
    groupItems: undefined,
    lineTotalAmount: sumSliceLinePriceY(amountSlice),
  };
}

/** 按开立时间把中药方行插入西药/诊疗列表 */
function interleaveAdviceRowsByOpenedTime(
  western: readonly AdviceRowItem[],
  tcmRows: readonly AdviceRowItem[],
  timeSortOrder: AdviceListTimeSortOrder,
): AdviceRowItem[] {
  const merged = [...western, ...tcmRows];
  return merged.toSorted((a, b) => {
    const aTime = String(a.openedTimeRaw ?? a.startTime ?? '').trim();
    const bTime = String(b.openedTimeRaw ?? b.startTime ?? '').trim();
    if (aTime === '' && bTime !== '') return 1;
    if (aTime !== '' && bTime === '') return -1;
    const timeCompare =
      timeSortOrder === 'asc' ? aTime.localeCompare(bTime) : bTime.localeCompare(aTime);
    if (timeCompare !== 0) {
      return timeCompare;
    }
    return String(a.key).localeCompare(String(b.key));
  });
}

/**
 * 药品成组 + 可选中药按方合并。
 * @param includeTcmPrescriptions 病案首页为 true：饮片按处方号合成一行，代煎挂方下；开立 Tab 不传
 */
export function mergeMedicationAdviceRowsForListDisplay(
  rows: readonly DoctorStationAdviceRowDto[],
  timeSortOrder: AdviceListTimeSortOrder = 'desc',
  options?: { includeTcmPrescriptions?: boolean },
): AdviceRowItem[] {
  const includeTcm = options?.includeTcmPrescriptions === true;
  const tcmDtos: DoctorStationAdviceRowDto[] = [];
  const pendingNonHerb: DoctorStationAdviceRowDto[] = [];
  // 先抽出饮片，再凭方号/basedOn 认代煎，避免皮试「关联」后缀导致 companion 漏挂
  for (const dto of rows) {
    if (includeTcm && isTcmHerbAdviceDto(dto)) {
      tcmDtos.push(dto);
      continue;
    }
    pendingNonHerb.push(dto);
  }
  const herbRequestIds = new Set<string>();
  const tcmPrescriptionNos = new Set<string>();
  for (const herb of tcmDtos) {
    const rid = String(herb.requestId ?? '').trim();
    if (rid) {
      herbRequestIds.add(rid);
    }
    const rx = String(herb.prescriptionNo ?? '').trim();
    if (rx) {
      tcmPrescriptionNos.add(rx);
    }
  }
  const decoctionDtos: DoctorStationAdviceRowDto[] = [];
  const restDtos: DoctorStationAdviceRowDto[] = [];
  for (const dto of pendingNonHerb) {
    if (isDecoctionCompanionDto(dto, herbRequestIds, tcmPrescriptionNos)) {
      // 代煎挂中药方下行；开立 Tab 仍不展示 companion
      if (includeTcm) {
        decoctionDtos.push(dto);
      }
      continue;
    }
    restDtos.push(dto);
  }
  const sorted = sortDoctorStationAdviceRowsStable(restDtos, timeSortOrder);
  const westernMerged = mergeConsecutiveRowsByDrugGroup(sorted, {
    getGroupKey: medicationGroupMergeKey,
    toGroupItem: doctorStationAdviceRowDtoToDrugGroupItem,
    buildMergedRow: ({ slice, groupItems }) => {
      const members = slice.map((d) => mapAdviceListRowDto(d));
      const firstMember = members[0];
      const headDto = slice[0];
      if (firstMember === undefined || headDto === undefined) {
        throw new Error('mergeMedicationAdviceRowsForListDisplay: empty slice');
      }
      if (slice.length === 1) {
        return { ...firstMember };
      }
      const gid = String(headDto.groupId ?? '').trim();
      const idPart = slice.map((d) => String(d.requestId ?? '').trim()).join('&');
      // 并组后扩展行不能只看首行：溶媒常无滴速，加药行才有
      const groupSpeed = pickGroupSpeedFromAdviceSlice(slice);
      const extensionDto: DoctorStationAdviceRowDto = {
        ...headDto,
        speedCode: groupSpeed.speedCode ?? headDto.speedCode,
        speedName: groupSpeed.speedName ?? headDto.speedName,
      };
      return {
        ...firstMember,
        key: gid ? `merged-med-${gid}-${idPart}` : `merged-med-${idPart}`,
        lineTotalAmount: sumSliceLinePriceY(slice),
        groupItems,
        mergedAdviceRows: members,
        speedCode: groupSpeed.speedCode ?? firstMember.speedCode,
        speedName: groupSpeed.speedName ?? firstMember.speedName,
        extension: buildAdviceListExtensionLine(extensionDto) || undefined,
      };
    },
  });
  if (!includeTcm) {
    return pinNursingLevelAdviceRowsToFirst(westernMerged);
  }
  const decoctionByKey = new Map<string, DoctorStationAdviceRowDto>();
  // 代煎按处方号 / basedOn 挂到对应方
  for (const decoction of decoctionDtos) {
    for (const key of collectDecoctionMatchKeys(decoction)) {
      if (!decoctionByKey.has(key)) {
        decoctionByKey.set(key, decoction);
      }
    }
  }
  const tcmByKey = new Map<string, DoctorStationAdviceRowDto[]>();
  const sortedTcm = sortDoctorStationAdviceRowsStable(tcmDtos, 'asc');
  // 同处方号归入一方；无号则 requestId 各自成方
  for (const dto of sortedTcm) {
    const key = tcmPrescriptionMergeKey(dto) || `id:${String(dto.requestId ?? '').trim()}`;
    const bucket = tcmByKey.get(key);
    if (bucket) {
      bucket.push(dto);
    } else {
      tcmByKey.set(key, [dto]);
    }
  }
  const pendingSlices: DoctorStationAdviceRowDto[][] = [];
  const assignedDecoction: Array<DoctorStationAdviceRowDto | undefined> = [];
  // 各方按 sortNumber 排饮片，并摘走对应代煎 companion
  for (const slice of tcmByKey.values()) {
    const ordered = [...slice].toSorted((a, b) => {
      const as = Number(a.sortNumber ?? 0);
      const bs = Number(b.sortNumber ?? 0);
      if (as !== bs) {
        return as - bs;
      }
      return String(a.requestId ?? '').localeCompare(String(b.requestId ?? ''));
    });
    pendingSlices.push(ordered);
    assignedDecoction.push(takeDecoctionForHerbSlice(ordered, decoctionByKey));
  }
  // 仅一方且仍有未挂代煎：无号 companion 并入该方
  const leftoverDecoctions: DoctorStationAdviceRowDto[] = [];
  const leftoverSeen = new Set<DoctorStationAdviceRowDto>();
  // 同一 companion 可能有多个索引键，先去重
  for (const leftover of decoctionByKey.values()) {
    if (!leftoverSeen.has(leftover)) {
      leftoverSeen.add(leftover);
      leftoverDecoctions.push(leftover);
    }
  }
  if (pendingSlices.length === 1 && leftoverDecoctions.length > 0 && !assignedDecoction[0]) {
    assignedDecoction[0] = leftoverDecoctions[0];
  }
  const tcmRows: AdviceRowItem[] = [];
  // 各方合成一行：饮片竖排 + 代煎文案 + 金额加总
  for (let i = 0; i < pendingSlices.length; i += 1) {
    const ordered = pendingSlices[i];
    if (!ordered) {
      continue;
    }
    tcmRows.push(buildTcmPrescriptionListRow(ordered, assignedDecoction[i]));
  }
  const combined = interleaveAdviceRowsByOpenedTime(westernMerged, tcmRows, timeSortOrder);
  return pinNursingLevelAdviceRowsToFirst(combined);
}

// —— 旧行双击回填 ——

export interface UsageExtensionFallbacks {
  usage: string;
  frequency: string;
}

function parseUsageExtensionFromRow(
  extension: string,
  fallbacks: UsageExtensionFallbacks,
): {
  frequency: string;
  medicationDays: number;
  usage: string;
} {
  const s = String(extension || '').trim();
  const routeFallback = fallbacks.usage;
  const freqFallback = fallbacks.frequency;
  if (!s) {
    return {
      usage: routeFallback,
      frequency: freqFallback,
      medicationDays: 0,
    };
  }
  const dayMatchFull = s.match(/（(\d+)天）\s*$/);
  const dayMatchAsc = s.match(/\((\d+)天\)\s*$/);
  const dayMatch = dayMatchFull || dayMatchAsc;
  const medicationDays = dayMatch ? Math.max(1, Number(dayMatch[1]) || 1) : 0;
  const rest = dayMatch && dayMatch.index !== undefined ? s.slice(0, dayMatch.index).trim() : s;
  const idxCn = rest.indexOf('，');
  if (idxCn !== -1) {
    return {
      usage: rest.slice(0, idxCn).trim() || routeFallback,
      frequency: rest.slice(idxCn + 1).trim() || freqFallback,
      medicationDays,
    };
  }
  const commaIdx = rest.indexOf(', ');
  if (commaIdx !== -1) {
    return {
      usage: rest.slice(0, commaIdx).trim() || routeFallback,
      frequency: rest.slice(commaIdx + 2).trim() || freqFallback,
      medicationDays,
    };
  }
  return {
    usage: rest.trim() || routeFallback,
    frequency: freqFallback,
    medicationDays,
  };
}

function parseDoseText(text: string) {
  const chars = [...text.trim()];
  let numberPart = '';
  let unitPart = '';
  for (const char of chars) {
    if (!unitPart && ((char >= '0' && char <= '9') || char === '.')) {
      numberPart += char;
    } else {
      unitPart += char;
    }
  }
  return {
    value: numberPart ? Number(numberPart) : 0,
    unit: unitPart.trim(),
  };
}

export function buildFallbackAdviceDrugItemFromRow(
  row: AdviceRowItem,
  extensionFallbacks: UsageExtensionFallbacks,
): AdviceDrugItem {
  const content = row.content || '';
  const extension = row.extension || '';
  const dosageText = row.dosageText || '';
  const nameMatch = content.match(/^(.*?)【/);
  const specMatch = content.match(/【(.*?)】/);
  const pharmacyMatch = content.match(/（(.*?)）/);
  const parsedUsage = parseUsageExtensionFromRow(extension, extensionFallbacks);
  // 剂量段：`1支(2ml)/次` 时括号内为计量；无括号则整段为计量/同单位
  const doseSegment = (dosageText.split('，')[0] || '').replaceAll('/次', '').replaceAll('。', '');
  const doseInParen =
    doseSegment.match(/\(([^)]+)\)/)?.[1] || doseSegment.match(/（([^）]+)）/)?.[1];
  const singleRaw = String(doseInParen ?? doseSegment).trim();
  const totalRaw = (dosageText.split('，').find((part) => part.includes('共')) || '').replace(
    '共',
    '',
  );
  const singleParsed = parseDoseText(singleRaw);
  const totalParsed = parseDoseText(totalRaw.replace('。', ''));
  const doseQty =
    Number.isFinite(singleParsed.value) && singleParsed.value > 0 ? singleParsed.value : undefined;

  const rateFromRow = String(row.rateCode ?? '').trim();
  const tbl = String(row.productTableName ?? '').trim();
  const defId = String(row.productDefinitionId ?? '').trim();
  const selectedId = tbl && defId ? `${tbl}:${defId}` : `fallback-${row.key}`;
  const isService =
    String(row.resourceType ?? '')
      .trim()
      .toUpperCase() === 'SERVICE';
  const serviceQty = isService
    ? row.listQuantity !== undefined && row.listQuantity > 0
      ? row.listQuantity
      : totalParsed.value || undefined
    : undefined;
  const execQty = isService ? totalParsed.value : totalParsed.value;

  return {
    orderType: (row.type.split('/')[0] || '').trim(),
    selectedId,
    diagnosis: '',
    feeNature: '',
    skinTest: row.skinTestFlag === true,
    pharmacy: (pharmacyMatch?.[1] || '').trim(),
    singleDose: undefined,
    frequency: rateFromRow || parsedUsage.frequency,
    usage: parsedUsage.usage,
    medicationDays: isInpatientLongTermMedicationTherapy(row.therapyTypeCode)
      ? undefined
      : parsedUsage.medicationDays,
    serviceQuantity: serviceQty,
    totalAmount: execQty,
    selectedDrugName: nameMatch?.[1]?.trim() || content,
    selectedDrugSpec: specMatch?.[1] || '',
    selectedDrugPrice: '',
    doseMainUnit: '',
    doseSubUnit: singleParsed.unit,
    doseConvertRatio: doseQty,
    doseBandConvertK: undefined,
    totalAmountUnit: totalParsed.unit,
    totalUnitSplitCode: undefined,
    totalUnitSplitName: undefined,
    totalUnitSalesCode: undefined,
    totalUnitSalesName: undefined,
    totalAmountSplitToSalesK: undefined,
    expanded: true,
    tcmSpecialDecoction: '',
    productTableName: row.productTableName,
    sortNumber: row.sortNumber,
    groupId: String(row.groupId ?? '').trim() || undefined,
    therapyEnum: String(row.therapyTypeCode ?? '').trim() || undefined,
  };
}

export {
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
} from '#/utils/doctor-station/advice-catalog-stock-display';
