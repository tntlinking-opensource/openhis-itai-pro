import type { MedicalTechExecutionLineDto } from '#/api/medical-tech/medical-tech-execution.types';

import {
  canCancelSupplyDispense,
  canSelectSupplyForBatchExecute,
} from '#/utils/nurse-station/nurse-execute-status';

import { resolveMedicalTechSupplyStatusCode } from './medical-tech-patient-map';

/**
 * 诊疗行净已执行次数（完成 − 取消，下限 0；与后端 selectable / 次数门控同口径）。
 *
 * @param row 医技执行单诊疗行
 * @return 净已执行次数
 */
export function resolveMedicalTechNetExecutedCount(row: MedicalTechExecutionLineDto): number {
  const completed = row.completedCount ?? 0;
  const cancelled = row.cancelledCount ?? 0;
  const net = completed - cancelled;
  if (net < 0) {
    return 0;
  }
  return net;
}

/**
 * 诊疗行是否尚可批量执行（净次小于计划）。
 *
 * @param row 医技执行单诊疗行
 * @return 可执行时 true
 */
export function canExecuteMedicalTechServiceLine(row: MedicalTechExecutionLineDto): boolean {
  const planned = row.plannedExecutionCount ?? 0;
  return resolveMedicalTechNetExecutedCount(row) < planned;
}

/**
 * 诊疗行是否存在净剩可取消。
 *
 * @param row 医技执行单诊疗行
 * @return 可取消时 true
 */
export function canCancelMedicalTechServiceLine(row: MedicalTechExecutionLineDto): boolean {
  return resolveMedicalTechNetExecutedCount(row) > 0;
}

/**
 * 诊疗行勾选列是否可点：后端 selectable；缺省时按可执行或可取消回落。
 *
 * @param row 医技执行单诊疗行
 * @return 可勾选时 true
 */
export function isMedicalTechServiceRowSelectable(row: MedicalTechExecutionLineDto): boolean {
  if (row.selectable != null) {
    return row.selectable !== false;
  }
  return canExecuteMedicalTechServiceLine(row) || canCancelMedicalTechServiceLine(row);
}

/**
 * 耗材行是否可随批执行（待发放）。
 *
 * @param row 医技执行单耗材行
 * @return 可执行时 true
 */
export function canExecuteMedicalTechDeviceLine(row: MedicalTechExecutionLineDto): boolean {
  const statusCode = resolveMedicalTechSupplyStatusCode(row.dispenseStatusEnum);
  return canSelectSupplyForBatchExecute(statusCode);
}

/**
 * 耗材行是否可取消执行（已配药/已发放等）。
 *
 * @param row 医技执行单耗材行
 * @return 可取消时 true
 */
export function canCancelMedicalTechDeviceLine(row: MedicalTechExecutionLineDto): boolean {
  const statusCode = resolveMedicalTechSupplyStatusCode(row.dispenseStatusEnum);
  return canCancelSupplyDispense(statusCode);
}

/**
 * 按执行态挑选默认勾选诊疗行：优先尚可执行；全无待执行时再勾净剩可取消行。
 *
 * @param rows 本页诊疗行
 * @return 默认应勾选的行（保序）
 */
export function collectDefaultSelectedServiceLines(
  rows: MedicalTechExecutionLineDto[],
): MedicalTechExecutionLineDto[] {
  const executable: MedicalTechExecutionLineDto[] = [];
  for (const row of rows) {
    if (!isMedicalTechServiceRowSelectable(row)) {
      continue;
    }
    if (canExecuteMedicalTechServiceLine(row)) {
      executable.push(row);
    }
  }
  // 仍有待执行 → 默认勾待执行，便于批量执行
  if (executable.length > 0) {
    return executable;
  }
  const cancellable: MedicalTechExecutionLineDto[] = [];
  for (const row of rows) {
    if (!isMedicalTechServiceRowSelectable(row)) {
      continue;
    }
    if (canCancelMedicalTechServiceLine(row)) {
      cancellable.push(row);
    }
  }
  // 全部已执行满额 → 默认勾可取消行，便于取消执行
  return cancellable;
}
