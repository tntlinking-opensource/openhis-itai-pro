import type {
  NurseStationOrderAuditOrderType,
  NurseStationOrderAuditRowDto,
  NurseStationOrderAuditWriteItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.types';

import { formatDateTime } from '@vben/utils';

import {
  mergeConsecutiveRowsByDrugGroup,
  type DrugGroupDisplayItem,
  type DrugGroupKey,
} from '#/utils/doctor-station/drug-group-table';
import { formatLongTermMedicationDoseLine } from '#/utils/doctor-station/medication-dose-display';
import {
  buildMedicationContentTitleWithPackageSplit,
  mergePackageSplitIntoSpec,
} from '#/utils/doctor-station/package-split-ratio-display';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { SERVICE_CATEGORY_DISCHARGE } from '#/utils/print/constants/service-request-category-codes';

/** 表格行：与 AdviceOrderDisplayCell 及校对页列展示对齐 */
export interface NurseStationOrderAuditTableRow {
  id: string;
  requestId: string;
  orderType: NurseStationOrderAuditOrderType;
  encounterId: string;
  /** 诊疗类目 code（项目类型过滤：检验/检查/治疗/嘱托） */
  categoryEnum?: string;
  /** 停嘱时间原文案（halt 判定；有停嘱时非空） */
  stopTime: string;
  /** 开立时间展示（时间后跟开立人，BR-IP-ORD-031） */
  executeTime: string;
  /** 停嘱时间+停嘱医生合列展示；无停嘱为 — */
  stopTimeDisplay: string;
  /** 开立医生 */
  doctorName: string;
  typeLabel: string;
  /** 类型列文案（后端 orderKindDisplay，与 Tab 无关） */
  orderKindLabel: string;
  /** 长临 code（LONG_TERM / TEMPORARY） */
  therapyTypeCode?: string;
  /** 长临展示文案 */
  therapyLabel?: string;
  content: string;
  dosage: string;
  extension?: string;
  /** 校对人姓名（非未校对 Tab 展示） */
  performerCheckName: string;
  /** 停嘱校对人姓名 */
  stopCheckerName?: string;
  /** 是否停嘱待校（待校对列表醒目标识） */
  stopAuditPending?: boolean;
  halted?: boolean;
  statusEnum?: string;
  tcmFlag?: boolean;
  prescriptionNo?: string;
  lineRequestIds?: string[];
  /** 药品成组号（与医生站 group_id 同源） */
  groupId?: string;
  /** 成组括线展示项（同 groupId 合并后） */
  groupItems?: DrugGroupDisplayItem[];
  /** 成组合并前的成员行（写路径可展开；后端亦按 groupId 扩行） */
  mergedMedicationMembers?: NurseStationOrderAuditTableRow[];
  /** 是否皮试药（true 时医嘱内容旁展示「皮试」标记） */
  skinTestFlag?: boolean;
  /** 皮试临床状态 code（如 active=阳性） */
  skinTestOutcomeCode?: string;
  /** 皮试临床状态展示文案（如 阳性 / 阴性） */
  skinTestOutcomeText?: string;
}

/** 解析写操作锚点 orderType（禁止空值默认成药品） */
function resolveAuditOrderType(orderTypeRaw: string): NurseStationOrderAuditOrderType | null {
  const t = orderTypeRaw.trim().toUpperCase();
  if (t === 'MED_REQUEST') return 'MED_REQUEST';
  if (t === 'SERVICE_REQUEST') return 'SERVICE_REQUEST';
  return null;
}

/** 类型列：优先读后端 orderKindDisplay，回落本地推导（药品细化为西药/中药） */
export function resolveAuditOrderKindLabel(dto: NurseStationOrderAuditRowDto): string {
  const fromApi = String(dto.orderKindDisplay ?? '').trim();
  if (fromApi === '药品') {
    return dto.tcmFlag === true ? '中药' : '西药';
  }
  if (fromApi) return fromApi;
  const category = String(dto.categoryEnum ?? '').trim();
  if (category === '34') return '出院';
  if (category === '32') return '转科';
  if (category === '26') return '护理';
  // 嘱托类目 52：禁止回落「诊疗」（BR-IP-ORD-028）
  if (category === '52') return '嘱托';
  const orderType = resolveAuditOrderType(String(dto.orderType ?? ''));
  if (orderType === 'MED_REQUEST') {
    return dto.tcmFlag === true ? '中药' : '西药';
  }
  if (orderType === 'SERVICE_REQUEST') return '诊疗';
  return '—';
}

/** 长临 code → 短文案 */
function therapyEnumToLabel(code: string): string {
  const c = code.trim().toUpperCase();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  if (c === 'DISCHARGE_MEDICATION') return '出院带药';
  return '';
}

/** 列表长临 code */
function resolveAuditTherapyTypeCode(dto: NurseStationOrderAuditRowDto): string {
  return String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
}

/** 列表长临文案：优先 therapyDisplay，回落 therapyEnum */
function resolveAuditTherapyLabel(dto: NurseStationOrderAuditRowDto): string {
  const fromApi = String(dto.therapyDisplay ?? '').trim();
  if (fromApi) return fromApi;
  return therapyEnumToLabel(String(dto.therapyEnum ?? ''));
}

/** 组装列表「类型」列：长临 + 类型 */
function buildAuditTypeLabel(dto: NurseStationOrderAuditRowDto): string {
  const therapy = resolveAuditTherapyLabel(dto);
  const kind = resolveAuditOrderKindLabel(dto);
  if (therapy && kind && kind !== '—') return `${therapy}/${kind}`;
  return therapy || kind || '—';
}

/** 医嘱名称摘要：名称【5mg1片x100】；皮试结果由单元格内联角标展示 */
function buildAuditContentTitle(dto: NurseStationOrderAuditRowDto): string {
  if (dto.tcmFlag === true) {
    const collapsedName = String(dto.adviceDisplayName ?? '').trim();
    if (collapsedName) return collapsedName;
  }
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  const title = buildMedicationContentTitleWithPackageSplit(name, spec, dto.packageSplitRatioText);
  return title || '—';
}

/** 是否护理级别医嘱（categoryEnum=26；列表只展示级别名称，不展示频次） */
function isNursingLevelAuditDto(dto: Pick<NurseStationOrderAuditRowDto, 'categoryEnum'>): boolean {
  return String(dto.categoryEnum ?? '').trim() === '26';
}

/** 剂量/数量：长期「一次一片，每日三片，首日…」；中药方展示各味单次量 + 付数 */
function buildAuditDosageDisplay(dto: NurseStationOrderAuditRowDto): string {
  // 护理级别无剂量/频次展示，内容列只留级别名称
  if (isNursingLevelAuditDto(dto)) {
    return '';
  }
  if (dto.tcmFlag === true) {
    const singleDose = String(dto.dosageDisplay ?? '').trim();
    const fuCount = dto.chineseHerbsDoseQuantity;
    const fuText = fuCount != null && String(fuCount).trim() ? `${String(fuCount).trim()}付` : '';
    if (singleDose && fuText) {
      return `${singleDose} ${fuText}`;
    }
    return singleDose || fuText || '—';
  }
  // 长期药：单次量 + 日总量 + 可选首日用量（频次在用法行）
  const therapy = String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
  if (therapy === 'LONG_TERM') {
    const longTermLine = formatLongTermMedicationDoseLine({
      dosageDisplay: dto.dosageDisplay,
      dailyDoseDisplay: dto.dailyDoseDisplay,
      firstDose: dto.firstDose,
      firstDoseUnitCode: dto.firstDoseUnitCode,
      firstDoseUnitName: dto.firstDoseUnitName,
    });
    if (longTermLine) return longTermLine;
  }
  return String(dto.dosageDisplay ?? '').trim() || '—';
}

function mapAuditLineRequestIds(
  ids: NurseStationOrderAuditRowDto['lineRequestIds'],
): string[] | undefined {
  if (!ids?.length) return undefined;
  const mapped = ids
    .map((id) => preserveApiIdForRequest(id) ?? String(id ?? '').trim())
    .filter((id) => id.length > 0);
  return mapped.length > 0 ? mapped : undefined;
}

/** 用法、频次、滴速、备注拼成扩展行；护理级别不拼用法频次；出院行拼计划出院时间 */
function buildAuditExtensionLine(dto: NurseStationOrderAuditRowDto): string {
  const parts: string[] = [];
  // 出院医嘱：扩展行优先展示医生填写的计划出院时间与原因（BR-IP-AUD-006）
  const dischargeParts = buildDischargeAuditExtensionParts(dto);
  for (const part of dischargeParts) {
    parts.push(part);
  }
  const usage = String(dto.usageDisplay ?? '').trim();
  const freq = String(dto.frequencyDisplay ?? '').trim();
  const speed = String(dto.speedDisplay ?? '').trim();
  // 护理级别无频次展示，列表仅展示级别名称
  if (!isNursingLevelAuditDto(dto)) {
    if (usage && freq) {
      parts.push(`${usage}，${freq}`);
    } else if (usage || freq) {
      parts.push(usage || freq);
    }
    if (speed) {
      parts.push(`滴速：${speed}`);
    }
  }
  const remark = String(dto.remark ?? '').trim();
  if (remark) {
    parts.push(`备注：${remark}`);
  }
  return parts.join('；');
}

/**
 * 出院医嘱校对列表扩展片段：计划出院时间、原因。
 *
 * @param dto 校对列表行
 * @returns 非空片段；非出院行返回空数组
 */
function buildDischargeAuditExtensionParts(dto: NurseStationOrderAuditRowDto): string[] {
  if (String(dto.categoryEnum ?? '').trim() !== SERVICE_CATEGORY_DISCHARGE) {
    return [];
  }
  const parts: string[] = [];
  const plannedRaw = String(dto.plannedDischargeTime ?? '').trim();
  if (plannedRaw) {
    const plannedDisplay = formatAuditTimeMinute(plannedRaw) || plannedRaw;
    parts.push(`出院时间：${plannedDisplay}`);
  }
  const reason = String(dto.reasonText ?? '').trim();
  if (reason) {
    parts.push(`原因：${reason}`);
  }
  return parts;
}

/**
 * 校对列表时间展示到分钟（YYYY-MM-DD HH:mm）。
 *
 * @param raw 后端时间串
 * @returns 截到分钟的展示；无法解析时回落 formatDateTime
 */
function formatAuditTimeMinute(raw: string): string {
  const full = formatDateTime(raw);
  const matched = String(full ?? '').match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2})/);
  if (matched) {
    return matched[1]!;
  }
  return String(full ?? '').trim();
}

/** 列表行 DTO → 表格行（未成组合并） */
export function mapAuditRowDtoToTableRow(
  dto: NurseStationOrderAuditRowDto,
): NurseStationOrderAuditTableRow | null {
  const requestId = preserveApiIdForRequest(dto.requestId) ?? '';
  const orderType = resolveAuditOrderType(String(dto.orderType ?? ''));
  if (!orderType) {
    return null;
  }
  const listKey = String(dto.listRowKey ?? '').trim();
  const id =
    listKey ||
    (requestId && orderType
      ? `${requestId}\u0001${orderType}`
      : `audit-${requestId || Date.now()}`);
  const openedRaw = String(dto.reqOpenedTime ?? '').trim();
  const stopRaw = String(dto.stopTime ?? '').trim();
  const doctorName = String(dto.requesterName ?? '').trim() || '—';
  const stopperName = String(dto.stopperName ?? '').trim();
  const openedText = openedRaw ? formatDateTime(openedRaw) : '';
  const stopText = stopRaw ? formatDateTime(stopRaw) : '';
  // 开立时间后跟开立人；停嘱时间后跟停嘱人合列（BR-IP-ORD-031）
  const executeTime =
    openedText && doctorName !== '—' ? `${openedText} ${doctorName}` : openedText || '—';
  const stopTimeDisplay = stopText ? (stopperName ? `${stopText} ${stopperName}` : stopText) : '—';
  const statusEnum = String(dto.statusEnum ?? '')
    .trim()
    .toUpperCase();
  const orderKindLabel = resolveAuditOrderKindLabel(dto);
  const groupId = String(dto.groupId ?? dto.groupNo ?? '').trim() || undefined;

  return {
    id,
    requestId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? '',
    categoryEnum: String(dto.categoryEnum ?? '').trim() || undefined,
    stopTime: stopText,
    stopTimeDisplay,
    doctorName,
    typeLabel: buildAuditTypeLabel(dto),
    orderKindLabel,
    therapyTypeCode: resolveAuditTherapyTypeCode(dto) || undefined,
    therapyLabel: resolveAuditTherapyLabel(dto) || undefined,
    content: buildAuditContentTitle(dto),
    dosage: buildAuditDosageDisplay(dto),
    extension: buildAuditExtensionLine(dto) || undefined,
    performerCheckName: String(dto.performerCheckName ?? '').trim() || '—',
    stopCheckerName: String(dto.stopCheckerName ?? '').trim() || undefined,
    stopAuditPending: dto.stopAuditPending === true,
    executeTime,
    halted: statusEnum === 'STOPPED',
    statusEnum,
    tcmFlag: dto.tcmFlag === true ? true : undefined,
    prescriptionNo: String(dto.prescriptionNo ?? '').trim() || undefined,
    lineRequestIds: mapAuditLineRequestIds(dto.lineRequestIds),
    groupId,
    skinTestFlag: dto.skinTestFlag === true ? true : undefined,
    skinTestOutcomeCode: String(dto.skinTestOutcomeCode ?? '').trim() || undefined,
    skinTestOutcomeText: String(dto.skinTestOutcomeText ?? '').trim() || undefined,
  };
}

/** 成组括线展示项：对齐门诊护士站 / 医生站 DrugGroupDisplayItem */
function auditDtoToDrugGroupItem(dto: NurseStationOrderAuditRowDto): DrugGroupDisplayItem {
  const therapy = String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
  const longTermDoseLine =
    therapy === 'LONG_TERM'
      ? formatLongTermMedicationDoseLine({
          dosageDisplay: dto.dosageDisplay,
          dailyDoseDisplay: dto.dailyDoseDisplay,
          firstDose: dto.firstDose,
          firstDoseUnitCode: dto.firstDoseUnitCode,
          firstDoseUnitName: dto.firstDoseUnitName,
        })
      : '';
  const doseText = longTermDoseLine || String(dto.dosageDisplay ?? '').trim();
  return {
    selectedDrugName: String(dto.adviceDisplayName ?? '').trim(),
    selectedDrugSpec: mergePackageSplitIntoSpec(
      String(dto.specificationText ?? '').trim(),
      dto.packageSplitRatioText,
    ),
    skinTestOutcomeCode: String(dto.skinTestOutcomeCode ?? '').trim() || undefined,
    skinTestOutcomeText: String(dto.skinTestOutcomeText ?? '').trim() || undefined,
    pharmacy: '',
    singleDose: doseText,
    doseSubUnit: '',
    totalAmount: '',
    totalAmountUnit: '',
    usage: String(dto.usageDisplay ?? '').trim(),
    frequency: String(dto.frequencyDisplay ?? '').trim(),
    medicationDays: '',
    dosageDisplay: doseText || undefined,
    speedDisplay:
      String(dto.speedDisplay ?? '').trim() || String(dto.speedCode ?? '').trim() || undefined,
    remark: String(dto.remark ?? '').trim() || undefined,
  };
}

/** 西药成组合并键：仅药嘱且非中药方折叠行 */
function auditMedicationGroupMergeKey(dto: NurseStationOrderAuditRowDto): DrugGroupKey {
  const orderType = resolveAuditOrderType(String(dto.orderType ?? ''));
  if (orderType !== 'MED_REQUEST') {
    return '';
  }
  if (dto.tcmFlag === true) {
    return '';
  }
  const gid = String(dto.groupId ?? dto.groupNo ?? '').trim();
  return gid === '' ? '' : gid;
}

/** 成组同滴速：取组内首条非空 speedDisplay/speedCode（溶媒首行常为空） */
function pickGroupSpeedFromAuditSlice(slice: readonly NurseStationOrderAuditRowDto[]): {
  speedCode?: string;
  speedDisplay?: string;
} {
  for (const dto of slice) {
    const speedDisplay = String(dto.speedDisplay ?? '').trim();
    const speedCode = String(dto.speedCode ?? '').trim();
    if (speedDisplay || speedCode) {
      return {
        speedCode: speedCode || undefined,
        speedDisplay: speedDisplay || undefined,
      };
    }
  }
  return {};
}

/**
 * 相同 groupId 且连续的西药行合并为一行，挂 groupItems 供括线展示（与门诊护士站一致）。
 * 中药方已由后端按 prescription_no 折叠，此处不再二次合并。
 */
function mergeAuditMedicationRowsForDisplay(
  rows: NurseStationOrderAuditRowDto[],
): NurseStationOrderAuditTableRow[] {
  return mergeConsecutiveRowsByDrugGroup(rows, {
    getGroupKey: auditMedicationGroupMergeKey,
    toGroupItem: auditDtoToDrugGroupItem,
    buildMergedRow: ({ slice, groupItems }) => {
      const members = slice
        .map((dto) => mapAuditRowDtoToTableRow(dto))
        .filter((row): row is NurseStationOrderAuditTableRow => row != null);
      const firstMember = members[0];
      const headDto = slice[0];
      if (firstMember === undefined || headDto === undefined) {
        throw new Error('mergeAuditMedicationRowsForDisplay: empty slice');
      }
      if (members.length === 1) {
        return { ...firstMember };
      }
      // 成组写路径：代表行 requestId + 全员 lineRequestIds（后端亦按 groupId 扩行）
      const lineRequestIds = members.map((m) => m.requestId).filter((id) => id.length > 0);
      const gid = String(headDto.groupId ?? headDto.groupNo ?? '').trim();
      const idPart = lineRequestIds.join('&');
      // 成组任一行需皮试则合并行展示皮试标记；有登记结果时优先展示结果文案
      const skinTestFlag = members.some((m) => m.skinTestFlag === true) ? true : undefined;
      const skinTestOutcomeMember = members.find((m) => String(m.skinTestOutcomeText ?? '').trim());
      const skinTestOutcomeCode = skinTestOutcomeMember?.skinTestOutcomeCode;
      const skinTestOutcomeText = skinTestOutcomeMember?.skinTestOutcomeText;
      const groupSpeed = pickGroupSpeedFromAuditSlice(slice);
      const extensionDto: NurseStationOrderAuditRowDto = {
        ...headDto,
        speedCode: groupSpeed.speedCode ?? headDto.speedCode,
        speedDisplay: groupSpeed.speedDisplay ?? headDto.speedDisplay,
      };
      return {
        ...firstMember,
        id: gid ? `merged-med-${gid}-${idPart}` : `merged-med-${idPart}`,
        groupItems,
        mergedMedicationMembers: members,
        lineRequestIds,
        skinTestFlag,
        skinTestOutcomeCode,
        skinTestOutcomeText,
        extension: buildAuditExtensionLine(extensionDto) || undefined,
      };
    },
  });
}

/** 选中表格行 → 写请求 items（成组已展开为各成员 requestId；后端仍会再按 groupId 兜底扩行） */
export function mapTableRowsToWriteItems(
  rows: NurseStationOrderAuditTableRow[],
): NurseStationOrderAuditWriteItemDto[] {
  const items: NurseStationOrderAuditWriteItemDto[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    // 成组合并行：展开全部成员；否则单行 requestId（中药方由后端按处方号扩）
    const memberIds =
      row.mergedMedicationMembers?.map((m) => m.requestId).filter((id) => id.length > 0) ??
      (row.requestId ? [row.requestId] : []);
    for (const requestIdRaw of memberIds) {
      const requestId = preserveApiIdForRequest(requestIdRaw);
      if (!requestId) continue;
      const dedupeKey = `${row.orderType}:${requestId}`;
      if (seen.has(dedupeKey)) continue;
      seen.add(dedupeKey);
      items.push({
        orderType: row.orderType,
        requestId,
      });
    }
  }
  return items;
}

/** 接口分组列表 → encounterId → 表格行（含西药成组合并） */
export function indexAuditGroupsByEncounterId(
  groups: { encounterId?: string; orders?: NurseStationOrderAuditRowDto[] }[] | undefined,
): Map<string, NurseStationOrderAuditTableRow[]> {
  const map = new Map<string, NurseStationOrderAuditTableRow[]>();
  for (const group of groups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId);
    if (!encounterId) continue;
    const rawRows = (group.orders ?? []).filter((dto) => dto != null);
    map.set(encounterId, mergeAuditMedicationRowsForDisplay(rawRows));
  }
  return map;
}

/**
 * 校对「项目类型」多选（code 驱动，禁止用展示文案）。
 * 检验=22、检查=23、治疗=21、嘱托=52、护理=26、转科=32、出院=34。
 * 特殊诊疗须可校对（BR-IP-AUD-006/011；护理级别 BR-IP-EXE-027 仅禁执行不禁校对）。
 */
export type NurseStationOrderAuditProjectType =
  | 'discharge'
  | 'entrust'
  | 'exam'
  | 'lab'
  | 'nursing'
  | 'tcm'
  | 'transfer'
  | 'treatment'
  | 'west';

/** 项目类型全量（空选时按全选处理） */
export const NURSE_STATION_ORDER_AUDIT_PROJECT_TYPES: NurseStationOrderAuditProjectType[] = [
  'west',
  'tcm',
  'lab',
  'exam',
  'treatment',
  'entrust',
  'nursing',
  'transfer',
  'discharge',
];

/** 行归属的项目类型（用 orderType / tcmFlag / categoryEnum，禁止用展示文案） */
export function resolveAuditRowProjectType(
  row: Pick<NurseStationOrderAuditTableRow, 'categoryEnum' | 'orderType' | 'tcmFlag'>,
): NurseStationOrderAuditProjectType | null {
  if (row.tcmFlag === true) {
    return 'tcm';
  }
  if (row.orderType === 'MED_REQUEST') {
    return 'west';
  }
  const category = String(row.categoryEnum ?? '').trim();
  if (category === '22') {
    return 'lab';
  }
  if (category === '23') {
    return 'exam';
  }
  if (category === '21') {
    return 'treatment';
  }
  if (category === '52') {
    return 'entrust';
  }
  if (category === '26') {
    return 'nursing';
  }
  if (category === '32') {
    return 'transfer';
  }
  if (category === '34') {
    return 'discharge';
  }
  // 其余诊疗行归入治疗，避免 west Tab 普通诊疗被二次过滤抹掉
  if (row.orderType === 'SERVICE_REQUEST') {
    return 'treatment';
  }
  return null;
}

/** 按项目类型多选过滤表格行；selected 为空视为全选 */
export function filterAuditRowsByProjectTypes(
  rows: NurseStationOrderAuditTableRow[],
  selected: readonly NurseStationOrderAuditProjectType[],
): NurseStationOrderAuditTableRow[] {
  const effective = selected.length > 0 ? selected : NURSE_STATION_ORDER_AUDIT_PROJECT_TYPES;
  const allowed = new Set(effective);
  return rows.filter((row) => {
    const projectType = resolveAuditRowProjectType(row);
    return projectType !== null && allowed.has(projectType);
  });
}

/** 索引后按项目类型过滤各就诊下的行 */
export function filterAuditIndexByProjectTypes(
  index: Map<string, NurseStationOrderAuditTableRow[]>,
  selected: readonly NurseStationOrderAuditProjectType[],
): Map<string, NurseStationOrderAuditTableRow[]> {
  const next = new Map<string, NurseStationOrderAuditTableRow[]>();
  for (const [encounterId, rows] of index) {
    next.set(encounterId, filterAuditRowsByProjectTypes(rows, selected));
  }
  return next;
}

/** 合并多次 list 的就诊分组（按 encounterId 拼订单，行按 listRowKey/requestId+orderType 去重） */
export function mergeAuditPatientGroups(
  groupLists: Array<
    undefined | { encounterId?: string; orders?: NurseStationOrderAuditRowDto[] }[]
  >,
): { encounterId?: string; orders?: NurseStationOrderAuditRowDto[] }[] {
  const ordersByEncounter = new Map<string, NurseStationOrderAuditRowDto[]>();
  const seenRowKeyByEncounter = new Map<string, Set<string>>();

  for (const groups of groupLists) {
    for (const group of groups ?? []) {
      const encounterId = preserveApiIdForRequest(group.encounterId);
      if (!encounterId) continue;
      let orders = ordersByEncounter.get(encounterId);
      if (!orders) {
        orders = [];
        ordersByEncounter.set(encounterId, orders);
        seenRowKeyByEncounter.set(encounterId, new Set());
      }
      const seen = seenRowKeyByEncounter.get(encounterId)!;
      for (const dto of group.orders ?? []) {
        if (!dto) continue;
        const requestId = preserveApiIdForRequest(dto.requestId) ?? '';
        const orderType = String(dto.orderType ?? '')
          .trim()
          .toUpperCase();
        const listKey = String(dto.listRowKey ?? '').trim();
        const dedupeKey = listKey || `${requestId}\u0001${orderType}`;
        if (seen.has(dedupeKey)) continue;
        seen.add(dedupeKey);
        orders.push(dto);
      }
    }
  }

  const merged: { encounterId?: string; orders?: NurseStationOrderAuditRowDto[] }[] = [];
  for (const [encounterId, orders] of ordersByEncounter) {
    merged.push({ encounterId, orders });
  }
  return merged;
}
