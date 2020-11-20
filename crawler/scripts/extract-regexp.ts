import {promises as fs} from 'fs';

import * as acorn from 'acorn';
import * as walk from 'acorn-walk';
import type * as estree from 'estree';
import decompress from 'decompress';

import { Pkg } from '../src/pkg';

interface RegExpInfo {
  readonly package: string;
  readonly version: string;
  readonly path: string;
  readonly line: number | undefined;
  readonly column: number | undefined;
  readonly source: string;
  readonly flags: string;
};

const handleJS = (pkg: Pkg, path: string, source: string): RegExpInfo[] => {
  console.log(`=> handle ${path} (in ${pkg.package}@${pkg.version})`);
  const ast = acorn.parse(source, {ecmaVersion: 2020, sourceType: 'module', locations: true, allowHashBang: true});
  const infos: RegExpInfo[] = [];

  walk.simple(ast, {
    Literal(node: estree.Literal) {
      if (!(node.value instanceof RegExp)) {
        return;
      }

      const {source, flags} = node.value;
      infos.push({
        package: pkg.package,
        version: pkg.version,
        path,
        line: node.loc?.start.line,
        column: node.loc?.start.column,
        source,
        flags
      });
    },
    NewExpression(node: estree.NewExpression) {
      if (
        !(node.callee.type === "Identifier" && node.callee.name === "RegExp")
      ) {
        return;
      }
      if (!(node.arguments.length == 1 || node.arguments.length == 2)) {
        return;
      }
      if (
        !node.arguments.every(
          (arg) => arg.type === "Literal" && typeof arg.value === "string"
        )
      ) {
        return;
      }

      const [source, flags = ""] = node.arguments.map(
        (arg) => (arg as estree.Literal).value as string
      );
      infos.push({
        package: pkg.package,
        version: pkg.version,
        path,
        line: node.loc?.start.line,
        column: node.loc?.start.column,
        source,
        flags
      });
    },
    CallExpression(node: estree.CallExpression) {
      if (
        !(node.callee.type === "Identifier" && node.callee.name === "RegExp")
      ) {
        return;
      }
      if (!(node.arguments.length == 1 || node.arguments.length == 2)) {
        return;
      }
      if (
        !node.arguments.every(
          (arg) => arg.type === "Literal" && typeof arg.value === "string"
        )
      ) {
        return;
      }

      const [source, flags = ""] = node.arguments.map(
        (arg) => (arg as estree.Literal).value as string
      );
      infos.push({
        package: pkg.package,
        version: pkg.version,
        path,
        line: node.loc?.start.line,
        column: node.loc?.start.column,
        source,
        flags
      });
    },
  } as any);

  return infos;
};

const handleTgz = async (filename: string): Promise<RegExpInfo[]> => {
  console.log(`==> handle ${filename}`);
  const pkg = JSON.parse(await fs.readFile(`data/pkg/${filename.replace(/\.tgz$/, '.json')}`, 'utf8')) as Pkg;
  const infos: RegExpInfo[] = [];
  const files = await decompress(`data/pkg/${filename}`);
  for (const file of files) {
    try {
      if (file.path.endsWith('.js')) {
        infos.push(...handleJS(pkg, file.path, file.data.toString('utf8')));
      }
    } catch (err) {
      console.error(err);
    };
  }
  return infos;
};

const main = async () => {
  const infos: RegExpInfo[] = [];
  for (const filename of await fs.readdir('data/pkg')) {
    if (filename.endsWith('.tgz')) {
      infos.push(...await handleTgz(filename));
      console.log(`==> found ${infos.length} regexp`);
    }
  }

  const set: Set<string> = new Set();
  const setInfos: RegExpInfo[] = [];
  for (const info of infos) {
    const re = `/${info.source}/${info.flags}`;
    if (set.has(re)) {
      continue;
    }
    set.add(re);
    setInfos.push(info);
  }
  console.log(`==> reduced regexp: ${setInfos.length}`);
  await fs.writeFile('data/regexp.json', JSON.stringify(setInfos, undefined, '  '));
};

main();
