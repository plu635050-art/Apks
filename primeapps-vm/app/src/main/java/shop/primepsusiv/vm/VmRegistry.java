package shop.primepsusiv.vm;

import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LAB 2 — durable configuration only. No guest processes, COW block
 * devices, snapshot files or actual virtual machines are created here.
 */
public final class VmRegistry {
    public static final int MAX_CONFIGURED_INSTANCES = 64;
    private static final String KEY = "catalog_v1";
    private final Context context;
    private final SharedPreferences preferences;
    private final ArrayList<Rom> roms = new ArrayList<>();
    private final ArrayList<Instance> instances = new ArrayList<>();

    public static final class Rom {
        public final String id;
        public final String displayName;
        public final String uri;
        public final long sizeBytes;
        public final String sha256;

        Rom(String id, String displayName, String uri, long sizeBytes, String sha256) {
            this.id = id;
            this.displayName = displayName;
            this.uri = uri;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }
    }

    public static final class Instance {
        public final String id;
        public final String romId;
        public final String displayName;
        public final long createdAt;

        Instance(String id, String romId, String displayName, long createdAt) {
            this.id = id;
            this.romId = romId;
            this.displayName = displayName;
            this.createdAt = createdAt;
        }
    }

    public VmRegistry(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("primevm_registry", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        roms.clear();
        instances.clear();
        try {
            JSONObject doc = new JSONObject(preferences.getString(KEY, "{}"));
            JSONArray savedRoms = doc.optJSONArray("roms");
            if (savedRoms != null) for (int i = 0; i < savedRoms.length(); i++) {
                JSONObject item = savedRoms.getJSONObject(i);
                roms.add(new Rom(item.getString("id"), item.getString("name"),
                        item.getString("uri"), item.getLong("bytes"), item.getString("sha256")));
            }
            JSONArray savedInstances = doc.optJSONArray("instances");
            if (savedInstances != null) for (int i = 0; i < savedInstances.length(); i++) {
                JSONObject item = savedInstances.getJSONObject(i);
                instances.add(new Instance(item.getString("id"), item.getString("romId"),
                        item.getString("name"), item.getLong("createdAt")));
            }
        } catch (Exception error) {
            // Do not silently replace a potentially damaged persisted catalog.
            roms.clear();
            instances.clear();
            throw new IllegalStateException("Catálogo local ilegível. Dados não foram apagados.", error);
        }
    }

    private void persist() throws Exception {
        JSONObject doc = new JSONObject();
        JSONArray allRoms = new JSONArray();
        for (Rom rom : roms) {
            JSONObject r = new JSONObject();
            r.put("id", rom.id);
            r.put("name", rom.displayName);
            r.put("uri", rom.uri);
            r.put("bytes", rom.sizeBytes);
            r.put("sha256", rom.sha256);
            allRoms.put(r);
        }
        JSONArray allInstances = new JSONArray();
        for (Instance instance : instances) {
            JSONObject i = new JSONObject();
            i.put("id", instance.id);
            i.put("romId", instance.romId);
            i.put("name", instance.displayName);
            i.put("createdAt", instance.createdAt);
            allInstances.put(i);
        }
        doc.put("roms", allRoms);
        doc.put("instances", allInstances);
        if (!preferences.edit().putString(KEY, doc.toString()).commit()) {
            throw new IOException("Falha ao salvar o catálogo de instâncias");
        }
    }

    public List<Rom> roms() { return new ArrayList<>(roms); }
    public List<Instance> instances() { return new ArrayList<>(instances); }

    public Rom findRom(String romId) {
        for (Rom rom : roms) if (rom.id.equals(romId)) return rom;
        return null;
    }

    public Rom addRom(String name, Uri uri, long sizeBytes, String sha256) throws Exception {
        if (sizeBytes <= 0 || sha256 == null || sha256.length() != 64) {
            throw new IllegalArgumentException("Arquivo de ROM não validado");
        }
        for (Rom rom : roms) {
            if (rom.sha256.equals(sha256) && rom.sizeBytes == sizeBytes) return rom;
        }
        Rom rom = new Rom(UUID.randomUUID().toString(), name, uri.toString(), sizeBytes, sha256);
        roms.add(rom);
        try {
            persist();
        } catch (Exception e) {
            roms.remove(rom);
            throw e;
        }
        return rom;
    }

    /**
     * Creates distinct *configuration records* and empty host-side directories.
     * These are NOT running Android VMs or writable guest-disk overlays.
     */
    public List<Instance> registerInstances(String romId, int count) throws Exception {
        if (findRom(romId) == null) throw new IllegalArgumentException("ROM não encontrada");
        if (count < 1 || instances.size() + count > MAX_CONFIGURED_INSTANCES) {
            throw new IllegalArgumentException("Limite de 64 instâncias cadastradas neste LAB");
        }
        ArrayList<Instance> created = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            String id = UUID.randomUUID().toString();
            int sequence = instances.size() + created.size() + 1;
            File dir = instanceDirectory(id);
            File data = new File(dir, "data");
            File cache = new File(dir, "cache");
            if (!data.mkdirs() || !cache.mkdirs()) {
                throw new IOException("Não foi possível preparar o armazenamento isolado");
            }
            created.add(new Instance(id, romId, "Máquina " + sequence, System.currentTimeMillis()));
        }
        instances.addAll(created);
        try {
            persist();
        } catch (Exception error) {
            instances.removeAll(created);
            throw error;
        }
        return created;
    }

    public File instanceDirectory(String id) {
        if (!id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("ID inválido");
        return new File(new File(context.getFilesDir(), "instances"), id);
    }
}
