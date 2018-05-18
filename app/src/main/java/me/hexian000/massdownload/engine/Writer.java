package me.hexian000.massdownload.engine;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static me.hexian000.massdownload.DownloadApp.LOG_TAG;

public class Writer extends Thread {
	private final BlockingQueue<BufferBlock> q;
	private final Semaphore capacity;
	private final RandomAccessFile file;
	private final AtomicBoolean closed;

	Writer(int bufferSize, @NonNull File file, long length) throws IOException {
		super();
		q = new LinkedBlockingQueue<>();
		capacity = new Semaphore(bufferSize);
		this.file = new RandomAccessFile(file, "rw");
		this.file.setLength(length);
		closed = new AtomicBoolean(false);
	}

	public void write(@NonNull byte[] data, long offset) throws InterruptedException {
		BufferBlock block = new BufferBlock(data, offset);
		if (closed.get()) {
			throw new IllegalStateException("writer has been closed");
		}
		capacity.acquire(block.data.length);
		q.put(block);
	}

	@Override
	public void run() {
		try {
			while (!isInterrupted()) {
				BufferBlock block = q.take();
				if (block.data == null) {
					break;
				}
				file.seek(block.offset);
				file.write(block.data);
				// Log.v(LOG_TAG, "written " + block.data.length + " bytes at offset=" + block.offset);
				capacity.release(block.data.length);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "file write error", e);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "writer interrupted", e);
		} finally {
			try {
				file.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "file close error", e);
			}
			Log.d(LOG_TAG, "file closed");
		}
	}

	public void close() throws InterruptedException {
		if (closed.compareAndSet(false, true)) {
			q.put(new BufferBlock(null, -1));
		}
		join();
	}

	private class BufferBlock {
		long offset;
		byte[] data;

		BufferBlock(byte[] data, long offset) {
			this.data = data;
			this.offset = offset;
		}
	}
}
