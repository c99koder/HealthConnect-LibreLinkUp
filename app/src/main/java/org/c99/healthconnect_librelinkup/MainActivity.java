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

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.BloodGlucoseRecord;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import kotlin.jvm.JvmClassMappingKt;

public class MainActivity extends AppCompatActivity {
    private LibreLinkUp libreLinkUp;

    private EditText emailAddress;
    private EditText password;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        libreLinkUp = new LibreLinkUp(this);
        libreLinkUp.schedule();

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        emailAddress = findViewById(R.id.email);
        password = findViewById(R.id.password);
        status = findViewById(R.id.status);

        try {
            SharedPreferences user = getSharedPreferences("user", MODE_PRIVATE);
            if(user != null && user.contains("email")) {
                emailAddress.setText(user.getString("email", ""));
                status.setText("Logged in as " + user.getString("firstName", "") + " " + user.getString("lastName", ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        findViewById(R.id.login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(emailAddress.getText().length() > 0 && password.getText().length() > 0) {
                    new LoginTask().execute();
                }
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        registerForActivityResult(PermissionController.createRequestPermissionResultContract(), new ActivityResultCallback<Set<String>>() {
            @Override
            public void onActivityResult(Set<String> o) {

            }
        }).launch(new HashSet<String>(Arrays.asList(
                HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(BloodGlucoseRecord.class)),
                HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(BloodGlucoseRecord.class))
                )));
    }

    private class LoginTask extends AsyncTask<Void, Void, LibreLinkUp.LoginResult> {

        @Override
        protected LibreLinkUp.LoginResult doInBackground(Void... voids) {
            try {
                return libreLinkUp.login(emailAddress.getText().toString(), password.getText().toString());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(LibreLinkUp.LoginResult loginResult) {
            super.onPostExecute(loginResult);
            if(loginResult != null && loginResult.status == 0) {
                libreLinkUp.setAuthTicket(loginResult.data.authTicket);
                libreLinkUp.schedule();
                try {
                    SharedPreferences.Editor user = getSharedPreferences("user", MODE_PRIVATE).edit();
                    user.putString("firstName", loginResult.data.user.firstName);
                    user.putString("lastName", loginResult.data.user.lastName);
                    user.putString("email", loginResult.data.user.email);
                    user.commit();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                status.setText("Logged in as " + loginResult.data.user.firstName + " " + loginResult.data.user.lastName);
            } else {
                if(loginResult != null) {
                    if (loginResult.error != null)
                        android.util.Log.e("Libre", "Message: " + loginResult.error.message);
                }
                status.setText("Login failed. Check your username and password.");
            }
        }
    }
}