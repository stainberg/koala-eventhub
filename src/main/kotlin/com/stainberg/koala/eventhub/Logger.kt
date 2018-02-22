package com.stainberg.koala.eventhub

import android.util.Log

/**
 * Created by Stainberg on 22/02/2018.
 */
internal object Logger {

    fun d(log : String) {
        if(EventBus.DEBUG >= EventBus.LogLevel.LEVEL_D.get()) {
            val e = Exception()
            Log.d(e.stackTrace[1].className, e.stackTrace[1].methodName + " : " + log)
        }
    }

    fun i(log : String) {
        if(EventBus.DEBUG >= EventBus.LogLevel.LEVEL_V.get()) {
            val e = Exception()
            Log.i(e.stackTrace[1].className, e.stackTrace[1].methodName + " : " + log)
        }
    }

    fun v(log : String) {
        if(EventBus.DEBUG >= EventBus.LogLevel.LEVEL_V.get()) {
            val e = Exception()
            Log.v(e.stackTrace[1].className, e.stackTrace[1].methodName + " : " + log)
        }
    }

    fun w(log : String) {
        if(EventBus.DEBUG >= EventBus.LogLevel.LEVEL_W.get()) {
            val e = Exception()
            Log.w(e.stackTrace[1].className, e.stackTrace[1].methodName + " : " + log)
        }
    }

    fun e(log : String) {
        if(EventBus.DEBUG >= EventBus.LogLevel.LEVEL_E.get()) {
            val e = Exception()
            Log.e(e.stackTrace[1].className, e.stackTrace[1].methodName + " : " + log)
        }
    }
}