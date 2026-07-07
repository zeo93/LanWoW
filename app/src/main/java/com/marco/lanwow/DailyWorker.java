package com.marco.lanwow;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Controllo giornaliero in background delle nuove versioni su GitHub (anche ad app chiusa). */
public class DailyWorker extends Worker {

    public DailyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        NotificationHelper.ensureChannels(c);
        UpdateChecker.UpdateInfo update = UpdateChecker.checkSync(c);
        if (update != null) {
            NotificationHelper.notifyUpdate(c, update.version);
        }
        return Result.success();
    }

    /** Pianifica (una sola volta) il controllo giornaliero; sopravvive a riavvii e app chiusa. */
    public static void schedule(Context c) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(DailyWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                "controllo_aggiornamenti", ExistingPeriodicWorkPolicy.KEEP, req);
    }
}
