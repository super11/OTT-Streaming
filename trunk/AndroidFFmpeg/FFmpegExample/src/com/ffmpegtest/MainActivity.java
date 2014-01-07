package com.ffmpegtest;


import java.net.InetAddress;
import java.net.UnknownHostException;

import com.ffmpeg.rtplay.RTSPProxy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.CursorAdapter;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;


public class MainActivity extends Activity {

	private ListView mListView;
	private CursorAdapter mAdapter;
	private RTSPProxy proxy;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.open_video);
		
		Button btn_ok = (Button) findViewById(R.id.btn_ok);
		btn_ok.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v){
				TextView address = (TextView) findViewById(R.id.txt_address);
				LoadVideo(address.getText().toString());
			}
		});
		
		Button btn_player = (Button) findViewById(R.id.btn_player);
		btn_player.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				TextView address = (TextView) findViewById(R.id.txt_address);
				LoadDefaultPlayer(address.getText().toString());
			}
		});
	}


	private void LoadVideo(String url){
//		this.RTSPmyRTP(url, 5004);
		Intent intent = new Intent(AppConstants.VIDEO_PLAY_ACTION);
		String rtsp = "rtsp://localhost:16734/video.mp4";
//		intent.putExtra(AppConstants.VIDEO_PLAY_ACTION_EXTRA_URL, rtsp);
		intent.putExtra(AppConstants.VIDEO_PLAY_ACTION_EXTRA_URL, url);
		startActivity(intent);
	}
	
	public void LoadDefaultPlayer(String url){
		this.RTSPmyRTP(url, 5004);
		Intent myIntent = new Intent(MainActivity.this, DefaultPlayer.class);
		String rtsp = "rtsp://localhost:16734/video.mp4";
		myIntent.putExtra("url", rtsp);
		MainActivity.this.startActivity(myIntent);
	}

	
	private void RTSPmyRTP(String url, Integer port){
		proxy = new RTSPProxy();
        try {
            InetAddress addr = InetAddress.getByName(url);
            proxy.start(this, R.string.videoProfile_01, 16734, addr, port);

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_activity, menu);
		return true;
	}

	/*
	@Override
	public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
		Cursor cursor = (Cursor) mAdapter.getItem(position);
		String url = cursor.getString(MainAdapter.PROJECTION_URL);
		Intent intent = new Intent(AppConstants.VIDEO_PLAY_ACTION);
		intent.putExtra(AppConstants.VIDEO_PLAY_ACTION_EXTRA_URL, url);
		String encryptionKey = cursor.getString(MainAdapter.PROJECTION_ENCRYPTION_KEY);
		if (encryptionKey != null) {
			intent.putExtra(AppConstants.VIDEO_PLAY_ACTION_EXTRA_ENCRYPTION_KEY, encryptionKey);
		}
		startActivity(intent);
	}
	*/

}
