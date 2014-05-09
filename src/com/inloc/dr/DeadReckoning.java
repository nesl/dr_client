/*
 *  NESL - InLoc
 *  
 *  Dead Reckoning Service
 *  
 *  This file is part of the NESL InLoc Service Suite.
 *
 *    The NESL InLoc Service Suite is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    NESL InLoc Service Suite is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.

 *    You should have received a copy of the GNU General Public License
 *    along with The NESL InLoc Service Suite.  If not, see <http://www.gnu.org/licenses/>.
 */

/* Package Declaration */
package com.inloc.dr;

/* Imports */
import com.inloc.dr.R;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

/* Main Activity */
public class DeadReckoning extends Activity {
	// main activity tag
	private static final String TAG = "InLoc_DR";
	// global settings
    private SharedPreferences mSettings;
    // pedometer settings
    private PedometerSettings mPedometerSettings;
    // get step and angle text views
    private TextView mStepValueView;
    private TextView mAngleValueView;
    // dead reckoning views
    private int mStepValue;
    private int mAngleValue;
    // estimated orientation
    private int mOrientation;
    private TextView mOrientationView;
    // Set when user selected Quit from menu, can be used by onPause, onStop, onDestroy
    private boolean mQuitting = false; 
    private boolean mIsRunning;
    // the main background service
    private StepService mService;
    // path canvas
    private PathView mPathView;
    private final int PATHVIEW_ID = 100;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "[ACTIVITY] onCreate");
        super.onCreate(savedInstanceState);
        // initialize variables
        mStepValue = 0;
        mAngleValue = 0;
        mOrientation = 0;
        
        // set the view
        setContentView(R.layout.main);
        
        // initialize path canvas
        final LinearLayout view = (LinearLayout) findViewById(R.id.canvas_row);
        mPathView = new PathView(this);
        mPathView.setId(PATHVIEW_ID);
        view.addView(mPathView);
        
    }
    
    /** Called when the activity is started */
    @Override
    protected void onStart() {
        Log.i(TAG, "[ACTIVITY] onStart");
        super.onStart();
    }

    /** Called when the activity is resumed */
    @Override
    protected void onResume() {
        Log.i(TAG, "[ACTIVITY] onResume");
        super.onResume();
        
        // get default preferences
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mPedometerSettings = new PedometerSettings(mSettings);
        
        // Read from preferences if the service was running on the last onPause
        mIsRunning = mPedometerSettings.isServiceRunning();
        
        // Start the service if this is considered to be an application start (last onPause was long ago)
        if (!mIsRunning && mPedometerSettings.isNewStart()) {
            startStepService();
            bindStepService();
        }
        else if (mIsRunning) {
            bindStepService();
        }
        
        mPedometerSettings.clearServiceRunning();

        // re-hook the UI views
        mStepValueView     = (TextView) findViewById(R.id.step_value);
        mAngleValueView	   = (TextView) findViewById(R.id.angle_value);
        mOrientationView   = (TextView) findViewById(R.id.orientation);
        mPathView          = (PathView) findViewById(PATHVIEW_ID);

    }
    
    /** Called when the activity is paused */
    @Override
    protected void onPause() {
        Log.i(TAG, "[ACTIVITY] onPause");
        if (mIsRunning) {
            unbindStepService();
        }
        if (mQuitting) {
            mPedometerSettings.saveServiceRunningWithNullTimestamp(mIsRunning);
        }
        else {
            mPedometerSettings.saveServiceRunningWithTimestamp(mIsRunning);
        }

        super.onPause();
    }

    /** Called when the activity is stopped */
    @Override
    protected void onStop() {
        Log.i(TAG, "[ACTIVITY] onStop");
        super.onStop();
    }

    /** Called when the activity is destroyed */
    protected void onDestroy() {
        Log.i(TAG, "[ACTIVITY] onDestroy");
        super.onDestroy();
    }
    
    /** Called when the activity is restarted */
    protected void onRestart() {
        Log.i(TAG, "[ACTIVITY] onRestart");
        super.onDestroy();
    }
    
    /** Called when the activity is first created to bind service */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ((StepService.StepBinder)service).getService();
            mService.registerCallback(mCallback);
            mService.reloadSettings();
        }
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };
    

    private void startStepService() {
        if (! mIsRunning) {
            Log.i(TAG, "[SERVICE] Start");
            mIsRunning = true;
            startService(new Intent(DeadReckoning.this,
                    StepService.class));
        }
    }
    
    private void bindStepService() {
        Log.i(TAG, "[SERVICE] Bind");
        bindService(new Intent(DeadReckoning.this, 
                StepService.class), mConnection, Context.BIND_AUTO_CREATE + Context.BIND_DEBUG_UNBIND);
    }

    private void unbindStepService() {
        Log.i(TAG, "[SERVICE] Unbind");
        unbindService(mConnection);
    }
    
    private void stopStepService() {
        Log.i(TAG, "[SERVICE] Stop");
        if (mService != null) {
            Log.i(TAG, "[SERVICE] stopService");
            stopService(new Intent(DeadReckoning.this,
                  StepService.class));
        }
        mIsRunning = false;
    }
    
    private void resetValues(boolean updateDisplay) {
        if (mService != null && mIsRunning) {
            mService.resetValues();                    
        }
        else {
            mStepValueView.setText("0");
            mAngleValueView.setText("0");
            mOrientationView.setText("?");

            SharedPreferences state = getSharedPreferences("state", 0);
            SharedPreferences.Editor stateEditor = state.edit();
            if (updateDisplay) {
                stateEditor.putInt("steps", 0);
                stateEditor.putInt("angle", 0);
                stateEditor.putInt("orientation", 0);
                stateEditor.commit();
            }
        }
    }

    private static final int MENU_SETTINGS = 8;
    private static final int MENU_QUIT     = 9;

    private static final int MENU_PAUSE = 1;
    private static final int MENU_RESUME = 2;
    private static final int MENU_RESET = 3;
    
    /* Creates the menu items */
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mIsRunning) {
            menu.add(0, MENU_PAUSE, 0, R.string.pause)
            .setIcon(android.R.drawable.ic_media_pause)
            .setShortcut('1', 'p');
        }
        else {
            menu.add(0, MENU_RESUME, 0, R.string.resume)
            .setIcon(android.R.drawable.ic_media_play)
            .setShortcut('1', 'p');
        }
        menu.add(0, MENU_RESET, 0, R.string.reset)
        .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
        .setShortcut('2', 'r');
        menu.add(0, MENU_SETTINGS, 0, R.string.settings)
        .setIcon(android.R.drawable.ic_menu_preferences)
        .setShortcut('8', 's')
        // fire up settings activity
        .setIntent(new Intent(this, Settings.class));
        menu.add(0, MENU_QUIT, 0, R.string.quit)
        .setIcon(android.R.drawable.ic_lock_power_off)
        .setShortcut('9', 'q');
        return true;
    }

    /* Handles item selections */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PAUSE:
                unbindStepService();
                stopStepService();
                return true;
            case MENU_RESUME:
                startStepService();
                bindStepService();
                return true;
            case MENU_RESET:
                resetValues(true);
                return true;
            case MENU_QUIT:
                resetValues(false);
                unbindStepService();
                stopStepService();
                mQuitting = true;
                finish();
                return true;
        }
        return false;
    }
 
    // Message handling
    private StepService.ICallback mCallback = new StepService.ICallback() {
        public void stepsChanged(int value) {
            mHandler.sendMessage(mHandler.obtainMessage(STEPS_MSG, value, 0));
        }
        public void angleChanged(int value) {
        	mHandler.sendMessage(mHandler.obtainMessage(ANGLE_MSG, value, 0));
        }
        public void orientChanged(int value) {
        	mHandler.sendMessage(mHandler.obtainMessage(ORIENT_MSG, value, 0));
        }
    };
    
    private static final int STEPS_MSG = 1;
    private static final int ANGLE_MSG = 2;
    private static final int ORIENT_MSG = 3;
    private static final int ORIENT_DFT = 0;
    private static final int ORIENT_OFF = 1;
    private static final int ORIENT_HND = 2;
    private static final int ORIENT_BDY = 3;
    
    private Handler mHandler = new Handler() {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                case STEPS_MSG:
                    mStepValue = (int)msg.arg1;
                    mStepValueView.setText("" + mStepValue);
                    mPathView.addStep(1.0);
                    mPathView.invalidate();
                    break;
                case ANGLE_MSG:
                	mAngleValue =(int)msg.arg1;
                	//if(mAngleValue <= 0){ mAngleValueView.setText("0");}
                	mAngleValueView.setText("" + mAngleValue);
                	mPathView.addTurn(mAngleValue);
                	break;
                case ORIENT_MSG:
                	mOrientation = (int)msg.arg1;
                	switch(mOrientation){
	                	case ORIENT_DFT:
	                		mOrientationView.setText("Unknown");
	                		break;
	                	case ORIENT_BDY:
	                		mOrientationView.setText("On Body (pocket)");
	                		break;
	                	case ORIENT_HND:
	                		mOrientationView.setText("In Hand");
	                		break;
	                	case ORIENT_OFF:
	                		mOrientationView.setText("Still");
	                		break;
	                	default:
	                		mOrientationView.setText("Unknown State");
                	}
                default:
                    super.handleMessage(msg);
            }
        }
    };


}