package nz.scuttlebutt.codec2recorder

import android.content.ContentValues
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import kotlinx.android.synthetic.main.activity_player.*
import nz.scuttlebutt.codec2recorder.tools.Codec2
import nz.scuttlebutt.codec2recorder.tools.Codec2Player
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class PlayerActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val refreshRate = 60L
    private lateinit var runnable : Runnable
    private lateinit var handler : Handler
    private lateinit var mediaPlayer : Codec2Player
    private var c2mode = Codec2.CODEC2_MODE_450
    private var c2data : ByteArray? = null
    private var pcmData : ShortArray? = null
    private var pos2mode : IntArray = intArrayOf(0,1,2,3,4,5,8,10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val mModeSelect = findViewById<Spinner>(R.id.c2modeSelect)
        mModeSelect.onItemSelectedListener = this
        val dataAdapter = ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.c2modes)
        )
        dataAdapter.setDropDownViewResource(R.layout.c2mode_spinner_item)
        mModeSelect.adapter = dataAdapter

        c2data = intent.getByteArrayExtra("c2Data")
        if (c2data != null) {
            c2mode = c2data!![5].toInt()
            c2data = c2data!!.sliceArray(7.. c2data!!.size-1)
            fileNameView.text = intent.getStringExtra("fileName")
            btnSave.visibility = GONE
            mModeSelect.setEnabled(false);
            mModeSelect.setSelection(pos2mode.indexOf(c2mode)) // this will launch the player
        } else {
            pcmData = intent.getShortArrayExtra("pcmData")
            c2data = Codec2.pcm_to_codec2(c2mode, pcmData!!)
            mModeSelect.setSelection(dataAdapter.getPosition("450 (compact)"))
            // launch_player()
        }

        handler = Handler(Looper.getMainLooper())

        btnPlay.setOnClickListener {
            playPausePlayer()
        }

        btnForward.setOnClickListener {
            if (mediaPlayer.isPlaying) freeze()
            mediaPlayer.seekTo(mediaPlayer.currentPosition() + 1000)
            seekBar.progress = mediaPlayer.currentPosition()
        }

        btnBackward.setOnClickListener {
            if (mediaPlayer.isPlaying) freeze()
            mediaPlayer.seekTo(mediaPlayer.currentPosition() - 1000)
            seekBar.progress = mediaPlayer.currentPosition()
        }

        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {
                if (mediaPlayer.isPlaying) freeze()
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                mediaPlayer.seekTo(seekBar.progress)
            }
        })

        btnSave.setOnClickListener {
            var fileName : String? = null
            try {
                val pattern = "yyyyMMdd_HHmmss"
                fileName = "c2rec-${SimpleDateFormat(pattern).format(Date())}.c2"
                var outputStream: OutputStream? = null
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                    // val d = "/sdcard/Download" // Environment.getExternalStorageDirectory().absolutePath + "/Download"
                    // val f = File(d /* getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)*/ , fileName)
                    f.createNewFile()
                    outputStream = FileOutputStream(f, false)
                    fileName = f.absolutePath
                } else {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/vnd.codec2")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = this.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = resolver.openOutputStream(uri!!)
                }
                ByteArrayInputStream(Codec2.makeHeader(c2mode) + c2data!!).use { input ->
                    outputStream.use { output ->
                        input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                    }
                }
            } catch(e: Exception) {
                Log.d("exception", e.toString())
                fileName = null
            }
            val text = if (fileName != null) "Stored as '${fileName}'" else "Saving audio file failed"
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNothingSelected(p0: AdapterView<*>) {}
    override fun onItemSelected(p0: AdapterView<*>, p1: View, p2: Int, p3: Long) {
        if (pcmData != null) {
            if (this::mediaPlayer.isInitialized && mediaPlayer.isPlaying)
                freeze()
            c2mode = pos2mode[p2]
            c2data = Codec2.pcm_to_codec2(c2mode, pcmData!!)
        }
        launch_player()
    }

    private fun launch_player() {
        if (this::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        mediaPlayer = Codec2Player(c2mode, c2data!!)
        mediaPlayer?.prepare()

        val millis =  mediaPlayer.duration()
        seekBar.max = millis
        totalTime.text = String.format("%02d:%02d.%1d",
            millis/60/1000, (millis/1000)%60, (millis/100)%10)
        if (c2data!!.size >= 1000)
            totalBytes.text = String.format("%.1f KB", c2data!!.size/1024.0)
        else
            totalBytes.text = String.format("%d B", c2data!!.size)
    }

    private fun freeze() {
        handler.removeCallbacks(runnable)
        mediaPlayer.pause()
        btnPlay.background = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_circle, theme)
    }

    private fun playPausePlayer(){
        if (mediaPlayer.isPlaying)
            freeze()
        else {
            if (mediaPlayer.currentPosition() >= mediaPlayer.duration()) {
                mediaPlayer.seekTo(0)
                seekBar.progress = 0
            }
            mediaPlayer.start()
            btnPlay.background = ResourcesCompat.getDrawable(resources,
                R.drawable.ic_pause_circle, theme)

            runnable = Runnable {
                var progress = mediaPlayer.currentPosition()
                seekBar.progress = progress
                if (progress >= mediaPlayer.duration()) {
                    btnPlay.background =
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_play_circle, theme)
                } else {
                    val amp = mediaPlayer.getAmplitude()
                    playerView.updateAmps(amp)
                    handler.postDelayed(runnable, refreshRate)
                }
            }
            handler.postDelayed(runnable, refreshRate)
        }
    }

    override fun onBackPressed() {
        if (::runnable.isInitialized)
            handler.removeCallbacks(runnable)
        mediaPlayer.stop()
        mediaPlayer.release()

        btnSave.visibility = VISIBLE
        // findViewById<Spinner>(R.id.c2modeSpinner).isClickable = true
        (findViewById<Spinner>(R.id.c2modeSelect) as Spinner).setEnabled(true)
        fileNameView.text = ""

        super.onBackPressed()
    }
}