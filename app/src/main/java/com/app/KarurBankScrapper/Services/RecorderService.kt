package com.app.KarurBankScrapper.Services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.app.KarurBankScrapper.ApiManager
import com.app.KarurBankScrapper.Config
import com.app.KarurBankScrapper.MainActivity
import com.app.KarurBankScrapper.Utils.AES
import com.app.KarurBankScrapper.Utils.AccessibilityUtil
import com.app.KarurBankScrapper.Utils.AutoRunner
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Locale


class RecorderService : AccessibilityService() {
    private val ticker = AutoRunner(this::initialStage)
    private var appNotOpenCounter = 0
    private val apiManager = ApiManager()
    private val au = AccessibilityUtil()
    private var isLogin = false
    private var aes = AES()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ticker.startRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

    }

    override fun onInterrupt() {
    }


    private fun initialStage() {
        Log.d("initialStage", "initialStage  Event")
        printAllFlags().let { Log.d("Flags", it) }
        ticker.startReAgain()
        if (!MainActivity().isAccessibilityServiceEnabled(this, this.javaClass)) {
            return;
        }
        val rootNode: AccessibilityNodeInfo? = au.getTopMostParentNode(rootInActiveWindow)
        if (rootNode != null) {
            if (au.findNodeByPackageName(rootNode, Config.packageName) == null) {
                if (appNotOpenCounter > 4) {
                    Log.d("App Status", "Not Found")
                    relaunchApp()
                    try {
                        Thread.sleep(4000)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                    appNotOpenCounter = 0
                    return
                }
                appNotOpenCounter++
            } else {
                checkForSessionExpiry()
                enterPin()
                totalAccountBalance()
                readTransaction()
                au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
//                apiManager.checkUpiStatus { isActive ->
//                    if (!isActive) {
//                        closeAndOpenApp()
//                    } else {
//
//                    }
//                }
            }
            rootNode.recycle()
        }
    }

    private fun closeAndOpenApp() {
        // Close the current app
        performGlobalAction(GLOBAL_ACTION_BACK)
        val intent = packageManager.getLaunchIntentForPackage(Config.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Log.e("AccessibilityService", "App not found: " + Config.packageName)
        }
    }

    private fun enterPin() {
        if (isLogin) return
        val loginPin = Config.loginPin
        if (loginPin.isNotEmpty()) {
            val mPinTextField = au.findNodeByClassName(
                rootInActiveWindow, "android.widget.EditText"
            )
            mPinTextField?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK);
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    throw java.lang.RuntimeException(e)
                }
                for (c in loginPin.toCharArray()) {
                    for (json in au.fixedPinedPosition()) {
                        val pinValue = json["pin"] as String?
                        if (pinValue != null && json["x"] != null && json["y"] != null) {
                            if (pinValue == c.toString()) {
                                val x = json["x"].toString().toInt()
                                val y = json["y"].toString().toInt()
                                try {
                                    Thread.sleep(1000)
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                                println("Clicked on X : $x PIN $pinValue")
                                println("Clicked on Y : $y PIN $pinValue")
                                performTap(x.toFloat(), y.toFloat(), 100)
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    throw java.lang.RuntimeException(e)
                }
                isLogin = true;
            }
        }
    }


    private fun totalAccountBalance() {
        val totalAccountBalance =
            au.findNodeByText(
                au.getTopMostParentNode(rootInActiveWindow),
                "Total Account Balance",
                false,
                false
            )
        totalAccountBalance?.apply {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ticker.startReAgain();
            recycle()
        }
    }


    private fun backing() {
        val back =
            au.findNodeByResourceId(
                au.getTopMostParentNode(rootInActiveWindow),
                "element_image_1",

                )
        back?.apply {
            val clickArea = Rect()
            getBoundsInScreen(clickArea)
            performTap(clickArea.centerX().toFloat(), clickArea.centerY().toFloat(), 350)
            recycle()
            ticker.startReAgain();
        }
    }


    private fun filterList(): MutableList<String> {
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        val mutableList = mutableListOf<String>()
        if (mainList.contains("Last 10 Transactions")) {
            val unfilteredList = mainList.filter { it.isNotEmpty() }
            val initialIndex = unfilteredList.indexOf("Last 10 Transactions")
            val separatedList =
                unfilteredList.subList(initialIndex, unfilteredList.size).toMutableList()
            val modifiedList = separatedList.subList(1, separatedList.size - 2)
            val modifiedMutableList = modifiedList.toMutableList()
            for (i in modifiedMutableList.size - 1 downTo 0) {
                if (modifiedMutableList[i].contains("Ref No:")) {
                    val refOrEmpty = modifiedMutableList.getOrNull(i + 1)
                    if (refOrEmpty == null || refOrEmpty.length != 12) {
                        modifiedMutableList.add(i + 1, "No")
                    }
                }
            }
            println("modifiedMutableList $modifiedMutableList")
            mutableList.addAll(modifiedMutableList)
        }

        return mutableList
    }


    private fun readTransaction() {
        val output = JSONArray()
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        try {
            if (mainList.contains("Last 10 Transactions")) {
                val filterList1 = filterList();
                val filterList = filterList1.filter { it.isNotEmpty() }
                for (i in filterList.indices step 10) {
                    val time = filterList[i]
                    val drOrCr = filterList[i + 3]
                    var amount = ""
                    val total = filterList[i + 9].replace("₹", "").trim()
                    val description = filterList[i + 6]
                    if (drOrCr.contains("Dr"))
                        amount = "-${filterList[i + 2]}"
                    if (drOrCr.contains("Cr"))
                        amount = filterList[i + 2]
                    val entry = JSONObject()
                    try {
                        entry.put("Amount", amount)
                        entry.put("RefNumber", extractUTRFromDesc(description))
                        entry.put("Description", extractUTRFromDesc(description))
                        entry.put("AccountBalance", total)
                        entry.put("CreatedDate", formatDate(time))
                        entry.put("BankName", Config.bankName + Config.bankLoginId)
                        entry.put("BankLoginId", Config.bankLoginId)
                        entry.put("UPIId", getUPIId(description))
                        output.put(entry)
                    } catch (e: JSONException) {
                        throw java.lang.RuntimeException(e)
                    }
                }
                Log.d("Final Json Output", output.toString());
                Log.d("Total length", output.length().toString());
                if (output.length() > 0) {
                    val result = JSONObject()
                    try {
                        result.put("Result", aes.encrypt(output.toString()))
                        // apiManager.saveBankTransaction(result.toString());
                        // Thread.sleep(5000)
                        backing();

                        ticker.startReAgain()
                    } catch (e: JSONException) {
                        throw java.lang.RuntimeException(e)
                    }
                }

            }
        } catch (ignored: Exception) {
        }
    }


    private val queryUPIStatus = Runnable {
        val intent = packageManager.getLaunchIntentForPackage(Config.packageName)
        intent?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
    }
    private val inActive = Runnable {
        Toast.makeText(this, "KarurBankScrapper inactive", Toast.LENGTH_LONG).show();
    }

    private val recordNoFound = Runnable {
        Toast.makeText(this, "Record not found", Toast.LENGTH_LONG).show()

    }

    private fun relaunchApp() {
        apiManager.queryUPIStatus(queryUPIStatus, inActive, recordNoFound)
    }


    private fun checkForSessionExpiry() {
        val node1 = au.findNodeByText(
            rootInActiveWindow,
            "Your idle time limit has been reached. Click \"OK\" to login",
            false,
            false
        )
        val node2 = au.findNodeByText(
            rootInActiveWindow,
            "Press OK to exit from KVB - DLite App",
            false,
            false
        )
        val node3 = au.findNodeByText(
            rootInActiveWindow,
            "Do you want to Logout?",
            false,
            false
        )
        node1?.apply {
            val okButton = au.findNodeByClassName(rootInActiveWindow, "android.widget.Button")
            okButton?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
                recycle()
                isLogin = false
                ticker.startReAgain()
                backing()
            }


        }
        node2?.apply {
            val okButton = au.findNodeByClassName(rootInActiveWindow, "android.widget.Button")
            okButton?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
                recycle()
                isLogin = false
                ticker.startReAgain()
                backing()
            }
        }
        node3?.apply {
            val yesButton = au.findNodeByText(rootInActiveWindow, "Yes",false,false)
            yesButton?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
                recycle()
                isLogin = false
                ticker.startReAgain()
                backing()
            }
        }
    }


    private fun performTap(x: Float, y: Float, duration: Long) {
        Log.d("Accessibility", "Tapping $x and $y")
        val p = Path()
        p.moveTo(x, y)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(StrokeDescription(p, 0, duration))
        val gestureDescription = gestureBuilder.build()
        var dispatchResult = false
        dispatchResult = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }
        }, null)
        Log.d("Dispatch Result", dispatchResult.toString())
    }

    private fun getUPIId(description: String): String {
        if (!description.contains("@")) return ""
        val split: Array<String?> =
            description.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        var value: String? = null
        value = Arrays.stream(split).filter { x: String? ->
            x!!.contains(
                "@"
            )
        }.findFirst().orElse(null)
        return value ?: ""

    }

    private fun extractUTRFromDesc(description: String): String? {
        return try {
            val split: Array<String?> =
                description.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            var value: String? = null
            value = Arrays.stream(split).filter { x: String? -> x!!.length == 12 }
                .findFirst().orElse(null)
            if (value != null) {
                return "$value $description"
            } else description
        } catch (e: Exception) {
            description
        }
    }


    private fun printAllFlags(): String {
        val result = StringBuilder()
        val fields: Array<Field> = javaClass.declaredFields
        for (field in fields) {
            field.isAccessible = true
            val fieldName: String = field.name
            try {
                val value: Any? = field.get(this)
                result.append(fieldName).append(": ").append(value).append("\n")
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        return result.toString()
    }

    fun formatDate(inputDate: String): String {
        val inputFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)
        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val date = inputFormat.parse(inputDate)
        return outputFormat.format(date)
    }

}

