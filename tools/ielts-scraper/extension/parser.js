// Pure DOM -> JSON parsers for ieltsonlinetests.com. No extension APIs here, so this file can be
// tested against saved HTML in any browser page.
(function (root) {
  const ORIGIN = "https://ieltsonlinetests.com";
  const SKILLS = ["listening", "reading", "writing", "speaking"];

  const clean = (s) => (s || "").replace(/ /g, " ").replace(/\s+/g, " ").trim();
  const abs = (href, base) => {
    try { return new URL((href || "").trim(), base || ORIGIN).href; } catch { return null; }
  };
  // The site serves the same tests under /vi, /th, /zh-hans; the unprefixed path has English labels.
  const unlocalise = (url) => url && url.replace(/^(https:\/\/ieltsonlinetests\.com)\/(vi|th|zh-hans)(?=\/|$)/, "$1");

  const KEEP = new Set(["P", "BR", "STRONG", "B", "EM", "I", "U", "SUP", "SUB", "SPAN", "DIV", "UL", "OL", "LI",
    "TABLE", "THEAD", "TBODY", "TR", "TD", "TH", "H1", "H2", "H3", "H4", "H5", "H6", "IMG", "LABEL"]);
  const DROP = new Set(["SCRIPT", "STYLE", "TEXTAREA", "BUTTON", "NOSCRIPT", "IFRAME", "AUDIO", "VIDEO"]);
  const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

  // Serialises a node keeping structure and text but no site styling, ids or handlers.
  function sanitise(node, base) {
    if (node.nodeType === 3) return esc(node.nodeValue.replace(/ /g, " "));
    if (node.nodeType !== 1 || DROP.has(node.tagName)) return "";
    const inner = Array.from(node.childNodes).map((c) => sanitise(c, base)).join("");
    if (!KEEP.has(node.tagName)) return inner;
    const tag = node.tagName.toLowerCase();
    if (tag === "img") {
      const src = abs(node.getAttribute("src") || node.getAttribute("data-src"), base);
      return src ? `<img src="${esc(src)}" alt="${esc(node.getAttribute("alt") || "")}">` : "";
    }
    if (tag === "br") return "<br>";
    let attrs = "";
    for (const a of ["colspan", "rowspan"]) {
      if (node.hasAttribute(a)) attrs += ` ${a}="${esc(node.getAttribute(a))}"`;
    }
    if ((tag === "span" || tag === "div" || tag === "label") && !attrs) return inner;
    return `<${tag}${attrs}>${inner}</${tag}>`;
  }
  const html = (node, base) => (node ? sanitise(node, base).replace(/\s+/g, " ").trim() : "");
  const images = (node, base) =>
    Array.from(node ? node.querySelectorAll("img, [data-src]") : [])
      .map((i) => abs(i.getAttribute("src") || i.getAttribute("data-src"), base))
      .filter((u, i, all) => u && all.indexOf(u) === i);

  // ---- library and collection pages -------------------------------------------------------

  function parseLibrary(doc, url) {
    const collections = [];
    for (const item of doc.querySelectorAll(".mocktest-item")) {
      const book = clean(item.querySelector(".mocktest-item__title")?.textContent);
      for (const a of item.querySelectorAll("a.mocktest-item__pack[href]")) {
        const pack = clean(a.querySelector(".mocktest-item__pack-title")?.textContent);
        collections.push({ title: clean(`${book} ${pack}`), url: unlocalise(abs(a.getAttribute("href"), url)) });
      }
    }
    const pages = Array.from(doc.querySelectorAll(".pager__item a[href*='page=']"))
      .map((a) => Number(new URL(a.getAttribute("href"), url).searchParams.get("page")))
      .filter(Number.isInteger);
    return { collections, lastPage: pages.length ? Math.max(...pages) : 0 };
  }

  function parseCollection(doc, url) {
    const tests = [];
    for (const card of doc.querySelectorAll(".practice-list__card")) {
      const skills = {};
      for (const item of card.querySelectorAll(".practice-item")) {
        const icon = item.querySelector(".practice-item__icon");
        const skill = SKILLS.find((s) => icon?.classList.contains(`-${s}`));
        const link = item.querySelector("a.practice-item__feature-item.-solution[href]");
        if (skill && link) skills[skill] = unlocalise(abs(link.getAttribute("href"), url));
      }
      tests.push({ name: clean(card.querySelector(".practice-list__title")?.textContent), skills });
    }
    return { title: clean(doc.querySelector("h1")?.textContent), url: unlocalise(url), tests };
  }

  // ---- listening and reading ---------------------------------------------------------------

  function groupType(group, answers) {
    if (group.querySelector("input[type=checkbox]")) return "multiple_select";
    if (group.querySelector(".type_radio, input[type=radio]")) return "multiple_choice";
    if (group.querySelector("select.iot-question")) {
      const set = new Set(answers.map((a) => a.toUpperCase()));
      if (set.size && [...set].every((a) => ["TRUE", "FALSE", "NOT GIVEN"].includes(a))) return "true_false_not_given";
      if (set.size && [...set].every((a) => ["YES", "NO", "NOT GIVEN"].includes(a))) return "yes_no_not_given";
      return "matching";
    }
    if (group.querySelector("input.iot-question")) return "completion";
    return "unknown";
  }

  function parseGroup(group, base) {
    const solved = new Map();
    // Answers are an <li class="answer"> list under a group, or a <span class="answer"> inside a
    // multiple-choice question; the explanation is the collapsed block next to either.
    for (const el of group.querySelectorAll(".answer")) {
      // The label is "7", or "11-12" when one choose-two question covers two numbers.
      const number = clean(el.querySelector(":scope > b, :scope > span:not(.b-r)")?.textContent) || el.closest("[data-num]")?.getAttribute("data-num");
      const note = el.querySelector(".collapse") || el.parentElement.querySelector(".collapse");
      solved.set(number, {
        answer: clean(el.querySelector(".b-r")?.textContent),
        explanation: clean(note?.textContent) || null,
        // Listening only: where in the recording the answer is heard, e.g. "0:02:57".
        audio_start: (el.querySelector(".listen-from-here") || el.parentElement.querySelector(".listen-from-here"))?.getAttribute("data") || null,
      });
    }

    // Work on a copy: drop the answers, and turn every answer control into a {{number}} blank.
    const copy = group.cloneNode(true);
    for (const el of copy.querySelectorAll(".answer, .explanation")) {
      const parent = el.parentElement;
      el.remove();
      if (parent && parent !== copy && !parent.children.length && !clean(parent.textContent)) parent.remove();
    }
    const questions = [];
    for (const el of copy.querySelectorAll("[data-num]")) {
      const number = el.getAttribute("data-num");
      const q = { number, prompt: "", options: [], ...(solved.get(number) || { answer: null, explanation: null, audio_start: null }) };
      if (el.matches("select, input, textarea")) {
        if (el.tagName === "SELECT") {
          q.options = Array.from(el.options).filter((o) => o.value).map((o) => ({ key: o.value, text: clean(o.textContent) }));
        }
        el.closest(".number-wrap-inline")?.querySelector(".number")?.remove();
        el.replaceWith(copy.ownerDocument.createTextNode(` {{${number}}} `));
      } else {
        q.prompt = clean(el.querySelector(".question-title")?.textContent);
        q.options = Array.from(el.querySelectorAll(".list-question > li")).map((li) => ({
          key: clean(li.querySelector(".lq-number")?.textContent),
          text: clean(li.querySelector("label")?.textContent),
        }));
      }
      questions.push(q);
    }
    // A blank's prompt is the sentence, list item or table cell it sits in.
    const blocks = Array.from(copy.querySelectorAll("p, li, td, th")).filter((b) => !b.querySelector("p, li, td, th"));
    for (const q of questions) {
      if (q.prompt) continue;
      const token = `{{${q.number}}}`;
      q.prompt = clean(blocks.find((b) => b.textContent.includes(token))?.textContent);
    }

    let label = null;
    for (let prev = group.previousElementSibling; prev && !prev.matches(".question"); prev = prev.previousElementSibling) {
      if (/^H[1-6]$/.test(prev.tagName)) { label = clean(prev.textContent); break; }
    }
    return {
      label,
      type: groupType(group, questions.map((q) => q.answer || "")),
      html: html(copy, base),
      image_urls: images(copy, base),
      questions,
    };
  }

  function parseObjective(doc, url, skill) {
    const parts = [];
    for (const section of doc.querySelectorAll("[id^='set-question-']")) {
      const n = section.id.replace("set-question-", "");
      const source = doc.getElementById(`set-container-${n}`);
      const content = source?.querySelector(".passage-content");
      const text = {
        title: clean(source?.querySelector(".subtitle")?.textContent) || null,
        description: clean(source?.querySelector(".passage-description")?.textContent) || null,
        html: html(content, url),
        image_urls: images(content, url),
      };
      parts.push({
        number: Number(n),
        title: clean(section.querySelector(".sl-caption span")?.textContent),
        [skill === "listening" ? "transcript" : "passage"]: content ? text : null,
        question_groups: Array.from(section.querySelectorAll("div.question")).map((g) => parseGroup(g, url)),
      });
    }
    // The key is rendered twice (desktop and mobile panels), so keep the first entry per number.
    const answer_key = [];
    for (const li of doc.querySelectorAll(".list-answer-item")) {
      const number = clean(li.querySelector(".number")?.textContent).replace(/\s+/g, "-"); // "11 12" -> "11-12"
      if (answer_key.some((a) => a.number === number)) continue;
      answer_key.push({ number, answer: clean(li.querySelector(".sys-answer")?.textContent) });
    }
    const out = { parts, answer_key };
    if (skill === "listening") {
      const urls = Array.from(doc.querySelectorAll(".player-listening [data-src], .player-listening source[src]"))
        .map((e) => abs(e.getAttribute("data-src") || e.getAttribute("src"), url));
      out.audio_urls = urls.filter((u, i) => u && urls.indexOf(u) === i);
    }
    return out;
  }

  // ---- writing and speaking (prompts only; the site publishes no model answers) ---------------

  function parseWriting(doc, url) {
    const tasks = [];
    let task = null;
    for (const el of doc.querySelectorAll(".tasks-content > *")) {
      if (/^H[1-6]$/.test(el.tagName)) {
        const m = clean(el.textContent).match(/task\s*(\d)/i);
        task = { number: m ? Number(m[1]) : tasks.length + 1, paragraphs: [], image_urls: [] };
        tasks.push(task);
      } else if (task && el.matches("img")) {
        task.image_urls.push(...images(el.parentElement, url).filter((u) => u === abs(el.getAttribute("src"), url)));
      } else if (task && el.matches("p, ul, ol, table, div:not(.show-more-wp)")) {
        task.image_urls.push(...images(el, url));
        const t = clean(el.textContent);
        if (t) task.paragraphs.push(t);
      }
    }
    return {
      tasks: tasks.map((t) => {
        const all = t.paragraphs.join(" ");
        const meta = (p) => /^You should (spend about|write at least)/i.test(p);
        return {
          number: t.number,
          prompt: t.paragraphs.filter((p) => !meta(p)).join("\n"),
          image_urls: [...new Set(t.image_urls)],
          minutes: Number(all.match(/spend about (\d+) minutes/i)?.[1]) || null,
          min_words: Number(all.match(/at least (\d+) words/i)?.[1]) || null,
        };
      }),
    };
  }

  function parseSpeaking(doc, url) {
    // The <audio> elements on this page are another user's recorded attempt, so they are left out.
    const parts = [];
    for (const panel of doc.querySelectorAll(".recording")) {
      const no = clean(panel.querySelector(".recording__header-item.-part-no")?.textContent);
      parts.push({
        number: Number(no.match(/\d+/)?.[0]) || parts.length + 1,
        name: clean(panel.querySelector(".recording__header-item.-part-name")?.textContent),
        questions: Array.from(panel.querySelectorAll(".recording__question")).map((q) => {
          const title = q.querySelector(".recording__question-title").cloneNode(true);
          title.querySelectorAll(".recording__question-time, .sound-bars").forEach((e) => e.remove());
          const cue = q.querySelector(".recording__question-content");
          return { text: clean(title.textContent), cue_card_html: cue ? html(cue, url) : null, cue_card: cue ? clean(cue.textContent) : null };
        }),
      });
    }
    return { parts };
  }

  function parseTest(doc, url, skill) {
    const title = clean(doc.title).replace(/^Solution for\s+/i, "").replace(/\s*\|.*$/, "");
    const body =
      skill === "writing" ? parseWriting(doc, url)
      : skill === "speaking" ? parseSpeaking(doc, url)
      : parseObjective(doc, url, skill);
    return { skill, title, source_url: url, ...body };
  }

  // What a parsed test must contain to count as scraped; an empty result means the markup changed.
  function problems(test) {
    const issues = [];
    if (test.skill === "writing") {
      if (!test.tasks.length) issues.push("no writing tasks found");
      for (const t of test.tasks) if (!t.prompt) issues.push(`task ${t.number} has no prompt`);
    } else if (test.skill === "speaking") {
      if (!test.parts.some((p) => p.questions.length)) issues.push("no speaking questions found");
    } else {
      const qs = test.parts.flatMap((p) => p.question_groups.flatMap((g) => g.questions));
      if (!qs.length) issues.push("no questions found");
      const missing = qs.filter((q) => !q.answer).map((q) => q.number);
      if (missing.length) issues.push(`no answer for question ${missing.join(", ")}`);
      const seen = new Set(qs.map((q) => q.number));
      const unparsed = test.answer_key.map((a) => a.number).filter((n) => !seen.has(n));
      if (unparsed.length) issues.push(`answer key lists ${unparsed.join(", ")} but no such question was parsed`);
      if (test.parts.some((p) => !(p.passage || p.transcript))) issues.push("a part has no passage/transcript");
      if (test.skill === "listening" && !test.audio_urls.length) issues.push("no audio url");
    }
    return issues;
  }

  root.IeltsParser = { ORIGIN, SKILLS, unlocalise, parseLibrary, parseCollection, parseTest, problems };
})(typeof self !== "undefined" ? self : globalThis);
