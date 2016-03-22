package com.driving.events;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.ArrayList;

public class AccelerometerSensor implements SensorEventListener {

    private ArrayList<SensorEvent> mAccelerometerList = new ArrayList<SensorEvent>();
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private static AccelerometerSensor accelerometerSensor;

    public static AccelerometerSensor getInstance() {

        if (accelerometerSensor == null) {
            accelerometerSensor = new AccelerometerSensor();
        }
        return accelerometerSensor;
    }

    public void unregister() {
        mSensorManager.unregisterListener(this);
    }

    public void register(Handler mHandler, Context context) {

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //ACCELEROMETER_INTERVAL is 20000, i.e. 50 times in second (50 Hz)
        mSensorManager.registerListener(this, mSensor, Constants.ACCELEROMETER_INTERVAL, mHandler);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mAccelerometerList.add(event);
    }

    public ArrayList<SensorEvent> getAccelerometerList()
    {
        return mAccelerometerList;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
