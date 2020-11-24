import {promises as fs} from 'fs';

import { RegExpInfo } from '../src/regexp-info';

const main = async () => {
  const name = process.argv[2];
  console.log(`==> Process ${name}`);

  const filename = `../data/txt/${name}_processed.txt`;
  const input = await fs.readFile(filename, 'utf8');

  const infos: RegExpInfo[] = [];
  const lines = input.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const start = line.indexOf('/');
    const end = line.lastIndexOf('/');
    if (start >= 0 && end >= 0) {
      const source = line.substring(start + 1, end);
      const flags = line.substring(end + 1);
      infos.push({
        package: name,
        version: '',
        path: filename,
        line: i,
        column: 0,
        source,
        flags
      });
    }
  }

  console.log(`==> Save ../data/${name}-regexp.json`);
  await fs.writeFile(`../data/${name}-regexp.json`, JSON.stringify(infos, undefined, '  '));
};

main();