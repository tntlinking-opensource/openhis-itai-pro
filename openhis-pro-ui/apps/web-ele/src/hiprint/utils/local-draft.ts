const STORAGE_KEY = 'zxplat-hiprint-local-draft';

export interface LocalDesignDraft {
  PrintJson: Record<string, unknown>;
  samplePrintData: Record<string, unknown>;
  updatedAt?: string;
}

/** 从 localStorage 读取本地草稿 */
export function loadLocalDraft(): LocalDesignDraft | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as LocalDesignDraft;
    if (!parsed?.PrintJson || typeof parsed.PrintJson !== 'object') {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

/** 写入 localStorage 草稿 */
export function saveLocalDraft(draft: LocalDesignDraft): void {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      ...draft,
      updatedAt: new Date().toISOString(),
    }),
  );
}

/** 触发浏览器下载 JSON 文件 */
export function downloadJsonFile(filename: string, data: unknown): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** 选择本地 JSON 文件并解析 */
export function pickJsonFile(): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,application/json';
    input.addEventListener('change', () => {
      const file = input.files?.[0];
      if (!file) {
        reject(new Error('未选择文件'));
        return;
      }
      void file
        .text()
        .then((raw) => {
          try {
            const parsed = JSON.parse(raw) as Record<string, unknown>;
            resolve(parsed);
          } catch {
            reject(new Error('JSON 解析失败'));
          }
        })
        .catch(() => reject(new Error('读取文件失败')));
    });
    input.click();
  });
}
