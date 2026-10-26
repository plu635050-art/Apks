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
  required_tools=('mycp','aapt')
  for abi in expected:
   for tool in required_tools:
    p=f'assets/{abi}/{tool}'
    if p not in names:
     raise SystemExit(f'FALHA: ferramenta {p} ausente')
  for tool in sorted(x for x in names if x.startswith('assets/') and (x.endswith('/aapt') or x.endswith('/aapt2') or x.endswith('/mycp') or x in ('assets/mycp','assets/aapt'))):
   blob=z.read(tool)
   if blob[:4]==bytes((127,69,76,70)) and len(blob)>20:
    cls='64' if blob[4]==2 else '32'
    arch={40:'ARM',183:'ARM64',3:'x86',62:'x86_64'}.get(int.from_bytes(blob[18:20],'little'),'desconhecida')
    out.append(f'  {tool}: ELF{cls} {arch}')
   else:
    out.append(f'  {tool}: formato nao identificado')
  for abi, expected_arch in (('armeabi-v7a',(1,40)),('arm64-v8a',(2,183)),('x86',(1,3)),('x86_64',(2,62))):
   p=f'assets/{abi}/mycp'
   blob=z.read(p)
   found=(blob[4],int.from_bytes(blob[18:20],'little'))
   if found!=expected_arch:
    raise SystemExit(f'FALHA: mycp {abi} {found} != {expected_arch}')
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
