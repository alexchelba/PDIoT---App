package com.specknet.pdiotapp.loginHelpers

import android.content.Context
import android.widget.*
import android.util.Patterns

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
     * @return
     */
    fun isInputEditTextFilled(textInputEditText: EditText): Boolean {
        val value = textInputEditText.text.toString().trim()
        if (value.isEmpty()) {
            return false
        }

        return true
    }


    /**
     * method to check InputEditText has valid email .
     *
     * @param textInputEditText
     * @return
     */
    fun isInputEditTextEmail(textInputEditText: EditText): Boolean {
        val value = textInputEditText.text.toString().trim()
        if (value.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            return false
        }
        return true
    }

    /**
     * method to check both InputEditText value matches.
     *
     * @param textInputEditText1
     * @param textInputEditText2
     * @return
     */
    fun isInputEditTextMatches(textInputEditText1: EditText, textInputEditText2: EditText): Boolean {
        val value1 = textInputEditText1.text.toString().trim()
        val value2 = textInputEditText2.text.toString().trim()
        if (!value1.contentEquals(value2)) {
            return false
        }
        return true
    }

}