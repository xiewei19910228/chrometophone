/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.apps.chrometophone;

import java.util.ArrayList;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.google.android.c2dm.C2DMessaging;

/**
 * Main activity - takes user through set up.
 */
public class MainActivity extends Activity {
    public static final String UPDATE_UI_ACTION = "com.google.ctp.UPDATE_UI";
    public static final String AUTH_PERMISSION_ACTION = "com.google.ctp.AUTH_PERMISSION";

    private boolean mPendingAuth = false;
    private int mScreenId = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = Prefs.get(this);
        String deviceRegistrationID = prefs.getString("deviceRegistrationID", null);
        if (deviceRegistrationID != null) {
            setScreenContent(R.layout.connected);
        } else {
            setScreenContent(R.layout.intro);
        }

        registerReceiver(mUpdateUIReceiver, new IntentFilter(UPDATE_UI_ACTION));
        registerReceiver(mAuthPermissionReceiver, new IntentFilter(AUTH_PERMISSION_ACTION));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPendingAuth) {
            mPendingAuth = false;
            String regId = C2DMessaging.getRegistrationId(this);
            if (regId != null) {
                DeviceRegistrar.registerWithServer(this, regId);
            } else {
                C2DMessaging.register(this, DeviceRegistrar.SENDER_ID);
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUpdateUIReceiver);
        unregisterReceiver(mAuthPermissionReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.help, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.help: {
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void setScreenContent(int screenId) {
        mScreenId = screenId;
        setContentView(screenId);
        switch (screenId) {
              case R.layout.intro: {
                  setIntroScreenContent();
                  break;
              }
              case R.layout.select_account: {
                  setSelectAccountScreenContent();
                  break;
              }
              case R.layout.select_launch_mode: {
                  setSelectLaunchModeScreenContent();
                  break;
              }
              case R.layout.connected: {
                  setConnectedScreenContent();
                  break;
              }
        }
    }

    private void setIntroScreenContent() {
        TextView textView = (TextView) findViewById(R.id.intro_text);
        textView.setText(Html.fromHtml(getString((R.string.intro_text))));
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        Button exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        Button nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                setScreenContent(R.layout.select_account);
            }
        });
    }

    private void setSelectAccountScreenContent() {
        final Button prevButton = (Button) findViewById(R.id.prev);
        prevButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                setScreenContent(R.layout.intro);
            }
        });

        final Button nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                ListView listView = (ListView) findViewById(R.id.select_account);
                TextView account =
                        (TextView) listView.getChildAt(listView.getCheckedItemPosition());
                prevButton.setEnabled(false);
                nextButton.setEnabled(false);
                register((String) account.getText());
            }
        });

        // Display accounts
        String accounts[] = getGoogleAccounts();
        if (accounts.length == 0) {
            TextView promptText = (TextView) findViewById(R.id.select_text);
            promptText.setText(R.string.no_accounts);
            TextView nextText = (TextView) findViewById(R.id.click_next_text);
            nextText.setVisibility(TextView.INVISIBLE);
            nextButton.setEnabled(false);
        } else {
            ListView listView = (ListView) findViewById(R.id.select_account);
            listView.setAdapter(new ArrayAdapter<String>(this,
                    R.layout.account, accounts));
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setItemChecked(0, true);
        }
    }

    private void setSelectLaunchModeScreenContent() {
        Button prevButton = (Button) findViewById(R.id.prev);
        prevButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                setScreenContent(R.layout.select_account);
            }
        });

        Button nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                storeLaunchModePreference();
                setScreenContent(R.layout.connected);
            }
        });

        RadioButton manualButton = (RadioButton) findViewById(R.id.manual_launch);
        manualButton.setChecked(true);
    }

    private void setConnectedScreenContent() {
        SharedPreferences prefs = Prefs.get(this);
        TextView statusText = (TextView) findViewById(R.id.connected_with_account_text);
        statusText.setText(getString(R.string.connected_with_account_text) + " " +
                prefs.getString("accountName", "error"));

        if (prefs.getBoolean("launchBrowserOrMaps", false)) {
            RadioButton automaticButton = (RadioButton) findViewById(R.id.auto_launch);
            automaticButton.setChecked(true);
        } else {
            RadioButton manualButton = (RadioButton) findViewById(R.id.manual_launch);
            manualButton.setChecked(true);
        }

        RadioGroup launchMode = (RadioGroup) findViewById(R.id.launch_mode_radio);
        launchMode.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                storeLaunchModePreference();
            } });


        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                unregister();
            }
        });
    }

    private void storeLaunchModePreference() {
        SharedPreferences prefs = Prefs.get(this);
        SharedPreferences.Editor editor = prefs.edit();
        RadioGroup launchMode = (RadioGroup) findViewById(R.id.launch_mode_radio);
        editor.putBoolean("launchBrowserOrMaps",
                launchMode.getCheckedRadioButtonId() == R.id.auto_launch);
        editor.commit();
    }

    private void register(String account) {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        TextView textView = (TextView) findViewById(R.id.connecting_text);
        textView.setVisibility(ProgressBar.VISIBLE);

        SharedPreferences prefs = Prefs.get(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("accountName", account);
        editor.commit();

        C2DMessaging.register(this, DeviceRegistrar.SENDER_ID);
    }

    private void unregister() {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        TextView textView = (TextView) findViewById(R.id.disconnecting_text);
        textView.setVisibility(ProgressBar.VISIBLE);

        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setEnabled(false);

        C2DMessaging.unregister(this);
    }

    private String[] getGoogleAccounts() {
        ArrayList<String> accountNames = new ArrayList<String>();
        Account[] accounts = AccountManager.get(this).getAccounts();
        for (Account account : accounts) {
            if (account.type.equals("com.google")) {
                accountNames.add(account.name);
            }
        }

        String[] result = new String[accountNames.size()];
        accountNames.toArray(result);
        return result;
    }

    private void handleConnectingUpdate(int status) {
        if (status == DeviceRegistrar.REGISTERED_STATUS) {
            setScreenContent(R.layout.select_launch_mode);
        } else {
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
            progressBar.setVisibility(ProgressBar.INVISIBLE);
            TextView textView = (TextView) findViewById(R.id.connecting_text);
            textView.setText(status == DeviceRegistrar.AUTH_ERROR_STATUS ? R.string.auth_error_text :
                    R.string.connect_error_text);

            Button prevButton = (Button) findViewById(R.id.prev);
            prevButton.setEnabled(true);

            Button nextButton = (Button) findViewById(R.id.next);
            nextButton.setEnabled(true);
        }
    }

    private void handleDisconnectingUpdate(int status) {
        if (status == DeviceRegistrar.UNREGISTERED_STATUS) {
            setScreenContent(R.layout.intro);
        } else {
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
            progressBar.setVisibility(ProgressBar.INVISIBLE);

            TextView textView = (TextView) findViewById(R.id.disconnecting_text);
            textView.setText(R.string.disconnect_error_text);

            Button disconnectButton = (Button) findViewById(R.id.disconnect);
            disconnectButton.setEnabled(true);
        }
    }

    private final BroadcastReceiver mUpdateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScreenId == R.layout.select_account) {
                handleConnectingUpdate(intent.getIntExtra(
                        DeviceRegistrar.STATUS_EXTRA, DeviceRegistrar.ERROR_STATUS));
            } else if (mScreenId == R.layout.connected) {
                handleDisconnectingUpdate(intent.getIntExtra(
                        DeviceRegistrar.STATUS_EXTRA, DeviceRegistrar.ERROR_STATUS));
            }
        }
    };

    private final BroadcastReceiver mAuthPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getBundleExtra("AccountManagerBundle");
            if (extras != null) {
                Intent authIntent = (Intent) extras.get(AccountManager.KEY_INTENT);
                if (authIntent != null) {
                    mPendingAuth = true;
                    startActivity(authIntent);
                }
            }
        }
    };
}
