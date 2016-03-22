package com.driving.events;

public class Constants {


	public static boolean isDriveInProgress = false;
	public static boolean isDriveCheckInProgress = false;

    public static final int THREE_SECONDS = 3000;//in milli seconds
    public static final int TWO_SECONDS = 2000;//in milli seconds
    public static final int ONE_SECOND = 1000;//in milli seconds
    public static final int ONE_AND_HALF_SECOND = 1500;//in milli seconds
	public static final int GEOFENCE_RADIUS = 200;//in meters
	public static final int GPS_INTERVAL = 1000;//1 second
	public static final int ACCELEROMETER_INTERVAL = 20000;//50 times in second (50 Hz)
	public static final int GYROSCOPE_INTERVAL = 20000;//50 times in second (50 Hz)
	
	public static final int SPEEDING_EVENT = 1;
	public static final int HARD_TURN_EVENT = 2;
	public static final int HARD_BRAKING_EVENT = 3;
	public static final int HARD_ACCELERATION_EVENT = 4;
	public static final int PHONE_DISTRACTION_EVENT = 5;
	public static final int POTENTIAL_SEVERE_CRASH_EVENT = 6;
    public static final int PARKING_EVENT = 7;

    public static final int ACTIVITY_RECOGNITION_REQUEST_INTERVAL = 60000;
    public static final int POTENTIALSTOP_TIME_THRESHOLD = 300000;
    public static final int POTENTIALSTART_TIME_THRESHOLD = 300000;
    public static final int INITIAL_DELAY = 30000;
    public static final int FIXED_DELAY = 30000;
    public static final int CONFIDENCE_THRESHOLD = 75;
    public static final String GEOFENCE_NAME = "geofence01";
    public static final float BACKINDRIVE_SPEED_THRESHOLD = 3.57f;
    public static final float POTENTIALSTOP_SPEED_THRESHOLD = 6.7f;

    public static final float HARD_TURN_PEAK = 2.5f;
    public static final float PHONE_DISTRACTION_PEAK = 2.5f;
	public static final float ACCELEROMETER_PEAK = 20;
    public static final float FALLING_PEAK = 0.5f;
	public static final float HARD_ACCELERATION_PEAK = 3.57632f;
	public static final float HARD_BREAKING_LOWER_PEAK = -8;
    public static final float HARD_BREAKING_HIGHER_PEAK = -17;
    public static final float HIGH_SPEED_PEAK = 35.76f;
    public static final float PHONE_DISTRACTION_SPEEDLIMT = 8.94f;

}
