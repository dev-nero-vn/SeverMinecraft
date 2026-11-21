import fs from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";

const dataDir = path.resolve(process.env.DATA_DIR || "./data");
const journalPath = path.join(dataDir, "activity.json");
const MAX_ITEMS = 300;
let items = [];
let writeQueue = Promise.resolve();

export async function initializeActivityStore() {
  await fs.mkdir(dataDir, { recursive: true, mode: 0o700 });
  try {
    const raw = await fs.readFile(journalPath, "utf8");
    const parsed = JSON.parse(raw);
    items = Array.isArray(parsed) ? parsed.slice(0, MAX_ITEMS) : [];
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    items = [];
    await persist();
  }
}

async function persist() {
  const snapshot = JSON.stringify(items, null, 2);
  writeQueue = writeQueue.then(async () => {
    const temp = `${journalPath}.tmp`;
    await fs.writeFile(temp, snapshot, { mode: 0o600 });
    await fs.rename(temp, journalPath);
  });
  return writeQueue;
}

export async function addActivity({ source = "system", actor = "system", action, status = "info", detail = "" }) {
  const entry = {
    id: randomUUID(),
    timestamp: new Date().toISOString(),
    source: String(source).slice(0, 40),
    actor: String(actor).slice(0, 80),
    action: String(action || "activity").slice(0, 100),
    status: String(status).slice(0, 24),
    detail: String(detail).replace(/https?:\/\/\S+/gi, "[link]").replace(/[\r\n\t]/g, " ").slice(0, 240)
  };
  items.unshift(entry);
  items = items.slice(0, MAX_ITEMS);
  await persist();
  return entry;
}

export function getActivities(limit = 100) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 100, MAX_ITEMS));
  return items.slice(0, safeLimit).map((entry) => ({ ...entry }));
}
