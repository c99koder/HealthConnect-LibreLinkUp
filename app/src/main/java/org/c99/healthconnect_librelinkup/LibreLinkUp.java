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
import android.widget.Toast;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LibreLinkUp {
    private AuthTicket authTicket;
    private User user;
    private Context context;
    private String LIBRELINKUP_URL = "https://api-us.libreview.io";

    private final OkHttpClient client = new OkHttpClient();
    private final Moshi moshi = new Moshi.Builder().build();

    private final String LIBRELINKUP_VERSION = "4.16.0";
    private final String LIBRELINKUP_PRODUCT = "llu.ios";
    private final Headers LIBRELINKUP_HEADERS = new Headers.Builder()
            .add("Content-Type", "application/json")
            .add("version", LIBRELINKUP_VERSION)
            .add("product", LIBRELINKUP_PRODUCT)
            .build();

    private SharedPreferences getEncryptedSharedPreferences() throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                "cache",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public void schedule() {
        WorkManager.getInstance(context).cancelAllWork();

        if(authTicket != null && authTicket.token != null && !authTicket.token.isEmpty()) {
            WorkManager.getInstance(context).enqueue(
                    new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .addTag("sync")
                            .build());
            Toast.makeText(context, "Glucose sync job scheduled", Toast.LENGTH_SHORT).show();
            android.util.Log.i("LibreLinkUp", "Glucose sync job scheduled");
        }
    }

    public LibreLinkUp(Context context) {
        this.context = context;
        try {
            SharedPreferences cache = getEncryptedSharedPreferences();

            LIBRELINKUP_URL = cache.getString("url", "https://api-us.libreview.io");

            authTicket = new AuthTicket();
            authTicket.token = cache.getString("auth_token", null);
            authTicket.duration = cache.getLong("auth_duration", 0);
            authTicket.expires = cache.getLong("auth_expires", 0);

            user = new User();
            user.id = cache.getString("user_id", null);
            user.email = cache.getString("user_email", null);
            user.firstName = cache.getString("user_first_name", null);
            user.lastName = cache.getString("user_last_name", null);
        } catch (Exception e) {
            authTicket = null;
            user = null;
            e.printStackTrace();
        }
    }

    public AuthTicket getAuthTicket() {
        return authTicket;
    }

    public String getUrl() {
        return LIBRELINKUP_URL;
    }

    public void setUrl(String url) {
        LIBRELINKUP_URL = url;

        try {
            SharedPreferences.Editor cache = getEncryptedSharedPreferences().edit();
            cache.putString("url", url);
            cache.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setAuthTicket(AuthTicket ticket) {
        try {
            SharedPreferences.Editor cache = getEncryptedSharedPreferences().edit();
            if(ticket != null) {
                cache.putString("auth_token", ticket.token);
                cache.putLong("auth_duration", ticket.duration);
                cache.putLong("auth_expires", ticket.expires);
            } else {
                cache.remove("auth_token");
                cache.remove("auth_duration");
                cache.remove("auth_expires");
            }
            cache.commit();
            authTicket = ticket;
        } catch (Exception e) {
            authTicket = null;
        }
    }

    public void setUser(User user) {
        try {
            SharedPreferences.Editor cache = getEncryptedSharedPreferences().edit();
            if(user != null) {
                cache.putString("user_id", user.id);
                cache.putString("user_email", user.email);
                cache.putString("user_first_name", user.firstName);
                cache.putString("user_last_name", user.lastName);
            } else {
                cache.remove("user_id");
                cache.remove("user_email");
                cache.remove("user_first_name");
                cache.remove("user_last_name");
            }
            cache.commit();
            this.user = user;
        } catch (Exception e) {
            this.user = null;
        }
    }

    public User getUser() {
        return user;
    }

    public String AccountID() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = digest.digest(user.id.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public LoginResult login(String email, String password) throws IOException {
        JsonAdapter<LoginResult> loginResultJsonAdapter = moshi.adapter(LoginResult.class);

        JSONObject loginRequest = new JSONObject();
        try {
            loginRequest.put("email", email);
            loginRequest.put("password", password);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Request request = new Request.Builder()
                .url(LIBRELINKUP_URL + "/llu/auth/login")
                .headers(LIBRELINKUP_HEADERS)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), loginRequest.toString()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            return loginResultJsonAdapter.fromJson(response.body().string());
        }
    }

    public ConnectionsResult connections() throws IOException {
        JsonAdapter<ConnectionsResult> connectionsResultJsonAdapter = moshi.adapter(ConnectionsResult.class);
        Headers headers = new Headers.Builder().addAll(LIBRELINKUP_HEADERS)
                .add("Authorization", "Bearer " + authTicket.token)
                .add("Account-Id", AccountID())
                .build();

        Request request = new Request.Builder()
                .url(LIBRELINKUP_URL + "/llu/connections")
                .headers(headers)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            return connectionsResultJsonAdapter.fromJson(response.body().string());
        }
    }

    public static class User {
        public String id;
        public String firstName;
        public String lastName;
        public String email;
    }

    public static class AuthTicket {
        public String token;
        public long expires;
        public long duration;
    }

    public static class Sensor {
        public String deviceId;
        public String sn;
    }

    public static class GlucoseMeasurement {
        public String FactoryTimestamp;
        public String Timestamp;
        public int type;
        public int ValueInMgPerDl;
        public int TrendArrow;
        public String TrendMessage;
        public int MeasurementColor;
        public int GlucoseUnits;
        public float Value;
        public boolean isHigh;
        public boolean isLow;
    }

    public static class Connection {
        public String id;
        public String patientId;
        public String country;
        public int status;
        public String firstName;
        public String lastName;
        public Sensor sensor;
        public GlucoseMeasurement glucoseMeasurement;
        public GlucoseMeasurement glucoseItem;
    }

    public static class LibreLinkUpError {
        public String message;
    }

    public static class LibreLinkUpResult {
        public int status;
        public LibreLinkUpError error;
    }

    public static class LoginResult extends LibreLinkUpResult {
        public static class LoginResultData {
            User user;
            AuthTicket authTicket;
        };
        LoginResultData data;
    }

    public static class ConnectionsResult extends LibreLinkUpResult {
        List<Connection> data;
        AuthTicket ticket;
    }
}
