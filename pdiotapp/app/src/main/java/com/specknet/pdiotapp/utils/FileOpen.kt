package com.specknet.pdiotapp.utils

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException


class FileOpen : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var filepath: String
    private lateinit var filename: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.read_file)
        textView = findViewById(R.id.fileReader)
        filepath = intent.extras!!.getString("FilePath").toString()
        filename = intent.extras!!.getString("FileName").toString()
        openFile()
    }
    private fun openFile() {
        var string: String? = ""
        val stringBuilder = StringBuilder()
        val file = File(filepath)
        val reader = BufferedReader(FileReader(file))
        while (true) {
            try {
                if (reader.readLine().also { string = it } == null) break
            } catch (e: IOException) {
                e.printStackTrace()
            }
            stringBuilder.append(string).append("\n")
        }
        reader.close()
        textView.text = stringBuilder
    }
}
