package com.specknet.pdiotapp.recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.CountUpTimer
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*


class RecogniseActivity : AppCompatActivity() {

    private val TAG = "RecognisingActivity"

    private lateinit var startRecognisingButton: Button
    private lateinit var stopRecognisingButton: Button
    private lateinit var useRespeckButton: ToggleButton
    private lateinit var useThingyButton: ToggleButton

    private lateinit var recogImage: ImageView

    private var respeckOn = false
    private var thingyOn = false

    private var useRespeck = false
    private var useThingy = false

    private var mIsRespeckRecognising = false
    private var mIsThingyRecognising = false
    private lateinit var recogniser: TextView

    private val respeckFeatureSize = 6
    private val thingyFeatureSize = 9

    private val windowSize = 30
    private val stepSize = 15
    private val nr_classes = 5
    private lateinit var respeckWindow: Array<FloatArray>
    private lateinit var respeckCNN: Interpreter

    private lateinit var thingyWindow: Array<FloatArray>
    private lateinit var thingyCNN: Interpreter

    private lateinit var str: String
    private var resCounter = 0
    private var thinCounter = 0

    private var time = 0f

    // global broadcast receiver so we can unregister it
    private lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    private lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    private lateinit var looperRespeck: Looper
    private lateinit var looperThingy: Looper

    private lateinit var timer: TextView
    private lateinit var countUpTimer: CountUpTimer

    private var prevTime: Long = 0
    private var initialTime: Long = 0
    private lateinit var user_name : String
    private lateinit var activity_map: MutableMap<String, Long>

    private val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    private val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognise)

        setupViews()
        setupButtons()

        str = "Recognition result : "
        respeckWindow = Array(windowSize) {FloatArray(respeckFeatureSize)} //{respeckWindowRow}

        respeckCNN = Interpreter(loadModelFile("cnn_model_respeck.tflite"))

        thingyWindow = Array(windowSize) { FloatArray(thingyFeatureSize) }

        thingyCNN = Interpreter(loadModelFile("cnn_model_thingie.tflite"))

        recogImage = findViewById(R.id.recogImg)

        activity_map = mutableMapOf()
        user_name = intent.extras!!.getString("username").toString()

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    updateData(liveData)

                    respeckOn = true


                    time += 1

                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        // set up the broadcast receiver
        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = $liveData")

                    updateData(liveData)

                    thingyOn = true
                    time += 1

                }
            }
        }

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

        timer = findViewById(R.id.recog_count_up_timer_text)

        countUpTimer = object: CountUpTimer(1000) {
            override fun onTick(elapsedTime: Long) {
                val date = Date(elapsedTime)
                val formatter = SimpleDateFormat("HH:mm:ss", Locale.UK)
                formatter.timeZone = TimeZone.getTimeZone("GMT")
                val dateFormatted = formatter.format(date)
                val s = "Elapsed time : $dateFormatted"
                timer.text = s
            }
        }

    }

    /**
     * This method is to setup views
     */
    private fun setupViews() {
        recogniser = findViewById(R.id.recogniser)
        recogniser.visibility = View.VISIBLE
    }

    /**
     * This method is to update data for respeck
     * @param liveData
     */
    private fun updateData(liveData: RESpeckLiveData) {
        if (mIsRespeckRecognising) {
            resCounter+=1
            var respeckWindowRow = FloatArray(respeckFeatureSize)
            respeckWindowRow[0]=liveData.accelX
            respeckWindowRow[1]=liveData.accelY
            respeckWindowRow[2]=liveData.accelZ
            respeckWindowRow[3]=liveData.gyro.x
            respeckWindowRow[4]=liveData.gyro.y
            respeckWindowRow[5]=liveData.gyro.z
            if(resCounter<windowSize) {
                respeckWindow[resCounter-1]=respeckWindowRow.copyOf()
            } else
            if(resCounter==windowSize) {
                respeckWindow[windowSize-1] = respeckWindowRow.copyOf()
                str = predict(respeckWindow, thingyWindow)

                for (i in 0..windowSize - stepSize - 1)
                    respeckWindow[i] = respeckWindow[i + stepSize]


                val prevValue = activity_map.getOrElse(str, { 0 })
                activity_map.put(str, prevValue + countUpTimer.get_elapsedTime(prevTime))
                prevTime = countUpTimer._currentTime

                resCounter= resCounter - stepSize
            }
        }

        runOnUiThread {
            recogniser.text = str

            when(str) {
                "Recognition Result : Sitting/Standing" -> recogImage.setImageResource(R.drawable.ic_baseline_man_24)
                "Recognition Result : Walking" -> recogImage.setImageResource(R.drawable.ic_baseline_directions_walk_24)
                "Recognition Result : Running" -> recogImage.setImageResource(R.drawable.ic_baseline_directions_run_24)
                "Recognition Result : Lying down" -> recogImage.setImageResource(R.drawable.ic_iying_down)
                "Recognition Result : Falling" -> recogImage.setImageResource(R.drawable.ic_wet_floor)
                else -> recogImage.setImageResource(R.drawable.ic_ellipsis)
            }
        }
    }

    /**
     * This method is to update data for Thingy
     * @param liveData
     */
    private fun updateData(liveData: ThingyLiveData) {
        if (mIsThingyRecognising) {
            thinCounter+=1
            var thingyWindowRow = FloatArray(thingyFeatureSize)
            thingyWindowRow[0]=liveData.accelX
            thingyWindowRow[1]=liveData.accelY
            thingyWindowRow[2]=liveData.accelZ
            thingyWindowRow[3]=liveData.gyro.x
            thingyWindowRow[4]=liveData.gyro.y
            thingyWindowRow[5]=liveData.gyro.z
            thingyWindowRow[6]=liveData.mag.x
            thingyWindowRow[7]=liveData.mag.y
            thingyWindowRow[8]=liveData.mag.z

            if(thinCounter<windowSize) {
                thingyWindow[thinCounter-1]=thingyWindowRow.copyOf()
            } else if(thinCounter==windowSize) {

                thingyWindow[thinCounter-1] = thingyWindowRow.copyOf()
                if(!useRespeck) {
                    str = predict(respeckWindow, thingyWindow)
                    val prevValue = activity_map.getOrElse(str, { 0 })
                    activity_map.put(str, prevValue + countUpTimer.get_elapsedTime(prevTime))
                    prevTime = countUpTimer._currentTime

                }
                if (thinCounter == windowSize) {
                    for (i in 0..thingyWindow.size - stepSize - 1)
                        thingyWindow[i] = thingyWindow[i + stepSize]
                    thingyWindow[windowSize - stepSize] = thingyWindowRow

                    thinCounter -= stepSize
                }
            }
        }
        if(!useRespeck) {
            runOnUiThread {
                recogniser.text = str
                when(str) {
                    "Recognition Result : Sitting/Standing" -> recogImage.setImageResource(R.drawable.ic_baseline_man_24)
                    "Recognition Result : Walking" -> recogImage.setImageResource(R.drawable.ic_baseline_directions_walk_24)
                    "Recognition Result : Running" -> recogImage.setImageResource(R.drawable.ic_baseline_directions_run_24)
                    "Recognition Result : Lying down" -> recogImage.setImageResource(R.drawable.ic_iying_down)
                    "Recognition Result : Falling" -> recogImage.setImageResource(R.drawable.ic_wet_floor)
                    else -> recogImage.setImageResource(R.drawable.ic_ellipsis)
                }
            }
        }
    }

    /**
     * This method is to enable a view
     * @param view
     */
    private fun enableView(view: View) {
        view.isClickable = true
        view.isEnabled = true
    }

    /**
     * This method is to disable a view
     * @param view
     */
    private fun disableView(view: View) {
        view.isClickable = false
        view.isEnabled = false
    }

    /**
     * This method is to set up button's functionalities
     */
    private fun setupButtons() {
        startRecognisingButton = findViewById(R.id.start_recognising_button)
        stopRecognisingButton = findViewById(R.id.stop_recognising_button)

        useRespeckButton = findViewById(R.id.RespeckRecog)
        useThingyButton = findViewById(R.id.ThingyRecog)

        disableView(stopRecognisingButton)

        useRespeckButton.setOnCheckedChangeListener { _, isChecked ->
            useRespeck = isChecked
        }

        useThingyButton.setOnCheckedChangeListener { _, isChecked ->
            useThingy = isChecked
        }

        startRecognisingButton.setOnClickListener {

            //getInputs()
            if(!useRespeck && !useThingy) {
                Toast.makeText(this, "Please select which sensor(s) you are using.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if(useRespeck && useThingy) {
                if(!respeckOn || !thingyOn) {
                    Toast.makeText(this, "Respeck or Thingy is not on! Check connection.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
            }

            if(useRespeck) {
                if (!respeckOn) {
                    Toast.makeText(this, "Respeck is not on! Check connection.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
            }

            if(useThingy) {
                if (!thingyOn) {
                    Toast.makeText(this, "Thingy is not on! Check connection.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
            }

            Toast.makeText(this, "Starting recognising", Toast.LENGTH_SHORT).show()

            disableView(startRecognisingButton)
            disableView(useRespeckButton)
            disableView(useThingyButton)
            enableView(stopRecognisingButton)

            startRecognising()
        }

        stopRecognisingButton.setOnClickListener {
            Toast.makeText(this, "Stop recognising", Toast.LENGTH_SHORT).show()

            enableView(startRecognisingButton)
            enableView(useRespeckButton)
            enableView(useThingyButton)
            disableView(stopRecognisingButton)

            stopRecognising()
        }

    }

    /**
     * This method is to define what happens when the start button is pressed
     */
    private fun startRecognising() {

        countUpTimer.start()
        initialTime = countUpTimer._currentTime
        prevTime = initialTime
        mIsRespeckRecognising = useRespeck
        mIsThingyRecognising = useThingy
    }

    /**
     * This method is to define what happens when the stop button is pressed
     */
    private fun stopRecognising() {
        val prevValue = activity_map.getOrElse(str, {0})
        activity_map[str] = prevValue + countUpTimer.get_elapsedTime(prevTime)
        countUpTimer.stop()
        val totalTime = countUpTimer._currentTime - initialTime
        countUpTimer.reset()
        prevTime = 0
        val s = "Elapsed time : "
        timer.text = s
        str = "Recognition Result : "
        respeckWindow = Array(windowSize) {FloatArray(respeckFeatureSize)}
        mIsRespeckRecognising = false
        resCounter=0

        thingyWindow = Array(windowSize){ FloatArray(thingyFeatureSize) }
        mIsThingyRecognising = false
        thinCounter=0

        saveRecording(totalTime)

    }

    /**
     * This method is to predict the activity the user is doing based on data
     * @param resWindow
     * @param thinWindow
     */
    private fun predict(resWindow : Array<FloatArray>, thinWindow: Array<FloatArray>): String {
        if (useRespeck && useThingy) {

            val resOutput = FloatArray(nr_classes)
            respeckCNN.run(arrayOf(resWindow), arrayOf(resOutput))
            val thinOutput = FloatArray(nr_classes)
            thingyCNN.run(arrayOf(thinWindow), arrayOf(thinOutput))
            val resMaxIndex = resOutput.indices.maxBy { resOutput[it] } ?: -1
            val thinMaxIndex = thinOutput.indices.maxBy {thinOutput[it] } ?: -1
            val maxIndex = if(resOutput[resMaxIndex]>thinOutput[thinMaxIndex]) resMaxIndex else thinMaxIndex

            Log.d(TAG, "trainRecognising: most probable  " + Arrays.toString(resOutput))
            val resultString = "Recognition Result : " + mapOutputtoLabel(maxIndex)
            return resultString

        } else {
            if (useRespeck) {
                val output = FloatArray(nr_classes)
                Log.d(TAG, "Respeck Window : " + Arrays.deepToString(arrayOf(resWindow)))
                respeckCNN.run(arrayOf(resWindow), arrayOf(output))
                val maxIndex = output.indices.maxBy { output[it] }
                Log.d(TAG, "trainRecognising: most probable  " + Arrays.toString(output))
                if (maxIndex == null) return str
                val resultString = "Recognition Result : " + mapOutputtoLabel(maxIndex)
                return resultString
            } else if (useThingy) {
                val output = FloatArray(nr_classes)
                thingyCNN.run(arrayOf(thinWindow), arrayOf(output))
                val maxIndex = output.indices.maxBy { output[it] } ?: -1
                Log.d(TAG, "trainRecognising: most probable  " + Arrays.toString(output))
                val resultString = "Recognition Result : " + mapOutputtoLabel(maxIndex)
                return resultString
            }
        }
        return "Recognition Result : "
    }

    /**
     * This method is to load a tflite model in Android
     * @param tflite
     * @return MappedByteBuffer
     */
    @Throws(IOException::class)
    private fun loadModelFile(tflite : String): MappedByteBuffer {
        val MODEL_ASSETS_PATH = tflite
        val assetFileDescriptor = assets.openFd(MODEL_ASSETS_PATH)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startoffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset, declaredLength)
    }

    /**
     * This method is to associate label to output
     * @param i
     * @return string
     */
    private fun mapOutputtoLabel(i: Int) : String {
        when (i) {
            0 -> return "Sitting/Standing"
            1 -> return "Walking"
            2 -> return "Running"
            3 -> return "Lying down"
            4 -> return "Falling"
        }
        return ""
    }

    /**
     * This method is to save a recording
     * @param time
     */
    private fun saveRecording(time : Long) {
        val currentTime = System.currentTimeMillis()
        var formattedDate = ""
        try {
            formattedDate = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.UK).format(Date())
            Log.i(TAG, "saveRecording: formattedDate = $formattedDate")
        } catch (e: Exception) {
            Log.i(TAG, "saveRecording: error = $e")
            formattedDate = currentTime.toString()
        }

        val filename = "Recording_${formattedDate}.txt" // TODO format this to human readable

        val email = intent.extras!!.getString("email").toString()
        val hfile = File(getExternalFilesDir(null)!!.absolutePath + "/" + email)
        val file = File(hfile, filename)

        Log.d(TAG, "saveRecording: filename = $filename")

        val dataWriter: BufferedWriter

        // Create file for current day and append header, if it doesn't exist yet
        try {
            val exists = file.exists()
            dataWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true)))

            if (!exists) {
                Log.d(TAG, "saveRecording: filename doesn't exist")
                // the header columns in here
                formattedDate = SimpleDateFormat("dd.MM.yyyy 'at' HH:mm:ss", Locale.UK).format(Date())
                var sensorsUsed = "# Sensors used: "
                if(useRespeck&&useThingy) sensorsUsed += "Respeck, Thingy\n"
                else if(useRespeck) sensorsUsed +="Respeck\n"
                else if(useThingy) sensorsUsed +="Thingy\n"
                dataWriter.write("# Name: $user_name\n")
                dataWriter.write("# Date: $formattedDate\n")
                dataWriter.write(sensorsUsed)
                val formatter = SimpleDateFormat("HH:mm:ss", Locale.UK)
                formatter.timeZone = TimeZone.getTimeZone("GMT")
                dataWriter.write("# Duration: ${formatter.format(Date(time))}.\n")
                dataWriter.newLine()
                dataWriter.flush()
            }
            else {
                Log.d(TAG, "saveRecording: filename exists")
            }
            dataWriter.write("# Recorded activities:\n")
            dataWriter.write(mapToString(activity_map))
            dataWriter.flush()
            dataWriter.close()

            activity_map.clear()
            Toast.makeText(this, "Recording saved!", Toast.LENGTH_SHORT).show()

        }
        catch (e: IOException) {
            Toast.makeText(this, "Error while saving recording!", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "saveRespeckRecording: Error while writing to the respeck file: " + e.message )
        }
    }

    /**
     * This method is to prepare data to be written in a file
     * @param m
     * @return string
     */
    private fun mapToString(m: MutableMap<String, Long>) : String {
        var s = ""
        for(key in m.keys) {
            val k = key.split(":")[1]
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.UK)
            formatter.timeZone = TimeZone.getTimeZone("GMT")
            val v = formatter.format(Date(m[key]!!))
            if(v!="00:00:00")
                s += "##$k : $v\n"
        }
        return s
    }

    /**
     * This method is to destroy sensor connection
     */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }
}
