/*
 * Copyright (c) 2024 Sam Steele
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.c99.healthconnect_librelinkup;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.BloodGlucoseRecord;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.response.InsertRecordsResponse;
import androidx.health.connect.client.units.BloodGlucose;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class SyncWorker extends Worker {
    private final LibreLinkUp libreLinkUp;
    private final HealthConnectClient healthConnectClient;

    private final String GLUCOSE_KEY = "org.c99.healthconnect_librelinkup.glucose";
    private final String TREND_ARROW_KEY = "org.c99.healthconnect_librelinkup.trendArrow";
    private final String COLOR_KEY = "org.c99.healthconnect_librelinkup.color";
    private final String UNITS_KEY = "org.c99.healthconnect_librelinkup.units";
    private final String TIMESTAMP_KEY = "org.c99.healthconnect_librelinkup.timestamp";

    public SyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        libreLinkUp = new LibreLinkUp(context);
        healthConnectClient = HealthConnectClient.getOrCreate(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            LibreLinkUp.ConnectionsResult result = libreLinkUp.connections();
            libreLinkUp.setAuthTicket(result.ticket);
            LibreLinkUp.GlucoseMeasurement gm = result.data.get(0).glucoseMeasurement;
            ZonedDateTime time = ZonedDateTime.parse(gm.FactoryTimestamp + " +0000", DateTimeFormatter.ofPattern("M/d/y h:m:s a Z"));
            BloodGlucoseRecord r = new BloodGlucoseRecord(
                    Instant.from(time),
                    time.getOffset(),
                    BloodGlucose.milligramsPerDeciliter(gm.ValueInMgPerDl),
                    BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                    0,
                    BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                    new Metadata("", new DataOrigin(getApplicationContext().getPackageName()), Instant.from(time), null, 0, null, 0)
            );
            healthConnectClient.insertRecords(Collections.singletonList(r), new Continuation<InsertRecordsResponse>() {
                @NonNull
                @Override
                public CoroutineContext getContext() {
                    return EmptyCoroutineContext.INSTANCE;
                }

                @Override
                public void resumeWith(@NonNull Object o) {

                }
            });

            try {
                getApplicationContext().getPackageManager().getPackageInfo("com.google.android.wearable.app", PackageManager.GET_META_DATA);
                PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/glucose");
                putDataMapReq.getDataMap().putFloat(GLUCOSE_KEY, result.data.get(0).glucoseMeasurement.Value);
                putDataMapReq.getDataMap().putInt(COLOR_KEY, result.data.get(0).glucoseMeasurement.MeasurementColor);
                putDataMapReq.getDataMap().putInt(TREND_ARROW_KEY, result.data.get(0).glucoseMeasurement.TrendArrow);
                putDataMapReq.getDataMap().putInt(UNITS_KEY, result.data.get(0).glucoseMeasurement.GlucoseUnits);
                putDataMapReq.getDataMap().putString(TIMESTAMP_KEY, result.data.get(0).glucoseMeasurement.FactoryTimestamp);
                PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                Tasks.await(Wearable.getDataClient(getApplicationContext()).putDataItem(putDataReq));
            } catch (PackageManager.NameNotFoundException e) {
                //android wear app is not installed
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }

        return Result.success();
    }
}
