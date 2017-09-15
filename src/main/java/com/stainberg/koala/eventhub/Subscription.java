package com.stainberg.koala.eventhub;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by Stainberg on 4/22/16.
 */
public abstract class Subscription<T extends IEvent> {

    String id;
    HandleType handleType;
    Mode mode;
    protected Class<T> cls;

    public abstract void handleMessage(T event);

    public Subscription(HandleType t, Mode m) {
        handleType = t;
        mode = m;
        Type type = this.getClass().getGenericSuperclass();
        if (type != null && type instanceof ParameterizedType) {
            Type[] p = ((ParameterizedType) type).getActualTypeArguments();
            cls = (Class) p[0];
        }
    }

    public Subscription(HandleType handleType) {
        this(handleType, Mode.singleTask);
    }

    public Subscription(Mode mode) {
        this(HandleType.main, mode);
    }

    public Subscription() {
        this(HandleType.main, Mode.singleTask);
    }

    public Class<T> getEventClass() {
        return cls;
    }

    public Object getInvokeObject() {
        return getOuterObject(this);
    }

    private Object getOuterObject(Object object) {
        Field[] fields = object.getClass().getDeclaredFields();
        try {
            for (Field field : fields) {
                if(field.getName().contains("this$")) {
                    field.setAccessible(true);
                    Object result = field.get(object);
                    if(field.getName().equals("this$0")) {
                        return result;
                    } else {
                        return getOuterObject(result);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return this;
    }
}
