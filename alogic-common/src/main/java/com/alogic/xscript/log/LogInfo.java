package com.alogic.xscript.log;

import com.anysoft.stream.Flowable;


/**
 * 日志信息
 * @author duanyy
 *
 */
public class LogInfo implements Flowable {

	/**
	 * 活动
	 */
	protected String activity = "";
	/**
	 * 日志的信息
	 */
	protected String message = "";
	
	/**
	 * 日志级别(warn,error,info)
	 */
	protected String level="info";	
	
	/**
	 * 进度(-2至10001,-2代表非进度,-1代表还没开始,10001 代表已经完成,0-10000 代表以10000为基数的百分比)
	 */
	protected int progress = -2;
	
	@Override
	public String id(){
		return activity;
	}	
	
	@Override
	public boolean isAsync(){
		return true;
	}
	
	public LogInfo(String _activity,String _message,String _level,int _progress){
		activity = _activity;
		message = _message;
		level = _level;
		progress = _progress;
	}
	
	public LogInfo(String _activity,String _message,String _level){
		this(_activity,_message,_level,-2);
	}
	
	public LogInfo(String _activity,String _message){
		this(_activity,_message,"info");
	}
	
	public void message(String _activity,String _message){
		message = _message;
	}
	
	public void activity(String _activity){
		activity = _activity;
	}
	
	public void level(String _level){
		level = _level;
	}
	
	public void progress(int _progress){
		progress = _progress;
	}
	
	public String activity(){
		return activity;
	}
	
	public String message(){
		return message;
	}
	
	public String level(){
		return level;
	}
	
	public int progress(){
		return progress;
	}
	
	public String getValue(String varName, Object context, String defaultValue) {
		if (varName.equals("msg")){
			return message;
		}
		if (varName.equals("level")){
			return level;
		}
		if (varName.equals("progress")){
			return String.valueOf(progress);
		}
		return defaultValue;
	}

	public Object getContext(String varName) {
		return this;
	}

	@Override
	public String getRawValue(String varName, Object context, String dftValue) {
		return getValue(varName,context,dftValue);
	}		
	
	public String getStatsDimesion() {
		return level;
	}

}
