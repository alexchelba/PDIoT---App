package com.specknet.pdiotapp.fileViewer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.filesAdapter.FileRecyclerAdapter
import kotlinx.android.synthetic.main.show_files.*
import java.io.File

class FileViewer : AppCompatActivity() {

    private val activity = this@FileViewer
    private lateinit var fileAdapter: FileRecyclerAdapter
    private lateinit var fileRecycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.show_files)
        initRecyclerView()
        addData()
    }

    /**
     * This method is to initialize the recycler view for files and its adapter
     */
    private fun initRecyclerView() {
        fileRecycler = findViewById(R.id.fileRecyclerViewer)
        fileRecycler.layoutManager = LinearLayoutManager(this@FileViewer)
        fileAdapter = FileRecyclerAdapter()
        fileRecycler.adapter = fileAdapter
    }

    /**
     * This method is to send file data to the recycler view's list
     */
    private fun addData() {
        val email = intent.extras!!.getString("email").toString()
        val hfile = File(getExternalFilesDir(null)!!.absolutePath + "/" + email)
        val data = hfile.listFiles()!!.toList().sortedByDescending {x -> x.lastModified()}
        fileAdapter.submitList(data.toMutableList())
    }
}