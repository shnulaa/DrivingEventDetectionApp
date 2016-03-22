package com.driving.events;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.ArrayList;

public class GyroscopeSensor implements SensorEventListener {

    private ArrayList<SensorEvent> mGyroscopeList = new ArrayList<SensorEvent>();
    private static GyroscopeSensor gyroscopeSensor;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    public static GyroscopeSensor getInstance()
    {
        if (gyroscopeSensor == null) {
            gyroscopeSensor = new GyroscopeSensor();
        }
        return gyroscopeSensor;
    }

    public void unregister() {
        mSensorManager.unregisterListener(this);
    }

    public void register(Handler mHandler, Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        //GYROSCOPE_INTERVAL is 20000, i.e. 50 times in second (50 Hz)
        mSensorManager.registerListener(this, mSensor, Constants.GYROSCOPE_INTERVAL, mHandler);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        mGyroscopeList.add(event);
    }

    public ArrayList<SensorEvent> getGyroscopeList()
    {
        return mGyroscopeList;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
