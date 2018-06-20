package com.piggeh.minitrix;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences _prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        _prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        ((CheckBox) findViewById(R.id.settings_24hr)).setChecked(
                _prefs.getBoolean(getString(R.string.pref_24hr_key), getResources().getBoolean(R.bool.pref_24hr_default))
        );
    }

    public void onSettingClicked(View view){
        switch (view.getId()){
            case R.id.settings_24hr:
                _prefs.edit()
                        .putBoolean(getString(R.string.pref_24hr_key), ((CheckBox) view).isChecked())
                        .apply();
                break;
        }
    }
}
