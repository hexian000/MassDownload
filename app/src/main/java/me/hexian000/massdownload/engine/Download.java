package me.hexian000.massdownload.engine;

import android.support.annotation.NonNull;
import android.util.Log;

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

import static me.hexian000.massdownload.DownloadApp.LOG_TAG;

public class Download {
	private static final int MINIMAL_FORK = 2 * 1024 * 1024; // 2 MiB
	private final URL url;
	private final Writer writer;
	private final long length;
	private final Timer forkTimer;
	private final File file;
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
		file = new File(path.getPath() + "/" + filename);
		writer = new Writer(64 * 1024 * 1024, file, length);
		getters = Collections.synchronizedList(new ArrayList<>());
		forkTimer = new Timer();
	}

	@NonNull
	public String getFilename() {
		return filename;
	}

	public void start() {
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
		int healthy = getHealthyThreadCount();
		if (alive < 10 && alive == healthy) {
			Log.d(LOG_TAG, "start fork: " + healthy + "/" + alive);
			Getter maxGetter = null;
			long maxRemain = 0;
			long maxCost = -1;
			for (Getter getter : getters) {
				final long remain = getter.getRemainingSize();
				final long cost = getter.getConnectCost();
				if (remain > maxRemain) {
					maxRemain = remain;
					maxGetter = getter;
				}
				if (cost > maxCost) {
					maxCost = cost;
				}
			}
			if (maxCost >= 0 && maxGetter != null && maxRemain > MINIMAL_FORK) {
				final double timeRemain = (double) maxRemain / maxGetter.getDataRate();
				Log.d(LOG_TAG, "maxCost: " + maxCost + " timeRemain: " + timeRemain);
				if (maxCost < timeRemain / 2) { // worthy to fork
					Getter newGetter = maxGetter.fork();
					if (newGetter != null) {
						getters.add(newGetter);
					}
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
		for (Getter getter : getters) {
			getter.interrupt();
		}
	}

	public void join() throws InterruptedException {
		if (cancelled) {
			synchronized (forkTimer) {
				for (Getter getter : getters) {
					getter.join();
				}
			}
		} else {
			int i = 0;
			while (i < getters.size()) {
				getters.get(i).join();
				i++;
			}
		}
		writer.close();
		if (cancelled) {
			if (file.delete()) {
				Log.e(LOG_TAG, "deleted " + filename);
			} else {
				Log.e(LOG_TAG, "failed deleting " + filename);
			}
		}
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
