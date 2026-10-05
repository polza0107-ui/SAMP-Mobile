package com.samp.mobile.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConfigValidator {
    private static final long THAI_FONT_SIZE = 919260L;

    public static void validateConfigFiles(Context context) {
        try {
            File internalFilesDir = context.getFilesDir();
            if (internalFilesDir != null) {
                File file2 = new File(internalFilesDir, "SAMP/settings.ini");
                if (!file2.exists()) {
                    file2.getParentFile().mkdirs();
                    copyAsset(context.getAssets(), "settings.ini", file2.toString());
                }

                File internalFontDir = new File(internalFilesDir, "SAMP/fonts");
                internalFontDir.mkdirs();
                File font1 = new File(internalFontDir, "arial_bold.ttf");
                if (!font1.exists() || font1.length() != THAI_FONT_SIZE) {
                    copyAsset(context.getAssets(), "Fonts/arial_bold.ttf", font1.toString());
                }
                File font2 = new File(internalFontDir, "arial.ttf");
                if (!font2.exists() || font2.length() != THAI_FONT_SIZE) {
                    copyAsset(context.getAssets(), "Fonts/arial.ttf", font2.toString());
                }
            }

            File externalFilesDir = context.getExternalFilesDir(null);
            if (externalFilesDir != null) {
                File file = new File(externalFilesDir, "SAMP/settings.ini");
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    copyAsset(context.getAssets(), "settings.ini", file.toString());
                }

                File fontDir = new File(externalFilesDir, "SAMP/fonts");
                fontDir.mkdirs();
                File font1 = new File(fontDir, "arial_bold.ttf");
                if (!font1.exists() || font1.length() != THAI_FONT_SIZE) {
                    copyAsset(context.getAssets(), "Fonts/arial_bold.ttf", font1.toString());
                }
                File font2 = new File(fontDir, "arial.ttf");
                if (!font2.exists() || font2.length() != THAI_FONT_SIZE) {
                    copyAsset(context.getAssets(), "Fonts/arial.ttf", font2.toString());
                }
            }

            File gtaStorageDir = new File("/storage/emulated/0/GTA/SAMP/fonts");
            if (gtaStorageDir.exists() || gtaStorageDir.mkdirs()) {
                File gtaFont1 = new File(gtaStorageDir, "arial_bold.ttf");
                if (!gtaFont1.exists() || gtaFont1.length() != THAI_FONT_SIZE) {
                    copyAsset(context.getAssets(), "Fonts/arial_bold.ttf", gtaFont1.toString());
                }
                File gtaFont2 = new File(gtaStorageDir, "arial.ttf");
                if (!gtaFont2.exists() || gtaFont2.length() != THAI_FONT_SIZE) {
                    copyAsset(context.getAssets(), "Fonts/arial.ttf", gtaFont2.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static boolean copyAsset(AssetManager assetManager, String str, String str2) {
        try {
            InputStream open = assetManager.open(str);
            new File(str2).createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(str2);
            copyFile(open, fileOutputStream);
            open.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static void copyFile(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bArr = new byte[1024];
        while (true) {
            int read = inputStream.read(bArr);
            if (read != -1) {
                outputStream.write(bArr, 0, read);
            } else {
                return;
            }
        }
    }
}
