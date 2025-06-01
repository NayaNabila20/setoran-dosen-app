package com.example.setorandosen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.setorandosen.api.RetrofitClient
import com.example.setorandosen.model.ErrorResponse
import com.example.setorandosen.model.LoginRequest
import com.example.setorandosen.utils.SharedPreferencesHelper
import com.google.gson.Gson
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        sharedPreferencesHelper = SharedPreferencesHelper(this)

        // Jika sudah login, langsung ke Dashboard
        if (sharedPreferencesHelper.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        // Pre‑fill kredensial contoh (hapus di produksi)
        etUsername.setText("fitri.insani@uin-suska.ac.id")
        etPassword.setText("setorantif2025")

        btnLogin.setOnClickListener { login() }
    }

    private fun initViews() {
        etUsername  = findViewById(R.id.etUsername)
        etPassword  = findViewById(R.id.etPassword)
        btnLogin    = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun login() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username dan password harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        // 🔑 Buat objek LoginRequest (grant_type & client_id sudah default)
        val loginRequest = LoginRequest(username = username, password = password)

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Attempting login for user: $username")

                val response = RetrofitClient.authService.login(
                    grantType = loginRequest.grant_type,
                    clientId  = loginRequest.client_id,
                    clientSecret = loginRequest.client_secret,
                    username  = loginRequest.username,
                    password  = loginRequest.password,
                    scope = loginRequest.scope
                )

                Log.d(TAG, "Response code: ${response.code()}")
                Log.d(TAG, "Response body: ${response.body()}")
                Log.d(TAG, "Response error: ${response.errorBody()?.string()}")

                when {
                    response.isSuccessful && response.body() != null -> {
                        val loginResponse = response.body()!!

                        if (loginResponse.access_token.isNotEmpty()) {
                            // Simpan token & status login
                            sharedPreferencesHelper.saveToken(loginResponse.access_token)
                            loginResponse.refresh_token?.let {
                                sharedPreferencesHelper.saveRefreshToken(it)
                            }
                            sharedPreferencesHelper.saveLoginStatus(true)
                            sharedPreferencesHelper.saveUserInfo(username)

                            Toast.makeText(this@LoginActivity, "Login berhasil", Toast.LENGTH_SHORT).show()
                            navigateToDashboard()
                        } else {
                            Toast.makeText(this@LoginActivity, "Token tidak valid", Toast.LENGTH_SHORT).show()
                        }
                    }
                    response.code() == 401 -> {
                        val errorBody = response.errorBody()?.string()
                        val errorResponse = try {
                            Gson().fromJson(errorBody, ErrorResponse::class.java)
                        } catch (e: Exception) { null }

                        val errorMessage = errorResponse?.error_description ?: "Username atau password salah"
                        Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    response.code() == 400 -> {
                        Toast.makeText(this@LoginActivity, "Request tidak valid", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this@LoginActivity, "Login gagal: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                val errorMessage = when {
                    e.message?.contains("Unable to resolve host") == true -> "Tidak dapat terhubung ke server"
                    e.message?.contains("timeout") == true -> "Koneksi timeout"
                    else -> "Error: ${e.message}"
                }
                Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
    }
}