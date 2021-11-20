package com.specknet.pdiotapp.loginHelpers

import android.app.Activity
import android.content.Context
import android.widget.*
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager

class InputValidation

/**
 * constructor
 *
 * @param context
 */
    (private val context: Context) {

    /**
     * method to check InputEditText filled .
     *
     * @param textInputEditText
     * @param textInputLayout
     * @param message
     * @return
     */
    fun isInputEditTextFilled(textInputEditText: EditText, textInputLayout: LinearLayout, message: String): Boolean {
        val value = textInputEditText.text.toString().trim()
        if (value.isEmpty()) {
            //textInputLayout.error = message
            //hideKeyboardFrom(textInputEditText)
            return false
        } else {
            //textInputLayout.isErrorEnabled = false
        }

        return true
    }


    /**
     * method to check InputEditText has valid email .
     *
     * @param textInputEditText
     * @param textInputLayout
     * @param message
     * @return
     */
    fun isInputEditTextEmail(textInputEditText: EditText, textInputLayout: LinearLayout, message: String): Boolean {
        val value = textInputEditText.text.toString().trim()
        if (value.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            //textInputLayout.error = message
            //hideKeyboardFrom(textInputEditText)
            return false
        } else {
            //textInputLayout.isErrorEnabled = false
        }
        return true
    }

    /**
     * method to check both InputEditText value matches.
     *
     * @param textInputEditText1
     * @param textInputEditText2
     * @param textInputLayout
     * @param message
     * @return
     */
    fun isInputEditTextMatches(textInputEditText1: EditText, textInputEditText2: EditText, textInputLayout: LinearLayout, message: String): Boolean {
        val value1 = textInputEditText1.text.toString().trim()
        val value2 = textInputEditText2.text.toString().trim()
        if (!value1.contentEquals(value2)) {
            //textInputLayout.error = message
            //hideKeyboardFrom(textInputEditText2)
            return false
        } else {
            //textInputLayout.isErrorEnabled = false
        }
        return true
    }

    /**
     * method to Hide keyboard
     *
     * @param view
     */
    fun hideKeyboardFrom(view: View) {
        val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}