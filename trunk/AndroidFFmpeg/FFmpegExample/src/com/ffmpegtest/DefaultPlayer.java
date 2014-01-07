package com.ffmpegtest;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.VideoView;

public class DefaultPlayer extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		this.getWindow().clearFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		this.getWindow().setBackgroundDrawable(null);

		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		this.setContentView(R.layout.default_player);

		Intent intent = getIntent();
		String url = intent.getStringExtra("url"); //if it's a string you stored.
		
		VideoView vv = (VideoView) findViewById(R.id.player_view);
		vv.setVideoPath(url);
//		vv.setVideoURI(Uri.parse(url));
		vv.start();

		vv.setOnCompletionListener(new OnCompletionListener() {
		    @Override
		    public void onCompletion(MediaPlayer mp) {
		           finish();
		  }
		});		

	}
	
}
