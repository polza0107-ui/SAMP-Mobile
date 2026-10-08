package com.samp.mobile.launcher.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.samp.mobile.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AntiCheatScanDialog {
    private static final String TAG = "AntiCheatScan";

    public static final String REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/4KINGSOBAD-Tham/SAMP-Mobile/main/security.json";
    private static final String GAME_STORAGE_DIR = "/storage/emulated/0/GTA";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void startScan(Activity activity, Runnable onPass) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_anticheat_scan);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(false);

        TextView tvItem1Status = dialog.findViewById(R.id.tv_item1_status);
        TextView tvItem2Status = dialog.findViewById(R.id.tv_item2_status);
        TextView tvItem3Status = dialog.findViewById(R.id.tv_item3_status);
        TextView tvItem4Status = dialog.findViewById(R.id.tv_item4_status);

        ProgressBar progressBar = dialog.findViewById(R.id.progress_bar_scan);
        TextView tvSummary = dialog.findViewById(R.id.tv_scan_summary);
        TextView tvPercent = dialog.findViewById(R.id.tv_scan_percent);
        Button btnCancel = dialog.findViewById(R.id.btn_scan_cancel);

        final boolean[] isCancelled = {false};

        btnCancel.setOnClickListener(v -> {
            isCancelled[0] = true;
            dialog.dismiss();
            Toast.makeText(activity, "ยกเลิกการเข้าเล่นเกม", Toast.LENGTH_SHORT).show();
        });

        dialog.show();

        executor.execute(() -> {
            try {
                // Load rules from GitHub or local assets
                updateText(tvSummary, "กำลังดึงข้อมูลความปลอดภัยล่าสุด...");
                JSONObject rules = loadSecurityRules(activity);

                if (isCancelled[0]) return;

                boolean enableAntiCheat = rules.optBoolean("enable_anticheat", true);
                if (!enableAntiCheat) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        if (onPass != null) onPass.run();
                    });
                    return;
                }

                // Step 1: Cleo Scan
                updateStepUI(tvItem1Status, "กำลังสแกน...", "#FFB703", progressBar, 25, tvPercent, "25%", tvSummary, "กำลังตรวจเช็คไฟล์ Cleo ดัดแปลง...");
                Thread.sleep(400);

                List<String> cleoExts = jsonArrayToList(rules.optJSONArray("blacklist_extensions"));
                if (cleoExts.isEmpty()) {
                    cleoExts.add(".csa");
                    cleoExts.add(".csi");
                }
                String cleoViolation = scanForExtensions(new File(GAME_STORAGE_DIR), cleoExts);
                if (cleoViolation != null) {
                    handleViolation(activity, dialog, "ตรวจพบไฟล์ Cleo ดัดแปลง: " + cleoViolation, onPass);
                    return;
                }
                updateStepUI(tvItem1Status, "✔ ปลอดภัย", "#00E676", null, 0, null, null, null, null);

                if (isCancelled[0]) return;

                // Step 2: MonetLoader / Lua Whitelist Scan
                updateStepUI(tvItem2Status, "กำลังสแกน...", "#FFB703", progressBar, 50, tvPercent, "50%", tvSummary, "กำลังตรวจเช็คสคริปต์ MonetLoader...");
                Thread.sleep(400);

                List<String> whitelistLua = jsonArrayToList(rules.optJSONArray("whitelist_lua"));
                if (whitelistLua.isEmpty()) {
                    whitelistLua.add("4king_bridge.lua");
                }
                List<String> blacklistKeywords = jsonArrayToList(rules.optJSONArray("blacklist_keywords"));

                String luaViolation = scanMonetLoaderScripts(activity, whitelistLua, blacklistKeywords);
                if (luaViolation != null) {
                    handleViolation(activity, dialog, "ตรวจพบสคริปต์ต้องห้าม: " + luaViolation, onPass);
                    return;
                }
                updateStepUI(tvItem2Status, "✔ ปลอดภัย", "#00E676", null, 0, null, null, null, null);

                if (isCancelled[0]) return;

                // Step 3: Data Integrity Hash Check
                updateStepUI(tvItem3Status, "กำลังสแกน...", "#FFB703", progressBar, 75, tvPercent, "75%", tvSummary, "กำลังตรวจสอบความสมบูรณ์ของไฟล์ Data...");
                Thread.sleep(400);

                Map<String, String> protectedHashes = jsonObjectToMap(rules.optJSONObject("protected_hashes"));
                String hashViolation = verifyFileHashes(protectedHashes);
                if (hashViolation != null) {
                    handleViolation(activity, dialog, "ไฟล์ถูกดัดแปลง (แอบแก้ค่าเกม): " + hashViolation, onPass);
                    return;
                }
                updateStepUI(tvItem3Status, "✔ ปลอดภัย", "#00E676", null, 0, null, null, null, null);

                if (isCancelled[0]) return;

                // Step 4: Memory Hacks & Tools Scan
                updateStepUI(tvItem4Status, "กำลังสแกน...", "#FFB703", progressBar, 100, tvPercent, "100%", tvSummary, "กำลังตรวจสอบแอปพลิเคชันช่วยเล่น...");
                Thread.sleep(300);

                String appViolation = scanMemoryApps(activity);
                if (appViolation != null) {
                    handleViolation(activity, dialog, "ตรวจพบเครื่องมือแฮกความจำ: " + appViolation, onPass);
                    return;
                }
                updateStepUI(tvItem4Status, "✔ ปลอดภัย", "#00E676", null, 0, null, null, null, null);

                // Pass All Checks!
                updateText(tvSummary, "✅ ระบบปลอดภัย 100% พร้อมเข้าเล่น 4KING ROLEPLAY");
                Thread.sleep(500);

                mainHandler.post(() -> {
                    dialog.dismiss();
                    if (onPass != null) {
                        onPass.run();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "AntiCheat error", e);
                // In case of unexpected check crash, let user enter
                mainHandler.post(() -> {
                    dialog.dismiss();
                    if (onPass != null) {
                        onPass.run();
                    }
                });
            }
        });
    }

    private static void handleViolation(Activity activity, Dialog scanDialog, String reason, Runnable onPass) {
        mainHandler.post(() -> {
            if (scanDialog.isShowing()) {
                scanDialog.dismiss();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("⚠️ ตรวจพบการดัดแปลงไฟล์ตัวเกม");
            builder.setMessage(reason + "\n\nเพื่อความยุติธรรมและเท่าเทียมในเซิร์ฟเวอร์ 4KING ROLEPLAY ระบบจำเป็นต้องลบข้อมูลตัวเกมเดิมออก และให้ดาวน์โหลดข้อมูลใหม่");
            builder.setCancelable(false);
            builder.setPositiveButton("🗑️ ลบตัวเกมและดาวน์โหลดใหม่", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                    // 1. Delete modified game directory
                    deleteRecursive(new File(GAME_STORAGE_DIR));

                    // Clean external files dir as well if exists
                    File extFiles = activity.getExternalFilesDir(null);
                    if (extFiles != null) {
                        deleteRecursive(new File(extFiles, "data"));
                        deleteRecursive(new File(extFiles, "anim"));
                        deleteRecursive(new File(extFiles, "texdb"));
                    }

                    Toast.makeText(activity, "ลบไฟล์ดัดแปลงเรียบร้อย กำลังเปิดหน้าต่างดาวน์โหลดใหม่", Toast.LENGTH_LONG).show();

                    // 2. Force open GameDataDownloadDialog
                    GameDataDownloadDialog.show(activity, onPass);
                }
            });
            builder.setNegativeButton("ออก", (d, which) -> {
                d.dismiss();
                activity.finish();
            });

            AlertDialog alert = builder.create();
            alert.show();
        });
    }

    public static JSONObject loadSecurityRules(Context context) {
        // Try Remote GitHub first
        try {
            URL url = new URL(REMOTE_CONFIG_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 4KING-Security");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();
                return new JSONObject(sb.toString());
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Cannot fetch remote security.json, using fallback...", e);
        }

        // Fallback to local assets/security.json
        try {
            InputStream is = context.getAssets().open("security.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Cannot load local security.json", e);
        }

        return new JSONObject();
    }

    private static String scanForExtensions(File dir, List<String> extensions) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isDirectory()) {
                String subViolation = scanForExtensions(f, extensions);
                if (subViolation != null) return subViolation;
            } else {
                String name = f.getName().toLowerCase();
                for (String ext : extensions) {
                    if (name.endsWith(ext.toLowerCase())) {
                        return f.getName();
                    }
                }
            }
        }
        return null;
    }

    private static String scanMonetLoaderScripts(Activity activity, List<String> whitelist, List<String> blacklistKeywords) {
        List<File> searchDirs = new ArrayList<>();
        searchDirs.add(new File("/storage/emulated/0/monetloader"));
        searchDirs.add(new File("/storage/emulated/0/Android/media/com.samp.mobile/monetloader"));
        searchDirs.add(new File(GAME_STORAGE_DIR + "/monetloader"));

        File[] mediaDirs = activity.getExternalMediaDirs();
        if (mediaDirs != null) {
            for (File m : mediaDirs) {
                if (m != null) searchDirs.add(new File(m, "monetloader"));
            }
        }

        for (File dir : searchDirs) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName().toLowerCase();
                        if (name.endsWith(".lua") || name.endsWith(".luac")) {
                            // Check blacklist keywords
                            for (String keyword : blacklistKeywords) {
                                if (name.contains(keyword.toLowerCase())) {
                                    return f.getName();
                                }
                            }
                            // Check whitelist
                            boolean isWhitelisted = false;
                            for (String allowed : whitelist) {
                                if (name.equalsIgnoreCase(allowed.trim())) {
                                    isWhitelisted = true;
                                    break;
                                }
                            }
                            if (!isWhitelisted) {
                                return f.getName() + " (ไม่ได้อยู่ในรายการอนุญาต)";
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String verifyFileHashes(Map<String, String> protectedHashes) {
        for (Map.Entry<String, String> entry : protectedHashes.entrySet()) {
            String relPath = entry.getKey();
            String expectedHash = entry.getValue();

            File targetFile = new File(GAME_STORAGE_DIR, relPath);
            if (targetFile.exists() && targetFile.isFile()) {
                String actualHash = calculateSHA256(targetFile);
                if (actualHash != null && !actualHash.equalsIgnoreCase(expectedHash)) {
                    return relPath;
                }
            }
        }
        return null;
    }

    private static String scanMemoryApps(Activity activity) {
        String[] suspiciousPackages = {
                "catch_.me_.if_.you_.can_",
                "com.gg.speed",
                "org.cheatengine",
                "com.xmodgames"
        };

        android.content.pm.PackageManager pm = activity.getPackageManager();
        for (String pkg : suspiciousPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String calculateSHA256(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteRecursive(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDir.delete();
    }

    private static void updateStepUI(TextView tvStatus, String text, String colorHex,
                                     ProgressBar bar, int progress, TextView tvPercent, String percentText,
                                     TextView tvSummary, String summaryText) {
        mainHandler.post(() -> {
            if (tvStatus != null) {
                tvStatus.setText(text);
                tvStatus.setTextColor(Color.parseColor(colorHex));
            }
            if (bar != null) bar.setProgress(progress);
            if (tvPercent != null) tvPercent.setText(percentText);
            if (tvSummary != null) tvSummary.setText(summaryText);
        });
    }

    private static void updateText(TextView tv, String text) {
        mainHandler.post(() -> {
            if (tv != null) tv.setText(text);
        });
    }

    private static List<String> jsonArrayToList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                list.add(array.optString(i));
            }
        }
        return list;
    }

    private static Map<String, String> jsonObjectToMap(JSONObject obj) {
        Map<String, String> map = new HashMap<>();
        if (obj != null) {
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, obj.optString(key));
            }
        }
        return map;
    }
}
