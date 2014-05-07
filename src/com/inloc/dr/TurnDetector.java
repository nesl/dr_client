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

import java.util.ArrayList;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Detects steps and notifies all listeners (that implement StepListener).
 * @author Levente Bagi
 * @todo REFACTOR: SensorListener is deprecated
 */
public class TurnDetector implements SensorEventListener
{
    private final static String TAG = "TurnDetector";
    private ArrayList<TurnListener> turnListeners = new ArrayList<TurnListener>();
    private final static int BUFFER_LEN = 64;
    // Ring Buffer
    private static float gyroBuffer[] = new float[BUFFER_LEN];
    private static int buffHead = 0;
    private static int buffTail = 0;
    private static int buffNumVals = 0;
    private static float gyroCurrentValue[] = {0, 0, 0};
    private final static float TURN_MINIMUM = 1; // degrees
    private final static int TURN_BACKOFF = 64; // samples
    private static int backoffTimer = 0;
    
    public TurnDetector() {
        // TODO: fill this up
    	
    }
    
    // Ring buffer subroutines
    public void addToBuffer(float val){
    	gyroBuffer[buffHead] = val;
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
    
    public float getBufferSum(){
    	float sum = 0;
    	for( int i=buffTail; i!=buffHead; i=(i+1)%BUFFER_LEN ){
    		sum += gyroBuffer[i];
    		//Log.i("TurnDetector","" + gyroBuffer[i]);
    	}
    	//Log.i("TurnDetector","Final sum: " + sum);
    	return sum;
    }
    
    public void addTurnListener(TurnListener sl) {
        turnListeners.add(sl);
    }
    
    //public void onSensorChanged(int sensor, float[] values) {
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor; 
        synchronized (this) {
            // if we got gyroscope data, we parse it here.
        	if( sensor.getType() == Sensor.TYPE_GYROSCOPE ){
        		//Log.i("TurnDetector","onSensorChanged");
        		// grab current data and throw it into an array
        		gyroCurrentValue[0] = event.values[0];
        		gyroCurrentValue[1] = event.values[1];
        		gyroCurrentValue[2] = event.values[2];
        		
        		// add the Z-axis gyro component to the buffer
        		addToBuffer(gyroCurrentValue[2]);
        		        		
        		// calculate the current cumulative sum
        		float bufferSum = getBufferSum();
        		
        		// only try to calculate turns if we're not in backoff
        		if( backoffTimer <= 0 ){
        			//Log.i("TurnDetector","Here" + bufferSum);
        			float estimatedTurn = bufferSum*1.1f; // fudge factor
        			if( Math.abs(estimatedTurn) > TURN_MINIMUM){
        				//Log.i("TurnDetector","Here" + estimatedTurn);
        				// Turn detected--notify our listeners!
                        for (TurnListener turnListener : turnListeners) {
                            turnListener.onTurn((int)estimatedTurn);
                        }
        				// backoff
        				backoffTimer = TURN_BACKOFF;
        				
        			}
        			
        		}else{
        			backoffTimer--;
        			if(backoffTimer == 0){
        				for (TurnListener turnListener : turnListeners) {
                            turnListener.onTurn(0);
                        }
        			}
        		}
        	}
        }
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

}