package com.espressif.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.espressif.ui.adapters.LanguageListAdapter;
import com.espressif.ui.models.Language;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import avs.Avsconfig;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class LanguageListActivity extends AppCompatActivity {

    private static final String TAG = LanguageListActivity.class.getSimpleName();

    private ListView listView;
    private LanguageListAdapter adapter;
    private ArrayList<Language> languages;
    private int selectedLang;
    private SweetAlertDialog pDialog;

    private String deviceHostAddress;

    private Session session;
    private Security security;
    private Transport transport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_language);
        setSupportActionBar(toolbar);

        deviceHostAddress = getIntent().getStringExtra(LoginWithAmazon.KEY_HOST_ADDRESS);
        selectedLang = getIntent().getExtras().getInt(AppConstants.KEY_DEVICE_LANGUAGE);

        initViews();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {

                transport = new SoftAPTransport(deviceHostAddress + ":80");
                security = new Security0();
                session = new Session(transport, security);
                session.init(null);

                session.sessionListener = new Session.SessionListener() {

                    @Override
                    public void OnSessionEstablished() {

                        Log.d(TAG, "Session established");
                        changeLanguage(position);
                    }

                    @Override
                    public void OnSessionEstablishFailed(Exception e) {
                        Log.e(TAG, "Session failed");
                        e.printStackTrace();
                        Toast.makeText(LanguageListActivity.this, R.string.error_session_init_failed, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                };
            }
        });
    }

    private void initViews() {

        listView = findViewById(R.id.language_list);
        String[] languageNames = getResources().getStringArray(R.array.language_array);
        languages = new ArrayList<>();

        for (int index = 0; index < languageNames.length; index++) {

            Language lang = new Language();
            lang.setLanguageName(languageNames[index]);
            lang.setSelected(false);

            if (selectedLang == index) {
                lang.setSelected(true);
            }
            languages.add(lang);
        }
        adapter = new LanguageListAdapter(this, R.id.tv_language_name, languages);
        listView.setAdapter(adapter);
    }

    private void changeLanguage(final int langValue) {

        final String progressMsg = getString(R.string.progress_set_language);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdSetAssistantLang languageChangeRequest = Avsconfig.CmdSetAssistantLang.newBuilder()
                .setAssistantLangValue(langValue)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetAssistantLang;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdAssistantLang(languageChangeRequest)
                .build();

        byte[] message = this.security.encrypt(payload.toByteArray());
        this.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Language change msg sent");
                Avsconfig.AVSConfigStatus responseStatus = processLanguageChangeResponse(returnData);

                if (responseStatus == Avsconfig.AVSConfigStatus.Success) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            adapter.setLanguageChecked(langValue);
                            hideProgressDialog();
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra(AppConstants.KEY_DEVICE_LANGUAGE, langValue);
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in changing language");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(LanguageListActivity.this, R.string.error_language_change, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processLanguageChangeResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = this.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetAssistantLang response = payload.getRespAssistantLang();
            status = response.getStatus();
            Log.d(TAG, "Language change msg status : " + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void showProgressDialog(String message) {

        if (pDialog == null || !pDialog.isShowing()) {
            pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText(message);
            pDialog.setCancelable(true);
            pDialog.show();
        }
    }

    private void hideProgressDialog() {

        if (pDialog != null) {
            pDialog.dismiss();
            pDialog = null;
        }
    }
}
