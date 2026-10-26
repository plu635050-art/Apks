#!/usr/bin/env python3
from pathlib import Path
import hashlib
import sys
from zipfile import ZipFile
root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('source/app/build/outputs/apk')
apks = sorted(root.rglob('*.apk'))
if not apks:
    raise SystemExit(f'ERRO: nenhum APK em {root}')
report = []
for apk in apks:
    with ZipFile(apk) as z:
        bad = z.testzip()
        if bad:
            raise SystemExit(f'ERRO ZIP: {apk}: {bad}')
        files = set(z.namelist())
        if not {'AndroidManifest.xml', 'classes.dex'}.issubset(files):
            raise SystemExit(f'ERRO: arquivos essenciais faltando em {apk}')
        abis = sorted({p.split('/')[1] for p in files if p.startswith('lib/') and len(p.split('/')) > 2})
        dig = hashlib.sha256(apk.read_bytes()).hexdigest()
        report.extend((f'APK: {apk}', f'Tamanho: {apk.stat().st_size} bytes', f'SHA256: {dig}', f'ABIs nativas: {abis or "nenhuma empacotada"}', 'Integridade ZIP: OK', ''))
Path('resultado-auditoria.txt').write_text('\n'.join(report), encoding='utf-8')
print('\n'.join(report))
