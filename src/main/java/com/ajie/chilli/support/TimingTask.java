package com.ajie.chilli.support;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时器
 *
 * @author niezhenjie
 *
 */
public class TimingTask implements Runnable {
	protected static final Logger logger = LoggerFactory
			.getLogger(TimingTask.class);
	/** 首次执行的时间 */
	private Date firstExecute;
	/** 周期执行的间隔 单位值毫秒 0表示只执行一次 */
	private long interval;
	/** 定时器 */
	private ScheduledExecutorService service;
	/** 定时执行的任务 */
	private Worker worker;

	/** 定时器名字 */
	private String name;

	/**
	 * 创建一个定时并周期性执行的定时器
	 * 
	 * @param worker
	 * @param firstExecute
	 * @param interval
	 */
	public TimingTask(Worker worker, Date firstExecuteDate, long interVal) {
		this(null, worker, firstExecuteDate, interVal);
	}

	/**
	 * 创建一个指定名字的定器时并周期性执
	 * 
	 * @param worker
	 * @param firstExecute
	 * @param interval
	 */
	public TimingTask(String name, Worker worker, Date firstExecuteDate,
			long interVal) {
		this.worker = worker;
		this.firstExecute = firstExecuteDate;
		this.interval = interVal;
		this.name = name;
		final String threadName;
		if (null == this.name) {
			threadName = "timing-thread";
		} else {
			threadName = this.name;
		}
		service = Executors
				.newSingleThreadScheduledExecutor(new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						Thread t = new Thread(r);
						t.setName(threadName);
						return t;
					}
				});
		long delay = firstExecute.getTime() - new Date().getTime();
		if (0 == interval) { // 只执行一次
			if (delay < 0) { // 只运行一次，且开始运行的时间已经过去了，不再执行
				service = null;
			} else {
				service.schedule(this, delay, TimeUnit.MILLISECONDS);
			}
		} else { // 周期性执行
			if (delay >= 0) {
				service.scheduleAtFixedRate(this, delay, interVal,
						TimeUnit.MILLISECONDS);
			} else {
				// 首次开始的时间已经过去了，根据interval计算下次运行的时间吧
				delay = Math.abs(delay);
				// 计算过去了的时间是多少个周期，如3点运行，周期是4小时，现在是9点，则超过了一个周期，下次运行是第二个周期的时间：3+4+4
				int time = (int) (delay / interval);
				long next = ((time + 1) * interval) - delay;
				if (0 == next) {
					next = interval;
				}
				service.scheduleAtFixedRate(this, next, interval,
						TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * 创建一个定时只执行一次的定时器
	 * 
	 * @param worker
	 * @param firstExecute
	 */
	public TimingTask(Worker worker, Date firstExecute) {
		this(worker, firstExecute, 0);
	}

	public static TimingTask createTimingTask(Worker worker, Date firstExecute) {
		return createTimingTask(worker, firstExecute, 0);
	}

	public static TimingTask createTimingTask(Worker worker,
			Date firstExecuteDate, long interVal) {
		return new TimingTask(worker, firstExecuteDate, interVal);
	}

	public static TimingTask createTimingTask(String name, Worker worker,
			Date firstExecuteDate, long interVal) {
		TimingTask timingTask = new TimingTask(name, worker, firstExecuteDate,
				interVal);
		return timingTask;
	}

	/**
	 * 创建一个定时器
	 * 
	 * @param name
	 * @param worker
	 * @param firstExecuteDate
	 *            首次执行时间，格式 HH:mm:ss
	 * @param interVal
	 *            定时间隔
	 * @return
	 */
	public static TimingTask createTimingTask(String name, Worker worker,
			String firstExecuteDate, long interVal) {
		if (null == firstExecuteDate) {
			throw new NullPointerException("首次执行时间为空");
		}
		String[] split = firstExecuteDate.split(":");
		String sh = null, sm = null, ss = null;
		for (int i = 0; i < split.length; i++) {
			if (i == 0) {
				sh = split[i];
			} else if (i == 1) {
				sm = split[i];
			} else if (i == 2) {
				ss = split[i];
			}
		}
		int h, m, s;
		try {
			h = Integer.valueOf(sh);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("首次执行时间格式错误：" + firstExecuteDate);
		}
		if (h > 24) {
			throw new IllegalArgumentException("首次执行时间小时数大于24："
					+ firstExecuteDate);
		}
		try {
			m = Integer.valueOf(sm);
		} catch (Exception e) {
			m = 0;
		}
		if (m >= 60) {
			throw new IllegalArgumentException("首次执行时间分钟数大于或等于60："
					+ firstExecuteDate);
		}
		try {
			s = Integer.valueOf(ss);
		} catch (Exception e) {
			s = 0;
		}
		if (s > 60) {
			throw new IllegalArgumentException("首次执行时间秒数大于或等于60："
					+ firstExecuteDate);
		}
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, h);
		cal.set(Calendar.MINUTE, m);
		cal.set(Calendar.SECOND, s);
		TimingTask timingTask = new TimingTask(name, worker, cal.getTime(),
				interVal);
		return timingTask;
	}

	@Override
	public void run() {
		try {
			worker.work();
		} catch (Exception e) {
			logger.error("定时任务执行失败", Thread.currentThread().getName(), e);
		}
	}
}
