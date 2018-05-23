package me.hexian000.massdownload.engine;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Download {
	private static final int MINIMAL_FORK = 1024 * 1024; // 1 MiB
	private final URL url;
	private final Writer writer;
	private final long length;
	private final Timer forkTimer;
	private final String filename;
	private List<Getter> getters;
	private boolean cancelled;

	public Download(@NonNull URL url, @NonNull File path) throws IOException {
		URLConnection urlConnection = url.openConnection();
		length = urlConnection.getContentLengthLong();
		if (length < 1) {
			throw new IOException("content-length < 1");
		}
		this.url = url;
		String filename = url.getFile();
		int pos = filename.lastIndexOf('/');
		if (pos != -1) {
			filename = filename.substring(pos + 1);
		}
		String disposition = urlConnection.getHeaderField("Content-Disposition");
		if (disposition != null) {
			Matcher m = Pattern.compile("filename=\"(.*)\"").matcher(disposition);
			if (m.find()) {
				filename = m.group(0);
			}
		}
		this.filename = filename;
		writer = new Writer(64 * 1024 * 1024,
				new File(path.getPath() + "/" + filename), length);
		getters = Collections.synchronizedList(new ArrayList<>());
		forkTimer = new Timer();
	}

	@NonNull
	public String getFilename() {
		return filename;
	}

	public void start() throws IOException {
		Getter getter = new Getter(url, writer, 0, length);
		getter.start();
		getters.add(getter);

		forkTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				synchronized (forkTimer) {
					if (!cancelled) {
						smartFork();
					}
				}
			}
		}, 5000, 5000);

		writer.start();
	}

	public int getAliveThreadCount() {
		int alive = 0;
		for (Getter getter : getters) {
			if (getter.isAlive()) {
				alive++;
			}
		}
		return alive;
	}

	public int getHealthyThreadCount() {
		int healthy = 0;
		for (Getter getter : getters) {
			if (getter.isAlive() && getter.isHealthy()) {
				healthy++;
			}
		}
		return healthy;
	}

	private void smartFork() {
		int alive = getAliveThreadCount();
		if (alive < 10 && alive == getHealthyThreadCount()) {
			Getter maxGetter = null;
			long maxRemain = 0;
			for (Getter getter : getters) {
				long remain = getter.getRemainingSize();
				if (remain > maxRemain) {
					maxRemain = remain;
					maxGetter = getter;
				}
			}
			if (maxGetter != null && maxRemain > MINIMAL_FORK) {
				Getter newGetter = maxGetter.fork();
				if (newGetter != null) {
					getters.add(newGetter);
				}
			}
		}
	}

	public long getLength() {
		return length;
	}

	public long getRemainingLength() {
		long remaining = 0;
		for (Getter getter : getters) {
			remaining += getter.getRemainingSize();
		}
		return remaining;
	}

	public void cancel() {
		cancelled = true;
		forkTimer.cancel();
		synchronized (forkTimer) {
			for (Getter getter : getters) {
				getter.interrupt();
			}
		}
	}

	public void join() throws InterruptedException {
		synchronized (forkTimer) {
			for (Getter getter : getters) {
				getter.join();
			}
		}
		writer.close();
	}

	public boolean isFailed() throws IllegalStateException {
		for (Getter getter : getters) {
			if (getter.isAlive()) {
				throw new IllegalStateException("download is still running");
			}
			if (getter.isFailed()) {
				return true;
			}
		}
		return false;
	}

}
