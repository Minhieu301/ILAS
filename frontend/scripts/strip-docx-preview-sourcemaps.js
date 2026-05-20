const fs = require('fs');
const path = require('path');

function stripSourceMapComments(filePath) {
  if (!fs.existsSync(filePath)) {
    return false;
  }

  const original = fs.readFileSync(filePath, 'utf8');
  const updated = original.replace(/\n?\/\/#[#@] sourceMappingURL=.*$/gm, '');

  if (updated === original) {
    return false;
  }

  fs.writeFileSync(filePath, updated, 'utf8');
  return true;
}

function walkAndStrip(rootDir) {
  if (!fs.existsSync(rootDir)) {
    return 0;
  }

  let updatedCount = 0;
  const entries = fs.readdirSync(rootDir, { withFileTypes: true });

  for (const entry of entries) {
    const entryPath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      updatedCount += walkAndStrip(entryPath);
      continue;
    }

    if (entry.isFile() && (entry.name.endsWith('.js') || entry.name.endsWith('.mjs'))) {
      if (stripSourceMapComments(entryPath)) {
        updatedCount += 1;
      }
    }
  }

  return updatedCount;
}

function findPackageRoot(startPath) {
  let currentDir = path.dirname(startPath);

  while (currentDir && currentDir !== path.dirname(currentDir)) {
    if (fs.existsSync(path.join(currentDir, 'package.json'))) {
      return currentDir;
    }

    currentDir = path.dirname(currentDir);
  }

  return null;
}

function main() {
  try {
    const entryPath = require.resolve('docx-preview');
    const packageRoot = findPackageRoot(entryPath);

    if (!packageRoot) {
      return;
    }

    const updatedCount = walkAndStrip(packageRoot);

    if (updatedCount > 0) {
      console.log(`Stripped source map comments from ${updatedCount} docx-preview file(s).`);
    }
  } catch (error) {
    if (error && error.code !== 'MODULE_NOT_FOUND') {
      console.warn('docx-preview source map cleanup skipped:', error.message);
    }
  }
}

main();
