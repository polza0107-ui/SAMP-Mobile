package com.samp.mobile.launcher.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.adapters.ServerInformationFragment;
import com.samp.mobile.game.SAMP;
import com.samp.mobile.launcher.util.AntiCheatScanDialog;
import com.samp.mobile.launcher.util.ButtonAnimator;
import com.samp.mobile.launcher.util.GameDataDownloadDialog;
import com.samp.mobile.launcher.util.SAMPServerInfo;
import com.samp.mobile.launcher.util.SampQueryAPI;
import com.samp.mobile.launcher.util.SettingsHelper;
import com.samp.mobile.launcher.util.SharedPreferenceCore;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

@Obfuscate
public class HomeFragment extends Fragment {

    private EditText mNicknameEdit;
    private Button mBtnUpdate;
    private Button mBtnStartGame;
    private ImageView mBtnSettings;

    // Server Status & Live Announcement Views
    private TextView mTvServerName;
    private TextView mTvServerPlayers;
    private View mServerStatusDot;
    private View mServerStatusCard;
    private TextView mTvAnnouncement;
    private View mAnnouncementContainer;

    // Config defaults (updated remotely from security.json)
    private String mServerAddress = "192.168.1.106";
    private int mServerPort = 7777;
    private String mServerName = "[TH] 4KING ROLEPLAY";
    private boolean mIsServerOnline = false;

    // Mod Patch Config (updated remotely from security.json)
    private int mRemoteModVersion = 1;
    private boolean mModUpdateRequired = false;
    private String mModPatchUrl = "";
    private String mModPatchFallbackUrl = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        if (getActivity() instanceof MainActivity) {
            MainActivity.hideKeyboard(getActivity());
        }

        mNicknameEdit = view.findViewById(R.id.home_nickname_edit);
        mBtnUpdate = view.findViewById(R.id.btn_update);
        mBtnStartGame = view.findViewById(R.id.btn_start_game);
        mBtnSettings = view.findViewById(R.id.btn_settings_top_right);

        // Server Status and Announcement Views
        mTvServerName = view.findViewById(R.id.tv_server_name);
        mTvServerPlayers = view.findViewById(R.id.tv_server_players);
        mServerStatusDot = view.findViewById(R.id.server_status_dot);
        mServerStatusCard = view.findViewById(R.id.server_status_card);
        mTvAnnouncement = view.findViewById(R.id.tv_announcement_text);
        mAnnouncementContainer = view.findViewById(R.id.card_announcement_bar);

        if (mTvAnnouncement != null) {
            mTvAnnouncement.setSelected(true); // Enable marquee scrolling
        }

        // Tap Server Card -> Open Server Details Dialog
        if (mServerStatusCard != null) {
            mServerStatusCard.setOnClickListener(v -> {
                try {
                    SAMPServerInfo serverInfo = new SAMPServerInfo();
                    serverInfo.setId(1);
                    serverInfo.setServerName(mServerName);
                    serverInfo.setAddress(mServerAddress);
                    serverInfo.setPort(mServerPort);
                    serverInfo.setCurrentPlayerCount(0);
                    serverInfo.setMaxPlayerCount(500);
                    serverInfo.setHasPassword(false);
                    serverInfo.setServerStatus(mIsServerOnline ? SAMPServerInfo.Status.ONLINE : SAMPServerInfo.Status.OFFLINE);

                    ServerInformationFragment dialog = ServerInformationFragment.newInstance(serverInfo);
                    dialog.show(getParentFragmentManager(), "ServerInformationFragment");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Fetch Live Server Config & Announcement from security.json (GitHub / local)
        loadRemoteServerAndAnnouncement();

        // Load Nickname (Bottom-Left)
        String currentNick = SettingsHelper.getNickName(getContext());
        if (mNicknameEdit != null) {
            mNicknameEdit.setText(currentNick);
            mNicknameEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String text = s.toString().trim();
                    if (!text.isEmpty()) {
                        SettingsHelper.setNickName(requireContext(), text);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Top-Right Settings Button -> Open FPS Settings Dialog
        if (mBtnSettings != null) {
            mBtnSettings.setOnTouchListener(new ButtonAnimator(getContext(), mBtnSettings));
            mBtnSettings.setOnClickListener(v -> showFpsSettingsDialog());
        }

        // Bottom-Right: Update Button (Dedicated for MOD PATCHING)
        if (mBtnUpdate != null) {
            mBtnUpdate.setOnTouchListener(new ButtonAnimator(getContext(), mBtnUpdate));
            mBtnUpdate.setOnClickListener(v -> {
                if (!GameDataDownloadDialog.isGameDataInstalled()) {
                    Toast.makeText(getContext(), "กรุณาดาวน์โหลดตัวเกมหลักก่อนทำการอัปเดตม็อด", Toast.LENGTH_SHORT).show();
                    return;
                }
                GameDataDownloadDialog.showModUpdate(getActivity(), mModPatchUrl, mModPatchFallbackUrl, mRemoteModVersion, () -> {
                    updateActionButtons();
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "อัปเดตม็อด v" + mRemoteModVersion + " สำเร็จ พร้อมเล่นแล้ว!", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        // Bottom-Right: Start Game Button (Merged Game Data Check + Play)
        if (mBtnStartGame != null) {
            mBtnStartGame.setOnTouchListener(new ButtonAnimator(getContext(), mBtnStartGame));
            mBtnStartGame.setOnClickListener(v -> {
                // 1. ถ้ายังไม่มีตัวเกมหลัก -> ให้ดาวน์โหลดตัวเกมหลักก่อน
                if (!GameDataDownloadDialog.isGameDataInstalled()) {
                    GameDataDownloadDialog.show(getActivity(), () -> {
                        updateActionButtons();
                    });
                    return;
                }

                // 2. ถ้ามีอัปเดตม็อดที่จำเป็นต้องลง -> บล็อกการเริ่มเกม
                if (getContext() != null) {
                    int installedModVer = GameDataDownloadDialog.getInstalledModVersion(getContext());
                    boolean needModUpdate = mModUpdateRequired && (installedModVer < mRemoteModVersion);
                    if (needModUpdate) {
                        Toast.makeText(getContext(), "⚠️ กรุณากดปุ่ม 'อัปเดตม็อด' ก่อนเริ่มเกม!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                // 3. ตรวจสอบชื่อตัวละคร
                if (mNicknameEdit != null) {
                    String name = mNicknameEdit.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "กรุณาใส่ชื่อตัวละครก่อนเริ่มเกม!", Toast.LENGTH_SHORT).show();
                        mNicknameEdit.requestFocus();
                        return;
                    }
                    SettingsHelper.setNickName(requireContext(), name);
                }

                // Main 4KING Server
                SAMPServerInfo targetServer = null;
                if (getActivity() instanceof MainActivity) {
                    MainActivity act = (MainActivity) getActivity();
                    if (!act.getServerList().isEmpty()) {
                        targetServer = act.getServerList().get(0);
                    }
                }

                if (targetServer == null) {
                    targetServer = new SAMPServerInfo();
                    targetServer.setId(1);
                    targetServer.setServerName(mServerName);
                    targetServer.setAddress(mServerAddress);
                    targetServer.setPort(mServerPort);
                    targetServer.setCurrentPlayerCount(0);
                    targetServer.setMaxPlayerCount(500);
                    targetServer.setHasPassword(false);
                    targetServer.setServerStatus(mIsServerOnline ? SAMPServerInfo.Status.ONLINE : SAMPServerInfo.Status.OFFLINE);
                } else {
                    targetServer.setServerName(mServerName);
                    targetServer.setAddress(mServerAddress);
                    targetServer.setPort(mServerPort);
                }

                final SAMPServerInfo finalTargetServer = targetServer;

                // เตือนกรณี Server OFFLINE
                if (!mIsServerOnline) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("🔴 เซิร์ฟเวอร์ปิดให้บริการ")
                            .setMessage("ขณะนี้เซิร์ฟเวอร์ยังไม่เปิดให้บริการ หรือกำลังปิดปรับปรุง\n\nต้องการลองเชื่อมต่อไปยังเซิร์ฟเวอร์หรือไม่?")
                            .setPositiveButton("ลองเชื่อมต่อ", (dialog, which) -> {
                                proceedToLaunchGame(finalTargetServer);
                            })
                            .setNegativeButton("ยกเลิก", null)
                            .show();
                    return;
                }

                proceedToLaunchGame(finalTargetServer);
            });
        }

        updateActionButtons();
        return view;
    }

    /**
     * อัปเดตสถานะและข้อความของปุ่มเริ่มเกม และปุ่มอัปเดตม็อด
     */
    private void updateActionButtons() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (mBtnStartGame == null || mBtnUpdate == null || getContext() == null) return;

            boolean gameInstalled = GameDataDownloadDialog.isGameDataInstalled();
            int installedModVer = GameDataDownloadDialog.getInstalledModVersion(getContext());
            boolean needModUpdate = mModUpdateRequired && (installedModVer < mRemoteModVersion);

            if (!gameInstalled) {
                // ยังไม่ได้ติดตั้งตัวเกมหลัก -> เปลี่ยนปุ่มเริ่มเกมเป็นดาวน์โหลดตัวเกม
                mBtnStartGame.setText("📥 ดาวน์โหลดตัวเกม");
                mBtnStartGame.setEnabled(true);
                mBtnStartGame.setAlpha(1.0f);

                // ปิดปุ่มอัปเดตม็อดไว้จนกว่าจะมีตัวเกมหลัก
                mBtnUpdate.setText("🔄 อัปเดตม็อด");
                mBtnUpdate.setEnabled(false);
                mBtnUpdate.setAlpha(0.5f);
            } else if (needModUpdate) {
                // มีตัวเกมแล้วแต่มีม็อดต้องอัปเดต -> ล็อกปุ่มเริ่มเกม
                mBtnStartGame.setText("🔒 ต้องอัปเดตม็อดก่อน");
                mBtnStartGame.setEnabled(true);
                mBtnStartGame.setAlpha(0.7f);

                // ปุ่มอัปเดตม็อดเด่นขึ้นมา
                mBtnUpdate.setText("⚡ อัปเดตม็อด (มีอัปเดตใหม่)");
                mBtnUpdate.setEnabled(true);
                mBtnUpdate.setAlpha(1.0f);
            } else {
                // ตัวเกมและม็อดพร้อมเล่น
                mBtnStartGame.setText("▶ เริ่มเกม");
                mBtnStartGame.setEnabled(true);
                mBtnStartGame.setAlpha(1.0f);

                mBtnUpdate.setText("🔄 อัปเดตม็อด");
                mBtnUpdate.setEnabled(true);
                mBtnUpdate.setAlpha(1.0f);
            }
        });
    }

    private void proceedToLaunchGame(final SAMPServerInfo targetServer) {
        if (!GameDataDownloadDialog.isGameDataInstalled()) {
            Toast.makeText(getContext(), "ยังไม่พบข้อมูลตัวเกม กรุณาดาวน์โหลด DATA ก่อนเข้าเล่น", Toast.LENGTH_SHORT).show();
            GameDataDownloadDialog.show(getActivity(), () -> {
                updateActionButtons();
                startAntiCheatAndLaunch(targetServer);
            });
        } else {
            startAntiCheatAndLaunch(targetServer);
        }
    }

    private void loadRemoteServerAndAnnouncement() {
        // Show initial checking state
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (mServerStatusDot != null) {
                    mServerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_checking);
                }
                if (mTvServerPlayers != null) {
                    mTvServerPlayers.setText("CHECKING ⏳ กำลังตรวจสอบ...");
                    mTvServerPlayers.setTextColor(Color.parseColor("#FFD600"));
                }
            });
        }

        new Thread(() -> {
            try {
                if (getContext() == null) return;
                JSONObject sec = AntiCheatScanDialog.loadSecurityRules(getContext());
                if (sec == null) return;

                // 1. Live Announcement
                JSONObject ann = sec.optJSONObject("announcement");
                if (ann != null) {
                    boolean enabled = ann.optBoolean("enabled", true);
                    String text = ann.optString("text", "");
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (enabled && !text.isEmpty() && mTvAnnouncement != null) {
                                mTvAnnouncement.setText(text);
                                mTvAnnouncement.setSelected(true);
                                if (mAnnouncementContainer != null) mAnnouncementContainer.setVisibility(View.VISIBLE);
                            } else if (!enabled && mAnnouncementContainer != null) {
                                mAnnouncementContainer.setVisibility(View.GONE);
                            }
                        });
                    }
                }

                // 2. Mod Patch Config
                JSONObject modObj = sec.optJSONObject("mod_patch");
                if (modObj != null) {
                    mRemoteModVersion = modObj.optInt("version", 1);
                    mModUpdateRequired = modObj.optBoolean("required", false);
                    mModPatchUrl = modObj.optString("url", "");
                    mModPatchFallbackUrl = modObj.optString("fallback_url", "");
                }
                updateActionButtons();

                // 3. Server Status & Config
                JSONObject srv = sec.optJSONObject("server");
                boolean configOnline = true;
                String offlineReason = "เซิร์ฟเวอร์ปิดให้บริการ";

                if (srv != null) {
                    mServerName = srv.optString("name", mServerName);
                    mServerAddress = srv.optString("ip", mServerAddress);
                    mServerPort = srv.optInt("port", mServerPort);
                    configOnline = srv.optBoolean("online", true);
                    offlineReason = srv.optString("offline_message", offlineReason);
                }

                if (getActivity() != null) {
                    final String sName = mServerName;
                    getActivity().runOnUiThread(() -> {
                        if (mTvServerName != null) mTvServerName.setText(sName);
                    });
                }

                // If admin disabled server from GitHub config
                if (!configOnline) {
                    mIsServerOnline = false;
                    final String reason = offlineReason;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (mServerStatusDot != null) {
                                mServerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
                            }
                            if (mTvServerPlayers != null) {
                                mTvServerPlayers.setText("OFFLINE 🔴 " + reason);
                                mTvServerPlayers.setTextColor(Color.parseColor("#FF5252"));
                            }
                        });
                    }
                    return;
                }

                // 4. Live UDP Query via SampQueryAPI
                boolean isOnline = false;
                String[] info = null;
                long ping = 0;
                SampQueryAPI query = new SampQueryAPI(mServerAddress, mServerPort);
                try {
                    long startTime = System.currentTimeMillis();
                    if (query.mo7166d()) {
                        info = query.mo7164b();
                        ping = System.currentTimeMillis() - startTime;
                        if (info != null && info.length >= 4) {
                            isOnline = true;
                        }
                    }
                } catch (Exception e) {
                    isOnline = false;
                } finally {
                    query.close();
                }

                mIsServerOnline = isOnline;

                if (isOnline && info != null) {
                    final int currentPlayers = Integer.parseInt(info[1]);
                    final int maxPlayers = Integer.parseInt(info[2]);
                    final String liveName = (info[3] != null && !info[3].trim().isEmpty()) ? info[3] : mServerName;
                    final long finalPing = ping;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (mServerStatusDot != null) {
                                mServerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_online);
                            }
                            if (mTvServerName != null) {
                                mTvServerName.setText(liveName);
                            }
                            if (mTvServerPlayers != null) {
                                mTvServerPlayers.setText("ONLINE 👥 " + currentPlayers + "/" + maxPlayers + "  📶 " + finalPing + "ms");
                                mTvServerPlayers.setTextColor(Color.parseColor("#00E5FF"));
                            }
                        });
                    }
                } else {
                    // SERVER IS OFFLINE / UNREACHABLE
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (mServerStatusDot != null) {
                                mServerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
                            }
                            if (mTvServerPlayers != null) {
                                mTvServerPlayers.setText("OFFLINE 🔴 เซิร์ฟเวอร์ปิดให้บริการ");
                                mTvServerPlayers.setTextColor(Color.parseColor("#FF5252"));
                            }
                        });
                    }
                }

            } catch (Exception e) {
                mIsServerOnline = false;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (mServerStatusDot != null) {
                            mServerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
                        }
                        if (mTvServerPlayers != null) {
                            mTvServerPlayers.setText("OFFLINE 🔴 ไม่สามารถเชื่อมต่อได้");
                            mTvServerPlayers.setTextColor(Color.parseColor("#FF5252"));
                        }
                    });
                }
            }
        }).start();
    }

    private void startAntiCheatAndLaunch(final SAMPServerInfo serverInfo) {
        if (getActivity() == null || getActivity().isFinishing()) return;

        // Save Server IP, Port and Password to settings.ini immediately
        SettingsHelper.setSetting(getContext(), "client", "host", serverInfo.getAddress());
        SettingsHelper.setSetting(getContext(), "client", "port", serverInfo.getPort());
        SettingsHelper.setSetting(getContext(), "client", "password", "");

        // Setup MonetLoader profile if enabled
        setupMonetLoaderProfile();

        // Run Anti-Cheat Scanning Modal
        AntiCheatScanDialog.startScan(getActivity(), this::launchGameDirectly);
    }

    private void setupMonetLoaderProfile() {
        try {
            if (getContext() != null && new SharedPreferenceCore().getBoolean(getContext(), "MLOADER") && getActivity() != null) {
                File[] mediaDirs = getActivity().getExternalMediaDirs();
                if (mediaDirs != null && mediaDirs.length > 0 && mediaDirs[0] != null) {
                    File compatDir = new File(mediaDirs[0], "monetloader/compat");
                    File profileFile = new File(compatDir, "profile.json");
                    if (!compatDir.exists()) {
                        compatDir.mkdirs();
                    }
                    if (!profileFile.exists()) {
                        FileWriter writer = new FileWriter(profileFile);
                        writer.append("{\n" +
                                "  \"gtasa_name\": \"libGTASA.so\",\n" +
                                "  \"profile_name\": \"SA-MP 0.3.7\",\n" +
                                "  \"compat_scripts\": [],\n" +
                                "  \"samp_name\": \"libsamp.so\",\n" +
                                "  \"receiveignorerpc_pattern\": \"F0B503AF2DE900????B004460068C16A20468847\",\n" +
                                "  \"cnetgame_ctor_pattern\": \"F0B503AF2DE9000788B00D46????9146????0446002079447A44\",\n" +
                                "  \"rakclientinterface_netgame_offset\": 528,\n" +
                                "  \"use_samp_touch_workaround\": true,\n" +
                                "  \"nveventinsertnewest_offset\": 2606320\n" +
                                "}");
                        writer.flush();
                        writer.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void launchGameDirectly() {
        if (getActivity() != null && !getActivity().isFinishing()) {
            Intent intent = new Intent(getActivity(), SAMP.class);
            startActivity(intent);
            getActivity().finish();
        }
    }

    private void showFpsSettingsDialog() {
        if (getContext() == null || getActivity() == null) return;

        final Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_fps_settings);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int currentFps = new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "FPS_LIMIT");
        if (currentFps == 0) currentFps = 90;

        RadioGroup group = dialog.findViewById(R.id.dialog_fps_group);
        RadioButton r30 = dialog.findViewById(R.id.fps_30);
        RadioButton r60 = dialog.findViewById(R.id.fps_60);
        RadioButton r90 = dialog.findViewById(R.id.fps_90);
        RadioButton r120 = dialog.findViewById(R.id.fps_120);

        if (currentFps == 30 && r30 != null) r30.setChecked(true);
        else if (currentFps == 90 && r90 != null) r90.setChecked(true);
        else if (currentFps == 120 && r120 != null) r120.setChecked(true);
        else if (r60 != null) r60.setChecked(true);

        Button btnSave = dialog.findViewById(R.id.dialog_fps_btn_save);
        if (btnSave != null) {
            btnSave.setOnTouchListener(new ButtonAnimator(getContext(), btnSave));
            btnSave.setOnClickListener(v -> {
                int chosenFps = 60;
                if (r30 != null && r30.isChecked()) chosenFps = 30;
                else if (r60 != null && r60.isChecked()) chosenFps = 60;
                else if (r90 != null && r90.isChecked()) chosenFps = 90;
                else if (r120 != null && r120.isChecked()) chosenFps = 120;

                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "FPS_LIMIT", chosenFps);
                SettingsHelper.setSetting(requireContext(), "gui", "FPSLimit", chosenFps);
                Toast.makeText(getContext(), "ตั้งค่าจำกัด FPS เป็น " + chosenFps + " เรียบร้อย", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNicknameEdit != null && getContext() != null) {
            mNicknameEdit.setText(SettingsHelper.getNickName(getContext()));
        }
        if (mTvAnnouncement != null) {
            mTvAnnouncement.setSelected(true);
        }
        updateActionButtons();
        loadRemoteServerAndAnnouncement();
    }
}
