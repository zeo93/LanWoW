package com.marco.lanwow;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText idInput = findViewById(R.id.input_client_id);
        TextInputEditText secretInput = findViewById(R.id.input_client_secret);
        idInput.setText(WarcraftLogs.savedClientId(this));
        secretInput.setText(WarcraftLogs.savedClientSecret(this));

        ((TextView) findViewById(R.id.wcl_help))
                .setMovementMethod(LinkMovementMethod.getInstance());

        MaterialButton save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> {
            WarcraftLogs.saveCredentials(this,
                    idInput.getText() != null ? idInput.getText().toString() : "",
                    secretInput.getText() != null ? secretInput.getText().toString() : "");
            Toast.makeText(this, R.string.credenziali_salvate, Toast.LENGTH_SHORT).show();
            finish();
        });

        TextView version = findViewById(R.id.version_text);
        version.setText(getString(R.string.versione_app, UpdateChecker.currentVersion(this)));

        MaterialButton check = findViewById(R.id.btn_check_update);
        check.setOnClickListener(v -> {
            check.setEnabled(false);
            UpdateChecker.checkAsync(this, (update, error) -> {
                check.setEnabled(true);
                if (update != null) {
                    UpdateChecker.showUpdateDialog(this, update);
                } else {
                    Toast.makeText(this, error != null
                            ? getString(R.string.errore_ricerca, error)
                            : getString(R.string.nessun_aggiornamento),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
