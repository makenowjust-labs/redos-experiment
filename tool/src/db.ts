export interface DB {

  readonly url: string;

  readonly fetched: string;

  readonly vulnerabilities: Vulnerability[];
}

export interface Vulnerability {

  readonly vulnerability: string;

  readonly link: string;

  readonly affectsPackage: string;

  readonly affectsVersion: string;

  readonly published: string;
}
