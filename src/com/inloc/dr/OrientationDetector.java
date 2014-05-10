package com.inloc.dr;

import java.util.ArrayList;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

public class OrientationDetector implements SensorEventListener{
	    private final static String TAG = "OrientationDetector";
	    private ArrayList<OrientationListener> listeners = new ArrayList<OrientationListener>();
	    private final static int BUFFER_LEN = 64;
	    // Ring Buffer
	    private static double accBuffer[][] = new double[3][BUFFER_LEN];
	    private static int buffHead = 0;
	    private static int buffTail = 0;
	    private static int buffNumVals = 0;
	    // Current values
	    private static double accCurrentValue[] = {0, 0, 0};
	    private final static int BACKOFF_DELAY = 64; // samples
	    private static int backoffTimer = 0;
	    // filtering variance
	    private final double filter_alpha = 0.5;
	    private double var_last = 0.0;
	    // orientation
	    private int orient = 0;
	    
	    public OrientationDetector() {
	        // TODO: fill this up
	    }
	    
	    // Ring buffer subroutines
	    public void addToBuffer(double[] vals){
	    	accBuffer[0][buffHead] = vals[0];
	    	accBuffer[1][buffHead] = vals[1];
	    	accBuffer[2][buffHead] = vals[2];
	    		    	
	    	if( buffNumVals < BUFFER_LEN ){
	    		buffNumVals++;
	    	}
	    	if( buffNumVals < BUFFER_LEN ){
	    		buffHead = (buffHead + 1)%BUFFER_LEN;
	    	}else{
	    		buffHead = (buffHead + 1)%BUFFER_LEN;
	    		buffTail = (buffTail + 1)%BUFFER_LEN;
	    	}
	    }

	    public void addOrientationListener(OrientationListener sl) {
	        listeners.add(sl);
	    }

	    public void onSensorChanged(SensorEvent event) {
	    	Sensor sensor = event.sensor; 
	    	synchronized (this) {
	    		// if we got accel data, we parse it here.
	    		if( sensor.getType() == Sensor.TYPE_ACCELEROMETER ){
	    			// grab current data and throw it into an array
	    			accCurrentValue[0] = event.values[0];
	    			accCurrentValue[1] = event.values[1];
	    			accCurrentValue[2] = event.values[2];
	    			
	    			addToBuffer(accCurrentValue);
	    			
	    			// only try to calculate orientation periodically 
	    			if( backoffTimer <= 0 ){
	    				// ========== get windowed variance =========
	    				double varX = Statistics.getVariance(accBuffer[0]);
	    				double varY = Statistics.getVariance(accBuffer[1]);
	    				double varZ = Statistics.getVariance(accBuffer[2]);
	    				
	    				double totalVar = varX + varY + varZ;
	    				// filter
	    				double var_new = (filter_alpha)*var_last + (1-filter_alpha)*totalVar;
	    				var_last = var_new;
	    					    				
	    				// ========== classify position =========
	    				if( var_new < 0.20){
	    					orient = 1;
	    				}else if( var_new < 3){
	    					orient = 2;
	    				}else{
	    					orient = 3;
	    				}
	    				
	    				// notify listeners
	    				for (OrientationListener lstnr : listeners) {
                            lstnr.onChange((int)orient);
                        }

	    				// backoff
	    				backoffTimer = BACKOFF_DELAY;
	    			}else{
	    				backoffTimer--;
	    			}
	    		}

	    	}
	    }

	    public void onAccuracyChanged(Sensor sensor, int accuracy) {
	        // TODO Auto-generated method stub
	    }

}
