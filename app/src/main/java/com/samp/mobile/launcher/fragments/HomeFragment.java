package com.samp.mobile.launcher.fragments;

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
    private TextView mTvAnnouncement;
    private View mAnnouncementContainer;

    // Config defaults (updated remotely from security.json)
    private String mServerAddress = "192.168.1.106";
    private int mServerPort = 7777;
    private String mServerName = "[TH] 4KING ROLEPLAY";

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
        mTvAnnouncement = view.findViewById(R.id.tv_announcement);
        mAnnouncementContainer = view.findViewById(R.id.announcement_container);

        if (mTvAnnouncement != null) {
            mTvAnnouncement.setSelected(true); // Required for Marquee text scroll
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
            mBtnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFpsSettingsDialog();
                }
            });
        }

        // Bottom-Right: Update Button (Open Game Data Download & Verify Dialog)
        if (mBtnUpdate != null) {
            mBtnUpdate.setOnTouchListener(new ButtonAnimator(getContext(), mBtnUpdate));
            mBtnUpdate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GameDataDownloadDialog.show(getActivity(), null);
                }
            });
        }

        // Bottom-Right: Start Game Button -> One-Click Play with Anti-Cheat
        if (mBtnStartGame != null) {
            mBtnStartGame.setOnTouchListener(new ButtonAnimator(getContext(), mBtnStartGame));
            mBtnStartGame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
                        targetServer.setServerStatus(SAMPServerInfo.Status.ONLINE);
                    } else {
                        targetServer.setServerName(mServerName);
                        targetServer.setAddress(mServerAddress);
                        targetServer.setPort(mServerPort);
                    }

                    final SAMPServerInfo finalTargetServer = targetServer;

                    // 1. Check if game data exists on device
                    if (!GameDataDownloadDialog.isGameDataInstalled()) {
                        Toast.makeText(getContext(), "ยังไม่พบข้อมูลตัวเกม กรุณาดาวน์โหลด DATA ก่อนเข้าเล่น", Toast.LENGTH_SHORT).show();
                        GameDataDownloadDialog.show(getActivity(), new Runnable() {
                            @Override
                            public void run() {
                                startAntiCheatAndLaunch(finalTargetServer);
                            }
                        });
                    } else {
                        startAntiCheatAndLaunch(finalTargetServer);
                    }
                }
            });
        }

        return view;
    }

    private void loadRemoteServerAndAnnouncement() {
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

                // 2. Server Status & Config
                JSONObject srv = sec.optJSONObject("server");
                int fallbackPlayers = 128;
                int fallbackMaxPlayers = 500;
                int fallbackPing = 18;

                if (srv != null) {
                    mServerName = srv.optString("name", mServerName);
                    mServerAddress = srv.optString("ip", mServerAddress);
                    mServerPort = srv.optInt("port", mServerPort);
                    fallbackPlayers = srv.optInt("fallback_players", fallbackPlayers);
                    fallbackMaxPlayers = srv.optInt("fallback_max_players", fallbackMaxPlayers);
                    fallbackPing = srv.optInt("fallback_ping", fallbackPing);
                }

                final int finalPlayers = fallbackPlayers;
                final int finalMax = fallbackMaxPlayers;
                final int finalPing = fallbackPing;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (mTvServerName != null) mTvServerName.setText(mServerName);
                        if (mTvServerPlayers != null) {
                            mTvServerPlayers.setText("ONLINE 👥 " + finalPlayers + "/" + finalMax + "  📶 " + finalPing + "ms");
                        }
                    });
                }

                // 3. Live UDP Query via SampQueryAPI
                try {
                    long startTime = System.currentTimeMillis();
                    SampQueryAPI query = new SampQueryAPI(mServerAddress, mServerPort);
                    if (query.mo7166d()) {
                        String[] info = query.mo7164b();
                        long ping = System.currentTimeMillis() - startTime;
                        if (info != null && info.length >= 4) {
                            int currentPlayers = Integer.parseInt(info[1]);
                            int maxPlayers = Integer.parseInt(info[2]);
                            String liveName = info[3];
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (mTvServerPlayers != null) {
                                        mTvServerPlayers.setText("ONLINE 👥 " + currentPlayers + "/" + maxPlayers + "  📶 " + ping + "ms");
                                    }
                                    if (!liveName.isEmpty() && mTvServerName != null) {
                                        mTvServerName.setText(liveName);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {}

            } catch (Exception e) {
                e.printStackTrace();
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

        // 2. Run Anti-Cheat Scanning Modal (One-click flow)
        AntiCheatScanDialog.startScan(getActivity(), new Runnable() {
            @Override
            public void run() {
                launchGameDirectly();
            }
        });
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
        if (currentFps == 0) currentFps = 60;

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
            btnSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int chosenFps = 60;
                    if (r30 != null && r30.isChecked()) chosenFps = 30;
                    else if (r60 != null && r60.isChecked()) chosenFps = 60;
                    else if (r90 != null && r90.isChecked()) chosenFps = 90;
                    else if (r120 != null && r120.isChecked()) chosenFps = 120;

                    new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "FPS_LIMIT", chosenFps);
                    SettingsHelper.setSetting(requireContext(), "gui", "FPSLimit", chosenFps);
                    Toast.makeText(getContext(), "ตั้งค่าจำกัด FPS เป็น " + chosenFps + " เรียบร้อย", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
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
    }
}
