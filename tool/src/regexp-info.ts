export interface RegExpInfo {
  readonly package: string;
  readonly version: string;
  readonly path: string;
  readonly line: number | undefined;
  readonly column: number | undefined;
  readonly source: string;
  readonly flags: string;
};
