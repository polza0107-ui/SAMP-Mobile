package com.samp.mobile.launcher.fragments;

import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.adapters.ServerInformationFragment;
import com.samp.mobile.launcher.util.ButtonAnimator;
import com.samp.mobile.launcher.util.SAMPServerInfo;
import com.samp.mobile.launcher.util.SettingsHelper;
import com.samp.mobile.launcher.util.SharedPreferenceCore;

@Obfuscate
public class HomeFragment extends Fragment {

    private EditText mNicknameEdit;
    private Button mBtnFps;
    private Button mBtnStart;

    private final String[] fpsOptions = {"30 FPS", "60 FPS", "90 FPS", "120 FPS"};
    private final int[] fpsValues = {30, 60, 90, 120};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        if (getActivity() instanceof MainActivity) {
            MainActivity.hideKeyboard(getActivity());
        }

        mNicknameEdit = view.findViewById(R.id.home_nickname_edit);
        mBtnFps = view.findViewById(R.id.home_btn_fps);
        mBtnStart = view.findViewById(R.id.home_btn_start);

        // Load Nickname
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

        // Update FPS Button text
        updateFpsButtonText();

        // FPS Button Click: Popup dialog to select FPS
        if (mBtnFps != null) {
            mBtnFps.setOnTouchListener(new ButtonAnimator(getContext(), mBtnFps));
            mBtnFps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFpsPickerDialog();
                }
            });
        }

        // Start Game Button Click: Save nickname and open Connect Dialog
        if (mBtnStart != null) {
            mBtnStart.setOnTouchListener(new ButtonAnimator(getContext(), mBtnStart));
            mBtnStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Save nickname
                    if (mNicknameEdit != null) {
                        String name = mNicknameEdit.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(getContext(), "กรุณาใส่ชื่อตัวละครก่อนเริ่มเกม!", Toast.LENGTH_SHORT).show();
                            mNicknameEdit.requestFocus();
                            return;
                        }
                        SettingsHelper.setNickName(requireContext(), name);
                    }

                    // Get main 4KING server info
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
                        targetServer.setAddress("192.168.1.112");
                        targetServer.setPort(7777);
                        targetServer.setCurrentPlayerCount(0);
                        targetServer.setMaxPlayerCount(50);
                        targetServer.setHasPassword(false);
                        targetServer.setServerStatus(SAMPServerInfo.Status.ONLINE);
                    }

                    // Show Connect Dialog directly
                    ServerInformationFragment dialog = new ServerInformationFragment(getActivity(), targetServer);
                    dialog.show();
                }
            });
        }

        // Community Links
        ImageView yt_image = view.findViewById(R.id.youtube_logo);
        if (yt_image != null) {
            yt_image.setOnTouchListener(new ButtonAnimator(getContext(), yt_image));
            yt_image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://bit.ly/x1y2z_yt")));
                }
            });
        }

        ImageView discord_image = view.findViewById(R.id.discord_logo);
        if (discord_image != null) {
            discord_image.setOnTouchListener(new ButtonAnimator(getContext(), discord_image));
            discord_image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/jvKM7HR3Dc")));
                }
            });
        }

        ImageView internet_logo = view.findViewById(R.id.internet_button);
        if (internet_logo != null) {
            internet_logo.setOnTouchListener(new ButtonAnimator(getContext(), internet_logo));
            internet_logo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://samp-mobile.shop")));
                }
            });
        }

        return view;
    }

    private void updateFpsButtonText() {
        if (mBtnFps == null || getContext() == null) return;
        int currentFps = new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "FPS_LIMIT");
        if (currentFps == 0) currentFps = 60;
        mBtnFps.setText("FPS: " + currentFps);
    }

    private void showFpsPickerDialog() {
        if (getContext() == null || getActivity() == null) return;
        int currentFps = new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "FPS_LIMIT");
        int selectedIndex = 1; // default 60
        for (int i = 0; i < fpsValues.length; i++) {
            if (fpsValues[i] == currentFps) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("เลือกจำกัดเฟรมเรต (FPS Limit)");
        builder.setSingleChoiceItems(fpsOptions, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int chosenFps = fpsValues[which];
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "FPS_LIMIT", chosenFps);
                SettingsHelper.setSetting(requireContext(), "gui", "FPSLimit", chosenFps);
                updateFpsButtonText();
                Toast.makeText(getContext(), "ตั้งค่า FPS เป็น " + chosenFps + " เรียบร้อย", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("ยกเลิก", null);
        builder.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNicknameEdit != null && getContext() != null) {
            mNicknameEdit.setText(SettingsHelper.getNickName(getContext()));
        }
        updateFpsButtonText();
    }
}
