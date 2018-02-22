package com.stainberg.koala.eventhub

import java.lang.reflect.ParameterizedType

/**
 * Created by Stainberg on 4/22/16.
 */
abstract class Subscription<in T : IEvent> constructor(val handleType: HandleType = HandleType.main, val mode: Mode = Mode.singleTask) {

    var id: String? = null
    var eventClass: Class<*>? = null
        protected set

    private val invokeObject: Any
        get() = getOuterObject(this)

    abstract fun handleMessage(event: T)

    init {
        val type = this.javaClass.genericSuperclass
        if (type != null && type is ParameterizedType) {
            val p = type.actualTypeArguments
            if(p[0] is Class<*>) {
                eventClass = p[0] as Class<*>
            }
        }
        id = if(mode == Mode.singleTask) {
            invokeObject.javaClass.name + "@" + this.javaClass.name
        } else {
            invokeObject.hashCode().toString() + "@" + this.javaClass.name
        }
    }

    private fun getOuterObject(obj: Any): Any {
        val fields = obj.javaClass.declaredFields
        try {
            for (field in fields) {
                if (field.name.contains("this$")) {
                    field.isAccessible = true
                    val result = field.get(obj)
                    return if (field.name == "this$0") {
                        result
                    } else {
                        getOuterObject(result)
                    }
                }
            }
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }

        return this
    }
}
