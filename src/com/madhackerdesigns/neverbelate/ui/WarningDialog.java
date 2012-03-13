/**
 * 
 */
package com.madhackerdesigns.neverbelate.ui;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.ads.AdRequest;
import com.google.ads.AdView;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.madhackerdesigns.neverbelate.R;
import com.madhackerdesigns.neverbelate.provider.AlertsContract;
import com.madhackerdesigns.neverbelate.provider.AlertsHelper;
import com.madhackerdesigns.neverbelate.service.NeverBeLateService;
import com.madhackerdesigns.neverbelate.service.ServiceCommander;
import com.madhackerdesigns.neverbelate.service.WakefulServiceReceiver;
import com.madhackerdesigns.neverbelate.settings.PreferenceHelper;
import com.madhackerdesigns.neverbelate.util.AdHelper;
import com.madhackerdesigns.neverbelate.util.Logger;
import com.pontiflex.mobile.webview.sdk.AdManagerFactory;
import com.pontiflex.mobile.webview.sdk.IAdManager;

/**
 * @author flintinatux
 *
 */
public class WarningDialog extends MapActivity implements ServiceCommander {

	// private static tokens
	private static final int ALERT_TOKEN = 1;
	private static final String LOG_TAG = "NeverBeLateWarning";
	private static final boolean ADMOB = true;
	
	// static strings for intent extra keys
	private static final String PACKAGE_NAME = "com.madhackerdesigns.neverbelate";
	public static final String EXTRA_URI = PACKAGE_NAME + ".uri";
		
	// fields to hold shared preferences and ad stuff
	private AdHelper mAdHelper;
	private IAdManager mAdManager;
	private boolean mAdJustShown = false;
	private PreferenceHelper mPrefs;
	
	// other fields
	private AlertQueryHandler mHandler;
	private ArrayList<EventHolder> mEventHolders = new ArrayList<EventHolder>();
	private boolean mInsistentStopped = false;
	private ViewSwitcher mSwitcher;
	private UserLocationOverlay mUserLocationOverlay;
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.warning_dialog);
		setTitle(R.string.advance_warning_title);
		Logger.d(LOG_TAG, "Title is set.");
		
		// Load the preferences and Pontiflex IAdManager
		Context applicationContext = getApplicationContext();
		mAdHelper = new AdHelper(applicationContext);
		mAdManager = AdManagerFactory.createInstance(getApplication());
		mPrefs = new PreferenceHelper(applicationContext);
		
		// Grab the view switcher, inflate and add the departure window and traffic views
		ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.view_switcher);
		View alertListView = View.inflate(this, R.layout.alert_list_view, null);
		View trafficView = View.inflate(this, R.layout.traffic_view_layout, null);
		switcher.addView(alertListView);
		switcher.addView(trafficView);
		mSwitcher = switcher;
		Logger.d(LOG_TAG, "ViewSwitcher loaded.");
		
		// Enable the user location to the map (early, to feed the location to the AdMob banner)
		MapView mapView = (MapView) findViewById(R.id.mapview);
		mUserLocationOverlay = new UserLocationOverlay(this, mapView);
		boolean providersEnabled = mUserLocationOverlay.enableMyLocation();
		if (providersEnabled) { 
			Logger.d(LOG_TAG, "User location updates enabled."); 
		}
		
		// Adjust the warning text to include early arrival if set
		final Long earlyArrival = mPrefs.getEarlyArrival() / 60000;
		if (!earlyArrival.equals(new Long(0))) {
			final TextView tv_warningText = (TextView) findViewById(R.id.warning_text);
			final Resources res = getResources();
			String warningText = res.getString(R.string.warning_text);
			String onTime = res.getString(R.string.on_time);
			String minutesEarly = res.getString(R.string.minutes_early);
			warningText = warningText.replaceFirst(onTime, earlyArrival + " " + minutesEarly);
			tv_warningText.setText(warningText);
		}
		
		// Load up the list of alerts
		loadAlertList();
		
		// Set the "View Traffic" button action
		Button trafficButton = (Button) findViewById(R.id.traffic_button);
		trafficButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// Switch to the traffic view and setup the MapView
				Logger.d(LOG_TAG, "'View Traffic' button clicked, switching to traffic view...");
				stopInsistentAlarm();
				loadTrafficView();
			}
			
		});
		Logger.d(LOG_TAG, "Traffic button added.");
		
		// Load up an AdMob banner
		if (ADMOB) {
			AdRequest request = new AdRequest();
			if (providersEnabled) { request.setLocation(mUserLocationOverlay.getLastFix()); }
			AdView adView = (AdView) findViewById(R.id.ad_view);
		    adView.loadAd(request);
		    Logger.d(LOG_TAG, "AdMob banner loaded.");
		}
		
		mUserLocationOverlay.disableMyLocation();
	}
	
	private void loadAlertList() {
		// Query the AlertProvider for alerts that are fired, but not dismissed
		ContentResolver cr = getContentResolver();
		if (mHandler == null) { mHandler = new AlertQueryHandler(cr); }
		Uri contentUri = AlertsContract.Alerts.CONTENT_URI;
		String[] projection = AlertsHelper.ALERT_PROJECTION;
		String selection = 
			AlertsContract.Alerts.FIRED + "=? AND " + 
			AlertsContract.Alerts.DISMISSED + "=?";
		String[] selectionArgs = new String[] { "1", "0" };
		mHandler.startQuery(ALERT_TOKEN, getApplicationContext(), 
				contentUri, projection, selection, selectionArgs, null);
		Logger.d(LOG_TAG, "AlertProvider queried for alerts.");
	}
	
	private class AlertQueryHandler extends AsyncQueryHandler {

		public AlertQueryHandler(ContentResolver cr) {
			super(cr);
		}

		/* (non-Javadoc)
		 * @see android.content.AsyncQueryHandler#onQueryComplete(int, java.lang.Object, android.database.Cursor)
		 */
		@Override
		protected void onQueryComplete(int token, Object context, Cursor cursor) {
			// Let the activity manage the cursor life-cycle
			startManagingCursor(cursor);
			Logger.d(LOG_TAG, "Query returned...");
			
			// Now fill in the content of the WarningDialog
			switch (token) {
			case ALERT_TOKEN:
				if (cursor.moveToFirst()) {
					// Store away the event information
					EventHolder eh = new EventHolder();
					eh.json = cursor.getString(AlertsHelper.PROJ_JSON);
					eh.title = cursor.getString(AlertsHelper.PROJ_TITLE);
					eh.location = cursor.getString(AlertsHelper.PROJ_LOCATION);
					mEventHolders.add(eh);
					
					// Calculate the departure window.  Note that first row in cursor should be
					// the first upcoming event instance, since it is sorted by begin time, ascending.
					long begin = cursor.getLong(AlertsHelper.PROJ_BEGIN);
					long duration = cursor.getLong(AlertsHelper.PROJ_DURATION);
					TextView departureWindow = (TextView) findViewById(R.id.departure_window);
					long departureTime = begin - duration;
					long now = new Date().getTime();
					Resources res = getResources();
					String unitMinutes = res.getString(R.string.unit_minutes);
					String departureString;
					if (departureTime > now) {
						departureString = "in " + (int)((departureTime - now) / 60000 + 1) 
							+ " " + unitMinutes + "!";
					} else {
						departureString = "NOW!";
					}
					departureWindow.setText(departureString);
					departureWindow.setOnClickListener(new OnClickListener() {

						public void onClick(View v) {
							// Stop insistent alarm
							stopInsistentAlarm();
						}
						
					});
					
					// Load the copyrights
					HashMap<String, String> hash = new HashMap<String, String>();
					String copyrightString = "";
					String copyrights;
					do {
						copyrights = cursor.getString(AlertsHelper.PROJ_COPYRIGHTS);
						if (hash.isEmpty()) {
							copyrightString += copyrights;
						} else if (! hash.containsKey(copyrights)) {
							hash.put(copyrights, copyrights);
							copyrightString += " | " + copyrights;	
						}
					} while (cursor.moveToNext());
					TextView copyrightText = (TextView) findViewById(R.id.copyright_text);
					copyrightText.setText(copyrightString);
					
					// Attach the cursor to the alert list view
					cursor.moveToFirst();
					EventListAdapter adapter = new EventListAdapter((Context) context, 
							R.layout.event_list_item, cursor, true);
					ListView lv = (ListView) findViewById(R.id.list_view);
					lv.setAdapter(adapter);
					lv.setOnItemClickListener(new OnItemClickListener() {

						public void onItemClick(AdapterView<?> arg0, View arg1,
								int arg2, long arg3) {
							// Stop insistent alarm
							stopInsistentAlarm();
						}
						
					});
				} else {
					// TODO: Do something else safe.  This really shouldn't happen, though.
				}
			}
			
			// Set the "Snooze" button label
			Resources res = getResources();
			Button snoozeButton = (Button) findViewById(R.id.snooze_button);
			int count = cursor.getCount();
			if (count > 1) {
				snoozeButton.setText(res.getString(R.string.snooze_all_button_text));
			} else {
				snoozeButton.setText(res.getString(R.string.snooze_button_text));
			}
			
			// Enable or disable snooze per user preference
			if (mPrefs.getEarlyArrival().equals(new Long(0))) {
				snoozeButton.setVisibility(View.GONE);
				Logger.d(LOG_TAG, "Snooze button disabled.");
			} else {
				snoozeButton.setOnClickListener(new OnClickListener() {
	
					public void onClick(View v) {
						// Snooze the alert, and finish the activity
						snoozeAlert();
						finish();
					}
					
				});
				Logger.d(LOG_TAG, "Snooze button added.");
			}
			
			
			// Set the "Dismiss" button action
			Button dismissButton = (Button) findViewById(R.id.dismiss_button);
			if (count > 1) {
				dismissButton.setText(res.getString(R.string.dismiss_all_button_text));
			} else {
				dismissButton.setText(res.getString(R.string.dismiss_button_text));
			}
			dismissButton.setOnClickListener(new OnClickListener() {

				/* (non-Javadoc)
				 * @see android.view.View.OnClickListener#onClick(android.view.View)
				 */
				public void onClick(View v) {
					// For now, to dismiss, cancel notification and finish
					dismissAlert();
					finish();
				}
				
			});
			Logger.d(LOG_TAG, "Dismiss button loaded.");
		}
	}
	
	/**
	 * 
	 */
	private void snoozeAlert() {
		// With respect to ads, consider a SNOOZE as a DISMISS
		mAdHelper.setWarningDismissed(true);
		
		// Set a new alarm to notify for this same event instance
		Logger.d(LOG_TAG, "'Snooze' button clicked.");
		long now = new Date().getTime();
		long warnTime = now + mPrefs.getSnoozeDuration();
		String warnTimeString = NeverBeLateService.FullDateTime(warnTime);
		Logger.d(LOG_TAG, "Alarm will be set to warn user again at " + warnTimeString);
		Context context = getApplicationContext();
		AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(context, WakefulServiceReceiver.class);
		intent.putExtra(EXTRA_SERVICE_COMMAND, NOTIFY);
		PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 
				PendingIntent.FLAG_UPDATE_CURRENT);
		alarm.set(AlarmManager.RTC_WAKEUP, warnTime, alarmIntent);
		
		// Ask the Service to just SNOOZE the event, which just clears the notification
		intent = new Intent(context, NeverBeLateService.class);
		intent.putExtra(EXTRA_SERVICE_COMMAND, SNOOZE);
		startService(intent);
	}
	
	/**
	 * 
	 */
	private void dismissAlert() {
		// Tell the AdHelper that this warning has been dismissed
		mAdHelper.setWarningDismissed(true);
		
		// Ask the Service to just DISMISS the alert, which marks the alert as DISMISSED. Duh.
		Context context = getApplicationContext();
		Intent cancelIntent = new Intent(context, NeverBeLateService.class);
		cancelIntent.putExtra(EXTRA_SERVICE_COMMAND, DISMISS);
		startService(cancelIntent);
	}
	
	private void stopInsistentAlarm() {
		Logger.d(LOG_TAG, "Stopping insistent alarm.");
		if (!mInsistentStopped) {
			mInsistentStopped = true;
			Context context = getApplicationContext();
			Intent i = new Intent(context, NeverBeLateService.class);
			i.putExtra(EXTRA_SERVICE_COMMAND, SILENCE);
			startService(i);
		}
	}

	private void switchToAlertListView() {
		mSwitcher.showPrevious();
		Logger.d(LOG_TAG, "Switched to alert list view.");
	}
	
	private void switchToTrafficView() {
		mSwitcher.showNext();
		Logger.d(LOG_TAG, "Switched to traffic view.");
	}
	
	/**
	 * 
	 */
	private void loadTrafficView() {
		// Log a little
		Logger.d(LOG_TAG, "Loading traffic view.");
		
		// Get mapview and add zoom controls
		MapView mapView = (MapView) findViewById(R.id.mapview);
		if (mapView != null) { Logger.d(LOG_TAG, "MapView loaded."); }
		mapView.setBuiltInZoomControls(true);		
		
		// Turn on the traffic (as early as possible)
		mapView.setTraffic(true);
		
		// Add the Back button action
		Button backButton = (Button) findViewById(R.id.back_button);
		backButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				switchToAlertListView(); 
				mUserLocationOverlay.disableMyLocation();
				mUserLocationOverlay.disableCompass();
			}
			
		});
	
		// Get the UserLocationOverlay to draw both flags and stay updated
		UserLocationOverlay overlay = mUserLocationOverlay;
		GeoPoint orig = null;
				
		// Parse the json directions data
		final Iterator<EventHolder> eventIterator = mEventHolders.iterator();
		do {
			try {
				// Get the next EventHolder
				EventHolder eh = eventIterator.next();
				
				// Get the zoom span and zoom center from the route
				JSONObject directions = new JSONObject(eh.json);
				JSONObject route = directions.getJSONArray("routes").getJSONObject(0);
				
				// If the origin is null, pull the origin coordinates
				JSONObject leg = route.getJSONArray("legs").getJSONObject(0);
				if (orig == null) {
					int latOrigE6 = (int) (1.0E6 * leg.getJSONObject("start_location").getDouble("lat"));
					int lonOrigE6 = (int) (1.0E6 * leg.getJSONObject("start_location").getDouble("lng"));
					orig = new GeoPoint(latOrigE6, lonOrigE6);
				}

				// Get the destination coordinates from the leg
				int latDestE6 = (int) (1.0E6 * leg.getJSONObject("end_location").getDouble("lat")); 
				int lonDestE6 = (int) (1.0E6 * leg.getJSONObject("end_location").getDouble("lng"));
				
				// Create a GeoPoint for the destination and push onto MapOverlay
				GeoPoint destPoint = new GeoPoint(latDestE6, lonDestE6);
				overlay.addDestination(destPoint, eh.title, eh.location);
			} catch (JSONException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		} while (eventIterator.hasNext());
		
		// Set the origin and draw the locations
		overlay.setOrigin(orig);
		overlay.drawLocations();
		overlay.enableMyLocation();
		overlay.enableCompass();
		
		// Load an interstitial ad if it's time
		AdHelper adHelper = mAdHelper;
		if (adHelper.isTimeToShowAd()) {
			adHelper.setAdShown(true);
			mAdJustShown = true;
			mAdManager.showAd();
		} else {
			// Otherwise, switch to the traffic view
			switchToTrafficView();
		}
		
	}
	
	
	/* (non-Javadoc)
	 * @see com.google.android.maps.MapActivity#onPause()
	 */
	@Override
	protected void onPause() {
		super.onPause();
		// Disable the user location
		if (mUserLocationOverlay != null) { 
			mUserLocationOverlay.disableMyLocation();
			mUserLocationOverlay.disableCompass();
		}
	}

	/* (non-Javadoc)
	 * @see com.google.android.maps.MapActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		// Re-enable the user location
		if (mUserLocationOverlay != null) { 
			mUserLocationOverlay.enableMyLocation();
			mUserLocationOverlay.enableCompass();
		}
		
		// Switch to the traffic view if an ad was just shown
		if (mAdJustShown) {
			switchToTrafficView();
			mAdJustShown = false;
		}
	}

	@Override
	protected boolean isRouteDisplayed() {
		// No route will be displayed, since that would cover up the traffic
		return false;
	}
	
	private class EventHolder {
		String json;
		String title;
		String location;
	}

}
