package me.hexian000.massdownload;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import me.hexian000.massdownload.engine.Download;

import static me.hexian000.massdownload.DownloadApp.CHANNEL_DOWNLOAD_STATE;
import static me.hexian000.massdownload.DownloadApp.LOG_TAG;
import static me.hexian000.massdownload.DownloadApp.sizeToString;

public class DownloadService extends Service {
	private NotificationManager notificationManager;
	private Download download;
	private Handler handler;
	private Notification.Builder builder;
	private boolean cancelling = false;

	@Override
	public void onCreate() {
		super.onCreate();
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		handler = new Handler();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			if (cancelling) {
				return START_NOT_STICKY;
			}
			cancelling = true;
			if (download != null) {
				download.cancel();
				builder.setContentTitle(getResources().getString(R.string.notification_stopping))
				       .setContentText("")
				       .setProgress(0, 0, true);
				notificationManager.notify(startId, builder.build());
				builder = null;
				new Thread(() -> {
					try {
						download.join();
					} catch (InterruptedException e) {
						Log.e(LOG_TAG, "error cancelling download", e);
					}
					handler.post(this::stopSelf);
				}).start();
			}
			return START_NOT_STICKY;
		} else if (download != null) {
			Toast.makeText(this, R.string.already_downloading, Toast.LENGTH_LONG).show();
			return START_NOT_STICKY;
		}

		builder = new Notification.Builder(getApplicationContext());
		builder.setContentIntent(null)
		       .setContentTitle(getResources().getString(R.string.notification_downloading))
		       .setSmallIcon(R.drawable.ic_file_download_black_24dp)
		       .setWhen(System.currentTimeMillis())
		       .setProgress(0, 0, true)
		       .setOngoing(true)
		       .setVisibility(Notification.VISIBILITY_PUBLIC);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Android 8.0+
			NotificationManager manager = (NotificationManager) getSystemService(
					Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				DownloadApp.createNotificationChannels(manager, getResources());
				builder.setChannelId(CHANNEL_DOWNLOAD_STATE);
			}
		} else {
			// Android 7.1
			builder.setPriority(Notification.PRIORITY_DEFAULT)
			       .setLights(0, 0, 0)
			       .setVibrate(null)
			       .setSound(null);
		}
		Intent cancel = new Intent(this, DownloadService.class);
		cancel.setAction("cancel");
		builder.addAction(
				new Notification.Action.Builder(null, getResources().getString(R.string.cancel),
						PendingIntent.getService(this, startId, cancel, 0)).build())
		       .setContentText(getResources().getString(R.string.notification_starting));

		startForeground(startId, builder.build());

		URL url = null;
		try {
			Uri uri = intent.getData();
			if (uri != null) {
				url = new URL(uri.toString());
			}
		} catch (MalformedURLException e) {
			Log.e(LOG_TAG, "malformed URL", e);
		}

		if (url != null) {
			final URL finalURL = url;
			new Thread(() -> this.downloadThread(startId, finalURL)).start();
		}

		return START_NOT_STICKY;
	}

	private void downloadThread(int startId, @NonNull URL url) {
		Timer statusTimer = new Timer();
		boolean failed = true;
		try {
			download = new Download(url,
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
			Log.d(LOG_TAG, "download created");
			download.start();
			Log.d(LOG_TAG, "download started normally");
			statusTimer.schedule(new TimerTask() {
				long lastLength = 0;

				@Override
				public void run() {
					handler.post(() -> {
						if (builder == null) {
							return;
						}
						long length = download.getLength();
						long now = length - download.getRemainingLength();
						double speed = (now - lastLength) / 2; // per 2 sec
						lastLength = now;
						String text = String.format(Locale.getDefault(),
								getResources().getString(R.string.notification_status),
								sizeToString(now), sizeToString(length),
								download.getHealthyThreadCount(), download.getAliveThreadCount());

						int progress = (int) (now * 1000 / length);
						builder.setContentTitle(download.getFilename())
						       .setContentText(text)
						       .setSubText(sizeToString(speed) + "/s")
						       .setStyle(new Notification.BigTextStyle().bigText(text))
						       .setProgress(1000, progress, false);
						notificationManager.notify(startId, builder.build());
					});
				}
			}, 2000, 2000);
			download.join();
			failed = download.isFailed();
			if (failed) {
				Log.d(LOG_TAG, "download failed");
			} else {
				Log.d(LOG_TAG, "download finished normally");
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "download not created", e);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "download thread interrupted", e);
		} finally {
			statusTimer.cancel();
			final boolean finalFailed = failed;
			handler.post(() -> {
				if (finalFailed) {
					Toast.makeText(DownloadService.this, R.string.download_failed,
							Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(DownloadService.this, R.string.download_success,
							Toast.LENGTH_SHORT).show();
				}
				DownloadService.this.stopSelf();
			});
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
