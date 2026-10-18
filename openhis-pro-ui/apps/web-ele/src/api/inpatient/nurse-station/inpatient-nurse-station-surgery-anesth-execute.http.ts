import type {
  LoadSurgeryAnesthExecuteDialogRequest,
  SubmitSurgeryAnesthExecuteRequest,
  SubmitSurgeryAnesthExecuteResponse,
  SurgeryAnesthExecuteDialogDto,
} from './inpatient-nurse-station-surgery-anesth-execute.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 手术/麻醉执行弹窗 */
const BASE = '/inpatientNurseStationSurgeryAnesthExecute';

/** 装载手术/麻醉执行弹窗 */
export async function httpLoadSurgeryAnesthExecuteDialog(
  body: LoadSurgeryAnesthExecuteDialogRequest,
): Promise<SurgeryAnesthExecuteDialogDto> {
  return publicRequestClient.post<SurgeryAnesthExecuteDialogDto>(
    `${BASE}/loadSurgeryAnesthExecuteDialog`,
    body,
  );
}

/** 提交手术/麻醉执行（挂接医嘱执行计费主路径） */
export async function httpSubmitSurgeryAnesthExecute(
  body: SubmitSurgeryAnesthExecuteRequest,
): Promise<SubmitSurgeryAnesthExecuteResponse> {
  return publicRequestClient.post<SubmitSurgeryAnesthExecuteResponse>(
    `${BASE}/submitSurgeryAnesthExecute`,
    body,
  );
}
