package com.example.quarantine_app;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    LocationListener listener;
    LocationManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        manager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        createListener();
    }

    private void createListener() {
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                addMarker(location.getLatitude(), location.getLongitude());
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if(mMap != null) {
            mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                @Override
                public View getInfoWindow(Marker marker) {
                    return null;
                }

                @Override
                public View getInfoContents(Marker marker) {
                    @SuppressLint("InflateParams") View v = getLayoutInflater().inflate(R.layout.info_window,null);

                    final TextView info = v.findViewById(R.id.info);
                    info.setText(marker.getTitle());

                    mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                        @Override
                        public void onInfoWindowClick(Marker marker) {

                        }
                    });

                    mMap.setOnInfoWindowLongClickListener(new GoogleMap.OnInfoWindowLongClickListener() {
                        @Override
                        public void onInfoWindowLongClick(Marker marker) {
                            marker.remove();
                        }
                    });
                    return v;
                }
            });


            mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                @Override
                public void onMapLongClick(LatLng latLng) {
                    addMarker(latLng.latitude, latLng.longitude);
                }
            });
        }
    }

    private void addMarker(double lat, double lon) {
        try {
            //LONG CLICK ON MAP
            String get_country = new JsonTask().execute("https://geocode.xyz/"+lat+","+lon+"?geoit=json").get();
            Map<String, String> country = readJson(get_country, null);

            String get_stats = new JsonTask().execute("https://api.covid19api.com/summary").get();

            if(country != null) {
                Map<String, String> stats = readJson(get_stats, country.get("prov"));

                if(stats != null) {
                    LatLng marker = new LatLng(lat, lon);
                    mMap.addMarker(new MarkerOptions().position(marker).title(stats.get("Country")
                            +"\nConfirmed: "+stats.get("TotalConfirmed")
                            +"\nDeaths: "+stats.get("TotalDeaths")
                            +"\nRecovered: "+stats.get("TotalRecovered")));


                    System.out.println("\n"+country.get("prov"));
                    System.out.println("\n"+stats.get("Country"));
                    System.out.println("\nConfirmed: "+stats.get("TotalConfirmed"));
                    System.out.println("\nDeaths: "+stats.get("TotalDeaths"));
                    System.out.println("\nRecovered: "+stats.get("TotalRecovered"));
                }
            } else {
                Toast.makeText(getApplicationContext(),"No data sry \n:/", Toast.LENGTH_SHORT).show();
            }
        } catch (ExecutionException | InterruptedException e) {
            System.out.println("Error");
            e.printStackTrace();
        }
    }


    @SuppressLint("StaticFieldLeak")
    private static class JsonTask extends AsyncTask<String, String, String> {
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected String doInBackground(String... params) {

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuilder buffer = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                }

                return buffer.toString();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result_json) {

        }
    }

    public Map<String, String> readJson(String jsonString, @Nullable String country) {
        if(jsonString != null && !jsonString.isEmpty()) {
            try {
                Map<String,String> hashMap = new HashMap<>();

                JSONObject mainObject = new JSONObject(jsonString);
                if(country == null) {
                    //UK to GB fix
                    if(mainObject.getString("prov").equals("UK"))
                        hashMap.put("prov","GB");
                    else
                        hashMap.put("prov",mainObject.getString("prov"));
                    return hashMap;

                } else {
                    JSONArray mainArray = mainObject.getJSONArray("Countries");
                    for (int i=0;i<mainArray.length();i++) {
                        JSONObject key = mainArray.getJSONObject(i);
                        if(key.getString("CountryCode").equals(country.toUpperCase())) {
                            hashMap.put("Country", key.getString("Country"));
                            hashMap.put("TotalConfirmed", Integer.toString((key.getInt("TotalConfirmed"))));
                            hashMap.put("TotalDeaths", Integer.toString((key.getInt("TotalDeaths"))));
                            hashMap.put("TotalRecovered", Integer.toString((key.getInt("TotalRecovered"))));
                            return hashMap;
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}