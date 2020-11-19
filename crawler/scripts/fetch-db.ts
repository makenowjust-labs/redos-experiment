import {URL} from 'url';
import {promises as fs} from 'fs';

import {load} from 'cheerio';
import got from 'got';
import dayjs from 'dayjs';

import {DB, Vulnerability} from '../src/db';
import delay from 'delay';

class SnykVulnSearchPage {

  public static InitURL = new URL("https://snyk.io/vuln/page/1?type=npm");

  public static async load(url: URL): Promise<SnykVulnSearchPage> {
    const response = await got(url);
    return new SnykVulnSearchPage(url, load(response.body));
  }

  private readonly url: URL;

  private readonly $: cheerio.Root;

  public constructor(url: URL, $: cheerio.Root) {
    this.url = url;
    this.$ = $;
  }

  public index(): number {
    const parts = this.url.pathname.split('/')
    const index = Number.parseInt(parts[parts.length - 1], 10);
    return Number.isNaN(index) ? 1 : index;
  }

  public nextPageURL(): URL | null {
    const paginationNext = this.$('.pagination__next');
    const nextPageHref = paginationNext.attr('href');
    return nextPageHref ? new URL(`${this.url.origin}${nextPageHref}`) : null;
  }

  public vulnerabilities(): Vulnerability[] {
    const rows = this.$('.table--comfortable > tbody > tr');
    const vulns: Vulnerability[] = [];
    rows.each((_, row) => {
      const vulnerability = this.$('td:nth-child(1) > span', row).text().trim();
      const link = this.$('td:nth-child(1) > span > a', row).attr('href');
      const affectsPackage = this.$('td:nth-child(2) > strong > a', row).text().trim();
      const affectsVersion = this.$('td:nth-child(2) > span', row).text().trim();
      const published = this.$('td:nth-child(4)', row).text().trim();
      if (vulnerability && link && affectsPackage && affectsVersion && published) {
        vulns.push({ vulnerability, link: `${this.url.origin}${link}`, affectsPackage, affectsVersion, published });
      }
    });
    return vulns;
  }
}

const FetchSpan = 30000;

const fetchPage = async (url: URL): Promise<URL | null> => {
  console.log(`==> fetch ${url}`);
  const page = await SnykVulnSearchPage.load(url);

  const fetched = dayjs().format('YYYY-MM-DD HH:mm:ss');
  const vulnerabilities = page.vulnerabilities();
  const db: DB = { url: url.toString(), fetched, vulnerabilities };

  const index = page.index().toString().padStart(2, '0');
  const path = `data/db/${index}.json`;
  console.log(`==> save ${path}`);
  await fs.writeFile(path, JSON.stringify(db, undefined, '  '));

  return page.nextPageURL();
};

const main = async () => {
  let url: URL | null = SnykVulnSearchPage.InitURL;

  while (url !== null) {
    url = await fetchPage(url);
    if (url) {
      console.log(`==> next URL: ${url}`);
      await delay(FetchSpan);
    }
  }
};

main();
