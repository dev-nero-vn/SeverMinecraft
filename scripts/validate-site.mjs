import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const read = (file) => fs.readFileSync(file, 'utf8');
const exists = (file) => assert.ok(fs.existsSync(file), `Thiếu tệp bắt buộc: ${file}`);

for (const file of ['index.html','styles.css','mc-theme.css','app.js','manifest.webmanifest','icon.svg','server-icon.png','minecraft-versions.json','scripts/sync-minecraft-versions.mjs']) exists(file);

const html = read('index.html');
const css = read('styles.css');
const theme = read('mc-theme.css');
const app = read('app.js');
const manifest = JSON.parse(read('manifest.webmanifest'));
const versionCatalog = JSON.parse(read('minecraft-versions.json'));
const versionSyncScript = read('scripts/sync-minecraft-versions.mjs');

assert.match(html, /<html lang="vi">/, 'Trang phải khai báo tiếng Việt');
assert.match(html, /name="viewport"/, 'Thiếu viewport cho thiết bị di động');
assert.match(html, /src="\.\/app\.js"[^>]*defer/, 'app.js phải được tải defer');
assert.match(html, /href="\.\/mc-theme\.css"/, 'Chưa nạp chủ đề Minecraft');
assert.ok(!html.includes(String.fromCharCode(92) + 'n  <link'), 'HTML còn ký tự xuống dòng bị ghi sai trong head');
assert.match(html, /data-view="versions"/, 'Thiếu trang phiên bản Minecraft');
assert.equal(versionCatalog.schemaVersion, 1, 'Sai schema danh mục phiên bản');
assert.equal(versionCatalog.sources.javaManifest, 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', 'Sai nguồn Java chính thức');
assert.match(versionSyncScript, /piston-meta\.mojang\.com\/mc\/game\/version_manifest_v2\.json/, 'Bộ đồng bộ thiếu nguồn Java');
assert.match(versionSyncScript, /net-secondary\.web\.minecraft-services\.net\/api\/v1\.0\/download\/links/, 'Bộ đồng bộ thiếu nguồn Bedrock');
assert.match(versionSyncScript, /process\.exitCode = 1/, 'Lỗi nguồn phải làm workflow thất bại, không ghi đè dữ liệu cũ');
assert.equal(manifest.lang, 'vi', 'Manifest phải đặt ngôn ngữ tiếng Việt');
assert.equal(manifest.display, 'standalone', 'Manifest phải hỗ trợ hiển thị dạng ứng dụng');
assert.ok(Array.isArray(manifest.icons) && manifest.icons.length > 0, 'Manifest thiếu biểu tượng');

for (const ref of [...html.matchAll(/(?:src|href)="\.\/([^"#?]+)"/g)].map(m => m[1])) {
  exists(ref);
}
for (const ref of [...app.matchAll(/["']\.\/([A-Za-z0-9_.-]+\.(?:png|svg|css|js))["']/g)].map(m => m[1])) {
  exists(ref);
}
for (const icon of manifest.icons) exists(icon.src.replace(/^\.\//, ''));

const png = fs.readFileSync('server-icon.png');
assert.equal(png.subarray(0,8).toString('hex'), '89504e470d0a1a0a', 'server-icon.png không phải PNG hợp lệ');
assert.equal(png.readUInt32BE(16), 64, 'server-icon.png phải rộng 64 px');
assert.equal(png.readUInt32BE(20), 64, 'server-icon.png phải cao 64 px');

const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
assert.equal(ids.length, new Set(ids).size, 'HTML có id bị trùng');
const staticIds = new Set(ids);
const dynamicIds = new Set(['selected-file','server-version','version-sync-status']);
for (const id of [...app.matchAll(/getElementById\(['"]([^'"]+)['"]\)/g)].map(m => m[1])) {
  assert.ok(staticIds.has(id) || dynamicIds.has(id), `JavaScript tham chiếu id không tồn tại: ${id}`);
}

const titleMatch = app.match(/const titles=(\{[\s\S]*?\});\nconst descriptions=/);
assert.ok(titleMatch, 'Không tìm thấy danh sách tiêu đề trang');
const titles = vm.runInNewContext('(' + titleMatch[1] + ')');
const navViews = [...html.matchAll(/<button class="nav-link[^"]*" data-view="([^"]+)"/g)].map(m => m[1]);
assert.equal(navViews.length, new Set(navViews).size, 'Có mục điều hướng bị lặp');
for (const view of navViews) assert.ok(Object.hasOwn(titles, view), `Mục điều hướng chưa có tiêu đề: ${view}`);
for (const view of Object.keys(titles)) assert.ok(navViews.includes(view), `Trang chưa có mục menu: ${view}`);

const renderBlock = app.match(/function render\(view\)\{([\s\S]*?)\nfunction go\(/)?.[1] || '';
const routeCases = [...renderBlock.matchAll(/case'([^']+)':/g)].map(m => m[1]);
for (const view of navViews) assert.ok(routeCases.includes(view), `Trang chưa có bộ điều hướng render: ${view}`);
for (const target of [...new Set([...app.matchAll(/data-go=["']([^"']+)["']/g)].map(m => m[1]))]) {
  assert.ok(Object.hasOwn(titles, target), `Nút điều hướng đến trang không tồn tại: ${target}`);
}
for (const action of ['new-community','new-private','backend']) {
  assert.ok(app.includes(`a==='${action}'`) || action === 'backend' && app.includes('data-action'), `Thiếu xử lý thao tác: ${action}`);
}

assert.match(css, /@media\(max-width:820px\)/, 'Thiếu bố cục cho màn hình nhỏ');
assert.match(css, /@media\(max-width:560px\)/, 'Thiếu bố cục cho điện thoại');
assert.match(theme, /prefers-reduced-motion/, 'Thiếu hỗ trợ giảm chuyển động');
assert.match(theme, /@keyframes mc-page-in/, 'Thiếu hoạt ảnh chuyển trang');
for (const cls of ['.mc-version-hero','.mc-pixel-ground','.mc-edition-card','.mc-version-row','.mc-update-safety']) {
  assert.ok(theme.includes(cls), `Thiếu thành phần chủ đề Minecraft: ${cls}`);
  assert.ok(app.includes(cls.slice(1)), `Trang phiên bản chưa sử dụng thành phần: ${cls}`);
}

function createHarness(seedValue = null, blockWrites = false, fetchImpl = undefined) {
  const handlers = {};
  const elements = new Map();
  const store = new Map();
  if (seedValue !== null) store.set('mc-platform-drafts', seedValue);
  const makeClassList = () => ({ add(){}, remove(){}, toggle(){ return false; } });
  function element(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        id, innerHTML: '', textContent: '', dataset: {}, focusCount: 0,
        classList: makeClassList(), addEventListener(){}, focus(){ this.focusCount += 1; }
      });
    }
    return elements.get(id);
  }
  const navButtons = navViews.map(view => ({ dataset: { view }, classList: makeClassList() }));
  const document = {
    getElementById: element,
    querySelectorAll(selector) { return selector === '.nav-link' ? navButtons : []; },
    addEventListener(type, fn) { handlers[type] = fn; }
  };
  const localStorage = {
    getItem(key) { return store.has(key) ? store.get(key) : null; },
    setItem(key, value) {
      if (blockWrites) throw new Error('Storage unavailable');
      store.set(key, String(value));
    }
  };
  class FakeFormData {
    constructor(form) { this.values = form.values || {}; }
    get(key) { return this.values[key] ?? null; }
  }
  vm.runInNewContext(app, {
    document, localStorage, FormData: FakeFormData, fetch: fetchImpl,
    setTimeout(){ return 1; }, clearTimeout(){},
    Date, JSON, Math, Number, String, Array, Object, Set
  }, { timeout: 1500 });
  function click({ view, go, action, deleteId } = {}) {
    const target = {
      closest(selector) {
        if (selector === '[data-view]' && view) return { dataset: { view } };
        if (selector === '[data-go]' && go) return { dataset: { go } };
        if (selector === '[data-action]' && action) return { dataset: { action } };
        if (selector === '[data-delete-draft]' && deleteId) return { dataset: { deleteDraft: deleteId } };
        return null;
      }
    };
    handlers.click({ target });
  }
  function submit(type, name = 'Server kiểm thử', maxPlayers = '42') {
    const form = {
      id: 'create-server-form',
      dataset: { type },
      values: { name, maxPlayers, version: '1.21.x', software: 'Paper', access: 'Riêng tư',
        crossplay: 'Có — nếu cấu hình tương thích', description: 'Bản kiểm thử' }
    };
    handlers.submit({ target: form, preventDefault(){} });
  }
  return { handlers, elements, store, click, submit, navButtons };
}

// Smoke test every navigation item: each page must render and update its breadcrumb exactly once.
const h = createHarness();
for (const view of navViews) {
  const focusBefore = h.elements.get('page-content').focusCount;
  h.click({ view });
  assert.equal(h.elements.get('breadcrumb-current').textContent, titles[view], `Sai breadcrumb: ${view}`);
  assert.ok(h.elements.get('page-content').innerHTML.length > 40, `Trang rỗng: ${view}`);
  assert.equal(h.elements.get('page-content').focusCount, focusBefore + 1, `Điều hướng bị gọi lặp: ${view}`);
}

// Private draft: player limit persists and user text is HTML-escaped.
h.click({ go: 'create' });
h.submit('private', '<img src=x onerror=alert(1)>', '42');
let saved = JSON.parse(h.store.get('mc-platform-drafts'));
assert.equal(saved.length, 1, 'Không lưu được máy chủ riêng');
assert.equal(saved[0].maxPlayers, 42, 'Số người chơi tối đa không được lưu đúng');
assert.ok(h.elements.get('page-content').innerHTML.includes('&lt;img'), 'Tên người dùng chưa được escape HTML');
assert.ok(!h.elements.get('page-content').innerHTML.includes('<img src=x'), 'Có nguy cơ chèn HTML từ tên máy chủ');

// Community empty state must open the community form, not the private form.
h.click({ view: 'community' });
assert.ok(h.elements.get('page-content').innerHTML.includes('data-action="new-community"'), 'Nút tạo cộng đồng bị thiếu');
h.click({ action: 'new-community' });
assert.ok(h.elements.get('page-content').innerHTML.includes('data-type="community"'), 'Nút cộng đồng mở sai biểu mẫu');
h.submit('community', 'Thế giới bạn bè');
saved = JSON.parse(h.store.get('mc-platform-drafts'));
assert.equal(saved.length, 2, 'Không lưu được máy chủ cộng đồng');
assert.equal(saved[1].type, 'community', 'Sai loại máy chủ cộng đồng');
assert.equal(saved[1].maxPlayers, null, 'Máy chủ cộng đồng không nên lưu giới hạn riêng');

// Delete draft must persist; malformed and oversized storage must be handled safely.
h.click({ deleteId: saved[1].id });
saved = JSON.parse(h.store.get('mc-platform-drafts'));
assert.equal(saved.length, 1, 'Xóa bản nháp không cập nhật bộ nhớ');

const corrupt = createHarness('{not-json');
assert.ok(corrupt.elements.get('page-content').innerHTML.length > 40, 'Dữ liệu localStorage lỗi làm hỏng trang');
const objectStorage = createHarness('{"not":"an array"}');
assert.equal(JSON.parse(objectStorage.store.get('mc-platform-drafts')).length, 0, 'Dữ liệu localStorage sai kiểu không được làm sạch');

const tooMany = Array.from({length: 25}, (_, i) => ({ id: String(i), type: 'private', name: 'Máy chủ ' + i, maxPlayers: 10 }));
const capped = createHarness(JSON.stringify(tooMany));
assert.equal(JSON.parse(capped.store.get('mc-platform-drafts')).length, 20, 'Giới hạn 20 cấu hình không được áp dụng khi đọc dữ liệu cũ');
capped.click({ go: 'create' });
capped.submit('private');
assert.match(capped.elements.get('toast').textContent, /giới hạn 20/, 'Không chặn cấu hình thứ 21');

const blocked = createHarness(null, true);
blocked.click({ go: 'create' });
blocked.submit('private');
assert.match(blocked.elements.get('toast').textContent, /Không thể lưu lâu dài/, 'Không cảnh báo khi trình duyệt chặn lưu trữ');

const mockCatalog = {
  schemaVersion: 1, generatedAt: '2026-11-12T08:00:00.000Z', syncStatus: 'synced',
  editions: {
    java: { latestRelease: '26.3', latestSnapshot: '26.4-snapshot-7', serverDownload: { url: 'https://piston-data.mojang.com/server.jar' }, versions: [{ id: '26.3', type: 'release', releaseTime: '2026-09-15T11:23:02Z' }, { id: '26.4-snapshot-7', type: 'snapshot', releaseTime: '2026-11-03T13:08:56Z' }] },
    bedrock: { latestRelease: { version: '1.26.63.1', windowsUrl: 'https://www.minecraft.net/bedrockdedicatedserver/bin-win/bedrock-server-1.26.63.1.zip', linuxUrl: 'https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-1.26.63.1.zip' }, latestPreview: null }
  }
};
const versionHarness = createHarness(null, false, async () => ({ ok: true, json: async () => mockCatalog }));
versionHarness.click({ view: 'versions' });
await new Promise(resolve => setTimeout(resolve, 20));
assert.ok(versionHarness.elements.get('page-content').innerHTML.includes('26.3'), 'Trang phiên bản không hiển thị Java latest');
assert.ok(versionHarness.elements.get('page-content').innerHTML.includes('1.26.63.1'), 'Trang phiên bản không hiển thị Bedrock latest');

console.log('KIỂM TRA THÀNH CÔNG: tài nguyên, manifest, điều hướng, biểu mẫu, giới hạn 20, lưu trữ, escape HTML và danh mục phiên bản Java/Bedrock.');
