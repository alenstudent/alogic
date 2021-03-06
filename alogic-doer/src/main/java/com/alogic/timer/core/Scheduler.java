package com.alogic.timer.core;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.alogic.ha.FailoverController;
import com.alogic.ha.FailoverListener;
import com.anysoft.util.BaseException;
import com.anysoft.util.Factory;
import com.anysoft.util.IOTools;
import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;
import com.anysoft.util.Settings;
import com.anysoft.util.XmlElementProperties;
import com.anysoft.util.XmlTools;
import com.anysoft.util.resource.ResourceFactory;

/**
 * 调度者
 * 
 * @author duanyy
 * @since 1.6.3.37
 * 
 * @version 1.6.3.38 [duanyy 20150812] <br>
 * - 增加集群功能 <br>
 * 
 * @version 1.6.4.37 [duanyy 20160321] <br>
 * - 优化锁被打断的时的处理 <br>
 * 
 * @version 1.6.4.39 [duanyy 20160325] <br>
 * - 采用concurrent包来调度定时器 <br>
 * - scheduler可以作为一个一次性的timer运行 <br>
 * 
 * @version 1.6.4.42 [duanyy 20160407] <br>
 * - 增加Linked实现 <br>
 * 
 * @version 1.6.7.9 [20170201 duanyy] <br>
 * - 采用SLF4j日志框架输出日志 <br>
 * 
 * @version 1.6.8.3 [20170328 duanyy] <br>
 * - 采用新的ha框架来进行分布式全局锁控制 <br>
 */
public interface Scheduler extends Timer,Runnable {
	/**
	 * 获取所管理的Timer列表
	 * @return Timer列表
	 */
	public Timer [] getTimers();
	
	/**
	 * 获取指定ID的timer
	 * @param id ID
	 * @return Timer
	 */
	public Timer get(String id);

	/**
	 * 将指定的Timer加入调度列表
	 * @param timer 定时器
	 */
	public void schedule(Timer timer);
	
	/**
	 * 按照指定的匹配规则调度指定的任务
	 * @param id 定时器的ID
	 * @param matcher 匹配器
	 * @param task 任务
	 */
	public void schedule(String id,Matcher matcher,Doer task);
	
	/**
	 * 按照指定的匹配规则调度指定的Runnable
	 * @param id 定时器的ID
	 * @param matcher 匹配器
	 * @param runnable 任务
	 */
	public void schedule(String id,Matcher matcher,Runnable runnable);
	
	/**
	 * 设置任务提交者
	 * @param committer 任务提交者
	 */
	public void setTaskCommitter(DoerCommitter committer);
	
	/**
	 * 删除指定ID的timer
	 * @param id ID
	 */
	public void remove(String id);
	
	/**
	 * 开始调度
	 */
	public void start();
	
	/**
	 * 停止调度
	 */
	public void stop();
	
	/**
	 * 等待线程结束直到超时
	 */
	public void join(long timeout);
	/**
	 * Abstract
	 * @author duanyy
	 *
	 */
	abstract public static class Abstract implements Scheduler,FailoverListener{
		protected static final Logger logger = LoggerFactory.getLogger(Timer.class);
		protected State state = State.Init;
		protected DoerCommitter comitter = null;
		protected long interval = 1000;
		protected FailoverController fc = null;
		protected Future<?> future = null;
		
		protected String id = "root";
		
		@Override
		public void setTaskCommitter(DoerCommitter _committer){
			comitter = _committer;
		}
		
		@Override
		public void configure(Properties p){
			interval = PropertiesConstants.getLong(p,"interval",interval,true);
			id = PropertiesConstants.getString(p, "id", "root");
			fc = getFailoverController(p);
			if (fc != null){
				fc.start(this);
			}
		}

		@Override
		public void configure(Element _e, Properties _properties){
			Properties p = new XmlElementProperties(_e,_properties);
			configure(p);
		}

		@Override
		public void report(Element xml) {
			if (xml != null){
				xml.setAttribute("id", getId());
				xml.setAttribute("type", "Scheduler");
				xml.setAttribute("module", getClass().getName());
				
				Timer[] timers = getTimers();
				if (timers.length > 0){
					Document doc = xml.getOwnerDocument();
					for (int i = 0 ; i < timers.length ; i ++){
						Timer t = timers[i];
						
						Element _timer = doc.createElement("timer");
						_timer.setAttribute("detail", "false");
						t.report(_timer);
						
						xml.appendChild(_timer);
					}
				}
				
				if (comitter != null){
					Document doc = xml.getOwnerDocument();
					Element _committer = doc.createElement("committer");
					comitter.report(_committer);
					xml.appendChild(_committer);
				}
			}
		}

		@Override
		public void report(Map<String, Object> json) {
			if (json != null){
				json.put("module", getClass().getName());
				json.put("id",getId());
				json.put("type","Scheduler");
				Timer[] timers = getTimers();
				if (timers.length > 0){
					List<Object> _timer = new ArrayList<Object>();
					
					for (int i = 0 ; i < timers.length ; i ++){
						Timer t = timers[i];
						
						Map<String,Object> _map = new HashMap<String,Object>();
						_map.put("detail", "false");
						t.report(_map);
						
						_timer.add(_map);
					}
					
					json.put("timer", _timer);
				}
				
				if (comitter != null){
					Map<String,Object> _comitter = new HashMap<String,Object>();
					comitter.report(_comitter);
					json.put("comitter", _comitter);
				}
			}
		}

		@Override
		public void schedule(String id, Matcher matcher, Doer task) {
			schedule(new Timer.Simple(id, matcher, task));
		}

		@Override
		public void schedule(String id, Matcher matcher, Runnable runnable) {
			schedule(new Timer.Simple(id, matcher, runnable));
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public State getState() {
			return state;
		}

		@Override
		public void pause() {
			if (state != State.Running){
				throw new BaseException("core.e1101","The current state is not Running,Can not pause.");
			}
			state = State.Paused;
		}

		@Override
		public void resume() {
			if (state != State.Paused){
				throw new BaseException("core.e1101","The current state is not Paused,Can not resume.");
			}
			state = State.Running;
		}
		
		@Override
		public void schedule(DoerCommitter committer) {
				//scheduler只能启动一次，所以只能状态为Init时启动
			if (state == State.Init){
				start();
			}
		}
		
		@Override
		public void start() {
			final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
			final Runnable self = this;
			service.schedule(
					new Runnable(){
						@Override
						public void run() {
							logger.info(String.format("Start scheduler[%s]....",getId()));
							state = State.Idle;							
							future = service.scheduleAtFixedRate(self, 5000, interval, TimeUnit.MILLISECONDS);	
						}}, 
					0, TimeUnit.MILLISECONDS);
		}		

		@Override
		public void reload(){
			Timer[] all = getTimers();
			for (Timer t:all){
				t.reload();
			}
		}		
		
		@Override
		public void stop(){
			state = State.Stopping;
			if (fc != null){
				fc.stop();
			}			
			logger.info(String.format("Try to stop the scheduler[%s]....[%s]",getId(),state.toString()));			
			if (future != null){
				//等待2秒钟，再打断线程
				try {
					Thread.sleep(2*interval);
				} catch (InterruptedException e) {
				}
				future.cancel(true);
			}	
			Timer [] timers = getTimers();
			for (Timer timer:timers){
				timer.stop();
			}
		}
		
		@Override
		public void run(){
			if (state == State.Idle){
				state = State.Running;
				try {
					if (fc != null){
						if (fc.isActive()){
							scheduleOnce();
						}
					}else{
						scheduleOnce();
					}
				}finally{
					state = State.Idle;
				}
			}
		}
		
		abstract protected void scheduleOnce();
		
		@Override
		public void join(long timeout){
			if (future != null){
				try {
					future.get(timeout, TimeUnit.MILLISECONDS);
				} catch (Exception e) {
					// nothing to do
				}
			}
		}
		
		@Override
		public Date forecastNextDate() {
			return new Date();
		}
		
		@Override
		public boolean isTimeToClear(){return true;}
		
		private FailoverController getFailoverController(Properties p) {
			FailoverController controller = null;
			String module = PropertiesConstants.getString(p,"failover","",true);
			if (StringUtils.isNotEmpty(module)){
				Factory<FailoverController> factory = new Factory<FailoverController>();
				try {
					controller = factory.newInstance(module, p);
				}catch (Exception ex){
					logger.error("Can not create FailoverController instance:" + module);
				}
			}
			return controller;
		}
		
		@Override
		public void becomeActive() {
			logger.info("Scheduler is active now.");
		}
		@Override
		public void becomeStandby() {
			logger.info("Scheduler is standby now.");
		}		
	}
	
	/**
	 * 简单实现
	 * @author duanyy
	 *
	 */
	public static class Simple extends Abstract{
		protected Hashtable<String,Timer> timers = new Hashtable<String,Timer>();
		protected boolean async = true;
		
		@Override
		public void configure(Properties p) {
			super.configure(p);
			async = PropertiesConstants.getBoolean(p,"async",async,true);
		}		
		
		@Override
		public Timer[] getTimers() {
			return timers.values().toArray(new Timer[0]);
		}

		@Override
		public Timer get(String id) {
			return timers.get(id);
		}

		@Override
		public void schedule(Timer timer) {
			timers.put(timer.getId(), timer);
		}

		@Override
		public void remove(String id) {
			timers.remove(id);
		}	
		
		@Override
		protected void scheduleOnce() {
			if (state != State.Running){
				//not running
				return ;
			}
			try {
				Iterator<Timer> iter = timers.values().iterator();
				final List<String> toBeClear = new ArrayList<String>();
				
				while (iter.hasNext()){
					final Timer timer = iter.next();
					
					if (async){
						Thread thread = new Thread(){
							public void run(){
								timer.schedule(comitter);
								if (timer.isTimeToClear()){
									toBeClear.add(timer.getId());
								}
							}
						};
						
						thread.setDaemon(true);
						thread.start();
					}else{
						timer.schedule(comitter);
						if (timer.isTimeToClear()){
							toBeClear.add(timer.getId());
						}
					}
				}
				
				for (String id:toBeClear){
					timers.remove(id);
				}
			}catch (Exception ex){
				logger.error("Erro when scheduling..",ex);
			}
		}
		
		@Override
		public Task newTask() {
			//never used
			return null;
		}
	}
	
	/**
	 * 基于XML配置的实现
	 * 
	 * @author duanyy
	 *
	 */
	public static class XMLed extends Simple{
		/**
		 * 缺省的Timer实现
		 */
		protected String dftTimer = Timer.XMLed.class.getName();
		
		@Override
		public void configure(Element _e, Properties _properties) {
			Properties p = new XmlElementProperties(_e,_properties);
			
			dftTimer = PropertiesConstants.getString(p,"dftTimerClass",dftTimer,true);
			
			configure(p);
			
			Element _committer = XmlTools.getFirstElementByPath(_e, "committer");
			if (_committer != null){
				Factory<DoerCommitter> factory = new Factory<DoerCommitter>();
				comitter = factory.newInstance(_committer, p, "module", ThreadPoolTaskCommitter.class.getName());
			}
			
			loadTimerFromElement(_e,p);
		}
		
		protected void loadTimerFromElement(Element root,Properties p){
			loadIncludeFiles(root,p);
			
			NodeList timerList = XmlTools.getNodeListByPath(root, "timer");
			Factory<Timer> factory = new Factory<Timer>();
			
			for (int i = 0 ;i < timerList.getLength(); i ++){
				Node n = timerList.item(i);
				if (n.getNodeType() != Node.ELEMENT_NODE){
					continue;
				}
				Element e = (Element)n;
				
				try {
					Timer timer = factory.newInstance(e, p, "module", dftTimer);
					if (timer != null){
						schedule(timer);
					}
				}catch (Exception ex){
					logger.error("Can not create timer",ex);
				}
			}
		}
		
		protected void loadIncludeFiles(Element root,Properties p){
			NodeList includes = XmlTools.getNodeListByPath(root, "include");
			
			for (int i = 0 ;i < includes.getLength() ; i++){
				Node n = includes.item(i);
				if (n.getNodeType() != Node.ELEMENT_NODE){
					continue;
				}
				Element e = (Element)n;
				String src = e.getAttribute("src");
				if (src == null || src.length() <= 0){
					continue;
				}
				
				Document doc = loadDocument(p.transform(src),null);
				if (doc == null){
					continue;
				}
				
				loadTimerFromElement(doc.getDocumentElement(),p);
			}
		}
		
		/**
		 * 从主/备地址中装入文档
		 * 
		 * @param master 主地址
		 * @param secondary 备用地址
		 * @return XML文档
		 */
		protected static Document loadDocument(String master,String secondary){
			ResourceFactory rm = Settings.getResourceFactory();
			if (null == rm){
				rm = new ResourceFactory();
			}
			
			Document ret = null;
			InputStream in = null;
			try {
				in = rm.load(master,secondary, null);
				ret = XmlTools.loadFromInputStream(in);		
			} catch (Exception ex){
				logger.error("Error occurs when load xml file,source=" + master, ex);
			}finally {
				IOTools.closeStream(in);
			}		
			return ret;
		}
	}
	
	/**
	 * 外部连接配置文件
	 * 
	 * @author duanyy
	 *
	 */
	public static class Linked extends XMLed{	

		@Override
		public void configure(Element _e, Properties _properties){
			Properties p = new XmlElementProperties(_e,_properties);
			
			String src = PropertiesConstants.getString(p,"src","");
			if (StringUtils.isNotEmpty(src)){
				Document doc = loadDocument(src,null);
				if (doc != null){
					super.configure(doc.getDocumentElement(),p);
				}else{
					super.configure(_e, _properties);
				}
			}else{
				logger.error("Can not find src attr in Linked module");
				super.configure(_e, _properties);
			}
		}		
	}	

}
