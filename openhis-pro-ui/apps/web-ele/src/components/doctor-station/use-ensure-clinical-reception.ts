import { inject } from 'vue';

import { ENSURE_CLINICAL_RECEPTION_KEY } from './clinical-reception-context';

/**
 * 医生站临床子页通用接诊守卫：inject 父级 `ensureReceptionForClinicalWork`。
 * Tab 激活、切换患者、写操作前调用 `ensureReceptionBeforeClinicalWork`。
 */
export function useEnsureClinicalReception() {
  const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);

  /** 未接诊则写库接诊；已接诊则切「在诊」并选中当前患者 */
  async function ensureReceptionBeforeClinicalWork(): Promise<boolean> {
    if (ensureClinicalReception) {
      return ensureClinicalReception();
    }
    return true;
  }

  return { ensureReceptionBeforeClinicalWork };
}
