package com.marco.lanwow;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "search";
    private static final String[] REGIONS = {"eu", "us", "kr", "tw"};

    private final Handler main = new Handler(Looper.getMainLooper());

    private AutoCompleteTextView regionInput;
    private TextInputEditText realmInput;
    private TextInputEditText nameInput;
    private ProgressBar progress;
    private TextView errorText;
    private LinearLayout results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        regionInput = findViewById(R.id.input_region);
        realmInput = findViewById(R.id.input_realm);
        nameInput = findViewById(R.id.input_name);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.error_text);
        results = findViewById(R.id.results);

        regionInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, REGIONS));

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        regionInput.setText(p.getString("region", "eu"), false);

        MaterialButton search = findViewById(R.id.btn_search);
        search.setOnClickListener(v -> doSearch());

        NotificationHelper.ensureChannels(this);
        askNotificationPermission();
        DailyWorker.schedule(this);
        UpdateChecker.checkOnStartup(this);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> { }).launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_favorites) {
            startActivity(new Intent(this, FavoritesActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doSearch() {
        String region = regionInput.getText().toString().trim().toLowerCase();
        String realm = realmInput.getText() != null ? realmInput.getText().toString().trim() : "";
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        if (region.isEmpty() || name.isEmpty()) {
            errorText.setText(R.string.compila_regione_nome);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("region", region).apply();
        errorText.setVisibility(View.GONE);
        results.removeAllViews();

        // Con il realm si va dritti alla scheda; senza si cerca per nome
        if (!realm.isEmpty()) {
            CharacterActivity.open(this, region, realm, name, "");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<RaiderIo.SearchResult> found = null;
            String error = null;
            try {
                found = RaiderIo.search(region, name);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<RaiderIo.SearchResult> fFound = found;
            final String fError = error;
            main.post(() -> {
                progress.setVisibility(View.GONE);
                if (fFound == null) {
                    errorText.setText(getString(R.string.errore_ricerca, fError));
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                if (fFound.isEmpty()) {
                    errorText.setText(R.string.nessun_risultato);
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                showResults(region, fFound);
            });
        }).start();
    }

    private void showResults(String region, List<RaiderIo.SearchResult> found) {
        Ui.addText(this, results, getString(R.string.risultati_ricerca, found.size()),
                15, getColor(R.color.gold), true);
        for (RaiderIo.SearchResult r : found) {
            LinearLayout col = Ui.newCard(this, results);
            Ui.addText(this, col, r.name, 18, Ui.classColor(r.cls), true);
            Ui.addText(this, col, r.realmName + " (" + region.toUpperCase() + ")"
                    + (r.cls.isEmpty() ? "" : " · " + r.cls), 14, 0, false);
            ((View) col.getParent()).setOnClickListener(v ->
                    CharacterActivity.open(this, region, r.realmSlug, r.name, r.cls));
        }
    }
}
