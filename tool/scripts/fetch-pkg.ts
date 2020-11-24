import {promises as fs} from 'fs';
import {URL} from 'url';

import delay from 'delay';
import * as pacote from 'pacote';

import {Vulnerability} from '../src/db';
import {Pkg} from '../src/pkg';

const FetchSpan = 1000;

const sanitize = (vuln: Vulnerability, extension: string): string => {
  const filename = `${vuln.affectsPackage}@${vuln.affectsVersion}.${extension}`;
  return filename.replace('/', '+');
};

const main = async () => {
  const vulns = JSON.parse(await fs.readFile('../data/redos-pkg.json', 'utf8')) as Vulnerability[];
  for (const vuln of vulns) {
    try {
      const filename = sanitize(vuln, 'tgz');
      console.log(`==> fetch ${vuln.affectsPackage}@${vuln.affectsVersion}`);
      const tar = await pacote.tarball(`${vuln.affectsPackage}@${vuln.affectsVersion}`);
      const dest = `../data/pkg/${filename}`;
      console.log(`==> save ${dest}`);
      await fs.writeFile(dest, tar);
      const pkg: Pkg = {
        package: vuln.affectsPackage,
        version: vuln.affectsVersion,
        url: tar.resolved
      };
      await fs.writeFile(`../data/pkg/${sanitize(vuln, 'json')}`, JSON.stringify(pkg));
      await delay(FetchSpan);
    } catch(err) {
      console.error(err);
    }
  }
};

main();
