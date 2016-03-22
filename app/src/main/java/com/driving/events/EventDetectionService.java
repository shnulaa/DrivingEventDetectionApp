package com.driving.events;


import android.app.Service;
import android.content.Intent;
import android.hardware.SensorEvent;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EventDetectionService extends Service {

    LocationDBHelper mLocationDBHelper;
    HandlerThread mHandlerThread;
    Handler mHandler;
    ScheduledExecutorService mScheduledExecutorService;
    ScheduledFuture mEventProcessorFutureRef;
    EventProcessorThread mEventProcessorThread;
    long timeOffsetValue;

    @Override
    public void onCreate() {
        super.onCreate();

        mLocationDBHelper = new LocationDBHelper(getApplicationContext());
        mHandlerThread = new HandlerThread("Sensor Thread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mScheduledExecutorService = Executors.newScheduledThreadPool(2);
        timeOffsetValue = System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(intent!=null && intent.getBooleanExtra("isDriveStarted", false)) {
            startEventProcessing();
        } else {
            stopEventProcessing();
        }
        return Service.START_STICKY;
    }

    public void startEventProcessing() {

        mLocationDBHelper.generateDriveID();
        GyroscopeSensor.getInstance().register(mHandler, getApplicationContext());
        GPSSensor.getInstance().register(mHandler, getApplicationContext());
        AccelerometerSensor.getInstance().register(mHandler, getApplicationContext());
        mEventProcessorThread = new EventProcessorThread();
        //INITIAL_DELAY and FIXED_DELAY is 30 seconds
        mEventProcessorFutureRef = mScheduledExecutorService.scheduleWithFixedDelay(mEventProcessorThread, Constants.INITIAL_DELAY, Constants.FIXED_DELAY, TimeUnit.MILLISECONDS);
    }

    public void stopEventProcessing() {

        GyroscopeSensor.getInstance().unregister();
        GPSSensor.getInstance().unregister();
        AccelerometerSensor.getInstance().unregister();
        mEventProcessorFutureRef.cancel(false);
    }



    class EventProcessorThread implements Runnable {

        ArrayList<Location> mGPSRawList = new ArrayList<Location>();
        ArrayList<SensorEvent> mAccelerometerRawList = new ArrayList<SensorEvent>();
        ArrayList<SensorEvent> mGyroscopeRawList = new ArrayList<SensorEvent>();
        ArrayList<SensorData> mAccelerometerPotentialList = new ArrayList<SensorData>();
        ArrayList<SensorData> mCrashPotentialList = new ArrayList<SensorData>();
        ArrayList<SensorData> mGyroscopePotentialList = new ArrayList<SensorData>();
        ArrayList<LocationData> mGPSPotentialList = new ArrayList<LocationData>();
        ArrayList<EventData> mEventList = new ArrayList<EventData>();
        SensorData mAccelerometerData;
        SensorData mGyroscopeData;
        LocationData mLocationData;
        double magnitude;
        boolean isHighSpeedEventPresent;
        EventData mEventData;

        @Override
        public void run() {

            transferData();

            detectPhoneDistraction();

            detectHardTurns();

            eventDetectionUsingGPS();

            eventDetectionUsingAccelerometer();

            fusingGPSAccelerometerEvents();

            processNonFusedGPSEvents();

            detectSevereCrashes();

            saveSensorEventInDB();

            clearData();

        }

        private void transferData()
        {
            //Transferring all the data from the main collection array and cleaning after that,
            // so that adding new values to the arrays doesn't get blocked
            mGPSRawList.addAll(GPSSensor.getInstance().getGPSList());
            GPSSensor.getInstance().getGPSList().clear();
            mAccelerometerRawList.addAll(AccelerometerSensor.getInstance().getAccelerometerList());
            AccelerometerSensor.getInstance().getAccelerometerList().clear();
            mGyroscopeRawList.addAll(GyroscopeSensor.getInstance().getGyroscopeList());
            GyroscopeSensor.getInstance().getGyroscopeList().clear();

        }

        private void clearData()
        {
            //Clear all the data from Arrays
            mGPSRawList.clear();
            mAccelerometerRawList.clear();
            mGyroscopeRawList.clear();
            mAccelerometerPotentialList.clear();
            mCrashPotentialList.clear();
            mGyroscopePotentialList.clear();
            mGPSPotentialList.clear();
            mEventList.clear();
        }

        private void saveSensorEventInDB()
        {
            //Updating the database with Event List and Location Trail
            mLocationDBHelper.updateEventDetails(mEventList);
            mLocationDBHelper.updateDrivingRoute(mGPSRawList);
        }

        private void detectSevereCrashes()
        {
            //Fusing, combining the falling of phone event with hard braking event for detecting severe crashes
            int sizeCrashPotentialList = mCrashPotentialList.size();
            int sizeEventList = mEventList.size();
            for (int i = 0; i < sizeCrashPotentialList; i++)
            {
                if(!mCrashPotentialList.get(i).isDuplicate)
                {
                    for (int j = 0; j < sizeEventList; j++)
                    {
                        if(mEventList.get(j).eventType == Constants.HARD_BRAKING_EVENT &&
                                Math.abs(mEventList.get(j).eventTime - mCrashPotentialList.get(i).time) < Constants.THREE_SECONDS)
                        {
                            mEventData = new EventData();
                            mEventData.eventType = Constants.POTENTIAL_SEVERE_CRASH_EVENT;
                            mEventData.eventTime = mEventList.get(j).eventTime;
                            mEventData.speed = mEventList.get(j).speed;
                            mEventData.latitude = mEventList.get(j).latitude;
                            mEventData.longitude = mEventList.get(j).longitude;
                            mEventData.acceleration = mEventList.get(j).acceleration;
                            mEventList.add(mEventData);
                            break;
                        }
                    }
                }
            }
        }



        private void detectPhoneDistraction()
        {
            //Calculating the magnitude (Length of vector) and
            //checking if its greater than 2.5 threshold (PHONE_DISTRACTION_PEAK)
            int sizeGyroscopeRawList =  mGyroscopeRawList.size();
            for (int i = 0; i < sizeGyroscopeRawList; i++) {
                magnitude = Math.sqrt(Math.pow(mGyroscopeRawList.get(i).values[0], 2) + Math.pow(mGyroscopeRawList.get(i).values[1], 2) + Math.pow(mGyroscopeRawList.get(i).values[2], 2));
                if(magnitude > Constants.PHONE_DISTRACTION_PEAK)
                {
                    mGyroscopeData = new SensorData();
                    mGyroscopeData.mSensorEvent = mGyroscopeRawList.get(i);
                    mGyroscopeData.magnitude = magnitude;
                    mGyroscopeData.time = (mGyroscopeRawList.get(i).timestamp/1000000L) + timeOffsetValue;
                    mGyroscopePotentialList.add(mGyroscopeData);

                }
            }

            //Removing the Gyroscope Potential Overlapping & Duplicate Data, which are close enough
            int sizeGyroscopePotentialtList = mGyroscopePotentialList.size();
            for (int i = 0; i < sizeGyroscopePotentialtList; i++)
            {
                for (int j = i+1; j < sizeGyroscopePotentialtList; j++)
                {
                    if(mGyroscopePotentialList.get(j).time - mGyroscopePotentialList.get(i).time  < Constants.THREE_SECONDS)
                    {
                        mGyroscopePotentialList.get(j).isDuplicate = true;
                    }
                }
            }

            //Capturing Phone Distraction Events location and checking for speed threshold
            boolean correspondingGPSFound;
            for (int i = 0; i < sizeGyroscopePotentialtList; i++)
            {
                if(!mGyroscopePotentialList.get(i).isDuplicate)
                {
                    correspondingGPSFound = false;
                    mEventData = new EventData();
                    for (int k = 0; k < mGPSRawList.size(); k++) {
                        // PHONE_DISTRACTION_SPEEDLIMT is 20 miles per hour
                        if(Math.abs(mGyroscopePotentialList.get(i).time - mGPSRawList.get(k).getTime()) < Constants.ONE_AND_HALF_SECOND
                                && mGPSRawList.get(k).getSpeed() > Constants.PHONE_DISTRACTION_SPEEDLIMT)
                        {
                            correspondingGPSFound = true;
                            mEventData.speed = mGPSRawList.get(k).getSpeed();
                            mEventData.latitude = mGPSRawList.get(k).getLatitude();
                            mEventData.longitude = mGPSRawList.get(k).getLongitude();
                            break;
                        }
                    }
                    if(correspondingGPSFound) {
                        mEventData.eventType = Constants.PHONE_DISTRACTION_EVENT;
                        mEventData.eventTime = mGyroscopePotentialList.get(i).time;
                        mEventList.add(mEventData);

                    }
                }
            }
        }

        private void eventDetectionUsingAccelerometer()
        {
            //Processing the Raw Accelerometer data for Hard Braking, Severe Crash, Fast Acceleration
            int sizeAccelerometerRawList = mAccelerometerRawList.size();
            for (int i = 0; i < sizeAccelerometerRawList; i++) {

                //ACCELEROMETER_PEAK is 20
                magnitude = Math.sqrt(Math.pow(mAccelerometerRawList.get(i).values[0], 2) + Math.pow(mAccelerometerRawList.get(i).values[1], 2) + Math.pow(mAccelerometerRawList.get(i).values[2], 2));
                if(magnitude > Constants.ACCELEROMETER_PEAK)
                {
                    //Potential Candidate for Fast Acceleration or Hard Braking
                    mAccelerometerData = new SensorData();
                    mAccelerometerData.mSensorEvent = mAccelerometerRawList.get(i);
                    mAccelerometerData.magnitude = magnitude;
                    mAccelerometerData.time = (mAccelerometerRawList.get(i).timestamp/1000000L) + timeOffsetValue;
                    mAccelerometerPotentialList.add(mAccelerometerData);
                }
                //FALLING_PEAK is 0.5
                else if(magnitude < Constants.FALLING_PEAK)
                {
                    //Potential Candidate for Severe Crash
                    mAccelerometerData = new SensorData();
                    mAccelerometerData.mSensorEvent = mAccelerometerRawList.get(i);
                    mAccelerometerData.magnitude = magnitude;
                    mAccelerometerData.time = (mAccelerometerRawList.get(i).timestamp/1000000L) + timeOffsetValue;
                    mCrashPotentialList.add(mAccelerometerData);
                }

            }
            //Removing the Accelerometer Potential Overlapping Data, which are close enough (within 1 second interval)
            int sizeAccePotentialList = mAccelerometerPotentialList.size();
            for (int i = 0; i < sizeAccePotentialList; i++)
            {
                for (int j = i+1; j < sizeAccePotentialList; j++)
                {
                    if(mAccelerometerPotentialList.get(j).time - mAccelerometerPotentialList.get(i).time  < Constants.ONE_SECOND)
                    {
                        mAccelerometerPotentialList.get(j).isDuplicate = true;
                    }
                }
            }
            //Removing the Crash (or Falling Phone) Potential Overlapping Data, which are close enough (within 1 second interval)
            int sizeCrashPotentialList = mCrashPotentialList.size();
            for (int i = 0; i < sizeCrashPotentialList; i++)
            {
                for (int j = i+1; j < sizeCrashPotentialList; j++)
                {
                    if(mCrashPotentialList.get(j).time - mCrashPotentialList.get(i).time  < Constants.ONE_SECOND)
                    {
                        mCrashPotentialList.get(j).isDuplicate = true;
                    }
                }
            }
        }

        private void eventDetectionUsingGPS()
        {
            //Processing the GPS data for Hard Braking, Fast Acceleration and High Speed
            int sizeGPSRawList = mGPSRawList.size();
            float acceleration = 0;
            isHighSpeedEventPresent = false;
            for (int i = 0; i < sizeGPSRawList-1; i++)
            {
                //calculating change in speed between two consecutive points
                acceleration =  mGPSRawList.get(i+1).getSpeed() - mGPSRawList.get(i).getSpeed();
                //Checking for HARD ACCELERATION PEAK (above 8 mph or 3.57632 meters per second)
                if(acceleration > Constants.HARD_ACCELERATION_PEAK)
                {
                    mLocationData = new LocationData();
                    mLocationData.eventType = Constants.HARD_ACCELERATION_EVENT;
                    mLocationData.mLocation = mGPSRawList.get(i+1);
                    mLocationData.acceleration = acceleration;
                    mGPSPotentialList.add(mLocationData);

                }
                //Checking for HARD BREAKING, between -8 mph (HARD_BREAKING_LOWER_PEAK) to -17 mph (HARD_BREAKING_HIGHER_PEAK)
                else if((acceleration > Constants.HARD_BREAKING_HIGHER_PEAK) && (acceleration < Constants.HARD_BREAKING_LOWER_PEAK))
                {
                    //Potential Candidate for Hard Brake or Severe Crash
                    mLocationData = new LocationData();
                    mLocationData.eventType = Constants.HARD_BRAKING_EVENT;
                    mLocationData.mLocation = mGPSRawList.get(i+1);
                    mLocationData.acceleration = acceleration;
                    mGPSPotentialList.add(mLocationData);
                }
                //Checking for HIGH SPEEDING PEAK (80 miles per hour or 35.76 meters per second)
                if(mGPSRawList.get(i).getSpeed() > Constants.HIGH_SPEED_PEAK && !isHighSpeedEventPresent)
                {
                    mEventData = new EventData();
                    mEventData.eventType = Constants.SPEEDING_EVENT;
                    mEventData.acceleration = acceleration;
                    mEventData.speed = mGPSRawList.get(i).getSpeed();
                    mEventData.latitude = mGPSRawList.get(i).getLatitude();
                    mEventData.longitude = mGPSRawList.get(i).getLongitude();
                    mEventData.eventTime =  mGPSRawList.get(i).getTime();
                    mEventList.add(mEventData);
                    isHighSpeedEventPresent = true;

                }
            }

            //Removing the GPS Potential Overlapping & Duplicate Data, which are close enough (within 3 seconds interval)
            int sizeGPSPotentialList = mGPSPotentialList.size();
            for (int i = 0; i < sizeGPSPotentialList; i++)
            {
                for (int j = i+1; j < sizeGPSPotentialList; j++)
                {
                    if(mGPSPotentialList.get(j).time - mGPSPotentialList.get(i).time  < Constants.THREE_SECONDS)
                    {
                        mGPSPotentialList.get(j).isDuplicate = true;
                    }
                }
            }
        }

        private void processNonFusedGPSEvents()
        {
            //Adding GPS events to mEventList, which are not fused with accelerometer data
            int sizeGPSPotentialList = mGPSPotentialList.size();
            for (int i = 0; i < sizeGPSPotentialList; i++)
            {
                if(!mGPSPotentialList.get(i).isFused && !mGPSPotentialList.get(i).isDuplicate)
                {
                    mEventData = new EventData();
                    mEventData.eventType = mGPSPotentialList.get(i).eventType;//Either Hard Braking or Acceleration
                    mEventData.acceleration = mGPSPotentialList.get(i).acceleration;
                    mEventData.speed = mGPSPotentialList.get(i).mLocation.getSpeed();
                    mEventData.eventTime = mGPSPotentialList.get(i).mLocation.getTime();
                    mEventData.latitude = mGPSPotentialList.get(i).mLocation.getLatitude();
                    mEventData.longitude = mGPSPotentialList.get(i).mLocation.getLongitude();
                    mEventList.add(mEventData);
                }
            }
        }

        private void fusingGPSAccelerometerEvents()
        {
            //Sensor fusion, Combining the values from GPS and Accelerometer data, to check the overlap of reported values.
            int sizeGPSPotentialList = mGPSPotentialList.size();
            int sizeAccePotentialList = mAccelerometerPotentialList.size();
            if(sizeGPSPotentialList>0 && sizeAccePotentialList>0)
            {
                for (int i = 0; i < sizeGPSPotentialList; i++)
                {
                    if(!mGPSPotentialList.get(i).isDuplicate)
                    {
                        for (int j = 0; j < sizeAccePotentialList; j++)
                        {
                            if(!mAccelerometerPotentialList.get(j).isDuplicate)
                            {
                                long timeDifference = Math.abs(mGPSPotentialList.get(i).time - mAccelerometerPotentialList.get(j).time);
                                //If the reported acceleration value is within 2 seconds of the GPS reported value, then they are fused together
                                if(timeDifference < Constants.TWO_SECONDS)
                                {
                                    mGPSPotentialList.get(i).isFused = true;
                                    mEventData = new EventData();
                                    if(mGPSPotentialList.get(i).eventType == Constants.HARD_ACCELERATION_EVENT)
                                    {
                                        mEventData.eventType = Constants.HARD_ACCELERATION_EVENT;
                                    }
                                    else
                                    {
                                        mEventData.eventType = Constants.HARD_BRAKING_EVENT;
                                    }
                                    mEventData.isFused = true;
                                    mEventData.acceleration = mGPSPotentialList.get(i).acceleration;
                                    mEventData.speed = mGPSPotentialList.get(i).mLocation.getSpeed();
                                    mEventData.eventTime = mGPSPotentialList.get(i).mLocation.getTime();
                                    mEventData.latitude = mGPSPotentialList.get(i).mLocation.getLatitude();
                                    mEventData.longitude = mGPSPotentialList.get(i).mLocation.getLongitude();
                                    mEventList.add(mEventData);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void detectHardTurns()
        {
            float fourthAngle, thirdAngle, secondAngle, firstAngle, averageTurnAngle = 0;
            int sizeGPSList = mGPSRawList.size();
            for (int i = 0; i < sizeGPSList - 4; i++)
            {
                //Calculating fourth angle
                if(mGPSRawList.get(i+4).getBearing() < 90 && mGPSRawList.get(i+3).getBearing() > 270)
                {
                    fourthAngle = (mGPSRawList.get(i+4).getBearing()+360) - mGPSRawList.get(i+3).getBearing();
                }
                else if(mGPSRawList.get(i+4).getBearing() > 270 && mGPSRawList.get(i+3).getBearing() < 90)
                {
                    fourthAngle = (mGPSRawList.get(i+3).getBearing()+360) - mGPSRawList.get(i+4).getBearing();
                }
                else
                {
                    fourthAngle =  Math.abs(mGPSRawList.get(i + 4).getBearing() - mGPSRawList.get(i + 3).getBearing());
                }
                //Calculating third angle
                if(mGPSRawList.get(i+3).getBearing() < 90 && mGPSRawList.get(i+2).getBearing() > 270)
                {
                    thirdAngle = (mGPSRawList.get(i+3).getBearing()+360) - mGPSRawList.get(i+2).getBearing();
                }
                else if(mGPSRawList.get(i+3).getBearing() > 270 && mGPSRawList.get(i+2).getBearing() < 90)
                {
                    thirdAngle = (mGPSRawList.get(i+2).getBearing()+360) - mGPSRawList.get(i+3).getBearing();
                }
                else
                {
                    thirdAngle =  Math.abs(mGPSRawList.get(i + 3).getBearing() - mGPSRawList.get(i + 2).getBearing());
                }
                //Calculating second angle
                if(mGPSRawList.get(i+2).getBearing() < 90 && mGPSRawList.get(i+1).getBearing() > 270)
                {
                    secondAngle = (mGPSRawList.get(i+2).getBearing()+360) - mGPSRawList.get(i+1).getBearing();
                }
                else if(mGPSRawList.get(i+2).getBearing() > 270 && mGPSRawList.get(i+1).getBearing() < 90)
                {
                    secondAngle = (mGPSRawList.get(i+1).getBearing()+360) - mGPSRawList.get(i+2).getBearing();
                }
                else
                {
                    secondAngle =  Math.abs(mGPSRawList.get(i + 2).getBearing() - mGPSRawList.get(i + 1).getBearing());
                }
                //Calculating first angle
                if(mGPSRawList.get(i+1).getBearing() < 90 && mGPSRawList.get(i).getBearing() > 270)
                {
                    firstAngle = (mGPSRawList.get(i+1).getBearing()+360) - mGPSRawList.get(i).getBearing();
                }
                else if(mGPSRawList.get(i+1).getBearing() > 270 && mGPSRawList.get(i).getBearing() < 90)
                {
                    firstAngle = (mGPSRawList.get(i).getBearing()+360) - mGPSRawList.get(i+1).getBearing();
                }
                else
                {
                    firstAngle =  Math.abs(mGPSRawList.get(i + 1).getBearing() - mGPSRawList.get(i).getBearing());
                }
                //Calculating average angle
                averageTurnAngle = (fourthAngle + thirdAngle + secondAngle + firstAngle)/4;
                //HARD_TURN_PEAK is 22.5f
                if(averageTurnAngle>Constants.HARD_TURN_PEAK)
                {
                    //This is considered as hard turn and adding this hard turn to Detected Event List Array
                    mEventData = new EventData();
                    mEventData.eventType = Constants.HARD_TURN_EVENT;
                    mEventData.speed = mGPSRawList.get(i+2).getSpeed();
                    mEventData.latitude = mGPSRawList.get(i+2).getLatitude();
                    mEventData.longitude = mGPSRawList.get(i+2).getLongitude();
                    mEventData.eventTime = mGPSRawList.get(i+2).getTime();
                    mEventList.add(mEventData);
                }
            }
        }



    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
