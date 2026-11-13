// Headless version of the extension's crawl: same parser, no browser, no clicking.
//
//   npm install
//   node crawl.mjs --out data/scrape [--match 2025,2026] [--limit 3] [--delay 1500] [--force]
//
// Writes one JSON file per collection, in the format the extension writes. A collection whose file
// already exists without failures is skipped, so an interrupted run can be started again.
import fs from "node:fs";
import path from "node:path";
import { JSDOM } from "jsdom";
import "./extension/parser.js";

const { ORIGIN, parseLibrary, parseCollection, parseTest, problems } = globalThis.IeltsParser;
const SKILLS = ["listening", "reading", "writing", "speaking"];

const args = process.argv.slice(2);
const flag = (name) => args.includes(`--${name}`);
const option = (name, fallback) => (args.includes(`--${name}`) ? args[args.indexOf(`--${name}`) + 1] : fallback);
const out = option("out");
if (!out) {
  console.error("Usage: node crawl.mjs --out <folder> [--match text,text] [--limit N] [--delay ms] [--force]");
  process.exit(2);
}
const match = (option("match", "") || "").toLowerCase().split(",").filter(Boolean);
const limit = Number(option("limit", "0")) || Infinity;
const pause = Math.max(500, Number(option("delay", "1500")));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let lastRequestAt = 0;

// One request at a time, never closer together than the pause; only overload and server errors are retried.
async function fetchDoc(url) {
  for (let attempt = 1; ; attempt++) {
    await sleep(Math.max(0, lastRequestAt + pause - Date.now()));
    lastRequestAt = Date.now();
    let failure;
    try {
      const res = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" }, redirect: "follow" });
      if (res.ok) {
        if (new URL(res.url).pathname.includes("/account/login")) throw new Error("redirected to the login page");
        return new JSDOM(await res.text(), { url }).window.document;
      }
      failure = `HTTP ${res.status}`;
      if (res.status !== 429 && res.status < 500) throw new Error(failure);
    } catch (e) {
      if (e instanceof TypeError) failure = `network error (${e.cause?.code || e.message})`;
      else throw e;
    }
    if (attempt === 4) throw new Error(`${failure} after ${attempt} attempts`);
    const wait = 5000 * 2 ** (attempt - 1);
    console.warn(`  ${failure} for ${url}; retrying in ${wait / 1000}s`);
    await sleep(wait);
  }
}

const slugOf = (url) => new URL(url).pathname.split("/").filter(Boolean).pop();

async function scrapeCollection(entry) {
  const collection = parseCollection(await fetchDoc(entry.url), entry.url);
  if (!collection.tests.length) throw new Error("no tests found on the collection page");
  const data = { collection: { title: entry.title, url: entry.url }, scraped_at: new Date().toISOString(), tests: [], failures: [] };
  for (const test of collection.tests) {
    const record = { name: test.name, sections: {} };
    for (const skill of SKILLS) {
      const url = test.skills[skill];
      if (!url) {
        data.failures.push({ test: test.name, skill, reason: "no link on the collection page" });
        continue;
      }
      try {
        const section = parseTest(await fetchDoc(url), url, skill);
        const issues = problems(section);
        if (issues.length) data.failures.push({ test: test.name, skill, url, reason: issues.join("; ") });
        record.sections[skill] = section;
      } catch (e) {
        data.failures.push({ test: test.name, skill, url, reason: e.message });
      }
    }
    data.tests.push(record);
  }
  return data;
}

fs.mkdirSync(out, { recursive: true });
const found = new Map();
for (let page = 0, last = 0; page <= last; page++) {
  const url = `${ORIGIN}/ielts-exam-library?page=${page}`;
  const lib = parseLibrary(await fetchDoc(url), url);
  last = Math.max(last, lib.lastPage);
  for (const c of lib.collections) found.set(c.url, c);
}
if (!found.size) {
  console.error("No collections found on the library page - the site markup may have changed.");
  process.exit(2);
}
const picked = [...found.values()]
  .filter((c) => !match.length || match.some((m) => `${c.title} ${c.url}`.toLowerCase().includes(m)))
  .slice(0, limit);
console.log(`${found.size} collections in the library, ${picked.length} selected.`);

let saved = 0, withProblems = 0, failed = 0, skipped = 0;
for (const [i, entry] of picked.entries()) {
  const file = path.join(out, `${slugOf(entry.url)}.json`);
  if (!flag("force") && fs.existsSync(file) && !JSON.parse(fs.readFileSync(file, "utf8")).failures.length) {
    skipped++;
    continue;
  }
  try {
    const data = await scrapeCollection(entry);
    fs.writeFileSync(file, JSON.stringify(data, null, 2));
    saved++;
    const sections = data.tests.reduce((n, t) => n + Object.keys(t.sections).length, 0);
    console.log(`[${i + 1}/${picked.length}] ${entry.title}: ${sections} sections` + (data.failures.length ? `, ${data.failures.length} with problems` : ""));
    for (const f of data.failures) console.log(`      - ${f.test} / ${f.skill}: ${f.reason}`);
    if (data.failures.length) withProblems++;
  } catch (e) {
    failed++;
    console.error(`[${i + 1}/${picked.length}] ${entry.title}: FAILED - ${e.message}`);
  }
}
console.log(`\n${saved} collections saved (${withProblems} with problems), ${skipped} already there, ${failed} failed.`);
process.exit(failed ? 1 : 0);
