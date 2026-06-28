export type PrintData = Record<string, unknown>;

export type PrintMapper<TSource = unknown> = (source: TSource) => PrintData;
