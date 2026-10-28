#!/system/bin/sh
# Diagnostico controlado: le dados, inicia o app, filtra log. Nao edita APK nem ROM.
set -u
PKG=com.vphonegaga.titan
OUT=/sdcard/Download/VPhoneGaGa_Gold_Android16_Diagnostico.txt
[ "$(id -u)" = "0" ] || { echo "Executar como root via su -c"; exit 1; }
pm path "$PKG" >/dev/null 2>&1 || { echo "Original nao instalado"; exit 2; }
{
 echo "GAGA GOLD DIAGNOSTICO $(date)"
 echo "Android $(getprop ro.build.version.release) / API $(getprop ro.build.version.sdk)"
 echo "Modelo $(getprop ro.product.model)"
 echo "ABI $(getprop ro.product.cpu.abilist)"
 echo "PAGE_SIZE $(getconf PAGE_SIZE)"
 echo "SELinux $(getenforce 2>/dev/null)"
 echo "Compat PM $(getprop pm.16kb.app_compat.disabled)"
 echo "Compat linker $(getprop bionic.linker.16kb.app_compat.enabled)"
 pm path "$PKG"
 dumpsys package "$PKG" 2>/dev/null | grep -E -m 8 'versionCode=|versionName=|targetSdk=|minSdk='
} > "$OUT"
monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 18
{
 echo "--- PROCESSOS ---"
 ps -A 2>/dev/null | grep -Ei 'vphonegaga|goxome|userkernel|vphone' | head -n 25
 echo "--- LOGCAT FILTRADO (sem limpar logs) ---"
 logcat -b main -b system -b crash -d -v threadtime -t 6000 2>/dev/null | grep -i -E 'com\.vphonegaga\.titan|goxome|VPhone|userkernel|androidfs|ENGINE_ERROR|JNI|AndroidRuntime|FATAL EXCEPTION|Fatal signal|signal 11|SIGSEGV|SIGABRT|linker|dlopen|UnsatisfiedLinkError|ClassNotFoundException|NoClassDefFoundError|denied|zygote|seccomp|execve|fork failed|mmap failed|page size' | tail -n 650
} >> "$OUT"
echo "Gerado: $OUT. Revise antes de compartilhar."
