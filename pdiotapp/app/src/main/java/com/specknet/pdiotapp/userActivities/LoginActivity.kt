package com.specknet.pdiotapp.userActivities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.specknet.pdiotapp.MainActivity

import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.loginHelpers.InputValidation
import com.specknet.pdiotapp.userDatabase.DatabaseHelper


class LoginActivity : AppCompatActivity(), View.OnClickListener {

    private val activity = this@LoginActivity

    private lateinit var textInputLayoutEmail: LinearLayout
    private lateinit var textInputLayoutPassword: LinearLayout

    private lateinit var textInputEditTextEmail: EditText
    private lateinit var textInputEditTextPassword: EditText

    private lateinit var ButtonLogin: Button

    private lateinit var textViewLinkRegister: Button
    private lateinit var textViewLinkForgot: Button

    private lateinit var inputValidation: InputValidation
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.login_page)

        // hiding the action bar
        supportActionBar!!.hide()

        // initializing the views
        initViews()

        // initializing the listeners
        initListeners()

        // initializing the objects
        initObjects()
    }

    /**
     * This method is to initialize views
     */
    private fun initViews() {

        textInputLayoutEmail = findViewById<LinearLayout>(R.id.L_Email)
        textInputLayoutPassword = findViewById<LinearLayout>(R.id.L_Password)

        textInputEditTextEmail = findViewById<EditText>(R.id.L_EmailInput)
        textInputEditTextPassword = findViewById<EditText>(R.id.L_PasswordInput)

        ButtonLogin = findViewById<Button>(R.id.LoginSubmit)

        textViewLinkRegister = findViewById<Button>(R.id.SignupButton)
        textViewLinkForgot = findViewById<Button>(R.id.ForgotButton)

    }

    /**
     * This method is to initialize listeners
     */
    private fun initListeners() {

        ButtonLogin.setOnClickListener(this)
        textViewLinkRegister.setOnClickListener(this)
        textViewLinkForgot.setOnClickListener(this)
    }

    /**
     * This method is to initialize objects to be used
     */
    private fun initObjects() {

        databaseHelper = DatabaseHelper(activity)
        inputValidation = InputValidation(activity)

    }

    /**
     * This implemented method is to listen the click on view
     *
     * @param v
     */
    override fun onClick(v: View) {
        when (v.id) {
            R.id.LoginSubmit -> verifyFromSQLite()
            R.id.SignupButton -> {
                // Navigate to RegisterActivity
                val intentRegister = Intent(applicationContext, RegisterActivity::class.java)
                startActivity(intentRegister)
            }
            R.id.ForgotButton -> {
                // Navigate to RegisterActivity
                val intentRegister = Intent(applicationContext, ForgetActivity::class.java)
                startActivity(intentRegister)
            }
        }
    }

    /**
     * This method is to validate the input text fields and verify login credentials from SQLite
     */
    private fun verifyFromSQLite() {

        if (!inputValidation.isInputEditTextFilled(textInputEditTextEmail)) {
            hideKeyboardFrom(textInputLayoutEmail)
            Toast.makeText(this, "Wrong e-mail or password", Toast.LENGTH_SHORT).show()
            return
        }
        if (!inputValidation.isInputEditTextEmail(textInputEditTextEmail)) {
            hideKeyboardFrom(textInputLayoutEmail)
            Toast.makeText(this, "Wrong e-mail or password", Toast.LENGTH_SHORT).show()
            return
        }
        if (!inputValidation.isInputEditTextFilled(textInputEditTextPassword)) {
            hideKeyboardFrom(textInputLayoutEmail)
            Toast.makeText(this, "Wrong e-mail or password", Toast.LENGTH_SHORT).show()
            return
        }

        if (databaseHelper.checkUser(textInputEditTextEmail.text.toString().trim { it <= ' ' },
                                    textInputEditTextPassword.text.toString().trim { it <= ' ' })) {

            val user = databaseHelper.getUser(textInputEditTextEmail.text.toString().trim { it <= ' ' })
            val name = user.username
            val email = user.email

            /*
            val file = File(getExternalFilesDir(null)!!.absolutePath)
            val userFile = File(file, email)
            if(!userFile.exists()) userFile.mkdirs()
            */

            //databaseHelper.updateUserSesh(user, true)
            val accountsIntent = Intent(activity, MainActivity::class.java)
            accountsIntent.putExtra("username", name)
            accountsIntent.putExtra("email", email)
            emptyInputEditText()
            startActivity(accountsIntent)


        } else {

            // Snack Bar to show success message that record is wrong
            //Snackbar.make(nestedScrollView!!, getString(R.string.error_valid_email_password), Snackbar.LENGTH_LONG).show()
            Toast.makeText(this, "Wrong e-mail or password", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * This method is to empty all input edit text
     */
    private fun emptyInputEditText() {
        textInputEditTextEmail.text = null
        textInputEditTextPassword.text = null
    }

    /**
     * This method is to hide keyboard away from user
     * @param view
     */
    private fun hideKeyboardFrom(view: View) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}