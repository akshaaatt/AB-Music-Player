package com.music.player.bhandari.m.activity;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.appinvite.FirebaseAppInvite;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.music.player.bhandari.m.R;
import com.music.player.bhandari.m.UIElementHelper.MyDialogBuilder;
import com.music.player.bhandari.m.fcm.CountryInfo;
import com.music.player.bhandari.m.model.Constants;
import com.music.player.bhandari.m.service.NotificationListenerService;
import com.music.player.bhandari.m.service.PlayerService;
import com.music.player.bhandari.m.MyApp;
import com.music.player.bhandari.m.utils.UtilityFun;


import java.util.ArrayList;
import io.github.inflationx.viewpump.ViewPumpContextWrapper;

/**
 Copyright 2017 Amit Bhandari AB

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

public class ActivityPermissionSeek extends AppCompatActivity {


    final private int MY_PERMISSIONS_REQUEST = 0;
    private static String[] PERMISSIONS = {Manifest.permission.READ_PHONE_STATE
            , Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private boolean mBound=false;
    private boolean mInAppBillingBound = false;
    private ServiceConnection playerServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            PlayerService.PlayerBinder playerBinder = (PlayerService.PlayerBinder) service;
            PlayerService playerService = playerBinder.getService();
            MyApp.setService(playerService);
            mBound=true;
            Log.v(Constants.TAG,"LAUNCH MAIN ACTIVITY");

            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                    !MyApp.getPref().getBoolean(getString(R.string.pref_never_ask_notitication_permission), false) &&
                    !NotificationListenerService.isListeningAuthorized(ActivityPermissionSeek.this)){
                startActivity(new Intent(ActivityPermissionSeek.this, ActivityRequestNotificationAccess.class));
            }else {
                //startActivity(new Intent(ActivityPermissionSeek.this, ActivityLyricCard.class).putExtra("lyric", "Wow"));
                startActivity(new Intent(ActivityPermissionSeek.this, ActivityMain.class));
            }
            finish();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound=false;
        }
    };

    private IInAppBillingService mService;

    private ServiceConnection inAppBillingConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mInAppBillingBound = false;
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            mInAppBillingBound =true;
            //check if user has already removed ads
            //in case, he removed ads, and reinstalled app
            Bundle ownedItems = null;
            try {
                ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
            } catch (RemoteException e) {

                e.printStackTrace();
                return;
            }

            int response = ownedItems.getInt("RESPONSE_CODE");
            if (response == 0) {
                ArrayList<String> ownedSkus =
                        ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");

                if (ownedSkus != null) {
                    for (int i = 0; i < ownedSkus.size(); ++i) {
                        String sku = ownedSkus.get(i);
                        if (sku.equals(getString(R.string.remove_ads))) {
                            //Toast.makeText(getApplicationContext()
                            // , "You already have removed the ads!",Toast.LENGTH_LONG).show();
                            MyApp.getPref().edit().putBoolean(getString(R.string.pref_remove_ads_after_payment),true).apply();
                            return;
                        }
                    }
                    MyApp.getPref().edit().putBoolean(getString(R.string.pref_remove_ads_after_payment),false).apply();
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //check version and make changes if any

        /*
          This will finish your Launcher Activity before it is displayed by detecting that there is already a task running,
          and app should instead resume to the last visible Activity.
          https://stackoverflow.com/a/21022876/5430666
         */
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }

        //setContentView(R.layout.splash_activity);

        if(!hasPermissions(this, PERMISSIONS)) {
            try {
                permissionDetailsDialog();
            }catch (Exception e){
                RequestPermission();
            }
        }else {
            bindService();
        }

        setNotificationChannelForOreoPlus();

        //if(MyApp.getPref().getBoolean(getString(R.string.pref_first_install),true)) {
            //checkForDeepLink();
        //}

        changeSettingsForVersion();

        new CountryInfo().start();

        try {
            initializeRemoteConfig();
        }catch (Exception e){
            //unknown crash in firebase library
            Log.d("ActivityPermissionSeek", "onCreate: " + e.getLocalizedMessage());
        }
        //log selected font to know which font is used maximum
        //logFont();
    }

    private void initializeRemoteConfig() {

        final FirebaseRemoteConfig mRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings remoteConfigSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(60)
                .build();
        mRemoteConfig.setConfigSettingsAsync(remoteConfigSettings);
        mRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

        // cache expiration in seconds
        long cacheExpiration = 3600L;   //1 hour

        //expire the cache immediately for development mode .
        /*if (mRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }*/

        // fetch
        mRemoteConfig.fetch(cacheExpiration)
                .addOnCompleteListener(this, task -> {
                    Log.d("ActivityPermissionSeek", "onComplete: ");
                    if (task.isSuccessful()) {
                        mRemoteConfig.fetchAndActivate();

                        final String message = FirebaseRemoteConfig.getInstance().getString("developer_message");

                        //new developer message, update UI
                        if (!MyApp.getPref().getString("developer_message", "").equals(message)) {
                            MyApp.getPref().edit().putString("developer_message", message).apply();
                            MyApp.getPref().edit().putBoolean("new_dev_message", true).apply();
                        }
                    }
                });
    }



    private void permissionDetailsDialog(){
        new MyDialogBuilder(this)
                .title(R.string.permission_details_title)
                .content(R.string.permission_details_content)
                .positiveText(R.string.permission_details_pos)
                .negativeText(getString(R.string.cancel))
                .cancelable(false)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        RequestPermission();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        finish();
                    }
                })
                .show();
    }

    private void setNotificationChannelForOreoPlus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    /* Create or update. */
                NotificationChannel channel = new NotificationChannel("channel_01",
                        "Playback Notification",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setSound(null, null);

                NotificationChannel channel2 = new NotificationChannel("channel_02",
                        "Instant Lyrics",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setSound(null, null);

                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel2);
            }
        }catch (Exception ignored){}
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase));
    }

    private void RequestPermission(){
        // Here, thisActivity is the current activity

            ActivityCompat.requestPermissions(this,
                    PERMISSIONS,
                    MY_PERMISSIONS_REQUEST);

    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void changeSettingsForVersion() {

        //if first install
        if(MyApp.getPref().getBoolean(getString(R.string.pref_first_install),true)) {
            MyApp.getPref().edit().putBoolean(getString(R.string.pref_first_install),false).apply();
            MyApp.getPref().edit().putInt(getString(R.string.pref_theme), Constants.PRIMARY_COLOR.GLOSSY).apply();
            MyApp.getPref().edit().putInt(getString(R.string.pref_theme_color), Constants.PRIMARY_COLOR.BLACK).apply();
            MyApp.getPref().edit().putBoolean(getString(R.string.pref_prefer_system_equ),false).apply();
            MyApp.getPref().edit().putInt(getString(R.string.pref_text_font), Constants.TYPEFACE.MANROPE).apply();
            MyApp.getPref().edit().putInt(getString(R.string.pref_theme_id), Constants.DEFAULT_THEME_ID).apply();
            MyApp.getPref().edit().putInt(getString(R.string.pref_text_font), Constants.TYPEFACE.MANROPE).apply();

        }

        //disable lock screen album art
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            MyApp.getPref().edit().putBoolean(getString(R.string.pref_lock_screen_album_Art), false).apply();
        }

        setDeprecatedPreferencesValues();
    }

    private void setDeprecatedPreferencesValues() {
        if(MyApp.getPref().getInt(getString(R.string.pref_click_on_notif)
                ,-1)!= Constants.CLICK_ON_NOTIF.OPEN_DISC_VIEW){
            MyApp.getPref().edit().putInt(getString(R.string.pref_click_on_notif)
                    ,Constants.CLICK_ON_NOTIF.OPEN_DISC_VIEW).apply();
        }

        //REMOVED PREFERENCES

        if(MyApp.getPref().getFloat(getString(R.string.pref_disc_size)
                ,-1)!=Constants.DISC_SIZE.MEDIUM){
            MyApp.getPref().edit().putFloat(getString(R.string.pref_disc_size)
                    ,Constants.DISC_SIZE.MEDIUM).apply();
        }

        /*if(!MyApp.getPref().getBoolean(getString(R.string.pref_album_lib_view)
                ,false)){
            MyApp.getPref().edit().putBoolean(getString(R.string.pref_album_lib_view)
                    ,true).apply();
        }*/
    }

    private void bindService(){
        //initialize music library instance
       // MusicLibrary.getInstance();

        startService(new Intent(this,PlayerService.class));
        try {
            Intent playerServiceIntent = new Intent(this, PlayerService.class);
            bindService(playerServiceIntent, playerServiceConnection, Context.BIND_AUTO_CREATE);
        }catch (Exception ignored){

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if(mBound) {
                unbindService(playerServiceConnection);
                mBound=false;
            }

            if(mInAppBillingBound){
                unbindService(inAppBillingConnection);
                mInAppBillingBound = false;
            }
        }catch (Exception ignored){

        }

    }

    private void logFont(){

        if(MyApp.getPref().getBoolean(getString(R.string.pref_font_already_logged), false)){
            return;
        }

        try {
            int textFontPref = MyApp.getPref().getInt(getString(R.string.pref_text_font), Constants.TYPEFACE.MONOSPACE);
            String fontString = "";
            switch (textFontPref){
                case Constants.TYPEFACE.MONOSPACE:
                    fontString = "MONOSPACE";
                    break;

                case Constants.TYPEFACE.SOFIA:
                    fontString = "SOFIA";
                    break;

                case Constants.TYPEFACE.SYSTEM_DEFAULT:
                    fontString = "SYSTEM_DEFAULT";
                    break;

                case Constants.TYPEFACE.MANROPE:
                    fontString = "MANROPE";
                    break;

                case Constants.TYPEFACE.ASAP:
                    fontString = "ASAP";
                    break;
            }
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, fontString );
            UtilityFun.logEvent(bundle);
            MyApp.getPref().edit().putBoolean(getString(R.string.pref_font_already_logged), true).apply();
        }catch (Exception ignored){
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {

                if(grantResults.length==0){
                    return;
                }
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED
                        //&& grantResults[2] == PackageManager.PERMISSION_GRANTED
                        ) {
                    bindService();
                } else {

                    if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                        //READ PHONE STATE DENIED
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Toast.makeText(this, getString(R.string.phone_stat_perm_required), Toast.LENGTH_LONG).show();
                        finish();
                    }else if(grantResults[1] == PackageManager.PERMISSION_DENIED) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Toast.makeText(this, getString(R.string.storage_perm_required), Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }
            break;

        }
    }
}
