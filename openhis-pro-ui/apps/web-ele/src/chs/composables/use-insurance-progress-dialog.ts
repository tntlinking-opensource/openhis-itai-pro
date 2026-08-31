/**
 * 医保读卡 / 预结算过程进度弹窗状态（对齐门诊收费 ChargeProgressDialog 用法）。
 */
import { ref } from 'vue';

import type { InsuranceProgressStep } from '#/chs/components/insurance-progress-dialog.vue';

export interface InsuranceProgressReporter {
  clearCount: () => void;
  close: () => void;
  open: (title: string, steps: InsuranceProgressStep[], detail?: string) => void;
  setCount: (current: number, total: number, labelText?: string) => void;
  setStep: (index: number, detail?: string) => void;
}

export function useInsuranceProgressDialog() {
  const visible = ref(false);
  const title = ref('处理中');
  const steps = ref<InsuranceProgressStep[]>([]);
  const activeIndex = ref(0);
  const detail = ref('');
  const countCurrent = ref<null | number>(null);
  const countTotal = ref<null | number>(null);
  const countLabelText = ref<null | string>(null);

  const progress: InsuranceProgressReporter = {
    open(nextTitle, nextSteps, nextDetail = '') {
      title.value = nextTitle;
      steps.value = nextSteps;
      activeIndex.value = 0;
      detail.value = nextDetail;
      countCurrent.value = null;
      countTotal.value = null;
      countLabelText.value = null;
      visible.value = true;
    },
    setStep(index, nextDetail) {
      activeIndex.value = index;
      if (nextDetail !== undefined) {
        detail.value = nextDetail;
      }
    },
    setCount(current, total, labelText) {
      countCurrent.value = Math.max(0, current);
      countTotal.value = Math.max(0, total);
      countLabelText.value = labelText ?? null;
    },
    clearCount() {
      countCurrent.value = null;
      countTotal.value = null;
      countLabelText.value = null;
    },
    close() {
      visible.value = false;
      detail.value = '';
      countCurrent.value = null;
      countTotal.value = null;
      countLabelText.value = null;
    },
  };

  return {
    activeIndex,
    countCurrent,
    countLabelText,
    countTotal,
    detail,
    progress,
    steps,
    title,
    visible,
  };
}
