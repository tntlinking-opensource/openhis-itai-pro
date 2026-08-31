import { ElMessage } from 'element-plus';

import { loadGuidePrintSlipApi } from '#/api/peis/personal-registration';

import { print, PRINT_SCENARIO } from './runtime';

export interface PrintPeisGuideOptions {
  encounterId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框 */
  background?: boolean;
}

/**
 * 拉取后端体检指引单 slip 并静默打印。
 */
export async function printPeisGuide(options: PrintPeisGuideOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法打印指引单');
    return { ok: false as const };
  }

  let slip;
  try {
    slip = await loadGuidePrintSlipApi({ encounterId });
  } catch (error) {
    const message = error instanceof Error ? error.message : '获取指引单打印数据失败';
    ElMessage.error(message);
    return { ok: false as const, errorMessage: message };
  }

  if (!slip) {
    ElMessage.warning('未获取到指引单打印数据');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.peisGuidePrint,
    source: slip,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.peisGuidePrint,
      encounterId: slip.encounterId ?? encounterId,
    },
    jobName: options.jobName ?? '体检指引单',
    selectPrinter: options.background ? 'never' : 'auto',
    background: options.background === true,
  });
}
