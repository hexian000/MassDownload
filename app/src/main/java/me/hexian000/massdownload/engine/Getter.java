package me.hexian000.massdownload.engine;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import static me.hexian000.massdownload.DownloadApp.LOG_TAG;

public class Getter extends Thread {
	private static final int BUFFER_SIZE = 8 * 1024; // 8 KiB
	private static final int RETRY_COUNT = 3;
	private static final int RETRY_INTERVAL = 5 * 1000; // 5 seconds
	private final URL url;
	private final Writer writer;
	private long currentPosition;
	private long endPosition;
	private boolean healthy;
	private boolean failed;
	private BufferedInputStream bufferedInputStream;

	Getter(@NonNull URL url, @NonNull Writer writer, long start, long end) throws IOException {
		super();
		this.url = url;
		currentPosition = start;
		endPosition = end;
		this.writer = writer;
		healthy = false;
		failed = false;

		connect();
	}

	@Override
	protected void finalize() throws Throwable {
		if (bufferedInputStream != null) {
			try {
				bufferedInputStream.close();
			} catch (IOException ignored) {
			}
		}
		super.finalize();
	}

	public long getRemainingSize() {
		return Math.max(endPosition - currentPosition, 0);
	}

	public boolean isHealthy() {
		return healthy;
	}

	@Nullable
	public Getter fork() {
		long pos = getRemainingSize() / 2 + currentPosition;
		pos -= pos % BUFFER_SIZE;
		if (pos <= currentPosition + BUFFER_SIZE || pos > endPosition) {
			return null; // too small to fork!
		}
		Getter getter = null;
		try {
			getter = new Getter(url, writer, pos, endPosition);
			if (!isInterrupted() && currentPosition + BUFFER_SIZE < pos) {
				endPosition = pos;
				getter.start();
			} else { // too late, give up
				getter = null;
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "fork error", e);
		}
		return getter;
	}

	public boolean isFailed() {
		return failed;
	}

	@Override
	public void run() {
		for (int retry = 0; retry < RETRY_COUNT; retry++) {
			final long oldPos = currentPosition;
			try {
				try {
					if (bufferedInputStream == null) {
						connect();
					}
					download();
					break;
				} catch (IOException e) {
					Log.e(LOG_TAG, "file get error, retry=" + retry, e);
					healthy = false;
					Thread.sleep(RETRY_INTERVAL);
				} finally {
					if (bufferedInputStream != null) {
						try {
							bufferedInputStream.close();
						} catch (IOException ignored) {
						} finally {
							bufferedInputStream = null;
						}
					}
				}
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "writer interrupted", e);
				break;
			} finally {
				if (oldPos < currentPosition) {
					retry = 0;
				}
			}
		}
		failed = currentPosition < endPosition;
	}

	private void connect() throws IOException {
		URLConnection urlConnection = url.openConnection();
		urlConnection.setRequestProperty("Range",
				"bytes=" + currentPosition + "-" + endPosition);
		bufferedInputStream = new BufferedInputStream(urlConnection.getInputStream(), BUFFER_SIZE);
	}

	private void download() throws IOException, InterruptedException {
		byte[] buf = new byte[BUFFER_SIZE];
		while (currentPosition < endPosition) {
			if (isInterrupted()) {
				throw new InterruptedException();
			}
			int len = bufferedInputStream.read(buf, 0, BUFFER_SIZE);
			if (len == -1) {
				break;
			} else {
				healthy = true;
				len = (int) Math.min(getRemainingSize(), len);
				byte[] writeBuf = new byte[len];
				System.arraycopy(buf, 0, writeBuf, 0, len);
				writer.write(writeBuf, currentPosition);
				currentPosition += len;
			}
		}
	}
}
