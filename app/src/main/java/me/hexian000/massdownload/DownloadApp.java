package me.hexian000.massdownload;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DownloadApp extends Application {
	public static final String LOG_TAG = "MassDownload";
	public final static String CHANNEL_DOWNLOAD_STATE = "download_state";
	private static final int PERMISSIONS_REQUEST_CODE = 0;

	static void createNotificationChannels(NotificationManager manager, Resources res) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_DOWNLOAD_STATE,
					res.getString(R.string.channel_download_state),
					NotificationManager.IMPORTANCE_DEFAULT);
			channel.enableLights(false);
			channel.enableVibration(false);
			channel.setSound(null, null);

			manager.createNotificationChannel(channel);
		}
	}

	public static String sizeToString(double size) {
		if (size < 2.0 * 1024.0) { // Byte
			return String.format(Locale.getDefault(), "%.0fB", size);
		} else if (size < 2.0 * 1024.0 * 1024.0) { // KB
			return String.format(Locale.getDefault(), "%.2fKB", size / 1024.0);
		} else if (size < 2.0 * 1024.0 * 1024.0 * 1024.0) { // MB
			return String.format(Locale.getDefault(), "%.2fMB", size / 1024.0 / 1024.0);
		} else { // GB
			return String.format(Locale.getDefault(), "%.2fGB", size / 1024.0 / 1024.0 / 1024.0);
		}
	}

	public static void grantPermissions(Activity activity) {
		List<String> permissions = new ArrayList<>();
		if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
		}
		if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}
		if (permissions.size() > 0) {
			activity.requestPermissions(permissions.toArray(new String[]{}),
					PERMISSIONS_REQUEST_CODE);
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}
}
