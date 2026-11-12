const { ORIGIN, parseLibrary, parseCollection, parseTest, problems } = IeltsParser;
const $ = (id) => document.getElementById(id);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let stopping = false;
let lastRequestAt = 0;

function log(message, level) {
  const line = document.createElement("div");
  if (level) line.className = level;
  line.textContent = `${new Date().toLocaleTimeString()}  ${message}`;
  $("log").append(line);
  $("log").scrollTop = $("log").scrollHeight;
}

// One request at a time, never closer together than the configured pause. Cookies are left out so
// every page is the anonymous view, whatever account the browser is signed in to.
async function fetchDoc(url) {
  const pause = Math.max(500, Number($("delay").value) || 1500);
  for (let attempt = 1; ; attempt++) {
    await sleep(Math.max(0, lastRequestAt + pause - Date.now()));
    lastRequestAt = Date.now();
    let failure;
    try {
      const res = await fetch(url, { credentials: "omit", redirect: "follow" });
      if (res.ok) {
        if (new URL(res.url).pathname.includes("/account/login")) throw new Error("redirected to the login page");
        return new DOMParser().parseFromString(await res.text(), "text/html");
      }
      failure = `HTTP ${res.status}`;
      // Only overload and server errors are worth retrying; a 403/404 will not change.
      if (res.status !== 429 && res.status < 500) throw new Error(failure);
    } catch (e) {
      if (e instanceof TypeError) failure = `network error (${e.message})`;
      else throw e;
    }
    if (attempt === 4) throw new Error(`${failure} after ${attempt} attempts`);
    const wait = 5000 * 2 ** (attempt - 1);
    log(`${failure} for ${url}; retrying in ${wait / 1000}s`, "warn");
    await sleep(wait);
  }
}

const slugOf = (url) => new URL(url).pathname.split("/").filter(Boolean).pop();
const finished = async () => new Set((await chrome.storage.local.get("finished")).finished || []);

async function loadCollections() {
  $("load").disabled = true;
  try {
    const found = new Map();
    let last = 0;
    for (let page = 0; page <= last; page++) {
      const url = `${ORIGIN}/ielts-exam-library?page=${page}`;
      const lib = parseLibrary(await fetchDoc(url), url);
      last = Math.max(last, lib.lastPage);
      for (const c of lib.collections) found.set(c.url, c);
      log(`Library page ${page + 1}/${last + 1}: ${lib.collections.length} collections`);
    }
    if (!found.size) throw new Error("no collections found on the library page - the site markup may have changed");
    const done = await finished();
    $("collections").replaceChildren(...[...found.values()].map((c) => {
      const isDone = done.has(slugOf(c.url));
      const label = document.createElement("label");
      const box = Object.assign(document.createElement("input"), { type: "checkbox", value: c.url, checked: !isDone });
      box.dataset.title = c.title;
      label.append(box, ` ${c.title}${isDone ? " (done)" : ""}`);
      if (isDone) label.className = "done";
      return label;
    }));
    $("count").textContent = `(${found.size})`;
    for (const id of ["all", "none", "forget", "start"]) $(id).disabled = false;
  } catch (e) {
    log(`Could not load the collection list: ${e.message}`, "err");
  } finally {
    $("load").disabled = false;
  }
}

function save(filename, data) {
  const blobUrl = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
  return new Promise((resolve, reject) => {
    chrome.downloads.download({ url: blobUrl, filename: `ielts-scrape/${filename}`, conflictAction: "overwrite", saveAs: false }, (id) => {
      const error = chrome.runtime.lastError;
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
      if (error || id === undefined) reject(new Error(error?.message || "download was not started"));
      else resolve();
    });
  });
}

async function scrapeCollection(entry, skills) {
  const collection = parseCollection(await fetchDoc(entry.url), entry.url);
  if (!collection.tests.length) throw new Error("no tests found on the collection page");
  const out = { collection: { title: entry.title, url: entry.url }, scraped_at: new Date().toISOString(), tests: [], failures: [] };
  for (const test of collection.tests) {
    const record = { name: test.name, sections: {} };
    for (const skill of skills) {
      if (stopping) return null;
      const url = test.skills[skill];
      if (!url) {
        out.failures.push({ test: test.name, skill, reason: "no link on the collection page" });
        continue;
      }
      try {
        const section = parseTest(await fetchDoc(url), url, skill);
        const issues = problems(section);
        if (issues.length) {
          out.failures.push({ test: test.name, skill, url, reason: issues.join("; ") });
          log(`${test.name} / ${skill}: ${issues.join("; ")}`, "warn");
        }
        record.sections[skill] = section;
      } catch (e) {
        out.failures.push({ test: test.name, skill, url, reason: e.message });
        log(`${test.name} / ${skill}: ${e.message}`, "err");
      }
    }
    out.tests.push(record);
  }
  return out;
}

async function run() {
  const skills = [...document.querySelectorAll(".skill:checked")].map((b) => b.value);
  const picked = [...$("collections").querySelectorAll("input:checked")].map((b) => ({ url: b.value, title: b.dataset.title }));
  if (!skills.length || !picked.length) return log("Pick at least one skill and one collection.", "warn");

  stopping = false;
  $("start").disabled = true;
  $("stop").disabled = false;
  let saved = 0, withProblems = 0, failed = 0;
  for (const [i, entry] of picked.entries()) {
    if (stopping) break;
    log(`[${i + 1}/${picked.length}] ${entry.title}`);
    try {
      const data = await scrapeCollection(entry, skills);
      if (!data) break;
      const slug = slugOf(entry.url);
      await save(`${slug}.json`, data);
      saved++;
      const sections = data.tests.reduce((n, t) => n + Object.keys(t.sections).length, 0);
      if (data.failures.length) {
        // Left unmarked so it is re-selected next time; the file lists what was wrong in "failures".
        withProblems++;
        log(`Saved ${slug}.json: ${sections} sections, ${data.failures.length} with problems`, "warn");
      } else {
        const done = await finished();
        done.add(slug);
        await chrome.storage.local.set({ finished: [...done] });
        log(`Saved ${slug}.json: ${sections} sections`);
      }
    } catch (e) {
      failed++;
      log(`${entry.title}: ${e.message}`, "err");
    }
  }
  const left = picked.length - saved - failed;
  log(`${stopping ? "Stopped" : "Finished"}: ${saved} saved (${withProblems} with problems), ${failed} failed, ${left} not attempted.`,
    failed || withProblems ? "warn" : undefined);
  $("start").disabled = false;
  $("stop").disabled = true;
}

$("load").onclick = loadCollections;
$("start").onclick = run;
$("stop").onclick = () => { stopping = true; log("Stopping after the current request..."); };
$("all").onclick = () => $("collections").querySelectorAll("input").forEach((b) => (b.checked = true));
$("none").onclick = () => $("collections").querySelectorAll("input").forEach((b) => (b.checked = false));
$("forget").onclick = async () => { await chrome.storage.local.remove("finished"); log("Cleared the finished list; load the list again."); };
