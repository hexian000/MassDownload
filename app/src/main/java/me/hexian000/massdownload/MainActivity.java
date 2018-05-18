package me.hexian000.massdownload;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {

	@Override
	protected void onResume() {
		super.onResume();

		Uri uri = getIntent().getData();
		if (uri != null) {
			String scheme = uri.getScheme();
			if ("http".equals(scheme) || "https".equals(scheme)) {
				Intent intent = new Intent(this, DownloadService.class);
				intent.setData(uri);
				startForegroundServiceCompat(intent);
				getIntent().setData(null);
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		DownloadApp.grantPermissions(this);
	}

	private void startForegroundServiceCompat(Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}
}
