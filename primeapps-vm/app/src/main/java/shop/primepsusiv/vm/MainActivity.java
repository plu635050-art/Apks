package shop.primepsusiv.vm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_ROM = 41;
    private static final byte[] MAGIC_7Z = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final int BACKGROUND = 0xFF0B111C;
    private static final int BLUE = 0xFF8EB7FF;
    private static final int MUTED = 0xFFB8C4D6;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private VmRegistry registry;
    private String selectedRomId = null;
    private TextView status;
    private TextView selectedRom;
    private LinearLayout instancesRoot;
    private Button selectRom;
    private Button addThree;
    private Button addOne;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            registry = new VmRegistry(this);
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this).setTitle("Catálogo protegido")
                    .setMessage(error.getMessage()).setPositiveButton("Fechar", (dialog, which) -> finish())
                    .setCancelable(false).show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), dp(18), dp(24), dp(24));
        scroll.addView(column);

        TextView title = text("PrimeApps VM", 25, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        column.addView(title);
        column.addView(text("LAB 2 — Cadastro e clonagem de configurações", 17, BLUE));
        column.addView(text("Motor de virtualização NÃO integrado. Nenhuma máquina executa neste LAB.", 14, MUTED));

        TextView env = text(environmentInfo(), 13, MUTED);
        env.setPadding(0, dp(12), 0, dp(12));
        column.addView(env);

        selectRom = action("Importar ROM local (.7z)", column, this::openRom);
        selectedRom = text("ROM selecionada: nenhuma", 15, Color.WHITE);
        selectedRom.setPadding(0, dp(10), 0, dp(5));
        column.addView(selectedRom);

        addThree = action("Preparar 3 instâncias da mesma ROM", column,
                () -> register(3));
        addOne = action("Preparar mais uma instância", column,
                () -> register(1));

        status = text("A ROM-base é referenciada uma vez. Cada instância ganha um ID e diretório próprios. "
                + "Isso ainda não cria um disco Android inicializável.", 13, MUTED);
        status.setPadding(0, dp(11), 0, dp(12));
        column.addView(status);

        TextView heading = text("Instâncias cadastradas", 20, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        column.addView(heading);
        instancesRoot = new LinearLayout(this);
        instancesRoot.setOrientation(LinearLayout.VERTICAL);
        column.addView(instancesRoot);

        setContentView(scroll);
        render();
    }

    private void render() {
        List<VmRegistry.Rom> roms = registry.roms();
        if (selectedRomId == null && !roms.isEmpty()) selectedRomId = roms.get(roms.size() - 1).id;
        VmRegistry.Rom selected = selectedRomId == null ? null : registry.findRom(selectedRomId);
        selectedRom.setText(selected == null ? "ROM selecionada: nenhuma"
                : "ROM: " + selected.displayName + " (" + selected.sizeBytes + " bytes)\n"
                        + "SHA-256: " + selected.sha256);

        boolean allowed = selected != null
                && registry.instances().size() < VmRegistry.MAX_CONFIGURED_INSTANCES;
        addOne.setEnabled(allowed);
        addThree.setEnabled(allowed && registry.instances().size() + 3 <= VmRegistry.MAX_CONFIGURED_INSTANCES);

        instancesRoot.removeAllViews();
        List<VmRegistry.Instance> all = registry.instances();
        if (all.isEmpty()) instancesRoot.addView(text("Ainda não há instâncias cadastradas.", 14, MUTED));
        for (VmRegistry.Instance inst : all) {
            VmRegistry.Rom rom = registry.findRom(inst.romId);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13), dp(12), dp(13), dp(12));
            card.setBackgroundColor(0xFF19283E);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layout.topMargin = dp(9);
            instancesRoot.addView(card, layout);
            TextView name = text(inst.displayName + "   •   Preparada, não iniciada", 16, Color.WHITE);
            name.setTypeface(null, Typeface.BOLD);
            card.addView(name);
            card.addView(text("Base: " + (rom == null ? "ausente" : rom.displayName)
                    + "\nID: " + inst.id
                    + "\nArmazenamento privado: instances/" + inst.id + "/data"
                    + "\nBoot simultâneo: indisponível até integração do motor", 12, MUTED));
            action("Clonar cadastro desta ROM (+1)", card, () -> {
                selectedRomId = inst.romId;
                register(1);
            });
        }
    }

    private void register(int count) {
        if (selectedRomId == null) return;
        try {
            VmRegistry.Rom rom = registry.findRom(selectedRomId);
            if (rom == null) throw new IllegalStateException("ROM inexistente");
            boolean grantPresent = false;
            for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
                if (p.getUri().toString().equals(rom.uri) && p.isReadPermission()) {
                    grantPresent = true;
                    break;
                }
            }
            if (!grantPresent) {
                throw new IllegalStateException("O acesso à ROM foi perdido. Importe o arquivo novamente.");
            }
            registry.registerInstances(selectedRomId, count);
            status.setText(count + " cadastro(s) criados. A imagem-base não foi copiada. "
                    + "Cada cadastro possui armazenamento próprio vazio; o motor ainda não inicializa ROMs.");
            render();
        } catch (Exception error) {
            alert("Não foi possível criar instâncias", error.getMessage());
        }
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
        if (request != REQUEST_ROM || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = fileName(uri);
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags == 0) throw new SecurityException("Sem permissão de leitura");
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException error) {
            alert("Acesso permanente necessário",
                    "O provedor de arquivos não autorizou acesso após reiniciar. "
                            + "Escolha a ROM num gerenciador compatível com Documentos.");
            return;
        }
        selectRom.setEnabled(false);
        status.setText("Validando " + name + " ... Nenhum arquivo será modificado.");
        ioExecutor.execute(() -> inspectRom(uri, name));
    }

    private void inspectRom(Uri uri, String name) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Provedor não abriu o arquivo");
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            byte[] head = new byte[6];
            int headCount = 0;
            int count;
            long size = 0;
            while ((count = in.read(buffer)) != -1) {
                if (count == 0) continue;
                int n = Math.min(count, head.length - headCount);
                if (n > 0) {
                    System.arraycopy(buffer, 0, head, headCount, n);
                    headCount += n;
                }
                hash.update(buffer, 0, count);
                size += count;
            }
            boolean valid = size > 0 && headCount == MAGIC_7Z.length;
            for (int i = 0; i < MAGIC_7Z.length && valid; i++) valid = head[i] == MAGIC_7Z[i];
            if (!valid) throw new IllegalArgumentException("Arquivo não possui cabeçalho 7z válido");
            StringBuilder digest = new StringBuilder();
            for (byte b : hash.digest()) digest.append(String.format(Locale.US, "%02x", b & 0xFF));
            final long fileSize = size;
            final String checksum = digest.toString();
            runOnUiThread(() -> {
                try {
                    VmRegistry.Rom rom = registry.addRom(name, uri, fileSize, checksum);
                    selectedRomId = rom.id;
                    status.setText("ROM cadastrada. Identificação por SHA-256 concluída. "
                            + "O conteúdo do arquivo não foi descompactado nem validado contra uma imagem de referência.");
                    render();
                } catch (Exception error) {
                    alert("Erro ao salvar ROM", error.getMessage());
                } finally {
                    selectRom.setEnabled(true);
                }
            });
        } catch (Exception error) {
            runOnUiThread(() -> {
                status.setText("Falha de importação: " + error.getMessage());
                selectRom.setEnabled(true);
            });
        }
    }

    private String fileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
            // Provider may not expose the optional display name.
        }
        return "rom-local.7z";
    }

    private Button action(String name, LinearLayout parent, Runnable click) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        button.setOnClickListener(v -> click.run());
        parent.addView(button);
        return button;
    }

    private TextView text(String content, int sp, int color) {
        TextView text = new TextView(this);
        text.setText(content);
        text.setTextSize(sp);
        text.setTextColor(color);
        return text;
    }

    private void alert(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private String environmentInfo() {
        long pageSize;
        try {
            pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
        } catch (Exception error) {
            pageSize = -1;
        }
        return "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "  •  Processo: " + (Process.is64Bit() ? "64 bits" : "32 bits")
                + "  •  Página: " + pageSize + " B";
    }

    private int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
