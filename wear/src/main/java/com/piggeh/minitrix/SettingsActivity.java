package com.piggeh.minitrix;

import android.content.SharedPreferences;
import android.os.Build;
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

        //Set up preferences
        _prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        //Set up checkboxes on screen to match preferences
        ((CheckBox) findViewById(R.id.settings_24hr)).setChecked(
                _prefs.getBoolean(getString(R.string.pref_24hr_key), getResources().getBoolean(R.bool.pref_24hr_default))
        );
        CheckBox viewTall = ((CheckBox) findViewById(R.id.settings_tall));
        CheckBox viewSeeThru = ((CheckBox) findViewById(R.id.settings_seethru));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            //Device is running Wear OS 2.0 or higher, hide preferences for 1.X
            viewTall.setVisibility(View.GONE);
            viewSeeThru.setVisibility(View.GONE);
        } else {
            //Device is running Android Wear 1.5 or lower, set up preferences for 1.X
            viewTall.setChecked(
                    _prefs.getBoolean(getString(R.string.pref_tall_key), getResources().getBoolean(R.bool.pref_tall_default))
            );
            viewSeeThru.setChecked(
                    _prefs.getBoolean(getString(R.string.pref_seethru_key), getResources().getBoolean(R.bool.pref_seethru_default))
            );
        }
    }

    public void onSettingClicked(View view){
        switch (view.getId()){
            case R.id.settings_24hr:
                _prefs.edit()
                        .putBoolean(getString(R.string.pref_24hr_key), ((CheckBox) view).isChecked())
                        .apply();
                break;
            case R.id.settings_tall:
                _prefs.edit()
                        .putBoolean(getString(R.string.pref_tall_key), ((CheckBox) view).isChecked())
                        .apply();
                break;
            case R.id.settings_seethru:
                _prefs.edit()
                        .putBoolean(getString(R.string.pref_seethru_key), ((CheckBox) view).isChecked())
                        .apply();
                break;
        }
    }
}
