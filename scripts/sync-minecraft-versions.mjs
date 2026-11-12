import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUTPUT = path.join(ROOT, 'minecraft-versions.json');
const JAVA_MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const BEDROCK_LINKS_URL = 'https://net-secondary.web.minecraft-services.net/api/v1.0/download/links';
const BEDROCK_PAGE_URL = 'https://www.minecraft.net/en-us/download/server/bedrock';

async function fetchJson(url, label) {
  const response = await fetch(url, {
    headers: {
      accept: 'application/json',
      'user-agent': 'SeverMinecraft-VersionCatalog/1.0'
    },
    signal: AbortSignal.timeout(20000)
  });
  if (!response.ok) throw new Error(`${label}: HTTP ${response.status}`);
  const type = response.headers.get('content-type') || '';
  if (!type.includes('json')) throw new Error(`${label}: nguồn không trả về JSON`);
  return response.json();
}

function assertVersion(value, label) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*$/.test(value)) {
  }
  return value;
}

function officialUrl(value, hosts, label) {
  let url;
  try { url = new URL(value); } catch { throw new Error(`${label}: URL không hợp lệ`); }
  if (url.protocol !== 'https:' || !hosts.includes(url.hostname)) {
    throw new Error(`${label}: URL không thuộc máy chủ tải chính thức được cho phép`);
  }
  return url.toString();
}

function parseBedrockLink(links, type, required) {
  const record = links.find((item) => item?.downloadType === type);
  if (!record) {
    if (required) throw new Error(`Thiếu liên kết Bedrock chính thức: ${type}`);
    return null;
  }
  const url = officialUrl(record.downloadUrl, ['www.minecraft.net'], type);
  const match = url.match(/bedrock-server-([^/]+)\.zip$/i);
  if (!match) throw new Error(`Không đọc được phiên bản Bedrock từ ${type}`);
  return { version: assertVersion(match[1], type), url };
}

async function buildCatalog() {
  const [manifest, bedrockResponse] = await Promise.all([
    fetchJson(JAVA_MANIFEST_URL, 'Java version manifest'),
    fetchJson(BEDROCK_LINKS_URL, 'Bedrock official download links')
  ]);

  if (!manifest?.latest?.release || !Array.isArray(manifest.versions)) {
    throw new Error('Java version manifest thiếu latest.release hoặc versions');
  }

  const latestRelease = assertVersion(manifest.latest.release, 'Java latest release');
  const latestSnapshot = assertVersion(manifest.latest.snapshot, 'Java latest snapshot');
  const javaVersions = manifest.versions
    .filter((entry) => entry && typeof entry.id === 'string' &&
      ['release', 'snapshot', 'old_beta', 'old_alpha'].includes(entry.type) &&
      typeof entry.url === 'string')
    .map((entry) => ({
      id: assertVersion(entry.id, 'Java version id'),
      type: entry.type,
      releaseTime: typeof entry.releaseTime === 'string' ? entry.releaseTime : null,
      metadataUrl: officialUrl(entry.url, ['piston-meta.mojang.com'], 'Java version metadata'),
      sha1: typeof entry.sha1 === 'string' && /^[a-f0-9]{40}$/i.test(entry.sha1) ? entry.sha1 : null,
      complianceLevel: Number.isInteger(entry.complianceLevel) ? entry.complianceLevel : null
    }));

  if (!javaVersions.some((entry) => entry.id === latestRelease && entry.type === 'release')) {
    throw new Error('Không tìm thấy metadata cho Java release mới nhất');
  }

  const latestReleaseEntry = manifest.versions.find((entry) => entry.id === latestRelease);
  const latestJavaMetadata = await fetchJson(
    officialUrl(latestReleaseEntry.url, ['piston-meta.mojang.com'], 'Java latest metadata'),
    'Java latest version metadata'
  );
  const javaServer = latestJavaMetadata?.downloads?.server;
  if (!javaServer?.url || !/^[a-f0-9]{40}$/i.test(javaServer.sha1 || '')) {
    throw new Error('Metadata Java mới nhất chưa có server download URL/SHA-1 hợp lệ');
  }

  const links = bedrockResponse?.result?.links;
  if (!Array.isArray(links)) throw new Error('API Bedrock không trả về danh sách links');

  const bedrockWindows = parseBedrockLink(links, 'serverBedrockWindows', true);
  const bedrockLinux = parseBedrockLink(links, 'serverBedrockLinux', true);
  if (bedrockWindows.version !== bedrockLinux.version) {
    throw new Error('Phiên bản Bedrock Windows và Linux không khớp; giữ nguyên danh mục cũ');
  }
  const bedrockPreviewWindows = parseBedrockLink(links, 'serverBedrockPreviewWindows', false);
  const bedrockPreviewLinux = parseBedrockLink(links, 'serverBedrockPreviewLinux', false);
  let bedrockPreview = null;
  if (bedrockPreviewWindows && bedrockPreviewLinux &&
      bedrockPreviewWindows.version === bedrockPreviewLinux.version) {
    bedrockPreview = {
      version: bedrockPreviewWindows.version,
      windowsUrl: bedrockPreviewWindows.url,
      linuxUrl: bedrockPreviewLinux.url
    };
  }

  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    syncStatus: 'synced',
    sources: {
      javaManifest: JAVA_MANIFEST_URL,
      bedrockOfficialDownloadLinks: BEDROCK_LINKS_URL,
      bedrockOfficialDownloadPage: BEDROCK_PAGE_URL
    },
    editions: {
      java: {
        latestRelease,
        latestSnapshot,
        serverDownload: {
          version: latestRelease,
          url: officialUrl(javaServer.url, ['piston-data.mojang.com'], 'Java server.jar'),
          sha1: javaServer.sha1.toLowerCase()
        },
        versions: javaVersions
      },
      bedrock: {
        latestRelease: {
          version: bedrockWindows.version,
          windowsUrl: bedrockWindows.url,
          linuxUrl: bedrockLinux.url
        },
        latestPreview: bedrockPreview
      }
    },
    compatibilityNotes: [
      'Danh mục phiên bản chính thức không tự chứng minh Paper, Purpur, Spigot, Forge, Fabric, NeoForge, Quilt hoặc Plugin/Mod đã tương thích.',
      'Phải kiểm tra phiên bản phần mềm máy chủ và Mod/Plugin riêng trước khi cho phép nâng cấp.',
      'Không tự nâng cấp thế giới đang chạy. Phải sao lưu, kiểm tra tương thích, bảo trì và xác nhận trước khi thay đổi.',
      'Bedrock Dedicated Server chính thức có gói Windows/Linux; tài liệu Minecraft Support nêu không có bản ARM chính thức. Không coi gói Linux x86-64 là chạy được trên Vivo Android ARM.'
    ]
  };
}

try {
  const catalog = await buildCatalog();
  const json = JSON.stringify(catalog, null, 2) + '\n';
  await fs.writeFile(OUTPUT, json, 'utf8');
  console.log(`Đã đồng bộ Java ${catalog.editions.java.latestRelease} và Bedrock ${catalog.editions.bedrock.latestRelease.version}`);
  console.log(`Đã ghi ${catalog.editions.java.versions.length} mục phiên bản Java vào minecraft-versions.json`);
} catch (error) {
  console.error(`Không cập nhật danh mục; giữ nguyên dữ liệu cũ: ${error.message}`);
  process.exitCode = 1;
}
