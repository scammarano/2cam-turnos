"""Build a prototype source ZIP; intentionally NOT a Signage update or an APK."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parents[1]
out = root / 'dist'
out.mkdir(exist_ok=True)
archive = out / '2cam-turnos-v0.1.0-t0.zip'
entries = [root / n for n in ['README.md', 'VERSION', 'package.json']]
for folder in ['public', 'docs', 'database', 'tests', 'scripts']:
    entries.extend(p for p in (root / folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for source in sorted(entries):
        z.write(source, '2cam-turnos/' + source.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert '2cam-turnos/public/demo/index.html' in z.namelist()
    assert all(not name.endswith(('.apk', '.env', '.pem', '.key')) for name in z.namelist())
sha = hashlib.sha256(archive.read_bytes()).hexdigest()
(out / 'SHA256SUMS.txt').write_text(f'{sha}  {archive.name}\n')
print(f'{archive.name}: {archive.stat().st_size} bytes; sha256 {sha}')
