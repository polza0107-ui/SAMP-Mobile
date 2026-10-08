package com.samp.mobile.game;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.bytedance.shadowhook.ShadowHook;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.launcher.util.ConfigValidator;
import com.samp.mobile.launcher.util.SharedPreferenceCore;
import com.samp.mobile.launcher.util.SignatureChecker;
import com.wardrumstudios.utils.WarMedia;

@Obfuscate
public class GTASA extends WarMedia {
    // public static GTASA gtasaSelf = null;
    static String vmVersion;
    private boolean once = false;

    static {
        ShadowHook.init(new ShadowHook.ConfigBuilder()
                .setMode(ShadowHook.Mode.UNIQUE)
                .build());

        vmVersion = null;
        System.out.println("**** Loading SO's");
        try {
            vmVersion = System.getProperty("java.vm.version");
            System.out.println("vmVersion " + vmVersion);
            System.loadLibrary("ImmEmulatorJ");
        }
        catch (ExceptionInInitializerError | UnsatisfiedLinkError ignored) {
        }
        System.loadLibrary("GTASA");
        System.loadLibrary("bass");
        System.loadLibrary("samp");
    }

    public static void staticEnterSocialClub()
    {
        //  gtasaSelf.EnterSocialClub();
    }

    public static void staticExitSocialClub() {
        //gtasaSelf.ExitSocialClub();
    }

    public void AfterDownloadFunction() {

    }

    public void EnterSocialClub() {

    }

    public void ExitSocialClub() {

    }

    public boolean ServiceAppCommand(String str, String str2)
    {
        return false;
    }

    public int ServiceAppCommandValue(String str, String str2)
    {
        return 0;
    }

    public native void main();

    public void onActivityResult(int i, int i2, Intent intent)
    {
        super.onActivityResult(i, i2, intent);
    }

    public void onConfigurationChanged(Configuration configuration)
    {
        super.onConfigurationChanged(configuration);
    }

    public void onCreate(Bundle bundle)
    {
        if(!once)
        {
            once = true;
        }

        //if(!SignatureChecker.isSignatureValid(this, getPackageName()))
        //{
        //Toast.makeText(this, "Use original launcher! No remake", Toast.LENGTH_LONG).show();
        //return;
        //}

        System.out.println("GTASA onCreate");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            try {
                android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {}
        }
        ConfigValidator.validateConfigFiles(this);
        //  gtasaSelf = this;
        super.onCreate(bundle);

        // Unlock High Display Refresh Rate (90Hz / 120Hz / 144Hz) for Game Window
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.view.Display display = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    display = getDisplay();
                } else {
                    display = getWindowManager().getDefaultDisplay();
                }
                if (display != null) {
                    android.view.Display.Mode[] modes = display.getSupportedModes();
                    android.view.Display.Mode bestMode = null;
                    float maxRate = 60.0f;
                    for (android.view.Display.Mode mode : modes) {
                        if (mode.getRefreshRate() > maxRate) {
                            maxRate = mode.getRefreshRate();
                            bestMode = mode;
                        }
                    }
                    if (bestMode != null) {
                        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
                        params.preferredDisplayModeId = bestMode.getModeId();
                        getWindow().setAttributes(params);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (new SharedPreferenceCore().getBoolean(this, "MLOADER")) {
            try {
                System.loadLibrary("monetloader");
            } catch (ExceptionInInitializerError | UnsatisfiedLinkError e) {
                Log.e("AXL", e.getMessage());
            }
        }
    }

    public void onDestroy()
    {
        System.out.println("GTASA onDestroy");
        super.onDestroy();
    }

    public boolean onKeyDown(int i, KeyEvent keyEvent)
    {
        return super.onKeyDown(i, keyEvent);
    }

    public void onPause()
    {
        System.out.println("GTASA onPause");
        super.onPause();
    }

    public void onRestart()
    {
        System.out.println("GTASA onRestart");
        super.onRestart();
    }

    public void onResume()
    {
        System.out.println("GTASA onResume");
        super.onResume();
    }

    public void onStart()
    {
        System.out.println("GTASA onStart");
        super.onStart();
    }

    public void onStop()
    {
        System.out.println("GTASA onStop");
        super.onStop();
    }

    public native void setCurrentScreenSize(int i, int i2);
}