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

package org.c99.healthconnect_librelinkup

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.c99.healthconnect_librelinkup.ui.theme.HealthConnectLibreLinkUpTheme

data class LoginUiState(
    var email: String = "",
    var password: String = "",
    var status: String = "",
    var version: String = "Version",
    var isIgnoringBatteryOptimizations: Boolean = false
)

class LoginViewModel: ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun setStatus(status: String) {
        _uiState.value = _uiState.value.copy(status = status)
    }

    fun setVersion(version: String) {
        _uiState.value = _uiState.value.copy(version = version)
    }

    fun setIsIgnoringBatteryOptimizations(isIgnoringBatteryOptimizations: Boolean) {
        _uiState.value = _uiState.value.copy(isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations)
    }
}

class MainActivity : ComponentActivity() {
    private val libreLinkUp: LibreLinkUp = LibreLinkUp(this)
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainView(
                onLoginButtonClicked = { onLoginButtonClicked() },
                onDisableBatteryRestrictionsButtonClicked = { onDisableBatteryRestrictionsButtonClicked() }
            )
        }

        val user = getSharedPreferences("user", MODE_PRIVATE)
        if (user != null && user.contains("email")) {
            viewModel.setEmail(user.getString("email", "").toString())
            viewModel.setStatus("Logged in as " +
                    user.getString("firstName", "") + " " +
                    user.getString("lastName", "")
            )
        }

        val availabilityStatus = HealthConnectClient.getSdkStatus(this)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            Toast.makeText(this, "HealthConnect is unavailable", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage("com.android.vending")
                        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        putExtra("overlay", true)
                        putExtra("callerId", packageName)
                    }
                )
            } catch (e: ActivityNotFoundException) {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    }
                )
            }
            Toast.makeText(this, "HealthConnect not installed or requires an update", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        checkPermissions()

        viewModel.setVersion("Version " + packageManager.getPackageInfo(packageName, 0).versionName)
    }

    private fun checkPermissions() {
        val permissions =
            setOf(
                HealthPermission.getReadPermission(BloodGlucoseRecord::class),
                HealthPermission.getWritePermission(BloodGlucoseRecord::class),
            )

        val requestPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(permissions)) {
                libreLinkUp.schedule()
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val granted =
                    HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    libreLinkUp.schedule()
                } else {
                    requestPermissions.launch(permissions)
                }
            } catch (e: IllegalStateException) {
                //HealthConnect not installed
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        viewModel.setIsIgnoringBatteryOptimizations(powerManager.isIgnoringBatteryOptimizations(
            packageName
        ))
    }

    @SuppressLint("BatteryLife")
    private fun onDisableBatteryRestrictionsButtonClicked() {
        val intent = android.content.Intent()
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.setData(android.net.Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun onLoginButtonClicked() {
        if (viewModel.uiState.value.email.isNotBlank() && viewModel.uiState.value.password.isNotBlank()) {
            CoroutineScope(Dispatchers.Default).launch {
                val loginResult =
                    libreLinkUp.login(viewModel.uiState.value.email, viewModel.uiState.value.password)
                if (loginResult != null && loginResult.status == 0) {
                    libreLinkUp.authTicket = loginResult.data.authTicket
                    CoroutineScope(Dispatchers.Main).launch {
                        libreLinkUp.schedule()
                    }
                    try {
                        val user = getSharedPreferences("user", MODE_PRIVATE).edit()
                        user.putString("firstName", loginResult.data.user.firstName)
                        user.putString("lastName", loginResult.data.user.lastName)
                        user.putString("email", loginResult.data.user.email)
                        user.apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    viewModel.setStatus("Logged in as " + loginResult.data.user.firstName + " " + loginResult.data.user.lastName)
                } else {
                    if (loginResult != null) {
                        if (loginResult.error != null) Log.e(
                            "Libre",
                            "Message: " + loginResult.error.message
                        )
                    }
                    viewModel.setStatus("Login failed. Check your username and password.")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: ((String) -> Unit),
) = composed {
    val autofill = LocalAutofill.current
    val autofillNode = AutofillNode(onFill = onFill, autofillTypes = autofillTypes)
    LocalAutofillTree.current += autofillNode

    this.onGloballyPositioned {
        autofillNode.boundingBox = it.boundsInWindow()
    }.onFocusChanged { focusState ->
        autofill?.run {
            if (focusState.isFocused) {
                requestAutofillForNode(autofillNode)
            } else {
                cancelAutofillForNode(autofillNode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainView(viewModel: LoginViewModel = viewModel(),
             onLoginButtonClicked: () -> Unit = {},
             onDisableBatteryRestrictionsButtonClicked: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    HealthConnectLibreLinkUpTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(stringResource(id = R.string.title_activity_main))
                    }
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp
                    )
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { viewModel.setEmail(it) },
                    label = { Text(stringResource(id = R.string.prompt_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Email),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().autofill(
                        autofillTypes = listOf(AutofillType.EmailAddress),
                        onFill = { viewModel.setEmail(it) },
                    )
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.setPassword(it) },
                    label = { Text(stringResource(id = R.string.prompt_password)) },
                    singleLine = true,
                    visualTransformation =  PasswordVisualTransformation(),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onLoginButtonClicked(); }),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().autofill(
                        autofillTypes = listOf(AutofillType.Password),
                        onFill = { viewModel.setPassword(it) },
                    )
                )
                Button(onClick = { focusManager.clearFocus(); onLoginButtonClicked() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.button_login))
                }
                Text(uiState.status)
                Spacer(Modifier.weight(1f))
                if(!uiState.isIgnoringBatteryOptimizations) {
                    Text(
                        text = stringResource(id = R.string.battery_restricted),
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onDisableBatteryRestrictionsButtonClicked,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(id = R.string.disable_battery_restrictions))
                    }
                }
                Text(uiState.version)
            }
        }
    }
}

@Preview(
    showBackground = true,
    name = "Light"
)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark"
)
@Composable
fun Preview() {
    MainView()
}