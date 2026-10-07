package com.samp.mobile.launcher.util;

import android.content.Context;
import org.ini4j.Wini;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingsHelper {
    public static List<File> getSettingsFiles(Context context) {
        List<File> files = new ArrayList<>();
        if (context != null) {
            File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                files.add(new File(ext, "SAMP/settings.ini"));
            }
            File internal = context.getFilesDir();
            if (internal != null) {
                files.add(new File(internal, "SAMP/settings.ini"));
            }
        }
        files.add(new File("/storage/emulated/0/GTA/SAMP/settings.ini"));
        files.add(new File("/sdcard/GTA/SAMP/settings.ini"));
        files.add(new File("/storage/emulated/0/Android/data/com.samp.mobile/files/SAMP/settings.ini"));
        return files;
    }

    public static String getNickName(Context context) {
        for (File f : getSettingsFiles(context)) {
            if (f.exists()) {
                try {
                    Wini ini = new Wini(f);
                    String name = ini.get("client", "name");
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                } catch (Exception ignored) {}
            }
        }
        return "Tham_Player";
    }

    public static String getServerHost(Context context) {
        for (File f : getSettingsFiles(context)) {
            if (f.exists()) {
                try {
                    Wini ini = new Wini(f);
                    String host = ini.get("client", "host");
                    if (host != null && !host.trim().isEmpty()) {
                        return host.trim();
                    }
                } catch (Exception ignored) {}
            }
        }
        return "188.212.158.39";
    }

    public static void setNickName(Context context, String name) {
        if (name == null || name.trim().isEmpty()) return;
        setSetting(context, "client", "name", name.trim());
    }

    public static void setServer(Context context, String host, int port) {
        setSetting(context, "client", "host", host);
        setSetting(context, "client", "port", port);
    }

    public static void setSetting(Context context, String section, String key, Object value) {
        for (File f : getSettingsFiles(context)) {
            try {
                if (!f.exists()) {
                    File parent = f.getParentFile();
                    if (parent != null && parent.exists()) {
                        f.createNewFile();
                    }
                }
                if (f.exists()) {
                    Wini ini = new Wini(f);
                    ini.put(section, key, value);
                    ini.store();
                }
            } catch (Exception ignored) {}
        }
    }

    public static String getSetting(Context context, String section, String key, String defaultVal) {
        for (File f : getSettingsFiles(context)) {
            if (f.exists()) {
                try {
                    Wini ini = new Wini(f);
                    String val = ini.get(section, key);
                    if (val != null) return val;
                } catch (Exception ignored) {}
            }
        }
        return defaultVal;
    }
}
