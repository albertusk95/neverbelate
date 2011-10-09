/**
 * 
 */
package com.madhackerdesigns.neverlate;

import android.content.Context;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

/**
 * @author flintinatux
 *
 */
public class UserLocationOverlay extends MyLocationOverlay {

	private Context mContext;
	
	/**
	 * @param context
	 * @param mapView
	 */
	public UserLocationOverlay(Context context, MapView mapView) {
		// Constructor from super class
		super(context, mapView);
		mContext = context;
	}

	/* (non-Javadoc)
	 * @see com.google.android.maps.MyLocationOverlay#onTap(com.google.android.maps.GeoPoint, com.google.android.maps.MapView)
	 */
	@Override
	public boolean onTap(GeoPoint p, MapView map) {
		boolean tapped = super.onTap(p, map);
		// Toast that this is the user location
		if (tapped) {
			Toast.makeText(mContext, "This is your current location", Toast.LENGTH_SHORT).show();
		}
		return tapped;
	}

	
}
