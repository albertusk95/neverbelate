/**
 * 
 */
package com.madhackerdesigns.neverbelate.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.madhackerdesigns.neverbelate.R;
import com.madhackerdesigns.neverbelate.provider.AlertsContract;
import com.madhackerdesigns.neverbelate.service.NeverBeLateService;
import com.madhackerdesigns.neverbelate.service.ServiceCommander;
import com.madhackerdesigns.neverbelate.settings.NeverBeLateSettings;
import com.madhackerdesigns.neverbelate.util.AdHelper;

/**
 * @author flintinatux
 *
 */
public class TestActivity extends Activity implements ServiceCommander {

	private AdHelper mAdHelper;
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test_activity);
		
		// Load up a new AdHelper
		mAdHelper = new AdHelper(getApplicationContext());
		
		// Grab the test button and add some action
		Button testButton = (Button) findViewById(R.id.test_button);
		testButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// Tell NeverBeLateService to check for travel times
				Context context = getApplicationContext();
				Intent serviceIntent = new Intent(context, NeverBeLateService.class);
				serviceIntent.putExtra(EXTRA_SERVICE_COMMAND, CHECK_TRAVEL_TIMES);
//				NeverBeLateService.sendWakefulWork(context, serviceIntent);
				startService(serviceIntent);
			}
			
		});
		
		// Setup the Settings button
		Button settingsButton = (Button) findViewById(R.id.settings_button);
		settingsButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// Send user to the Settings menu
				Intent intent = new Intent(getApplicationContext(), NeverBeLateSettings.class);
	        	startActivity(intent);
			}
			
		});
		
		// Setup the Reset alert list button
		Button resetAlertListButton = (Button) findViewById(R.id.reset_alert_list_button);
		resetAlertListButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// Delete all AlertsProvider entries
				getContentResolver().delete(AlertsContract.Alerts.CONTENT_URI, null, null);
			}
			
		});
		
		// Setup the Reset AdHelper button
		Button resetAdHelperButton = (Button) findViewById(R.id.reset_ad_helper_button);
		resetAdHelperButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// Reset the AdHelper values
				mAdHelper.initFirstTimeUser();
			}
			
		});
		
		// Initialize the Pontiflex AdManager
//		IAdManager adManager = AdManagerFactory.createInstance(getApplication());
		
		
	}

}