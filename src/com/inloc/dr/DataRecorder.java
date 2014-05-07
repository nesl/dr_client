package com.inloc.dr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.Calendar;

import android.os.Environment;

public class DataRecorder {
	private File recordFile;
	private String filename;
	private FileWriter fw;
	public DataRecorder(){
		filename = "Pedometer_" + Calendar.DAY_OF_MONTH +
				"-" + Calendar.MONTH + "-" + Calendar.YEAR + 
				"_" + Calendar.HOUR_OF_DAY + "-" + Calendar.MINUTE;
		recordFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath(),filename);
	}
	public void writeRecord(String record){
		try{
			fw = new FileWriter(filename, true);
			fw.write(System.currentTimeMillis() + "," + record);
			fw.flush();
			fw.close();
		}catch(Exception e){e.printStackTrace();}
	}
}
