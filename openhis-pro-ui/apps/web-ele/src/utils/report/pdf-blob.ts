export function pdfBlobToObjectUrl(blob: Blob | null | undefined): string {
  if (!blob || blob.size <= 0) {
    return '';
  }
  return URL.createObjectURL(blob);
}

export function revokePdfObjectUrl(url: string | null | undefined): void {
  const trimmed = url?.trim();
  if (trimmed?.startsWith('blob:')) {
    URL.revokeObjectURL(trimmed);
  }
}
