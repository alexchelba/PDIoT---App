package com.specknet.pdiotapp.recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max


class RecogniseActivity : AppCompatActivity() {

    private val TAG = "RecognisingActivity"

    lateinit var startRecognisingButton: Button
    lateinit var stopRecognisingButton: Button
    lateinit var useRespeckButton: ToggleButton
    lateinit var useThingyButton: ToggleButton

    var respeckOn = false
    var thingyOn = false

    var useRespeck = false
    var useThingy = false

    private var mIsRespeckRecognising = false
    private var mIsThingyRecognising = false
    lateinit var recogniser: TextView

    val respeckFeatureSize = 6
    val thingyFeatureSize = 6

    val windowSize = 30
    val nr_classes = 5
    lateinit var respeckWindow: Array<FloatArray>
    lateinit var respeckWindowRow: FloatArray
    lateinit var respeckCNN: Interpreter

    lateinit var thingyWindow: Array<FloatArray>
    lateinit var thingyWindowRow: FloatArray
    lateinit var thingyCNN: Interpreter

    lateinit var str: String
    var resCounter = 0
    var thinCounter = 0



    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet


    var time = 0f
    lateinit var allRespeckData: LineData
    lateinit var allThingyData: LineData

    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper



    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognise)

        setupViews()
        setupButtons()
        setupCharts()

        str = "Recognition result : "
        respeckWindowRow = FloatArray(respeckFeatureSize)
        respeckWindow = Array(windowSize) {respeckWindowRow}

        respeckCNN = Interpreter(loadModelFile("model_basic.tflite"))

        thingyWindowRow = FloatArray(thingyFeatureSize)
        thingyWindow = Array(windowSize) {thingyWindowRow}

        thingyCNN = Interpreter(loadModelFile("model_basic.tflite"))

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    //Log.d("Live", "onReceive: liveData = " + liveData)

                    updateData(liveData)

                    respeckOn = true

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("respeck", x, y, z)

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
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    updateData(liveData)

                    thingyOn = true

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("thingy", x, y, z)

                }
            }
        }

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)


    }

    private fun setupViews() {
        recogniser = findViewById(R.id.recogniser)
        recogniser.visibility = View.VISIBLE
    }

    private fun updateData(liveData: RESpeckLiveData) {
        if (mIsRespeckRecognising) {
            resCounter+=1
            respeckWindowRow[0]=liveData.accelX
            respeckWindowRow[1]=liveData.accelY
            respeckWindowRow[2]=liveData.accelZ
            respeckWindowRow[3]=liveData.gyro.x
            respeckWindowRow[4]=liveData.gyro.y
            respeckWindowRow[5]=liveData.gyro.z
            if(resCounter<windowSize) {
                respeckWindow[resCounter-1]=respeckWindowRow
            } else
            if(resCounter==windowSize) {
                for (i in 0..respeckWindow.size - 2)
                    respeckWindow[i] = respeckWindow[i + 1]
                respeckWindow[windowSize-1] = respeckWindowRow
                str = predict(respeckWindow, thingyWindow)
                resCounter-=1
            }
        }

        runOnUiThread {
            recogniser.text = str
        }
    }

    private fun updateData(liveData: ThingyLiveData) {
        if (mIsThingyRecognising) {
            thinCounter+=1
            thingyWindowRow[0]=liveData.accelX
            thingyWindowRow[1]=liveData.accelY
            thingyWindowRow[2]=liveData.accelZ
            thingyWindowRow[3]=liveData.gyro.x
            thingyWindowRow[4]=liveData.gyro.y
            thingyWindowRow[5]=liveData.gyro.z
            if(thinCounter<windowSize) {
                thingyWindow[thinCounter-1]=thingyWindowRow
            } else
                if(thinCounter==windowSize) {
                    for (i in 0..thingyWindow.size - 2)
                        thingyWindow[i] = thingyWindow[i + 1]
                    thingyWindow[windowSize-1] = thingyWindowRow
                    if(!useRespeck) {
                        str = predict(respeckWindow, thingyWindow)
                    }
                    thinCounter-=1
                }
        }
        if(!useRespeck) {
            runOnUiThread {
                recogniser.text = str
            }
        }
    }

    private fun enableView(view: View) {
        view.isClickable = true
        view.isEnabled = true
    }

    private fun disableView(view: View) {
        view.isClickable = false
        view.isEnabled = false
    }

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

    private fun startRecognising() {

        mIsRespeckRecognising = useRespeck
        mIsThingyRecognising = useThingy
    }

    private fun stopRecognising() {

        //countUpTimer.stop()
        //countUpTimer.reset()
        str = "Recognition Result : "
        respeckWindowRow = FloatArray(respeckFeatureSize)
        respeckWindow = Array(windowSize){respeckWindowRow}
        //Log.d(TAG, "stopRecognising " + respeckWindow.size)
        mIsRespeckRecognising = false
        resCounter=0

        thingyWindowRow = FloatArray(thingyFeatureSize)
        thingyWindow = Array(windowSize){thingyWindowRow}
        //Log.d(TAG, "stopRecognising " + respeckWindow.size)
        mIsThingyRecognising = false
        thinCounter=0

    }

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

    @Throws(IOException::class)
    private fun loadModelFile(tflite : String): MappedByteBuffer {
        val MODEL_ASSETS_PATH = tflite
        val assetFileDescriptor = assets.openFd(MODEL_ASSETS_PATH)
        val fileInputStream = FileInputStream(assetFileDescriptor.getFileDescriptor())
        val fileChannel = fileInputStream.getChannel()
        val startoffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.getDeclaredLength()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset, declaredLength)
    }

    private fun mapOutputtoLabel(i: Int) : String {
        when (i) {
            0 -> return "Sitting/Standing"
            1 -> return "Walking"
            2 -> return "Running"
            3 -> return "Lying down"
            4 -> return "Falling"
            /*
            0 -> return "Sitting"
            1 -> return "Climbing stairs"
            2 -> return "Running"
            3 -> return "Walking at normal speed"
            4 -> return "Falling on the back"
            5 -> return "Sitting bent forward"
            6 -> return "Sitting bent backward"
            7 -> return "Lying down on the back"
            8 -> return "Desk work"
            9 -> return "Falling on knees"
            10 -> return "Lying down on stomach"
            11 -> return "Movement"
            12 -> return "Lying down left"
            13 -> return "Lying down right"
            14 -> return "Descending stairs"
            15 -> return "Standing"
            16 -> return "Falling on the left"
            17 -> return "Falling on the right"
             */
        }
        return ""
    }

    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart2)
        thingyChart = findViewById(R.id.thingy_chart2)

        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Thingy

        time = 0f
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()

    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }
}
