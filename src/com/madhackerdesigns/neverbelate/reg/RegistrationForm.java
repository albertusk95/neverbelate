/*
* Copyright (C) 2007 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.madhackerdesigns.neverbelate.reg;

import java.util.List;
import java.util.regex.Pattern;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.madhackerdesigns.neverbelate.R;
import com.madhackerdesigns.neverbelate.ui.LauncherActivity;
import com.madhackerdesigns.neverbelate.util.Logger;
import com.pontiflex.mobile.webview.sdk.AdManagerFactory;
import com.pontiflex.mobile.webview.sdk.IAdManager;

/**
 * @author flintinatux
 *
 */
public class RegistrationForm extends Activity {

	// Member fields for the Registration Form
	private Button mBtnCountry;
	private String mCountryCode;
	private Cursor mCountryCursor;
	private CountriesDB mDB;
	private Registration mRegistration;
	
	// Unique dialog id's
	private static final int DIALOG_COUNTRY = 0;
	private static final int DIALOG_DECLINE = 1;
    private static final int DIALOG_INCOMPLETE = 2;
	private static final int DIALOG_THANKS = 3;
	
	// Email regex pattern (borrowed from Android API 8+)
	public static final Pattern EMAIL_PATTERN
	        = Pattern.compile(
	            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
	            "\\@" +
	            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
	            "(" +
	            "\\." +
	            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
	            ")+"
	        );
    
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Initialize the Registration and countries data
		Context context = getApplicationContext();
		mRegistration = new Registration(context);
		mDB = new CountriesDB(context);
		
		// If already registered, send user on to LauncherActivity
		if (mRegistration.isRegistered()) {
			bypassRegistration();
			return;
		}
		
		// Initialize the activity
		super.onCreate(savedInstanceState);
		setContentView(R.layout.registration_form);
		setTitle(R.string.reg_form_title);
		
		// Pre-pop user's email address
		String userEmail = "";
		Account[] accounts = AccountManager.get(this).getAccounts();
		for (Account account : accounts) {
		    if (EMAIL_PATTERN.matcher(account.name).matches()) {
		        // Just go ahead and use the first match
		    	userEmail = account.name;
		    	break;
		    }
		}
		((TextView) findViewById(R.id.email)).setText(userEmail);
		
		// Set the behavior of the country "spinner"
		mBtnCountry = (Button) findViewById(R.id.btn_country);
		mBtnCountry.setOnClickListener(new OnClickListener() {

			@SuppressWarnings("deprecation")
			public void onClick(View v) {
				// Show country select dialog
				showDialog(DIALOG_COUNTRY);
			}
			
		});
		
		// Pre-pop the country code and zip code
		(new PrepopZipCodeTask()).execute();
		
		// Set the OnClick behavior of the decline text
		TextView declineText = (TextView) findViewById(R.id.decline_text);
		declineText.setOnClickListener(new OnClickListener() {

			@SuppressWarnings("deprecation")
			public void onClick(View v) {
				showDialog(DIALOG_DECLINE);
			}
			
		});
		
		// Set the OnClick behavior of the Register button
		Button regButton = (Button) findViewById(R.id.btn_register);
		regButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				registerUser();
			}
			
		});
		
	} 

	private void bypassRegistration() {
		Intent i = new Intent(getApplicationContext(), LauncherActivity.class);
		startActivity(i);
		RegistrationForm.this.finish();
	}

	@SuppressWarnings("deprecation")
	private void registerUser() {
		// Pull registration data from form
		String firstName = (String) ((TextView) findViewById(R.id.first_name)).getText().toString();
		String lastName = (String) ((TextView) findViewById(R.id.last_name)).getText().toString();
		String email = (String) ((TextView) findViewById(R.id.email)).getText().toString();
		String zipCode = (String) ((TextView) findViewById(R.id.zip_code)).getText().toString();
		
		// Remind user to complete form if he didn't.
		if ( 	isEmpty(firstName) || 
				isEmpty(lastName) ||
				isEmpty(email) ||
				isEmpty(mCountryCode) ||
				isEmpty(zipCode) ) {
			
			// Show reminder dialog and return.
			showDialog(DIALOG_INCOMPLETE);
			return;
			
		}
		
		// If registration validates, permanently store the data.
		Registration reg = mRegistration;
		reg.setFirstName(firstName);
		reg.setLastName(lastName);
		reg.setEmail(email);
		reg.setZipCode(zipCode);
		reg.setCountryCode(mCountryCode);
		reg.setRegistered(true);
		
		// Insert reg data into Pontiflex
		// Initialize the Pontiflex Ad Manager
		IAdManager adManager = AdManagerFactory.createInstance(getApplication());
		// set registration mode to ad hoc, user can skip registration
		adManager.setRegistrationRequired(false);
		adManager.setRegistrationMode(IAdManager.RegistrationMode.RegistrationAdHoc);
		// Write data into the Ad Manager registration storage
		adManager.setRegistrationData("first_name", firstName);
		adManager.setRegistrationData("last_name", lastName);
		adManager.setRegistrationData("email", email);
		adManager.setRegistrationData("postal_code", zipCode);
		
		// Then say thanks and pass them on to the main launcher screen
		showDialog(DIALOG_THANKS);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (id) {
		case DIALOG_COUNTRY:
			Context context = getApplicationContext();
			mCountryCursor = mDB.getCountries();
			builder.setTitle(R.string.country_prompt)
				.setAdapter(
					new CountryListAdapter(context, R.layout.country_list_item, mCountryCursor, false), 
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int which) {
							Cursor c = mCountryCursor;
							c.moveToPosition(which);
							String code = c.getString(CountriesDB.PROJ_CODE);
							setCountryCode(code);
							dialog.cancel();
						}
					
					});
			return builder.create();
	    case DIALOG_DECLINE:
	    	// Ask user if they really want to decline registration.
	    	builder.setTitle(R.string.dialog_decline_title)
	    		.setMessage(R.string.dialog_decline_message)
	    		.setPositiveButton(R.string.dialog_decline_btn_yes, new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						bypassRegistration();
						dialog.cancel();
					}
					
				})
	    		.setNegativeButton(R.string.dialog_decline_btn_no, new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
					
				});
	    	return builder.create();
	    case DIALOG_INCOMPLETE:
	    	// Remind user to complete the form to register.
	    	builder.setTitle(R.string.dialog_incomplete_title)
	    		.setMessage(R.string.dialog_incomplete_message)
	    		.setNeutralButton(R.string.dialog_incomplete_btn_ok, new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
					
				});
	    	return builder.create();
	    case DIALOG_THANKS:
	    	// Thank the user for registering and pass them on to the main activity
	    	builder.setTitle(R.string.dialog_thanks_title)
	    		.setCancelable(false)
	    		.setMessage(R.string.dialog_thanks_message)
	    		.setNeutralButton(R.string.dialog_thanks_btn_ok, new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						bypassRegistration();
						dialog.cancel();
					}
					
				});
	    	return builder.create();
	    }
	    return null;
	}
	
	private class PrepopZipCodeTask extends AsyncTask<Object, Object, GeoCodeResult> {

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected GeoCodeResult doInBackground(Object... params) {
			try {
				// Reverse geocode the user's location
				LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
				List<String> providers = lm.getAllProviders();
				Location loc = null;
				for (String provider : providers) {
					// Just roll through location providers until we get one
					loc = lm.getLastKnownLocation(provider);
					if (loc != null) { break; }
				}
				return (new GeoCoder()).reverseGeoCode(loc.getLatitude(), loc.getLongitude());
			} catch (Exception e) {
				e.printStackTrace();
				Logger.d("Prepop of country and zip codes failed.");
				return null;
			}
		}

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(GeoCodeResult result) {
			try {
				// Set country code if available
				setCountryCode(result.getCountryCode());
				
				// Set the zip code if needed and available
				((TextView) findViewById(R.id.zip_code)).setText(result.getZipCode());
			} catch (Exception e) {
				e.printStackTrace();
				Logger.d("Prepop of country and zip codes failed.");
			} 
		}
	}
	
	private void setCountryCode (String code) {
		// Find the country for that code
		mCountryCode = code;
		Cursor c = mDB.getCountryByCode(code);
		if (c.moveToFirst()) {
			// Set the country name as the button text
			mBtnCountry.setText(c.getString(CountriesDB.PROJ_NAME));
			
			TextView zipLabel = (TextView) findViewById(R.id.zip_code_label);
			TextView zipField = (TextView) findViewById(R.id.zip_code);
			if (c.getInt(CountriesDB.PROJ_ZIP) == 1) {
				// Change name of zip field if not in US
				if (! mCountryCode.equals("US")) {
					zipLabel.setText(R.string.postal_code);
				}
				zipLabel.setVisibility(View.VISIBLE);
				zipField.setVisibility(View.VISIBLE);
			} else {
				// Hide the zip field if not needed
				zipLabel.setVisibility(View.GONE);
				zipField.setVisibility(View.GONE);
			}
		}
		c.close();
	}
	
	private boolean isEmpty (String s) {
		return (s == null || s.length() == 0);
	}
	
	

}
