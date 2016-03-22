package com.driving.events;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import java.util.ArrayList;


public class LocationDBHelper extends SQLiteOpenHelper {

    private Context mContext;
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "DrivingDatabase";

	// Table Names
	private static final String TABLE_EVENT_DETAILS = "EventDetails";
	private static final String TABLE_DRIVING_ROUTE = "DrivingRoute";

	// Common column names
	private static final String ID = "id";
	private static final String DRIVE_ID = "driveId";

	// TABLE_EVENT_DETAILS Table - column names
	private static final String EVENT_TIME = "eventTime";
	private static final String EVENT_LATITUDE = "evenLatitude";
	private static final String EVENT_LONGITUDE = "eventLongitude";
	private static final String EVENT_TYPE = "eventType";
	private static final String EVENT_ACCELERATION = "eventAcceleration";
	private static final String EVENT_DRIVING_SPEED = "eventSpeed";
	private static final String EVENT_ISFUSED = "eventIsFused";
	
	// TABLE_DRIVING_ROUTE Table - column names
	private static final String ROUTE_TIME = "routeTime";
	private static final String ROUTE_LATITUDE = "routeLatitude";
	private static final String ROUTE_LONGITUDE = "routeLongitude";

	// TABLE_EVENT_DETAILS table create statement
	private static final String CREATE_TABLE_EVENT_DETAILS = "CREATE TABLE " + TABLE_EVENT_DETAILS
			+ "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + DRIVE_ID + " TEXT," + EVENT_TYPE + " INTEGER,"+
            EVENT_ACCELERATION + " REAL,"+ EVENT_TIME + " INTEGER,"  + EVENT_LATITUDE + " REAL," + EVENT_DRIVING_SPEED
            + " REAL," + EVENT_ISFUSED + " INTEGER," + EVENT_LONGITUDE + " REAL" +")";

	// TABLE_DRIVING_ROUTE table create statement
	private static final String CREATE_TABLE_DRIVING_ROUTE = "CREATE TABLE " + TABLE_DRIVING_ROUTE +
            "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + DRIVE_ID + " TEXT," + ROUTE_TIME +
            " INTEGER," + ROUTE_LATITUDE + " REAL," + ROUTE_LONGITUDE + " REAL" + ")";

	public LocationDBHelper(Context mContext) {
		super(mContext, DATABASE_NAME, null, DATABASE_VERSION);
		this.mContext = mContext;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// creating required tables
		db.execSQL(CREATE_TABLE_DRIVING_ROUTE);
		db.execSQL(CREATE_TABLE_EVENT_DETAILS);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// on upgrade drop older tables
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENT_DETAILS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_DRIVING_ROUTE);
		// create new tables
		this.onCreate(db);
	}

    public String generateDriveID() {

        String driveId = "drive_"+ String.valueOf(System.currentTimeMillis());
        SharedPreferences sp = mContext.getSharedPreferences("DrivingEvents", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("driveId", driveId);
        ed.commit();

        return driveId;
    }

    public String getCurrentDriveID() {

        SharedPreferences mSharedPreferences = mContext.getSharedPreferences("DrivingEvents", Context.MODE_PRIVATE);
        return mSharedPreferences.getString("driveId", "NoDriveExists");
    }

	public void updateDrivingRoute(ArrayList<Location> mLocationDataList) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            String driveId = getCurrentDriveID();
            for (int i = 0; i < mLocationDataList.size(); i++) {

                Location mLocationData = mLocationDataList.get(i);
                ContentValues values = new ContentValues();
                values.put(DRIVE_ID, driveId);
                values.put(ROUTE_TIME, mLocationData.getTime());
                values.put(ROUTE_LATITUDE, mLocationData.getLatitude());
                values.put(ROUTE_LONGITUDE, mLocationData.getLongitude());
                db.insert(TABLE_DRIVING_ROUTE, null, values);
            }
            db.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
	}

	public void updateEventDetails(ArrayList<EventData> mEventDataList) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            String driveId = getCurrentDriveID();
            for (int i = 0; i < mEventDataList.size(); i++) {

                EventData mEventData = mEventDataList.get(i);
                ContentValues values = new ContentValues();
                values.put(DRIVE_ID, driveId);
                values.put(EVENT_TIME, mEventData.eventTime);
                values.put(EVENT_TYPE, mEventData.eventType);
                values.put(EVENT_LATITUDE, mEventData.latitude);
                values.put(EVENT_LONGITUDE, mEventData.longitude);
                if(mEventData.isFused) {
                    values.put(EVENT_ISFUSED, 1);
                } else {
                    values.put(EVENT_ISFUSED, 0);
                }
                switch(mEventData.eventType) {
                    case Constants.SPEEDING_EVENT:
                        values.put(EVENT_ACCELERATION, mEventData.acceleration);
                        values.put(EVENT_DRIVING_SPEED, mEventData.speed);
                        break;
                    case Constants.HARD_BRAKING_EVENT:
                        values.put(EVENT_ACCELERATION, mEventData.acceleration);
                        values.put(EVENT_DRIVING_SPEED, mEventData.speed);
                        break;
                    case Constants.HARD_ACCELERATION_EVENT:
                        values.put(EVENT_ACCELERATION, mEventData.acceleration);
                        values.put(EVENT_DRIVING_SPEED, mEventData.speed);
                        break;
                    case Constants.POTENTIAL_SEVERE_CRASH_EVENT:
                        values.put(EVENT_ACCELERATION, mEventData.acceleration);
                        values.put(EVENT_DRIVING_SPEED, mEventData.speed);
                        break;
                    case Constants.PHONE_DISTRACTION_EVENT:
                        values.put(EVENT_DRIVING_SPEED, mEventData.speed);
                        values.put(EVENT_ACCELERATION, 0);
                        break;
                    case Constants.HARD_TURN_EVENT:
                        values.put(EVENT_DRIVING_SPEED, mEventData.speed);
                        values.put(EVENT_ACCELERATION, 0);
                        break;
                    case Constants.PARKING_EVENT:
                        values.put(EVENT_ACCELERATION, 0);
                        values.put(EVENT_DRIVING_SPEED, 0);
                        break;
                }
                db.insert(TABLE_EVENT_DETAILS, null, values);
            }
            db.close();
        }catch (Exception e) {
            e.printStackTrace();
        }

	}

	public ArrayList<EventData> getEventDetails() {

		ArrayList<EventData> mEventDataList = new ArrayList<EventData>();
		String selectQuery = "SELECT * FROM " + TABLE_EVENT_DETAILS + " WHERE " + DRIVE_ID +" = '"+ getCurrentDriveID()+"'" ;
		try {
			SQLiteDatabase db = this.getReadableDatabase();
			Cursor c = db.rawQuery(selectQuery, null);
			if (c.moveToFirst()) {
				do {
					EventData mEventData = new EventData();
					mEventData.eventTime = c.getInt((c.getColumnIndex(EVENT_TIME)));
					mEventData.eventType = c.getInt((c.getColumnIndex(EVENT_TYPE)));
                    mEventData.latitude = c.getDouble((c.getColumnIndex(EVENT_LATITUDE)));
                    mEventData.longitude = c.getDouble((c.getColumnIndex(EVENT_LONGITUDE)));
                    mEventData.speed = c.getFloat((c.getColumnIndex(EVENT_DRIVING_SPEED)));
                    mEventData.acceleration = c.getFloat((c.getColumnIndex(EVENT_ACCELERATION)));
                    if(c.getInt(c.getColumnIndex(EVENT_ISFUSED))==1) {
                        mEventData.isFused = true;
                    } else {
                        mEventData.isFused = false;
                    }
					mEventDataList.add(mEventData);
				} while (c.moveToNext());
			}
			db.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mEventDataList;
	}

	public ArrayList<Location> getDrivingRoute() {

		ArrayList<Location> mLocationList = new ArrayList<Location>();
		String selectQuery = "SELECT * FROM " + TABLE_DRIVING_ROUTE + " WHERE " + DRIVE_ID +" = '"+ getCurrentDriveID()+"'" ;
		try {
			SQLiteDatabase db = this.getReadableDatabase();
			Cursor c = db.rawQuery(selectQuery, null);
			if (c.moveToFirst()) {
				do {
                    Location mLocation = new Location("fromdatabase");
                    mLocation.setLatitude(c.getDouble(c.getColumnIndex(ROUTE_LATITUDE)));
                    mLocation.setLongitude(c.getDouble(c.getColumnIndex(ROUTE_LONGITUDE)));
                    mLocation.setTime(c.getLong(c.getColumnIndex(ROUTE_TIME)));
                    mLocationList.add(mLocation);
				} while (c.moveToNext());
			}
			db.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mLocationList;
	}

}