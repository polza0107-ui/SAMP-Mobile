package com.samp.mobile.launcher.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.samp.mobile.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameDataDownloadDialog {
    private static final String TAG = "GameDataDownload";

    public static final String PREF_NAME = "4king_launcher";
    public static final String KEY_INSTALLED_MOD_VERSION = "installed_mod_version";

    public static final String DATA_DOWNLOAD_URL = "https://github.com/4KINGSOBAD-Tham/SAMP-Mobile/releases/download/v1.0.0/GTA.zip";
    public static final String DATA_DOWNLOAD_FALLBACK_URL = "https://github.com/4KINGSOBAD-Tham/SAMP-Mobile/releases/latest/download/GTA.zip";

    public static final String PATCH_DOWNLOAD_URL = "https://github.com/4KINGSOBAD-Tham/SAMP-Mobile/releases/download/v1.0.0/patch.zip";
    public static final String PATCH_DOWNLOAD_FALLBACK_URL = "https://github.com/4KINGSOBAD-Tham/SAMP-Mobile/releases/latest/download/patch.zip";

    public static final String TARGET_EXTRACT_DIR = "/storage/emulated/0/";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isGameDataInstalled() {
        File gtaDir = new File(TARGET_EXTRACT_DIR, "GTA");
        File dataDir = new File(gtaDir, "data");
        File animDir = new File(gtaDir, "anim");
        File modelsDir = new File(gtaDir, "models");

        return (dataDir.exists() && dataDir.isDirectory() && dataDir.list() != null && dataDir.list().length > 0)
                || (animDir.exists() && animDir.isDirectory())
                || (modelsDir.exists() && modelsDir.isDirectory());
    }

    public static int getInstalledModVersion(Context context) {
        if (context == null) return 0;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_INSTALLED_MOD_VERSION, 0);
    }

    public static void setInstalledModVersion(Context context, int version) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_INSTALLED_MOD_VERSION, version)
                .apply();
    }

    public static boolean checkStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void requestStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                Toast.makeText(activity, "กรุณาเปิดสิทธิ์ 'อนุญาตให้เข้าถึงไฟล์ทั้งหมด' เพื่อติดตั้งตัวเกม", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(intent);
            }
        } else {
            activity.requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 101);
        }
    }

    /**
     * Dialog สำหรับดาวน์โหลดตัวเกมหลัก (GTA.zip)
     */
    public static void show(Activity activity, Runnable onComplete) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_game_download);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(false);

        TextView tvStatusTitle = dialog.findViewById(R.id.tv_status_title);
        TextView tvStatusDetail = dialog.findViewById(R.id.tv_status_detail);
        ProgressBar progressBar = dialog.findViewById(R.id.progress_bar_download);
        TextView tvPercent = dialog.findViewById(R.id.tv_progress_percent);
        TextView tvSpeed = dialog.findViewById(R.id.tv_progress_speed);
        Button btnCancel = dialog.findViewById(R.id.btn_download_cancel);
        Button btnAction = dialog.findViewById(R.id.btn_download_action);

        boolean alreadyInstalled = isGameDataInstalled();
        if (alreadyInstalled) {
            tvStatusTitle.setText("ตรวจพบข้อมูลตัวเกมในเครื่องแล้ว");
            tvStatusDetail.setText("จุดติดตั้ง: /storage/emulated/0/GTA (พร้อมเล่น หรือกดเพื่อดาวน์โหลดใหม่)");
            btnAction.setText("🔄 ดาวน์โหลดใหม่");
        } else {
            tvStatusTitle.setText("ยังไม่ได้ติดตั้งข้อมูลตัวเกม (DATA)");
            tvStatusDetail.setText("จำเป็นต้องดาวน์โหลดไฟล์ GTA.zip ลงที่ /storage/emulated/0/");
            btnAction.setText("⚡ เริ่มดาวน์โหลด");
        }

        final boolean[] isRunning = {false};

        btnCancel.setOnClickListener(v -> {
            isRunning[0] = false;
            dialog.dismiss();
        });

        btnAction.setOnClickListener(v -> {
            if (btnAction.getText().toString().contains("เสร็จสิ้น") || btnAction.getText().toString().contains("เริ่มเกม")) {
                dialog.dismiss();
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }

            if (!checkStoragePermissions(activity)) {
                requestStoragePermissions(activity);
                Toast.makeText(activity, "กรุณาเปิดสิทธิ์เข้าถึงไฟล์ก่อนเริ่มดาวน์โหลด", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isRunning[0]) return;
            isRunning[0] = true;

            btnAction.setEnabled(false);
            btnAction.setText("⏳ กำลังดำเนินการ...");
            btnCancel.setEnabled(false);

            startDownloadAndExtract(activity, dialog, tvStatusTitle, tvStatusDetail, progressBar, tvPercent, tvSpeed, btnAction, btnCancel, onComplete);
        });

        dialog.show();
    }

    private static void startDownloadAndExtract(Activity activity, Dialog dialog,
                                               TextView tvStatusTitle, TextView tvStatusDetail,
                                               ProgressBar progressBar, TextView tvPercent, TextView tvSpeed,
                                               Button btnAction, Button btnCancel, Runnable onComplete) {

        executor.execute(() -> {
            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir();
            }
            File tempZip = new File(cacheDir, "GTA_download.zip");

            try {
                // 1. Download Phase
                updateUI(tvStatusTitle, "กำลังเชื่อมต่อเพื่อดาวน์โหลด...");
                updateUI(tvStatusDetail, "กำลังดาวน์โหลดไฟล์ข้อมูลตัวเกม (GTA.zip)...");

                boolean downloaded = downloadFile(DATA_DOWNLOAD_URL, DATA_DOWNLOAD_FALLBACK_URL, tempZip, (downloadedBytes, totalBytes, speedBytesPerSec) -> {
                    int percent = (totalBytes > 0) ? (int) ((downloadedBytes * 100) / totalBytes) : 0;
                    String downloadedMb = String.format(Locale.US, "%.1f MB", downloadedBytes / (1024.0 * 1024.0));
                    String totalMb = (totalBytes > 0) ? String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0)) : "กำลังคำนวณ";
                    String speedStr = formatSpeed(speedBytesPerSec);

                    mainHandler.post(() -> {
                        progressBar.setProgress(percent);
                        tvPercent.setText(percent + "%");
                        tvSpeed.setText(downloadedMb + " / " + totalMb + " (" + speedStr + ")");
                    });
                });

                if (!downloaded || !tempZip.exists() || tempZip.length() == 0) {
                    throw new Exception("ดาวน์โหลดไฟล์ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
                }

                // 2. Extraction Phase
                updateUI(tvStatusTitle, "กำลังแตกไฟล์ข้อมูลตัวเกม...");
                updateUI(tvStatusDetail, "กำลังติดตั้งลงโฟลเดอร์ /storage/emulated/0/ กรุณารอสักครู่");
                mainHandler.post(() -> {
                    progressBar.setIndeterminate(true);
                    tvPercent.setText("แตกไฟล์...");
                });

                File targetDir = new File(TARGET_EXTRACT_DIR);
                unzip(tempZip, targetDir, (fileName, currentCount) -> {
                    updateUI(tvStatusDetail, "กำลังติดตั้ง: " + fileName);
                });

                tempZip.delete();

                updateUI(tvStatusTitle, "✅ ติดตั้งตัวเกมเสร็จสมบูรณ์!");
                updateUI(tvStatusDetail, "ติดตั้งลงใน /storage/emulated/0/GTA เรียบร้อยแล้ว");
                mainHandler.post(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(100);
                    tvPercent.setText("100%");
                    tvSpeed.setText("เสร็จสิ้น");
                    btnAction.setEnabled(true);
                    btnAction.setText("✔ เสร็จสิ้น");
                    btnCancel.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download/Extract error", e);
                final String errMsg = e.getMessage() != null ? e.getMessage() : "เกิดข้อผิดพลาดในการติดตั้ง";
                mainHandler.post(() -> {
                    progressBar.setIndeterminate(false);
                    tvStatusTitle.setText("❌ ติดตั้งไม่สำเร็จ");
                    tvStatusDetail.setText(errMsg);
                    btnAction.setEnabled(true);
                    btnAction.setText("🔄 ลองใหม่อีกครั้ง");
                    btnCancel.setEnabled(true);
                });
            }
        });
    }

    /**
     * Dialog สำหรับดาวน์โหลดอัปเดตม็อดโดยเฉพาะ (patch.zip)
     */
    public static void showModUpdate(Activity activity, String customUrl, String customFallbackUrl, int targetVersion, Runnable onComplete) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_game_download);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(false);

        TextView tvStatusTitle = dialog.findViewById(R.id.tv_status_title);
        TextView tvStatusDetail = dialog.findViewById(R.id.tv_status_detail);
        ProgressBar progressBar = dialog.findViewById(R.id.progress_bar_download);
        TextView tvPercent = dialog.findViewById(R.id.tv_progress_percent);
        TextView tvSpeed = dialog.findViewById(R.id.tv_progress_speed);
        Button btnCancel = dialog.findViewById(R.id.btn_download_cancel);
        Button btnAction = dialog.findViewById(R.id.btn_download_action);

        int installedVer = getInstalledModVersion(activity);
        tvStatusTitle.setText("📦 อัปเดตม็อด / สกินเซิร์ฟเวอร์");
        tvStatusDetail.setText("เวอร์ชันที่ติดตั้ง: v" + installedVer + " -> เวอร์ชันล่าสุด: v" + targetVersion + "\nดาวน์โหลดแพตช์ม็อดเพื่อติดตั้งลงในตัวเกม");
        btnAction.setText("⚡ เริ่มอัปเดตม็อด");

        final boolean[] isRunning = {false};

        btnCancel.setOnClickListener(v -> {
            isRunning[0] = false;
            dialog.dismiss();
        });

        final String finalUrl = (customUrl != null && !customUrl.trim().isEmpty()) ? customUrl : PATCH_DOWNLOAD_URL;
        final String finalFallback = (customFallbackUrl != null && !customFallbackUrl.trim().isEmpty()) ? customFallbackUrl : PATCH_DOWNLOAD_FALLBACK_URL;

        btnAction.setOnClickListener(v -> {
            if (btnAction.getText().toString().contains("เสร็จสิ้น") || btnAction.getText().toString().contains("เริ่มเกม")) {
                dialog.dismiss();
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }

            if (!checkStoragePermissions(activity)) {
                requestStoragePermissions(activity);
                Toast.makeText(activity, "กรุณาเปิดสิทธิ์เข้าถึงไฟล์ก่อนเริ่มอัปเดตม็อด", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isRunning[0]) return;
            isRunning[0] = true;

            btnAction.setEnabled(false);
            btnAction.setText("⏳ กำลังดำเนินการ...");
            btnCancel.setEnabled(false);

            startModDownloadAndExtract(activity, dialog, finalUrl, finalFallback, targetVersion,
                    tvStatusTitle, tvStatusDetail, progressBar, tvPercent, tvSpeed, btnAction, btnCancel, onComplete);
        });

        dialog.show();
    }

    private static void startModDownloadAndExtract(Activity activity, Dialog dialog,
                                                 String url, String fallbackUrl, int targetVersion,
                                                 TextView tvStatusTitle, TextView tvStatusDetail,
                                                 ProgressBar progressBar, TextView tvPercent, TextView tvSpeed,
                                                 Button btnAction, Button btnCancel, Runnable onComplete) {

        executor.execute(() -> {
            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir();
            }
            File tempZip = new File(cacheDir, "patch_download.zip");

            try {
                updateUI(tvStatusTitle, "กำลังดาวน์โหลดไฟล์อัปเดตม็อด...");
                updateUI(tvStatusDetail, "กำลังดาวน์โหลด patch.zip จากเซิร์ฟเวอร์...");

                boolean downloaded = downloadFile(url, fallbackUrl, tempZip, (downloadedBytes, totalBytes, speedBytesPerSec) -> {
                    int percent = (totalBytes > 0) ? (int) ((downloadedBytes * 100) / totalBytes) : 0;
                    String downloadedMb = String.format(Locale.US, "%.1f MB", downloadedBytes / (1024.0 * 1024.0));
                    String totalMb = (totalBytes > 0) ? String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0)) : "กำลังคำนวณ";
                    String speedStr = formatSpeed(speedBytesPerSec);

                    mainHandler.post(() -> {
                        progressBar.setProgress(percent);
                        tvPercent.setText(percent + "%");
                        tvSpeed.setText(downloadedMb + " / " + totalMb + " (" + speedStr + ")");
                    });
                });

                if (!downloaded || !tempZip.exists() || tempZip.length() == 0) {
                    throw new Exception("ดาวน์โหลดไฟล์อัปเดตม็อดไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
                }

                updateUI(tvStatusTitle, "กำลังติดตั้งม็อด...");
                updateUI(tvStatusDetail, "กำลังแตกไฟล์ม็อดลงตัวเกม (/storage/emulated/0/)...");
                mainHandler.post(() -> {
                    progressBar.setIndeterminate(true);
                    tvPercent.setText("ติดตั้งม็อด...");
                });

                File targetDir = new File(TARGET_EXTRACT_DIR);
                unzip(tempZip, targetDir, (fileName, currentCount) -> {
                    updateUI(tvStatusDetail, "กำลังติดตั้งม็อด: " + fileName);
                });

                // Save installed mod version
                setInstalledModVersion(activity, targetVersion);

                tempZip.delete();

                updateUI(tvStatusTitle, "✅ อัปเดตม็อดสำเร็จแล้ว!");
                updateUI(tvStatusDetail, "ติดตั้งม็อดเวอร์ชัน v" + targetVersion + " เรียบร้อย พร้อมเข้าเล่น");
                mainHandler.post(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(100);
                    tvPercent.setText("100%");
                    tvSpeed.setText("เสร็จสิ้น");
                    btnAction.setEnabled(true);
                    btnAction.setText("✔ เสร็จสิ้น");
                    btnCancel.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Mod update error", e);
                final String errMsg = e.getMessage() != null ? e.getMessage() : "เกิดข้อผิดพลาดในการอัปเดตม็อด";
                mainHandler.post(() -> {
                    progressBar.setIndeterminate(false);
                    tvStatusTitle.setText("❌ อัปเดตม็อดไม่สำเร็จ");
                    tvStatusDetail.setText(errMsg);
                    btnAction.setEnabled(true);
                    btnAction.setText("🔄 ลองอีกครั้ง");
                    btnCancel.setEnabled(true);
                });
            }
        });
    }

    private interface DownloadProgressListener {
        void onProgress(long downloadedBytes, long totalBytes, long speedBytesPerSec);
    }

    private interface ExtractListener {
        void onExtract(String fileName, int count);
    }

    private static boolean downloadFile(String primaryUrl, String fallbackUrl, File outputFile, DownloadProgressListener listener) throws Exception {
        String[] urlsToTry = {primaryUrl, fallbackUrl};
        Exception lastException = null;

        for (String urlStr : urlsToTry) {
            if (urlStr == null || urlStr.trim().isEmpty()) continue;

            InputStream in = null;
            FileOutputStream out = null;
            HttpURLConnection conn = null;

            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) 4KING-Launcher");

                int responseCode = conn.getResponseCode();
                int redirects = 0;
                while ((responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307) && redirects < 5) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(60000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) 4KING-Launcher");
                    responseCode = conn.getResponseCode();
                    redirects++;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP response error: " + responseCode);
                }

                String contentType = conn.getContentType();
                if (contentType != null && contentType.toLowerCase().contains("text/html")) {
                    Log.d(TAG, "HTML returned, extracting confirmation token if Google Drive...");
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder html = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        html.append(line);
                    }
                    reader.close();

                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("name=[\"']confirm[\"']\\s+value=[\"']([^\"']+)[\"']");
                    java.util.regex.Matcher matcher = pattern.matcher(html);
                    String confirmToken = "t";
                    if (matcher.find()) {
                        confirmToken = matcher.group(1);
                    }

                    conn.disconnect();
                    url = new URL(urlStr + "&confirm=" + confirmToken);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(60000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) 4KING-Launcher");
                    responseCode = conn.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new Exception("Drive confirmation failed: HTTP " + responseCode);
                    }
                }

                long totalBytes = conn.getContentLengthLong();
                in = new BufferedInputStream(conn.getInputStream());
                out = new FileOutputStream(outputFile);

                byte[] buffer = new byte[64 * 1024];
                long downloadedBytes = 0;
                int bytesRead;

                long lastTime = System.currentTimeMillis();
                long bytesSinceLastTime = 0;
                long speed = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;
                    bytesSinceLastTime += bytesRead;

                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 500) {
                        speed = (bytesSinceLastTime * 1000) / (now - lastTime);
                        lastTime = now;
                        bytesSinceLastTime = 0;
                        if (listener != null) {
                            listener.onProgress(downloadedBytes, totalBytes, speed);
                        }
                    }
                }

                out.flush();
                if (listener != null) {
                    listener.onProgress(downloadedBytes, totalBytes, speed);
                }
                return true;

            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "Failed downloading from " + urlStr + ", trying fallback...", e);
            } finally {
                if (out != null) {
                    try { out.close(); } catch (Exception ignored) {}
                }
                if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        if (lastException != null) throw lastException;
        return false;
    }

    private static void unzip(File zipFile, File targetDir, ExtractListener listener) throws Exception {
        if (!zipFile.exists() || zipFile.length() < 100) {
            throw new Exception("ไฟล์ที่ดาวน์โหลดมามีขนาดผิดปกติ (" + (zipFile.exists() ? zipFile.length() : 0) + " bytes)");
        }

        // Validate ZIP magic header (0x50, 0x4B)
        try (java.io.FileInputStream fis = new java.io.FileInputStream(zipFile)) {
            byte[] magic = new byte[4];
            if (fis.read(magic) < 4 || magic[0] != 0x50 || magic[1] != 0x4B) {
                throw new Exception("ไฟล์ที่ดาวน์โหลดมาไม่ใช่ไฟล์ ZIP ที่สมบูรณ์");
            }
        }

        byte[] buffer = new byte[64 * 1024];
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File destFile = new File(targetDir, entryName);

                // Prevent zip slip vulnerability
                String canonicalDestPath = destFile.getCanonicalPath();
                String canonicalTargetDir = targetDir.getCanonicalPath();
                if (!canonicalDestPath.startsWith(canonicalTargetDir + File.separator) && !canonicalDestPath.equals(canonicalTargetDir)) {
                    throw new SecurityException("Zip entry is outside target dir: " + entryName);
                }

                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile))) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                        bos.flush();
                    }
                }

                zis.closeEntry();
                count++;
                if (listener != null && count % 5 == 0) {
                    listener.onExtract(destFile.getName(), count);
                }
            }
        }

        if (count == 0) {
            throw new Exception("ไม่พบไฟล์ในไฟล์ ZIP ที่ดาวน์โหลดมา");
        }
    }

    private static void updateUI(TextView textView, String text) {
        mainHandler.post(() -> {
            if (textView != null) textView.setText(text);
        });
    }

    private static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) return "0 KB/s";
        if (bytesPerSec < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0);
        } else {
            return String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
        }
    }
}
