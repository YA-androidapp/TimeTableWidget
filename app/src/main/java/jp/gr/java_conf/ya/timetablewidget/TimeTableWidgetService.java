package jp.gr.java_conf.ya.timetablewidget; // Copyright (c) 2018 YA <ya.androidapp@gmail.com> All rights reserved.

import android.app.Notification;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static jp.gr.java_conf.ya.timetablewidget.OdptUtil.getDateString;

public class TimeTableWidgetService extends Service {
    private static final String PREF_TODAY = "PREF_TODAY";
    private static final String PREF_TODAY_IS_HOLIDAY = "PREF_TODAY_IS_HOLIDAY";
    private static final SimpleDateFormat sdFormatLoad = new SimpleDateFormat("yyyy-MM-dd");
    private static final String URL_HOLIDAYS = "http://www8.cao.go.jp/chosei/shukujitsu/syukujitsu_kyujitsu.csv";
    private Boolean requestingLocationUpdates;
    private SharedPreferences pref_app;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private SettingsClient settingsClient;

    public TimeTableWidgetService() {
    }

    private static boolean parseCsv(final Date t0day, final String csvString) {
        Boolean result = false;

        String t0dayString = getDateString(t0day);

        // 休日判定
        try {
            final InputStream inputStream = new ByteArrayInputStream(csvString.getBytes("UTF-8"));
            final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            final BufferedReader bufferReader = new BufferedReader(inputStreamReader);
            String line;
            int i = -1;
            while ((line = bufferReader.readLine()) != null) {
                i++;

                if (i == 0)
                    continue;

                final String[] lineArray = line.split(",");

                try {
                    final Date date = sdFormatLoad.parse(lineArray[0]);

                    java.sql.Date dateSql = new java.sql.Date(date.getTime());
                    if (dateSql.toString().equals(t0dayString)) {
                        result = true;
                        break;
                    }
                } catch (Exception e) {
                }
            }
            bufferReader.close();
        } catch (Exception e) {
        }

        // 週末判定
        Calendar cal = Calendar.getInstance();
        cal.setTime(t0day);

        int dow = cal.get(Calendar.DAY_OF_WEEK);
        if (dow == 1 || dow == 7)
            result = true;

        return result;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand()");
        super.onStartCommand(intent, flags, startId);

        startForeground(startId, new Notification());

        init(intent);

        return START_STICKY;
    }

    private String getPref(String key) {
        if (pref_app == null)
            pref_app = PreferenceManager.getDefaultSharedPreferences(this);

        String value;
        try {
            value = pref_app.getString(key, "");
        } catch (Exception e) {
            value = "";
        }
        return value;
    }

    private void setPref(String key, String value) {
        if (pref_app == null)
            pref_app = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            SharedPreferences.Editor editor = pref_app.edit();
            editor.putString(key, value);
            editor.apply();
        } catch (Exception e) {
        }
    }

    private void init(Intent intent) {
        BroadcastReceiver roadcastReceiver = new OnClickReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TimeTableWidget.ON_CLICK);
        registerReceiver(roadcastReceiver, filter);

        int appWidgetId = -1;
        try {
            appWidgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
        } catch (Exception e) {
        }
        Log.d("TTW", "appWidgetId:" + appWidgetId);

        // 休日判定
        setHoliday();

        // Temp
        Map<String, String> querysAcquireStation = new HashMap<String, String>();
        querysAcquireStation.put("odpt:station", "odpt.Station:TokyoMetro.Chiyoda.Otemachi");
        (new OdptUtil()).acquireStationTimetable(querysAcquireStation);

        /*

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
        startLocationUpdates();
        */
    }

    @Override
    public void onDestroy() {
        log("onDestroy()");

        try {
            stopLocationUpdates();
        } catch (Exception e) {
        }

        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStart(Intent intent, int startId) {
        log("onStart()");
        super.onStart(intent, startId);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                Location location = locationResult.getLastLocation();
                // location.getLatitude() location.getLongitude() location.getAccuracy()
                // location.getAltitude() location.getSpeed() location.getBearing()
                log("location getLatitude: " + location.getLatitude());
                log("location getLongitude: " + location.getLongitude());

                // Temp
                /*
                String lat = Double.toString(location.getLatitude());
                String lon = Double.toString(location.getLongitude());

                Map<String, String> queryAcquirePlaces = new HashMap<String, String>();
                queryAcquirePlaces.put("rdf:type", "odpt:Station");
                queryAcquirePlaces.put("lon", lon);
                queryAcquirePlaces.put("lat", lat);
                queryAcquirePlaces.put("radius", "1000");
                (new OdptUtil()).acquirePlaces(queryAcquirePlaces);

                Map<String, String> querysAcquireStation = new HashMap<String, String>();
                querysAcquireStation.put("owl:sameAs", "odpt.Station:TokyoMetro.Chiyoda.Otemachi");
                (new OdptUtil()).acquireStationTimetable(querysAcquireStation);
                */
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void log(String str) {
        Log.v("TTWS", str);
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setFastestInterval(30 * 1000);
        locationRequest.setInterval(60 * 1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//        LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
//        PRIORITY_LOW_POWER
//        PRIORITY_NO_POWER
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();

        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    private void startLocationUpdates() {
        try {
            settingsClient.checkLocationSettings(locationSettingsRequest);
            fusedLocationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.myLooper());
            requestingLocationUpdates = true;
        } catch (SecurityException e) {
        }
    }

    private void stopLocationUpdates() {
        if (!requestingLocationUpdates)
            return;

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            requestingLocationUpdates = false;
        } catch (Exception e) {
        }
    }

    public Boolean isHoliday() {
        String checkedDay = getPref(PREF_TODAY);
        String now = getDateString(new Date());
        if (checkedDay.equals(now)) {
            return Boolean.parseBoolean(getPref(PREF_TODAY_IS_HOLIDAY));
        } else {
            return false;
        }

    }

    public void setHoliday() {
        try {
            final URL url = new URL(URL_HOLIDAYS);
            AsyncDlTask aAsyncDlTask = new AsyncDlTask(new AsyncDlTask.AsyncCallback() {

                public void onPreExecute() {
                }

                public void onProgressUpdate(int progress) {
                }

                public void onCancelled() {
                }

                public void onPostExecute(String[] result) {
                    Date t0day = OdptUtil.getT0day().getTime();
                    boolean isHoliday = parseCsv(t0day, result[0]);

                    setPref(PREF_TODAY, getDateString(t0day));
                    setPref(PREF_TODAY_IS_HOLIDAY, Boolean.toString(isHoliday));
                }
            });
            aAsyncDlTask.execute(url);
        } catch (Exception e) {
        }
    }

    public class OnClickReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if ("jp.co.casareal.oreobroadcastsample.ORIGINAL".equals(intent.getAction())) {
                String massage = intent.getStringExtra("message");
                Toast.makeText(context, massage, Toast.LENGTH_LONG).show();
            }
        }
    }

}