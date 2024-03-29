package com.app.KarurBankScrapper

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import okhttp3.RequestBody.Companion.toRequestBody

class ApiManager {

    companion object {
        private const val BASE_URL = "https://91.playludo.app/api/CommonAPI"
        private val client = OkHttpClient()
    }

    fun queryUPIStatus(active: Runnable, inActive: Runnable, recordNotFound: Runnable) {
        println("loginId ${Config.loginId}")
        val apiURL = "${BASE_URL}/GetUpiStatus?upiId=${Config.loginId}"
        val request = Request.Builder().url(apiURL).build()
        println("apiURL $apiURL")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiCallTask", "API Request Failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        Log.d("ApiCallTask", "API responseData: $responseData")
                        val output = Gson().fromJson(responseData, JsonObject::class.java)
                        Log.d("ApiCallTask", "API Response: $output")
                        Log.d("ERRRROR",output.get("ErrorMessage").toString());
                        if (output.has("Result") && output.get("Result").asInt == 1) {
                            Log.d("UPI Status", "Active")
                            active.run()
                        } else if (output.get("ErrorMessage").toString().contains("Record not found")) {
                            recordNotFound.run()
                        } else {
                            Log.d("UPI Status", "Inactive")
                            inActive.run()
                        }
                    } else {
                        Log.e("ApiCallTask", "API Response Error: ${response.body?.string()}")
                    }
                } catch (ignored: Exception) {
                }
            }
        })
    }


    fun saveBankTransaction(body: String) {
        val apiURL = "${BASE_URL}/SaveMobilebankTransaction"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(apiURL)
            .post(requestBody)
            .build()

        println("apiURL $apiURL")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiCallTask", "API Request Failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val output = Gson().fromJson(responseData, JsonObject::class.java)
                        Log.d("ApiCallTask", "API Response: $output")
                        if (output.get("ErrorMessage").asString.contains("Already exists")) {
                            updateDateForKarurBankScrapper();
                        } else {
                            updateDateForKarurBankScrapper();
                        }
                    } else {
                        Log.e("ApiCallTask", "API Response Error: ${response.body?.string()}")
                    }
                } catch (ignored: Exception) {
                }
            }
        })
    }

    fun updateDateForKarurBankScrapper() {
        val apiURL = "${BASE_URL}/UpdateDateBasedOnUpi?upiId=${Config.loginId}"

        println("apiURL $apiURL")
        val request = Request.Builder().url(apiURL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiCallTask", "API Response: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val output = Gson().fromJson(responseData, JsonObject::class.java)
                        Log.d("ApiCallTask", "API Response: $output")
                    } else {
                        Log.e("ApiCallTask", "API Response: ${response.body?.string()}")
                    }
                } catch (ignored: Exception) {
                }
            }
        })
    }

    fun checkUpiStatus(callback: (Boolean) -> Unit) {
        println("loginId ${Config.loginId}")
        val apiURL = "${BASE_URL}/GetUpiStatus?upiId=${Config.loginId}"
        val request = Request.Builder().url(apiURL).build()
        println("apiURL $apiURL")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiCallTask", "API Request Failed: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        Log.d("ApiCallTask", "API responseData: $responseData")
                        val output = Gson().fromJson(responseData, JsonObject::class.java)
                        Log.d("ApiCallTask", "API Response: $output")

                        if (output.has("Result") && output.get("Result").asInt == 1) {
                            Log.d("UPI Status", "Active")
                            callback(true)
                        } else {
                            Log.d("UPI Status", "Inactive")
                            callback(false)
                        }
                    } else {
                        Log.e("ApiCallTask", "API Response Error: ${response.body?.string()}")
                        callback(false)
                    }
                } catch (ignored: Exception) {
                    callback(false)
                }
            }
        })
    }

}
