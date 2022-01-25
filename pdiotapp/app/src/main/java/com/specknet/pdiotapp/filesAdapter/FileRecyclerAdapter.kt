package com.specknet.pdiotapp.filesAdapter

import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.specknet.pdiotapp.R
import java.io.File
import android.util.Log
import com.specknet.pdiotapp.utils.FileOpen


class FileRecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // list of files in the recycler view
    private lateinit var listFiles : MutableList<File>

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // inflating recycler item view
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item, parent, false)

        return FileViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is FileViewHolder -> {
                Log.i("FileAdd", "folder size: " + listFiles.size + "Position: " + position)
                holder.bindFile(listFiles[position])
            }
        }
    }

    /**
     * This method is to get the number of files in the list
     * @return integer
     */
    override fun getItemCount(): Int {
        return listFiles.size
    }

    /**
     * This method is to receive updated file list
     * @param files
     */
    fun submitList(files : MutableList<File>) {
        listFiles = files
        notifyDataSetChanged()
    }

    /**
     * This method is to remove item at given position
     * @param id
     * @return boolean
     */
    fun removeItem(id : Int) : Boolean {
        listFiles.removeAt(id)
        notifyItemRemoved(id)
        return true
    }
    /**
     * ViewHolder class
     */
    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener, View.OnLongClickListener {
        private val txtTitle: TextView
        private var filepath : File? = null

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
            txtTitle = itemView.findViewById(R.id.fileItem)
        }

        /**
         * This method is to add open functionality to short press on a file
         * @param view
         */
        override fun onClick(v: View) {
            val context = itemView.context
            val showContent = Intent(context, FileOpen::class.java)
            showContent.putExtra("FilePath", filepath!!.absolutePath)
            showContent.putExtra("FileName", txtTitle.text)
            context.startActivity(showContent)
        }

        /**
         * This method is to add delete functionality to long press on a file
         * @param view
         * @return boolean
         */
        override fun onLongClick(v: View):Boolean {

            val context = itemView.context
            val builder = AlertDialog.Builder(context)
            builder.setMessage("Are you sure you want to Delete?")
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    // Delete selected note from database
                    val id = listFiles.indexOf(filepath)
                    filepath!!.delete()
                    removeItem(id)
                }
                .setNegativeButton("No") { dialog, _ ->
                    // Dismiss the dialog
                    dialog.dismiss()
                }
            val alert = builder.create()
            alert.show()
            return true
        }

        /**
         * This method is to attach the file to its link
         * @param file
         */
        fun bindFile(file: File) {
            this.filepath = file
            txtTitle.text = file.name
        }
    }
}