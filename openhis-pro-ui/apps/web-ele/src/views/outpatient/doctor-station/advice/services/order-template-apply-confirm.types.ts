import type { DoctorStationAdviceDraftLineRequest } from '#/api/doctor-station/advice.types';

/** 组套套用确认弹窗：组套行类型 */
export type OrderTemplateApplyConfirmLineKind = 'DEVICE' | 'MEDICATION' | 'SERVICE';

/** 组套套用确认弹窗：组套行（可编辑数量 / 删除） */
export interface OrderTemplateApplyConfirmTemplateRow {
  key: string;
  /** 对应 preparedDraftLines 下标，用于删行后重建暂存批次 */
  lineIndex: number;
  lineKind: OrderTemplateApplyConfirmLineKind;
  itemName: string;
  kindLabel: string;
  /**
   * 可编辑数量。
   * <p>诊疗（SERVICE）= 单次执行数量；确认落库时再 × `executionCount`（BR-ORD-036）。
   * 药/材 = 开立总量。</p>
   */
  quantity: number;
  unitName: string;
  /** 诊疗行展示「单次数量 × 执行次数」摘要；药/材可选剂量文案 */
  dosageText?: string;
}

/** 组套套用确认弹窗打开参数 */
export interface OrderTemplateApplyConfirmDialogPayload {
  templateName: string;
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
}

/** 组套套用确认弹窗确认结果 */
export interface OrderTemplateApplyConfirmDialogResult {
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
}

/** prepareOrderTemplateApplyConfirm 产出：供弹窗展示与确认后套用 */
export interface OrderTemplateApplyConfirmPreview {
  templateName: string;
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
  draftLines: DoctorStationAdviceDraftLineRequest[];
}
