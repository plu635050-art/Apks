#!/usr/bin/env python3
"""Diagnostico do pacote original do APK Editor; nao atribui compatibilidade funcional automaticamente."""
from pathlib import Path
from zipfile import ZipFile
import sys,hashlib
root=Path(sys.argv[1])
out=[]
apks=list(root.rglob('*.apk'))
if not apks: raise SystemExit(f'Nenhum APK produzido em {root}')
for apk in apks:
 with ZipFile(apk) as z:
  assert z.testzip() is None, 'Arquivo ZIP corrompido'
  names=set(z.namelist())
  assert 'AndroidManifest.xml' in names
  assert 'classes.dex' in names
  libs={x for x in names if x.startswith('lib/') and x.endswith('.so')}
  abis=sorted({x.split('/')[1] for x in libs})
  syss=sorted(x for x in libs if x.endswith('libsyscheck.so'))
  expected={'armeabi-v7a','arm64-v8a','x86','x86_64'}
  if not expected.issubset(set(abis)):
   raise SystemExit('FALHA: ABIs faltando: '+str(expected-set(abis)))
  if not all(f'lib/{abi}/libsyscheck.so' in libs for abi in expected):
   raise SystemExit('FALHA: libsyscheck.so faltando em uma arquitetura')
  tools=[x for x in names if x.endswith(('/aapt','/aaptz','/mycp'))]
  for tool in tools:
   blob=z.read(tool)
   mode='ARM32' if len(blob)>20 and blob[:4]==b'\x7fELF' and blob[4]==1 and int.from_bytes(blob[18:20],'little')==40 else 'OUTRO'
   out.append(f'  Executavel {tool}: {mode}. Uso em Android somente 64-bit exige substituto compatível.')
  out.extend([
   f'APK: {apk.name}',
   f'Tamanho: {apk.stat().st_size}',
   f'SHA256: {hashlib.sha256(apk.read_bytes()).hexdigest()}',
   f'ABIs nativas: {", ".join(abis)}',
   f'libsyscheck: {", ".join(syss)}',
   'Integridade ZIP: OK',
   'Aviso: compilacao/estrutura NAO demonstram funcionamento de edicao em Android 16 64-bit-only.'
  ])
Path('relatorio-lab2.txt').write_text('\n'.join(out)+'\n',encoding='utf-8')
print('\n'.join(out))
