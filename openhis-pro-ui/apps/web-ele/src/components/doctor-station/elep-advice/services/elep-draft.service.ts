import type { ElepCatalogItemDto } from '#/api/doctor-station/elep-catalog.types';
import type {
  ElepDiagnosisForm,
  ElepDrugFormLine,
  ElepHistoryDetailDto,
} from '#/api/doctor-station/elep.types';

import dayjs from 'dayjs';

export interface ElepOpenForm {
  prescriptionId?: string;
  diseCodg: string;
  diseName: string;
  valiDays: string;
  diagnosis: ElepDiagnosisForm;
  drugLines: ElepDrugFormLine[];
}

const ELEP_TIME_FMT = 'YYYY-MM-DD HH:mm:ss';

export type ElepDrugLinePatchField =
  | 'drugCnt'
  | 'drugDosunt'
  | 'medcBegntime'
  | 'medcDays'
  | 'medcEndtime'
  | 'medcWayDscr'
  | 'sinDoscnt'
  | 'sinDosunt'
  | 'usedFrquName';

/**
 * 当前开方时刻，格式对齐 3-03。
 */
export function nowElepDateTime(): string {
  return dayjs().format(ELEP_TIME_FMT);
}

/**
 * 开始时间加用药天数得到结束时间。
 *
 * @param start 开始时间
 * @param days 用药天数
 */
export function addElepDays(start: string, days: string): string {
  const parsed = Number.parseInt(days, 10);
  const safeDays = Number.isFinite(parsed) && parsed > 0 ? parsed : 3;
  const base = start.trim() ? dayjs(start) : dayjs();
  if (!base.isValid()) {
    return dayjs().add(safeDays, 'day').format(ELEP_TIME_FMT);
  }
  return base.add(safeDays, 'day').format(ELEP_TIME_FMT);
}

/**
 * 新建本页空白开方表（尚未落库）。
 */
export function createEmptyElepOpenForm(): ElepOpenForm {
  return {
    diseCodg: '',
    diseName: '',
    valiDays: '3',
    diagnosis: {},
    drugLines: [],
  };
}

/**
 * 从详情回填本页表单。
 *
 * @param detail 后端详情
 */
export function formFromElepDetail(detail: ElepHistoryDetailDto): ElepOpenForm {
  return {
    prescriptionId: detail.prescriptionId,
    diseCodg: detail.diseCodg ?? '',
    diseName: detail.diseName ?? '',
    valiDays: detail.valiDays?.trim() || '3',
    diagnosis: {
      diagCode: detail.diagnosis?.diagCode,
      diagName: detail.diagnosis?.diagName ?? detail.mainDiagName,
    },
    drugLines: (detail.drugLines ?? []).map((line, index) => ({
      ...line,
      lineNo: line.lineNo ?? index + 1,
    })),
  };
}

/**
 * 目录项转药品行（用法、单次用量、服药时间由本页再填；起止默认开方时刻+天数）。
 *
 * @param item 1301 目录行
 * @param valiDays 方头有效天数，用作默认用药天数
 */
export function drugCatalogItemToFormLine(
  item: ElepCatalogItemDto,
  valiDays?: string,
): ElepDrugFormLine {
  const medcDays = valiDays?.trim() || '3';
  const medcBegntime = nowElepDateTime();
  return {
    medListCodg: item.hilistCode,
    drugGenname: item.hilistName,
    drugName: item.hilistName,
    drugSpec: item.spec,
    drugDosform: item.dosform,
    prdrName: item.prodentpName,
    drugCnt: '1',
    drugDosunt: '盒',
    medcDays,
    medcBegntime,
    medcEndtime: addElepDays(medcBegntime, medcDays),
  };
}

/**
 * 追加一行医保药品。
 *
 * @param form 本页表单
 * @param item 目录项
 */
export function appendDrugLine(form: ElepOpenForm, item: ElepCatalogItemDto): ElepOpenForm {
  const nextLines = [...form.drugLines, drugCatalogItemToFormLine(item, form.valiDays)];
  return {
    ...form,
    drugLines: nextLines.map((line, index) => ({ ...line, lineNo: index + 1 })),
  };
}

/**
 * 写入本页主诊断（不写就诊诊断表）。
 *
 * @param form 本页表单
 * @param item 1307 目录项
 */
export function applyDiagnosis(form: ElepOpenForm, item: ElepCatalogItemDto): ElepOpenForm {
  return {
    ...form,
    diagnosis: {
      diagCode: item.hilistCode,
      diagName: item.hilistName,
    },
  };
}

/**
 * 写入本页慢病病种；清空则按普通门诊上传。
 *
 * @param form 本页表单
 * @param item 1307 目录项；空则清病种
 */
export function applyDisease(form: ElepOpenForm, item?: ElepCatalogItemDto | null): ElepOpenForm {
  return {
    ...form,
    diseCodg: item?.hilistCode?.trim() || '',
    diseName: item?.hilistName?.trim() || '',
  };
}

/**
 * 按行号删除药品。
 *
 * @param form 本页表单
 * @param lineNo 1-based 行号
 */
export function removeDrugLine(form: ElepOpenForm, lineNo: number): ElepOpenForm {
  const nextLines = form.drugLines.filter((_, index) => index + 1 !== lineNo);
  return {
    ...form,
    drugLines: nextLines.map((line, index) => ({ ...line, lineNo: index + 1 })),
  };
}

/**
 * 改药品行字段；用法/频次同步院内码，改天数或开始时间则重算结束时间。
 *
 * @param line 当前行
 * @param field 字段
 * @param value 输入值
 */
export function applyDrugLinePatch(
  line: ElepDrugFormLine,
  field: ElepDrugLinePatchField,
  value: string,
): ElepDrugFormLine {
  const next: ElepDrugFormLine = { ...line, [field]: value };
  // 3-03 允许院内内部途径代码，描述即码
  if (field === 'medcWayDscr') {
    next.medcWayCodg = value.trim();
  }
  // 频次同口径：名称即编码
  if (field === 'usedFrquName') {
    next.usedFrquCodg = value.trim();
  }
  // 改天数或开始时间时，结束时间按开始+天数重算
  if (field === 'medcDays' || field === 'medcBegntime') {
    next.medcEndtime = addElepDays(next.medcBegntime ?? '', next.medcDays ?? '');
  }
  return next;
}

/**
 * 3-03 西药行医生必填项缺哪项。
 *
 * @param line 药品行
 * @param lineNo 行号
 */
export function resolveElepDrugLineError(line: ElepDrugFormLine, lineNo: number): string {
  const prefix = `第${lineNo}行药品`;
  if (!line.drugCnt?.trim()) return `${prefix}请填写总量`;
  if (!line.drugDosunt?.trim()) return `${prefix}请填写总量单位`;
  if (!line.medcWayDscr?.trim()) return `${prefix}请填写用法`;
  if (!line.usedFrquName?.trim()) return `${prefix}请填写频次`;
  if (!line.sinDoscnt?.trim()) return `${prefix}请填写单次用量`;
  if (!line.sinDosunt?.trim()) return `${prefix}请填写单次单位`;
  if (!line.medcDays?.trim()) return `${prefix}请填写用药天数`;
  if (!line.medcBegntime?.trim()) return `${prefix}请填写服药开始时间`;
  if (!line.medcEndtime?.trim()) return `${prefix}请填写服药结束时间`;
  return '';
}

/**
 * 诊断与药品是否达到保存门槛。
 *
 * @param form 本页表单
 */
export function canSubmitElepDraft(form: ElepOpenForm): boolean {
  const diagCode = form.diagnosis.diagCode?.trim();
  return Boolean(diagCode) && form.drugLines.length > 0;
}
