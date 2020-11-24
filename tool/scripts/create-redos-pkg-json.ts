import {promises as fs} from 'fs';

import {DB} from '../src/db';

const main = async () => {
  const vulns = [];
  for (const filename of await fs.readdir('../data/db')) {
    const db = JSON.parse(await fs.readFile(`../data/db/${filename}`, 'utf8')) as DB;
    vulns.push(...db.vulnerabilities.filter(vuln => vuln.vulnerability === 'Regular Expression Denial of Service (ReDoS)'));
  }
  console.log('==> save ../data/redos-pkg.json');
  await fs.writeFile(`../data/redos-pkg.json`, JSON.stringify(vulns, undefined, '  '));
};

main();
