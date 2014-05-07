/*
 *  Pedometer - Android App
 *  Copyright (C) 2009 Levente Bagi
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.inloc.dr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import com.inloc.dr.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;



/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application.  The {@link StepServiceController}
 * and {@link StepServiceBinding} classes show how to interact with the
 * service.
 *
 * <p>Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 */
public class StepService extends Service implements LocationListener{
	private static final String TAG = "name.bagi.levente.pedometer.StepService";
    private SharedPreferences mSettings;
    private PedometerSettings mPedometerSettings;
    private SharedPreferences mState;
    private SharedPreferences.Editor mStateEditor;
    private Utils mUtils;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private LocationManager mLocationManager;
    private static ConnectivityManager mConnManager;
    private static NetworkInfo netinfo;
    private StepDetector mStepDetector;
    private DataRecorder dtr;
    // private StepBuzzer mStepBuzzer; // used for debugging
    private StepDisplayer mStepDisplayer;
    private TurnDetector mTurnDetector;
    private TurnNotifier mTurnNotifier;
    
    private PowerManager.WakeLock wakeLock;
    private NotificationManager mNM;

    private int lSteps;
    private int mSteps;
    private int mAngle;
    private int mPace;
    private float mDistance;
    private float mSpeed;
    private float mCalories;
    

    private static final int RECORDS_PER_FILE = 10;
    private int records = 0;
    private File mOutputDir;
    private File current_file;
	public FileWriter fw;
	public static ArrayList<File> record_files = new ArrayList<File>();	// Add helper method that searches directory for matching files.
	
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class StepBinder extends Binder {
        StepService getService() {
            return StepService.this;
        }
    }
    
    @Override
    public void onCreate() {
        Log.i(TAG, "[SERVICE] onCreate");
        super.onCreate();
        
        dtr = new DataRecorder();
        
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        showNotification();
        
        // Load settings
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mPedometerSettings = new PedometerSettings(mSettings);
        mState = getSharedPreferences("state", 0);

        mUtils = Utils.getInstance();
        mUtils.setService(this);

        acquireWakeLock();
        
        // Start detecting
        mStepDetector = new StepDetector();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(
        		LocationManager.NETWORK_PROVIDER, 0, 0, this);
        mLocationManager.requestLocationUpdates(
        		LocationManager.GPS_PROVIDER, 0, 0, this);
        
        mTurnDetector = new TurnDetector();
        mTurnNotifier = new TurnNotifier();
        mTurnNotifier.addListener(mAngleListener);
        mTurnDetector.addTurnListener(mTurnNotifier);
        
        registerDetector();

        // Register our receiver for the ACTION_SCREEN_OFF action. This will make our receiver
        // code be called whenever the phone enters standby mode.
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        mStepDisplayer = new StepDisplayer(mPedometerSettings, mUtils);
        mStepDisplayer.setSteps(mSteps = mState.getInt("steps", 0));
        mStepDisplayer.addListener(mStepListener);
        mStepDetector.addStepListener(mStepDisplayer);

        lSteps = mSteps;
        mConnManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        File mExternalRoot = android.os.Environment.getExternalStorageDirectory(); 
		mOutputDir = new File (mExternalRoot.getAbsolutePath() + "/datalogs/");
		// attempt to make output directory
		if (!mOutputDir.exists()) {
            mOutputDir.mkdirs();
        }
        // Used when debugging:
        // mStepBuzzer = new StepBuzzer(this);
        // mStepDetector.addStepListener(mStepBuzzer);

        // Start voice
        reloadSettings();

        // Tell the user we started.
        Toast.makeText(this, getText(R.string.started), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(TAG, "[SERVICE] onStart");
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[SERVICE] onDestroy");

        // Unregister our receiver.
        unregisterReceiver(mReceiver);
        unregisterDetector();
        mLocationManager.removeUpdates(this);
        
        mStateEditor = mState.edit();
        mStateEditor.putInt("steps", mSteps);
        mStateEditor.putInt("angle", mAngle);
        mStateEditor.putInt("pace", mPace);
        mStateEditor.putFloat("distance", mDistance);
        mStateEditor.putFloat("speed", mSpeed);
        mStateEditor.putFloat("calories", mCalories);
        mStateEditor.commit();
        
        mNM.cancel(R.string.app_name);

        wakeLock.release();
        
        super.onDestroy();
        
        // Stop detecting
        mSensorManager.unregisterListener(mStepDetector);
        mSensorManager.unregisterListener(mTurnDetector);
        mConnManager = null;
        // Tell the user we stopped.
        Toast.makeText(this, getText(R.string.stopped), Toast.LENGTH_SHORT).show();
    }

    private void registerDetector() {
        mSensor = mSensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER /*| 
            Sensor.TYPE_MAGNETIC_FIELD | 
            Sensor.TYPE_ORIENTATION*/);
        mSensorManager.registerListener(mStepDetector,
            mSensor,
            SensorManager.SENSOR_DELAY_FASTEST);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(mTurnDetector,
        		mSensor,
        		SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterDetector() {
        mSensorManager.unregisterListener(mStepDetector);
        mSensorManager.unregisterListener(mTurnDetector);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "[SERVICE] onBind");
        return mBinder;
    }

    /**
     * Receives messages from activity.
     */
    private final IBinder mBinder = new StepBinder();

    public interface ICallback {
        public void stepsChanged(int value);
        public void paceChanged(int value);
        public void angleChanged(int value);
        public void distanceChanged(float value);
        public void speedChanged(float value);
        public void caloriesChanged(float value);
    }
    
    private ICallback mCallback;

    public void registerCallback(ICallback cb) {
        mCallback = cb;
        //mStepDisplayer.passValue();
        //mPaceListener.passValue();
    }
    
    private int mDesiredPace;
    private float mDesiredSpeed;
    
    
    public void reloadSettings() {
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        
        if (mStepDetector != null) { 
            mStepDetector.setSensitivity(
                    Float.valueOf(mSettings.getString("sensitivity", "10"))
            );
        }
        
        if (mStepDisplayer    != null) mStepDisplayer.reloadSettings();

    }
    
    public void resetValues() {
        mStepDisplayer.setSteps(0);
    }
    
    /**
     * Forwards pace values from PaceNotifier to the activity. 
     */
    private StepDisplayer.Listener mStepListener = new StepDisplayer.Listener() {
        public void stepsChanged(int value) {
            mSteps = value;
            passValue();
        }
        public void passValue() {
            if (mCallback != null) {
                mCallback.stepsChanged(mSteps);
            }
        }
    };

    
    private TurnNotifier.Listener mAngleListener = new TurnNotifier.Listener() {
    	public void angleChanged(int value){
    		String angle_post = "";
    		try{
    			angle_post += 
    					URLEncoder.encode("time","UTF-8") + "=" + URLEncoder.encode("" + System.currentTimeMillis(),"UTF-8") + "&" +
    					URLEncoder.encode("type","UTF-8") + "=" + URLEncoder.encode("dr","UTF-8") + "&" + 
    					URLEncoder.encode("val1","UTF-8") + "=" + URLEncoder.encode("" + value,"UTF-8") + "&" +
    					URLEncoder.encode("val2","UTF-8") + "=" + URLEncoder.encode("" + (mSteps - lSteps),"UTF-8");
    		}catch(UnsupportedEncodingException e){e.printStackTrace();}
    		logForPost(angle_post);
    		lSteps = mSteps;
    		// Do post to server.
    		mAngle = value;
    		passValue();
    	}
    	public void passValue() {
    		Log.i("TurnDetector","mAngleListener");
    		if(mCallback != null){
    			mCallback.angleChanged(mAngle);
    		}
    	}
    };


    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        CharSequence text = getText(R.string.app_name);
        Notification notification = new Notification(R.drawable.ic_notification, null,
                System.currentTimeMillis());
        notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        Intent pedometerIntent = new Intent();
        pedometerIntent.setComponent(new ComponentName(this, DeadReckoning.class));
        pedometerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                pedometerIntent, 0);
        notification.setLatestEventInfo(this, text,
                getText(R.string.notification_subtitle), contentIntent);

        mNM.notify(R.string.app_name, notification);
    }


    // BroadcastReceiver for handling ACTION_SCREEN_OFF.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check action just to be on the safe side.
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // Unregisters the listener and registers it again.
                StepService.this.unregisterDetector();
                StepService.this.registerDetector();
                if (mPedometerSettings.wakeAggressively()) {
                    wakeLock.release();
                    acquireWakeLock();
                }
            }
        }
    };

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        int wakeFlags;
        if (mPedometerSettings.wakeAggressively()) {
            wakeFlags = PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
        }
        else if (mPedometerSettings.keepScreenOn()) {
            wakeFlags = PowerManager.SCREEN_DIM_WAKE_LOCK;
        }
        else {
            wakeFlags = PowerManager.PARTIAL_WAKE_LOCK;
        }
        wakeLock = pm.newWakeLock(wakeFlags, TAG);
        wakeLock.acquire();
    }

	@Override
	public void onLocationChanged(Location location) {
		String gps_loc = "";
		try{
		gps_loc += 
			URLEncoder.encode("time","UTF-8") + "=" + URLEncoder.encode("" + System.currentTimeMillis(),"UTF-8") + "&" + 
			URLEncoder.encode("type","UTF-8") + "=" + URLEncoder.encode("gps","UTF-8") + "&" + 
			URLEncoder.encode("val1","UTF-8") + "=" + URLEncoder.encode("" + location.getLatitude(),"UTF-8") + "&" + 
			URLEncoder.encode("val2","UTF-8") + "=" + URLEncoder.encode("" + location.getLongitude(), "UTF-8");
		}catch(UnsupportedEncodingException e){e.printStackTrace();}
		// POST to server.
		logForPost(gps_loc);
        Toast.makeText(this, "GPS Location Changed!", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onProviderDisabled(String provider) {}
	@Override
	public void onProviderEnabled(String provider) {
		Toast.makeText(this, "GPS Location Enabled.", Toast.LENGTH_SHORT).show();
	}
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}
	
	public static boolean isOnline(){
		if(mConnManager == null) { return false; }
		netinfo = mConnManager.getActiveNetworkInfo();
		return (netinfo != null && netinfo.isConnected());
	}
	
	public void logForPost(String post_build){
		if(current_file == null || records == 0){
			current_file = new File(mOutputDir.getAbsolutePath(), System.currentTimeMillis() + ".log");
			record_files.add(current_file);
			Log.i(TAG,"Added new record. [" + records + "]");
			if(record_files.size() > 1)
				new DoPost().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
		}
		try{
			fw = new FileWriter(current_file, true);
			fw.write(post_build + "\n");
			fw.flush();
			fw.close();
			records = (records + 1)%RECORDS_PER_FILE;
		}catch(IOException e){e.printStackTrace();}
	}
	
	private class DoPost extends AsyncTask<Void, Void, Boolean>{
		public final String NESL_IP = "http://172.17.5.253:22056";
		public final String msg_storage = "step_service_post.db";
		public File database;
		public DoPost(){
			database = new File(Environment.getExternalStorageDirectory(),msg_storage);
		}
		@Override
		protected Boolean doInBackground(Void ... nothing) {
			if(!StepService.isOnline()){
				return false;
			}
			ArrayList<String> messages = new ArrayList<String>();
			try {
				Scanner s = new Scanner(record_files.get(0));
				while(s.hasNextLine()){
					messages.add(s.nextLine());
				}
				s.close();
			} catch (FileNotFoundException e) {
				try {
					database.createNewFile();
				} catch (IOException e1) {e1.printStackTrace();}
			}
			if(messages.size() == 0){return true;}
			// Acquired all lines to post
			int processed = 0;
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(NESL_IP);
			for(String postVars : messages){
				try {
					httppost.setEntity(new StringEntity(postVars));
					HttpResponse resp = httpclient.execute(httppost);
					Log.i(TAG,"Sending record: " + postVars + "\nResponse: " + resp.getStatusLine().getStatusCode());
					resp.getEntity().consumeContent();
					processed++;
				} catch (UnsupportedEncodingException e) {e.printStackTrace();}
				catch (ClientProtocolException e) {e.printStackTrace();}
				catch (IOException e) {e.printStackTrace();}
			}
			Log.i(TAG, "Processed " + processed + " records.");
			if(processed == RECORDS_PER_FILE){
				record_files.remove(0);
			}
			// For now assume that all lines were correctly processed.
			return true;
		}
		
	}
}

