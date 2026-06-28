/** sv-print RulerStyle（摘自 sv-print@0.2.21 dist/lib/constant） */
export interface RulerStyle {
  rulerId: string;
  size: number;
  startX: number;
  startY: number;
  width: number;
  height: number;
  scrollLeft: number;
  scrollTop: number;
  paperRect: boolean;
  paperRectColor: string;
  paperWidth: number;
  paperHeight: number;
  unitMode: 'mm' | 'px';
  unit: {
    backgroundColor: string;
    fontColor: string;
    fontSize: number;
  };
  backgroundColor: string;
  fontSize: number;
  fontColor: string;
  fontWeight: string;
  fontMode: string;
  gap: number;
  tickColor: string;
  tickWidth: number;
  border: boolean;
  borderColor: string;
  borderWidth: number;
  scale: number;
  show: boolean;
}

/** 标尺默认样式 */
export const DEFAULT_RULER_STYLE: RulerStyle = {
  rulerId: 'oh',
  size: 24,
  startX: 0,
  startY: 0,
  width: 1080,
  height: 1920,
  scrollLeft: 0,
  scrollTop: 0,
  paperRect: true,
  paperRectColor: '#f5f5f5',
  paperWidth: 210,
  paperHeight: 287,
  unitMode: 'mm',
  unit: {
    backgroundColor: '#fff',
    fontColor: '#343a40',
    fontSize: 10,
  },
  backgroundColor: '#fff',
  fontSize: 11,
  fontColor: '#1f1f1f',
  fontWeight: '',
  fontMode: 'right',
  gap: 3.78,
  tickColor: '#1f1f1f',
  tickWidth: 1,
  border: true,
  borderColor: '#1f1f1f',
  borderWidth: 1,
  scale: 1,
  show: true,
};
