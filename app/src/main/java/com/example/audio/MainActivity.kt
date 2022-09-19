package com.example.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.audio.ui.theme.AudioTheme
import kotlinx.coroutines.*
import java.io.*
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    lateinit var inputStream1: InputStream
    lateinit var recFile: File
    var recRunning = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recFileName = "testjv.raw"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        setContent {
            AudioTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    hasPermissions()
                    Column() {
                        val resultsViewModel = ResultsViewModel()
                        val text by resultsViewModel.liveText.observeAsState()
                        val theChecker by resultsViewModel.liveBoolean.observeAsState()

                        resultsViewModel.storage(false)
                        Button(onClick = {
                            GlobalScope.launch(Dispatchers.IO) { record() }
                            recRunning = true
                        }) {
                            Text(text = "RECORD")
                        }
                        Button(onClick = {
                            recRunning = false
                            resultsViewModel.storage(true)
                        }) {
                            Text(text = "STOP RECORD")
                        }
                        if (theChecker == true) {
                            inputStream1 =
                                FileInputStream(storageDir.toString() + "/" + recFileName)
                            resultsViewModel.update(storageDir.toString() + "/" + recFileName)
                        }

                        Button(onClick = {
                            if (!recRunning) {
                                GlobalScope.launch {
                                    inputStream1 =
                                        FileInputStream(storageDir.toString() + "/" + recFileName)
                                    val ft = async(Dispatchers.Default) {
                                        playAudio(inputStream1)
                                    }
                                    ft.await()
                                }
                            }
                        }) {
                            Text(text = "PLAY")
                        }
                        Text(text = text ?: "")
                    }
                }
            }
        }
    }

    class ResultsViewModel : ViewModel() {
        private val _text: MutableLiveData<String> = MutableLiveData()
        val liveText: LiveData<String> = _text

        private val _boolen: MutableLiveData<Boolean> = MutableLiveData()
        val liveBoolean: LiveData<Boolean> = _boolen

        fun update(text: String) {
            _text.value = text
        }

        fun storage(value: Boolean) {
            _boolen.value = value
        }

    }

    fun record() {
        val recFileName = "testjv.raw"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        try {
            recFile = File(storageDir.toString() + "/" + recFileName)
        } catch (ex: IOException) {
            Log.e("FYI", "Can't create audio file $ex")
        }
        try {
            val outputStream = FileOutputStream(recFile)
            val bufferedOutputStream = BufferedOutputStream(outputStream)
            val dataOutputStream = DataOutputStream(bufferedOutputStream)
            val minBufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val aFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            try {
                val recorder = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(aFormat)
                    .setBufferSizeInBytes(minBufferSize)
                    .build()
                val audioData = ByteArray(minBufferSize)
                recorder.startRecording()
                while (recRunning) {
                    val numofBytes = recorder.read(audioData, 0, minBufferSize)
                    if (numofBytes > 0) {
                        dataOutputStream.write(audioData)
                    }
                    Log.i("rec", "Going")
                }
                Log.i("rec", "STOP")
                recorder.stop()
            } catch (e: SecurityException) {
                Log.i("Audi", e.localizedMessage)
            }
            dataOutputStream.close()
        } catch (e: IOException) {
            Log.e("FYI", "Recording error $e")
        }

    }

    private fun hasPermissions(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d("DBG", "No audio recorder access")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1);
            return true
        }
        return true
    }


}


suspend fun playAudio(istream: InputStream): String {
    val minBufferSize = AudioTrack.getMinBufferSize(
        44100, AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val aBuilder = AudioTrack.Builder()

    val aAttr: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    val aFormat: AudioFormat = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(44100)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .build()

    val track = aBuilder.setAudioAttributes(aAttr)
        .setAudioFormat(aFormat)
        .setBufferSizeInBytes(minBufferSize)
        .build()
    track!!.setVolume(0.2f)

    val startTime = LocalTime.now().toString()
    track!!.play()
    Log.i("rec", "Sound")
    var i = 0
    val buffer = ByteArray(minBufferSize)
    try {
        i = istream.read(buffer, 0, minBufferSize)
        while (i != -1) {
            track!!.write(buffer, 0, i)
            i = istream.read(buffer, 0, minBufferSize)
        }
    } catch (e: IOException) {
        Log.e("FYI", "Stream read error $e")
    }
    try {
        withContext(Dispatchers.IO) {
            istream.close()
        }
    } catch (e: IOException) {
        Log.e("FYI", "Close error $e")
    }

    track!!.stop()
    track!!.release()
    return startTime
}
