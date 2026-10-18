import type {
  NurseStationTaskIncreaseHint,
  NurseStationTaskNotifyCard,
} from './nurse-station-task-notify.types';

import { onUnmounted, ref } from 'vue';

const MAX_STACK = 4;
const AUTO_DISMISS_MS = 12_000;

/** 护士站待校/待执增量浮层：不阻断操作，可点击跳转。 */
export function useNurseStationTaskNotify() {
  const items = ref<NurseStationTaskNotifyCard[]>([]);
  const dismissTimers = new Map<string, ReturnType<typeof setTimeout>>();
  let seq = 0;

  function clearDismissTimer(id: string) {
    const timer = dismissTimers.get(id);
    if (timer != null) {
      clearTimeout(timer);
      dismissTimers.delete(id);
    }
  }

  function dismiss(id: string) {
    clearDismissTimer(id);
    items.value = items.value.filter((row) => row.id !== id);
  }

  function scheduleDismiss(id: string) {
    clearDismissTimer(id);
    dismissTimers.set(
      id,
      setTimeout(() => {
        dismiss(id);
      }, AUTO_DISMISS_MS),
    );
  }

  function buildMessage(hint: NurseStationTaskIncreaseHint): string {
    const who = `${hint.bedNo || '-'} ${hint.patientName || '患者'}`.trim();
    if (hint.auditIncreased && hint.executeIncreased) {
      return `患者${who}有待校对/执行项目`;
    }
    if (hint.auditIncreased) {
      return `患者${who}有待校对项目`;
    }
    return `患者${who}有待执行项目`;
  }

  function resolveTargetTab(
    hint: NurseStationTaskIncreaseHint,
  ): NurseStationTaskNotifyCard['targetTab'] {
    // 先校对后执行：同时上升时优先校对
    if (hint.auditIncreased) {
      return 'ORDER_AUDIT';
    }
    return 'ORDER_EXECUTE';
  }

  /** 将轮询增量推入浮层（同患者同目标合并刷新文案） */
  function pushIncreases(hints: NurseStationTaskIncreaseHint[]) {
    if (hints.length === 0) {
      return;
    }
    for (const hint of hints) {
      const targetTab = resolveTargetTab(hint);
      const message = buildMessage(hint);
      const existing = items.value.find(
        (row) => row.encounterId === hint.encounterId && row.targetTab === targetTab,
      );
      if (existing) {
        existing.message = message;
        existing.patientName = hint.patientName;
        existing.bedNo = hint.bedNo;
        scheduleDismiss(existing.id);
        continue;
      }
      seq += 1;
      const id = `task-notify-${Date.now()}-${seq}`;
      items.value = [
        {
          id,
          encounterId: hint.encounterId,
          patientName: hint.patientName,
          bedNo: hint.bedNo,
          targetTab,
          message,
        },
        ...items.value,
      ].slice(0, MAX_STACK);
      scheduleDismiss(id);
    }
  }

  onUnmounted(() => {
    for (const id of [...dismissTimers.keys()]) {
      clearDismissTimer(id);
    }
    items.value = [];
  });

  return {
    dismiss,
    items,
    pushIncreases,
  };
}
