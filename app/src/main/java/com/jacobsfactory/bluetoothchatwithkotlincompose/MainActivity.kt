package com.jacobsfactory.bluetoothchatwithkotlincompose

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.jacobsfactory.bluetoothchatwithkotlincompose.ui.theme.BluetoothChatWithKotlinComposeTheme
import com.jacobsfactory.bluetoothchatwithkotlincompose.ui.theme.divider_color

class MainActivity : ComponentActivity() {

    private val viewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val address = result.data?.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""
            if (address.isEmpty())
                return@registerForActivityResult

            viewModel.connect(address)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BluetoothChatWithKotlinComposeTheme {
                FeatureThatRequiresPermissions(viewModel) {
                    Home(
                        modifier = Modifier.statusBarsPadding(),
                        makeDiscoverable = this::makeDiscoverable,
                        showDeviceList = { secure ->
                            activityResultLauncher.launch(
                                Intent(
                                    this,
                                    DeviceListActivity::class.java
                                ).also { it.putExtra(EXTRA_SECURE, secure) }
                            )
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.connectionState == ConnectionState.STATE_NONE) {
            viewModel.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stop()
    }

    @SuppressLint("MissingPermission")
    fun makeDiscoverable() {
        if (viewModel.mBluetoothAdapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivity(discoverableIntent)
        }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Home(
    modifier: Modifier = Modifier,
    showDeviceList: (Boolean) -> Unit,
    makeDiscoverable: () -> Unit,
    viewModel: MainViewModel
) {
    val scaffoldState = rememberScaffoldState()
    var showMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left
                        )
                        Text(
                            text = viewModel.stateString.value,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.caption,
                            textAlign = TextAlign.Left
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showDeviceList(true)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_bluetooth_searching_24),
                            contentDescription = ""
                        )
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            showMenu = false
                            showDeviceList(false)
                        }) {
                            Text("Connect a device -Insecure")
                        }
                        DropdownMenuItem(onClick = {
                            makeDiscoverable()
                            showMenu = false
                        }) {
                            Text("Make discoverable")
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(id = R.string.intro_message)
            )
            LazyList(
                Modifier.weight(1f),
                viewModel.conversations
            )
            var text by remember { mutableStateOf("") }
            val keyboardController = LocalSoftwareKeyboardController.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(60.dp)
                    .padding(5.dp)
            ) {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = text,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.write(text.toByteArray())
                            text = ""
                            keyboardController?.hide()
                        }),
                    onValueChange = { text = it }
                )
                Spacer(modifier = Modifier.width(5.dp))
                Button(
                    modifier = Modifier.fillMaxHeight(),
                    onClick = {
                        if (text.isNotEmpty()) {
                            viewModel.write(text.toByteArray())
                            text = ""
                            keyboardController?.hide()
                        }
                    }) {
                    Text("SEND")
                }
            }
        }
    }
}

@Composable
fun LazyList(modifier: Modifier = Modifier, conversations: List<String>) {
    val scrollState = rememberLazyListState()

    LazyColumn(state = scrollState, modifier = modifier) {
        items(conversations) { conversation ->
            Column(Modifier.fillParentMaxWidth()) {
                Row(
                    modifier = Modifier.height(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation,
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                Divider(color = divider_color)
            }
        }
    }
}
