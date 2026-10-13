const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'dist');
const frontendFiles = ['index.html', 'login.html', 'script.js', 'style.css'];

fs.rmSync(output, { recursive: true, force: true });
fs.mkdirSync(output, { recursive: true });

for (const file of frontendFiles) {
    fs.copyFileSync(path.join(root, file), path.join(output, file));
}

fs.cpSync(path.join(root, 'assets'), path.join(output, 'assets'), { recursive: true });

const apiBaseUrl = (process.env.BARBER_API_BASE_URL || '').trim().replace(/\/+$/, '');
fs.writeFileSync(
    path.join(output, 'api-config.js'),
    `window.BARBER_API_BASE_URL = ${JSON.stringify(apiBaseUrl)};\n`,
    'utf8'
);
