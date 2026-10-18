import { computed, ref } from 'vue';

import { defineStore } from 'pinia';

import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { INPATIENT_DOCTOR_SESSION_MAX } from './inpatient-doctor-session.constants';
import { resolveDoctorSessionEncounterId } from './map-inpatient-doctor-session';

export interface OpenDoctorSessionResult {
  added: number;
  activatedExisting: number;
  blockedByCap: number;
}

export const useInpatientDoctorSessionStore = defineStore('inpatientDoctorStationSession', () => {
  const sessions = ref<InpatientDoctorStationPatient[]>([]);
  const activeEncounterId = ref('');
  const lastTabByEncounterId = ref<Record<string, string>>({});

  const activeSession = computed(() => {
    const currentId = resolveDoctorSessionEncounterId(activeEncounterId.value);
    return (
      sessions.value.find((row) => resolveDoctorSessionEncounterId(row) === currentId) ?? null
    );
  });

  function isOpened(encounterId: string): boolean {
    const key = resolveDoctorSessionEncounterId(encounterId);
    if (!key) return false;
    return sessions.value.some((row) => resolveDoctorSessionEncounterId(row) === key);
  }

  function getSessionTab(encounterId: string): string {
    const key = resolveDoctorSessionEncounterId(encounterId);
    return lastTabByEncounterId.value[key] ?? '';
  }

  function setSessionTab(encounterId: string, tab: string) {
    const key = resolveDoctorSessionEncounterId(encounterId);
    if (!key || !tab) return;
    lastTabByEncounterId.value = { ...lastTabByEncounterId.value, [key]: tab };
  }

  function activate(encounterId: string) {
    const key = resolveDoctorSessionEncounterId(encounterId);
    if (!key || !isOpened(key)) return;
    activeEncounterId.value = key;
  }

  /** 打开患者：已打开则激活，未打开则追加 */
  function openSessions(patients: InpatientDoctorStationPatient[]): OpenDoctorSessionResult {
    let added = 0;
    let activatedExisting = 0;
    let blockedByCap = 0;
    let lastKey = '';

    for (const patient of patients) {
      const key = resolveDoctorSessionEncounterId(patient);
      if (!key) continue;
      if (isOpened(key)) {
        const nextRows = sessions.value.map((row) =>
          resolveDoctorSessionEncounterId(row) === key ? patient : row,
        );
        sessions.value = nextRows;
        activatedExisting += 1;
        lastKey = key;
        continue;
      }
      if (sessions.value.length >= INPATIENT_DOCTOR_SESSION_MAX) {
        blockedByCap += 1;
        continue;
      }
      sessions.value = [...sessions.value, patient];
      added += 1;
      lastKey = key;
    }

    if (lastKey) {
      activeEncounterId.value = lastKey;
    }
    return { added, activatedExisting, blockedByCap };
  }

  function closeSession(encounterId: string) {
    const key = resolveDoctorSessionEncounterId(encounterId);
    if (!key) return;
    const index = sessions.value.findIndex(
      (row) => resolveDoctorSessionEncounterId(row) === key,
    );
    if (index < 0) return;
    const nextRows = sessions.value.filter((_, i) => i !== index);
    sessions.value = nextRows;
    const nextTabs = { ...lastTabByEncounterId.value };
    delete nextTabs[key];
    lastTabByEncounterId.value = nextTabs;
    if (resolveDoctorSessionEncounterId(activeEncounterId.value) !== key) {
      return;
    }
    const neighbor = nextRows[index] ?? nextRows[index - 1] ?? null;
    activeEncounterId.value = neighbor ? resolveDoctorSessionEncounterId(neighbor) : '';
  }

  function closeOthers(keepEncounterId: string) {
    const keep = resolveDoctorSessionEncounterId(keepEncounterId);
    if (!keep) return;
    const nextTabs: Record<string, string> = {};
    if (lastTabByEncounterId.value[keep]) {
      nextTabs[keep] = lastTabByEncounterId.value[keep] ?? '';
    }
    sessions.value = sessions.value.filter(
      (row) => resolveDoctorSessionEncounterId(row) === keep,
    );
    lastTabByEncounterId.value = nextTabs;
    activeEncounterId.value = keep;
  }

  function closeAll() {
    sessions.value = [];
    activeEncounterId.value = '';
    lastTabByEncounterId.value = {};
  }

  /** 队列刷新后回写已打开患者的最新行 */
  function syncFromQueue(rows: InpatientDoctorStationPatient[]) {
    const byId = new Map(
      rows
        .map((row) => [resolveDoctorSessionEncounterId(row), row] as const)
        .filter(([id]) => Boolean(id)),
    );
    sessions.value = sessions.value.map((row) => {
      const fresh = byId.get(resolveDoctorSessionEncounterId(row));
      return fresh ?? row;
    });
  }

  return {
    activate,
    activeEncounterId,
    activeSession,
    closeAll,
    closeOthers,
    closeSession,
    getSessionTab,
    isOpened,
    openSessions,
    sessions,
    setSessionTab,
    syncFromQueue,
  };
});
