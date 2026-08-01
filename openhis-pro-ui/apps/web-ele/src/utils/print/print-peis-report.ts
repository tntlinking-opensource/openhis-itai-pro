import { ElMessage } from 'element-plus';

import { httpConfirmPrint, httpLoadReportPrintSlip } from '#/api/peis/report-print';

import { print, PRINT_SCENARIO } from './runtime';

export interface PrintPeisReportOptions {
  encounterId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框 */
  background?: boolean;
  /** 是否提示业务 confirmPrint 成功消息（合打批量时关闭） */
  notifyOnBizConfirm?: boolean;
}

/**
 * 拉取后端体检报告 slip 并静默打印（prepare → hiprint → printJob/confirmPrint），
 * 成功后记录业务侧打印事件。
 */
export async function printPeisReport(options: PrintPeisReportOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法打印体检报告');
    return { ok: false as const };
  }

  let slip;
  try {
    slip = await httpLoadReportPrintSlip({ encounterId });
  } catch (error) {
    const message = error instanceof Error ? error.message : '获取体检报告打印数据失败';
    ElMessage.error(message);
    return { ok: false as const, errorMessage: message };
  }

  if (!slip) {
    ElMessage.warning('未获取到体检报告打印数据');
    return { ok: false as const };
  }

  const printResult = await print({
    scenarioCode: PRINT_SCENARIO.peisReportPrint,
    source: slip,
    bizRef: {
      encounterId: slip.encounterId ?? encounterId,
      diagnosticReportId: slip.diagnosticReportId,
    },
    jobName: options.jobName ?? '体检总检报告',
    selectPrinter: options.background ? 'never' : 'auto',
    background: options.background === true,
  });

  if (printResult.ok) {
    try {
      const confirmRes = await httpConfirmPrint({ encounterId });
      if (options.notifyOnBizConfirm !== false && confirmRes.message) {
        ElMessage.success(confirmRes.message);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '业务打印事件记录失败';
      if (options.notifyOnBizConfirm !== false) {
        ElMessage.warning(message);
      }
    }
  }

  return printResult;
}
