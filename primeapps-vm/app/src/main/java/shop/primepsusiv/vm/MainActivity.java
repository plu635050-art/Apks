package shop.primepsusiv.vm;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.OsConstants;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_ROM = 41;
    private static final byte[] MAGIC_7Z = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView environment;
    private Button selectRom;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 17, 28));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL);
        column.setPadding(dp(28), dp(20), dp(28), dp(20));
        scroll.addView(column);

        TextView title = label("PrimeApps VM", 28, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        column.addView(title);
        column.addView(label("LAB 1 — Diagnóstico e importação de ROMs", 17, 0xFF8EB7FF));
        column.addView(label("Aplicativo independente. Nenhuma ROM é executada neste LAB.", 14, 0xFFB8C4D6));

        environment = label(environmentInfo(), 15, 0xFFE4E9F2);
        environment.setPadding(0, dp(18), 0, dp(14));
        column.addView(environment);

        selectRom = new Button(this);
        selectRom.setText("Selecionar ROM local (.7z)");
        selectRom.setAllCaps(false);
        selectRom.setOnClickListener(v -> openRom());
        column.addView(selectRom);

        status = label("Selecione androidfs_10.0.0.7z ou androidfs_7.1.2.7z.\n"
                + "Verificação: formato 7z, tamanho e SHA-256.\n"
                + "Motor de virtualização: NÃO integrado.", 15, Color.WHITE);
        status.setPadding(0, dp(16), 0, 0);
        column.addView(status);

        setContentView(scroll);
    }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_ROM);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_ROM || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final String name = fileName(uri);
        selectRom.setEnabled(false);
        status.setText("Analisando " + name + " ...\nO arquivo original não será modificado.");
        ioExecutor.execute(() -> inspectRom(uri, name));
    }

    private void inspectRom(Uri uri, String name) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("O provedor não abriu o arquivo");
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            byte[] head = new byte[6];
            int headCount = 0;
            int count;
            long size = 0;
            while ((count = in.read(buffer)) != -1) {
                if (count == 0) continue;
                int copy = Math.min(count, head.length - headCount);
                if (copy > 0) {
                    System.arraycopy(buffer, 0, head, headCount, copy);
                    headCount += copy;
                }
                hash.update(buffer, 0, count);
                size += count;
            }
            boolean sevenZip = headCount == MAGIC_7Z.length;
            for (int i = 0; i < head.length && sevenZip; i++) {
                if (head[i] != MAGIC_7Z[i]) sevenZip = false;
            }
            StringBuilder digest = new StringBuilder();
            for (byte b : hash.digest()) {
                digest.append(String.format(Locale.US, "%02x", b & 0xFF));
            }
            String report = "Arquivo: " + name + "\nTamanho: " + size + " bytes"
                    + "\nCabeçalho 7z: " + (sevenZip ? "válido" : "não reconhecido")
                    + "\nSHA-256: " + digest + "\n\n"
                    + "Integridade do arquivo externo: ainda não verificada contra hash de referência.\n"
                    + "Compatibilidade com motor: não testada.\n"
                    + "Inicialização da máquina virtual: não implementada neste LAB.";
            runOnUiThread(() -> status.setText(report));
        } catch (Exception error) {
            String message = "Falha ao ler ROM: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage();
            runOnUiThread(() -> status.setText(message));
        } finally {
            runOnUiThread(() -> selectRom.setEnabled(true));
        }
    }

    private String fileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
            // Nome de exibição opcional; fallback abaixo.
        }
        return "rom-local";
    }

    private String environmentInfo() {
        long pageSize;
        try {
            pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
        } catch (Exception error) {
            pageSize = -1;
        }
        return "Android do aparelho: " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nArquitetura do processo: " + (Process.is64Bit() ? "64 bits" : "32 bits")
                + "\nTamanho da página de memória: " + pageSize + " bytes"
                + "\nMotor virtual: ausente (diagnóstico inicial)";
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
