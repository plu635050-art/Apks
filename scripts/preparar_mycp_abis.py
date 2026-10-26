#!/usr/bin/env python3
"""Compila a ferramenta mycp original do APK Editor para as 4 ABIs.
Atualiza a copia para usar o ELF da arquitetura do aparelho.
Nao altera o formato da chamada: mycp <origem> <destino>.
"""
from pathlib import Path
import os
import subprocess
import sys
root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path("original").resolve()
ndk=Path(os.environ["ANDROID_HOME"])/"ndk"/os.environ.get("ANDROID_NDK_VERSION","25.2.9519653")
bin_dir=ndk/"toolchains"/"llvm"/"prebuilt"/"linux-x86_64"/"bin"
src=root/"apkEditor"/"src"/"main"/"jni"/"mycopy"/"jni"/"mycopy.c"
targets={
 "armeabi-v7a":"armv7a-linux-androideabi21-clang",
 "arm64-v8a":"aarch64-linux-android21-clang",
 "x86":"i686-linux-android21-clang",
 "x86_64":"x86_64-linux-android21-clang",
}
for abi,compiler in targets.items():
 output=root/"apkEditorPro"/"src"/"main"/"assets"/abi/"mycp"
 output.parent.mkdir(parents=True,exist_ok=True)
 subprocess.run([str(bin_dir/compiler),"-O2","-fPIE","-pie",str(src),"-o",str(output)],check=True)
 print(abi, output.stat().st_size, output)
target=root/"apkEditor"/"src"/"main"/"java"/"com"/"gmail"/"heagoo"/"apkeditor"/"MainActivity.java"
code=target.read_text(encoding="utf-8")
old='''        // Copy mycp
        try {
            File bin = new File(this.getFilesDir(), "mycp");
            if (!bin.exists() && (Build.VERSION.SDK_INT >= 20)) {
                InputStream input = getAssets().open("mycp");
                FileOutputStream output = new FileOutputStream(bin);
                IOUtils.copy(input, output);
                input.close();
                output.close();
                bin.setExecutable(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }'''
new='''        // Copy mycp using the ELF that matches the phone architecture.
        // Always refresh it: a prior 32-bit installation may have copied
        // an ARM32 binary into filesDir, which cannot run on 64-bit-only Android.
        try {
            if (Build.VERSION.SDK_INT >= 20) {
                String abi = (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS.length > 0)
                        ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
                File bin = new File(this.getFilesDir(), "mycp");
                InputStream input = getAssets().open(abi + "/mycp");
                FileOutputStream output = new FileOutputStream(bin, false);
                IOUtils.copy(input, output);
                input.close();
                output.close();
                bin.setExecutable(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }'''
if code.count(old)!=1:
 raise SystemExit(f'ERRO: trecho mycp esperado nao encontrado exatamente uma vez; matches={code.count(old)}')
target.write_text(code.replace(old,new),encoding="utf-8")
print("MainActivity: selecao mycp por ABI aplicada")
