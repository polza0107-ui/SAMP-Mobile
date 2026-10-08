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
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.adapters.ServerInformationFragment;
import com.samp.mobile.launcher.util.ButtonAnimator;
import com.samp.mobile.launcher.util.GameDataDownloadDialog;
import com.samp.mobile.launcher.util.SAMPServerInfo;
import com.samp.mobile.launcher.util.SettingsHelper;
import com.samp.mobile.launcher.util.SharedPreferenceCore;

@Obfuscate
public class HomeFragment extends Fragment {

    private EditText mNicknameEdit;
    private Button mBtnUpdate;
    private Button mBtnStartGame;
    private ImageView mBtnSettings;

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

        // Bottom-Right: Start Game Button -> Check DATA before Launch
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
                        targetServer.setServerName("[TH] 4KING ROLEPLAY");
                        targetServer.setAddress("192.168.1.106");
                        targetServer.setPort(7777);
                        targetServer.setCurrentPlayerCount(0);
                        targetServer.setMaxPlayerCount(50);
                        targetServer.setHasPassword(false);
                        targetServer.setServerStatus(SAMPServerInfo.Status.ONLINE);
                    }

                    final SAMPServerInfo finalTargetServer = targetServer;

                    // Check if game data exists on device
                    if (!GameDataDownloadDialog.isGameDataInstalled()) {
                        Toast.makeText(getContext(), "ยังไม่พบข้อมูลตัวเกม กรุณาดาวน์โหลด DATA ก่อนเข้าเล่น", Toast.LENGTH_SHORT).show();
                        GameDataDownloadDialog.show(getActivity(), new Runnable() {
                            @Override
                            public void run() {
                                openConnectDialog(finalTargetServer);
                            }
                        });
                    } else {
                        openConnectDialog(finalTargetServer);
                    }
                }
            });
        }

        return view;
    }

    private void openConnectDialog(SAMPServerInfo serverInfo) {
        if (getActivity() != null && !getActivity().isFinishing()) {
            ServerInformationFragment dialog = new ServerInformationFragment(getActivity(), serverInfo);
            dialog.show();
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
    }
}
