package dev.nosuch.apps.coroutinelab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import com.github.michaelbull.result.*
import dev.nosuch.apps.coroutinelab.ui.CoroutineLabTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

val AmbientAppState = ambientOf<AppState> { error("No AppState found.") }

class AppState(
    val name: String
) : CoroutineScope {
    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    val hasError = mutableStateOf(false)
    val errorText = mutableStateOf<String?>(null)
    val callbackValue = mutableStateOf<String?>(null)

    val stateFlowOfInt = MutableStateFlow(0)
    private var flowJob: Job? = null

    fun performCoroutineWithError() {
        launch {
            try {
                mightError()
                hasError.value = false
                errorText.value = null
            } catch (ex: Exception) {
                hasError.value = true
                errorText.value = ex.message
            }
        }
    }

    private suspend fun mightError() {
        delay(500)
        if (Random.nextBoolean())
            throw Exception("Bonk")
    }

    fun startIntFlow() {
        flowJob = launch {
            try {
                while (isActive) {
                    stateFlowOfInt.value = Random.nextInt(100)
                    delay(Random.nextLong(1000))
                }
            } catch (ex: CancellationException) {
                stateFlowOfInt.value = 0
            }
        }
    }

    fun cancelIntFlow() {
        flowJob?.cancel()
    }

    interface Callback {
        fun onValue(value: String)
        fun onError(error: Exception)
    }

    private fun fakeCallbackApi(callback: Callback) {
        launch {
            if (Random.nextBoolean()) {
                callback.onValue("Success")
            } else {
                callback.onError(Exception("Failed"))
            }
        }
    }

    private suspend fun callbackWrapper() = suspendCoroutine<String> { continuation ->
        fakeCallbackApi(object : Callback {
            override fun onValue(value: String) {
                continuation.resume(value)
            }

            override fun onError(error: Exception) {
                continuation.resumeWithException(error)
            }
        })
    }

    fun callWrapper() {
        launch {
            try {
                callbackValue.value = callbackWrapper()
            } catch (ex: Exception) {
                callbackValue.value = ex.message
            }
        }
    }

    private suspend fun sampleKotlinResultApi(): Result<String, Exception> {
        return try {
            Ok(callbackWrapper())
        } catch (ex: Exception) {
            Err(ex)
        }
    }

    fun alternateWrapperCall() {
        launch {
            sampleKotlinResultApi().mapBoth(
                {
                    callbackValue.value = it
                },
                {
                    callbackValue.value = it.message
                }
            )
        }
    }

}

var appState = mutableStateOf(AppState("Android"))

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appState = AppState("Android")

        setContent {
            Providers(
                AmbientAppState provides appState
            ) {
                CoroutineLabTheme {
                    // A surface container using the 'background' color from the theme
                    Surface(color = MaterialTheme.colors.background) {
                        Greeting()
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting() {
    val state = AmbientAppState.current

    val intFromFlow = state.stateFlowOfInt.collectAsState()

    Column(Modifier.padding(16.dp)) {

        Text(
            text = "Hello ${state.name}",
            color = if (state.hasError.value) Color.Red else Color.Black
        )

        state.errorText.value?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                color = if (state.hasError.value) Color.Red else Color.Black
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Int from Flow: ${intFromFlow.value}"
        )

        Spacer(Modifier.height(4.dp))

        Row {
            Button(onClick = {
                state.startIntFlow()
            }) {
                Text("Start Int Flow")
            }

            Spacer(Modifier.width(4.dp))

            Button(onClick = {
                state.cancelIntFlow()
            }) {
                Text("Cancel Int Flow")
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(onClick = {
            state.performCoroutineWithError()
        }) {
            Text("Perform Error")
        }

        Spacer(Modifier.height(4.dp))

        Row {
            Button(onClick = {
                state.callWrapper()
            }) {
                Text("Call Wrapper")
            }

            Button(onClick = {
                state.alternateWrapperCall()
            }) {
                Text("Alternate")
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = state.callbackValue.value ?: "<- Press Either button",
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Providers(
        AmbientAppState provides AppState("JP")
    ) {
        CoroutineLabTheme {
            Greeting()
        }
    }
}