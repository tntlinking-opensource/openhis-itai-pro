import type { JSONContent } from '@tiptap/core';

import { onBeforeUnmount, ref } from 'vue';

export type EmrSaveStatus = 'dirty' | 'saved' | 'saving';

const DB_NAME = 'emrEditorDrafts';
const STORE_NAME = 'drafts';
const DB_VERSION = 1;

export interface DraftRecord {
  content: JSONContent;
  docId: string;
  updatedAt: number;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'docId' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function saveDraft(docId: string, content: JSONContent): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    store.put({ docId, content, updatedAt: Date.now() } satisfies DraftRecord);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function loadDraft(docId: string): Promise<DraftRecord | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const req = store.get(docId);
    req.onsuccess = () => resolve((req.result as DraftRecord) ?? null);
    req.onerror = () => reject(req.error);
  });
}

async function deleteDraft(docId: string): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    store.delete(docId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export interface UseEmrAutoSaveOptions {
  /** 防抖间隔（毫秒），默认 3000 */
  debounceMs?: number;
  /** 文档唯一标识，用作 IndexedDB 主键 */
  docId: string;
  /** 编辑器变更时回调的最新 JSON */
  getContent: () => JSONContent | null;
  /** 恢复草稿时回调（仅 tryRestoreDraft 调用时触发） */
  onRestoreDraft?: (content: JSONContent) => void;
  /** 可选的远程保存接口；不传则仅本地草稿 */
  remoteSave?: (docId: string, content: JSONContent) => Promise<void>;
  /** 远程保存失败时回调（本地草稿失败不触发） */
  onRemoteSaveError?: (error: unknown) => void;
}

export function useEmrAutoSave(opts: UseEmrAutoSaveOptions) {
  const status = ref<EmrSaveStatus>('saved');
  const lastSavedAt = ref<null | number>(null);

  let timer: ReturnType<typeof setTimeout> | null = null;
  const debounceMs = opts.debounceMs ?? 3000;

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  async function flush(options?: { rethrowRemoteError?: boolean }) {
    clearTimer();
    const content = opts.getContent();
    if (!content || !opts.docId) return;

    status.value = 'saving';
    try {
      await saveDraft(opts.docId, content);
    } catch {
      status.value = 'dirty';
      return;
    }
    if (opts.remoteSave) {
      try {
        await opts.remoteSave(opts.docId, content);
      } catch (remoteErr) {
        status.value = 'dirty';
        opts.onRemoteSaveError?.(remoteErr);
        if (options?.rethrowRemoteError) {
          throw remoteErr;
        }
        return;
      }
    }
    lastSavedAt.value = Date.now();
    status.value = 'saved';
  }

  function markDirty() {
    status.value = 'dirty';
    clearTimer();
    timer = setTimeout(() => {
      flush();
    }, debounceMs);
  }

  /** 服务端或父组件载入正文后，将状态置为已保存（不触发远程保存） */
  function markSaved() {
    clearTimer();
    status.value = 'saved';
  }

  /** 查询是否有草稿，返回草稿记录或 null（不自动恢复） */
  async function checkDraft(): Promise<DraftRecord | null> {
    if (!opts.docId) return null;
    try {
      return await loadDraft(opts.docId);
    } catch {
      return null;
    }
  }

  /** 主动恢复草稿：调 onRestoreDraft 回调写入编辑器 */
  async function tryRestoreDraft() {
    const draft = await checkDraft();
    if (draft?.content && opts.onRestoreDraft) {
      opts.onRestoreDraft(draft.content);
      lastSavedAt.value = draft.updatedAt;
    }
  }

  async function clearDraft() {
    if (!opts.docId) return;
    try {
      await deleteDraft(opts.docId);
    } catch {
      // noop
    }
  }

  onBeforeUnmount(() => {
    clearTimer();
    const content = opts.getContent();
    if (content && status.value === 'dirty' && opts.docId) {
      saveDraft(opts.docId, content).catch(() => {});
    }
  });

  return {
    status,
    lastSavedAt,
    markDirty,
    markSaved,
    flush,
    checkDraft,
    tryRestoreDraft,
    clearDraft,
  };
}
