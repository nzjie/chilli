package com.ajie.chilli.remote.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ajie.chilli.remote.ConnectConfig;
import com.ajie.chilli.remote.SessionExt;
import com.ajie.chilli.remote.Worker;
import com.ajie.chilli.remote.exception.RemoteException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;

/**
 * ssh会话管理，基于多线程异步执行指令，适用于上传文件，不适用于反馈结果指令，如获取执行linux命令结果
 *
 * @author niezhenjie
 *
 */
public class AsynSshSessionMgr extends AbstractSshSessionMgr {

	private final static Logger logger = LoggerFactory.getLogger(AsynSshSessionMgr.class);
	/** 是否允许trace */
	protected final static boolean _TraceEnabled = logger.isTraceEnabled();

	/** 默认的名字前缀，可通过传入的值修改biz替换默认 */
	public static final String DEFAULT_NAME_PREFIX = "asyn-ssh-";

	/** 重连失败最大次数 */
	public final static int MAX_RETRY_COUNT = 3;

	/** 线程池 */
	ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 5, 2, TimeUnit.SECONDS,
			new ArrayBlockingQueue<Runnable>(5), new ThreadPoolExecutor.DiscardPolicy());

	/** 连接池全忙时，等待执行队列 */
	volatile private BlockingQueue<Worker> workqueue;

	public AsynSshSessionMgr(ConnectConfig config) {
		this(config, null);
	}

	public AsynSshSessionMgr(ConnectConfig config, String biz) {
		super(config, biz);
		workqueue = new ArrayBlockingQueue<>(config.getWorkerQueueSize());
		init();
		runWorker();
	}

	/**
	 * 初始化
	 */
	private void init() {
		synchronized (sessionPool) {
			int core = config.getCore();
			for (int i = 0; i < core; i++) {
				SessionExt session = openSession();
				if (null == session) {
					continue;
				}
				sessionPool.add(session);
			}
		}
		recycleWatch();
	}

	@Override
	public void execute(Worker worker) throws RemoteException {
		if (null == worker)
			throw new RemoteException("任务为空");
		addWorker(worker);
	}

	private void addWorker(Worker worker) throws RemoteException {
		synchronized (workqueue) {
			try {
				while (workqueue.size() >= DEFAULT_WAIT_SIZE) {
					workqueue.wait();
				}
				workqueue.put(worker);
				workqueue.notifyAll();
			} catch (InterruptedException e) {
				try {
					workqueue.put(worker);// 重试
					workqueue.notifyAll();
				} catch (InterruptedException e1) {
					logger.error("", e1);
				}
			}
		}
	}

	/**
	 * 轮询执行队列里的任务
	 */
	private void runWorker() {
		Thread t = new Thread() {
			public void run() {
				while (true) {
					synchronized (workqueue) {
						try {
							while (workqueue.size() == 0) {
								workqueue.wait();
							}
							final SessionExt session = getSession();
							final Worker work = workqueue.take();
							executor.execute(new Runnable() {
								@Override
								public void run() {
									runWorker(session, work);
								}
							});
							// 执行完毕，释放锁并唤醒其他线程
							workqueue.notifyAll();
						} catch (InterruptedException e) {
							logger.error("", e);
						}
					}
					// 退出同步块再执行

				}
			};
		};
		t.start();
	}

	/**
	 * 执行任务，执行完毕后将会话设置为空闲状态且将channel通道关闭
	 * 
	 * @param session
	 * @param worker
	 * @throws .RemoteException
	 */
	private void runWorker(SessionExt session, Worker worker) {
		if (null == session || null == worker)
			return;
		try {
			worker.run(session);
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			session.disconnectChannel();
			// 因为get的时候会锁，这里不需要锁
			session.setState(SessionExt.STATE_IDLE);
		}
	}

	public String toString() {
		return config.toString();
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		Properties prop = new Properties();
		InputStream is = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("server.properties");
		prop.load(is);
		String host = prop.getProperty("host");
		String passwd = prop.getProperty("passwd");
		String name = prop.getProperty("name");
		ConnectConfig config = ConnectConfig.valueOf(name, passwd, host, 22);
		// timeout一般来说需要设置大一点，否则会出现各种超时
		config.setTimeout(30000);
		config.setMax(10);
		config.setCore(2);
		final AsynSshSessionMgr mgr = new AsynSshSessionMgr(config);
		for (int i = 0; i < 50; i++) {
			final int j = i;
			Thread t = new Thread() {
				public void run() {
					try {
						mgr.execute(new Worker() {
							@Override
							public void run(SessionExt session) {
								try {
									InputStream stream = new FileInputStream(new File(
											"C:/Users/ajie/Desktop/arrow_top.png"));
									Channel channel = session.openChannel(3000, "sftp");
									ChannelSftp sftp = (ChannelSftp) channel;
									OutputStream out = sftp.put("/var/www/image/testimg" + (j + 1)
											+ ".png");
									byte[] buf = new byte[1024];
									int n = stream.read(buf);
									while (n != -1) {
										out.write(buf, 0, n);
										n = stream.read(buf);
									}
									out.flush();
									stream.close();
									out.close();
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						});
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				};
			};
			t.start();
		}

	}

	@Override
	public Logger getLogger() {
		return logger;
	}
}
