package com.shahu.weathery;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.shahu.weathery.adapter.LocationRecyclerViewAdapter;
import com.shahu.weathery.common.LocationSharedPreferences;
import com.shahu.weathery.common.OfflineDataSharedPreference;
import com.shahu.weathery.common.VolleyRequest;
import com.shahu.weathery.customui.CustomSearchDialog;
import com.shahu.weathery.customui.TextHolderSubstanceCaps;
import com.shahu.weathery.helper.Locator;
import com.shahu.weathery.helper.RecyclerViewItemHelper;
import com.shahu.weathery.helper.ValuesConverter;
import com.shahu.weathery.interface2.IRecyclerViewListener;
import com.shahu.weathery.interface2.IVolleyResponse;
import com.shahu.weathery.interface2.OnDragListener;
import com.shahu.weathery.interface2.OnSearchItemSelection;
import com.shahu.weathery.interface2.OnSwipeListener;
import com.shahu.weathery.model.CardModel;
import com.shahu.weathery.model.CitySearchItem;
import com.shahu.weathery.model.common.MainResponse;

import net.danlew.android.joda.JodaTimeAndroid;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ir.drax.netwatch.NetWatch;
import ir.drax.netwatch.cb.NetworkChangeReceiver_navigator;

import static com.shahu.weathery.common.Constants.CITIES_DATA_FOR_SEARCH_LIST;
import static com.shahu.weathery.common.Constants.CURRENT_LOCATION_HTTP_REQUEST;
import static com.shahu.weathery.common.Constants.WEATHER_BY_ID_HTTP_REQUEST;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static boolean mIsInternetAvailable = false;
    IRecyclerViewListener recyclerViewListener;
    private LocationSharedPreferences mLocationSharedPreferences;
    private OfflineDataSharedPreference mOfflineDataSharedPreference;
    private TextHolderSubstanceCaps mDate, mTime;
    private TextView mCityName;
    private RecyclerView mRecyclerViewLocations;
    private ImageView mAddNewButton;
    private ArrayList<CardModel> mCardModelArrayList = new ArrayList<>();
    private VolleyRequest mVolleyRequest;
    private IVolleyResponse mIVolleyResponseCallback = null;
    private LocationRecyclerViewAdapter mLocationRecyclerViewAdapter;
    private SwipeRefreshLayout pullToRefreshLayout;
    private String CURRENTLOCATIONCITYID = null;
    private String CURRENTLOCATIONDEFAULTCITYID = "001";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialization();
        setDateTime();
    }

    /**
     * initialize the variables.
     */
    private void initialization() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.getDefaultNightMode());
        JodaTimeAndroid.init(this);
        TedPermission.with(this)
                .setPermissionListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted() {
                        if (!setCurrentCoordinates()) {
                            fetchAllData(mLocationSharedPreferences.getAllLocations());
                        }
                    }

                    @Override
                    public void onPermissionDenied(List<String> deniedPermissions) {
                        fetchAllData(mLocationSharedPreferences.getAllLocations());
                    }
                })
                .setPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.INTERNET,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .check();

        NetWatch.builder(this)
                .setCallBack(new NetworkChangeReceiver_navigator() {
                    @Override
                    public void onConnected(int source) {
                        Log.d(TAG, "onConnected: ");
                        mOfflineDataSharedPreference.setInternetStatus(true);
                        pullToRefreshLayout.setEnabled(true);
                        setDisconnectStatusBar(false);
                        if (!setCurrentCoordinates()) {
                            fetchAllData(mLocationSharedPreferences.getAllLocations());
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        Log.d(TAG, "onDisconnected: ");
                        mOfflineDataSharedPreference.setInternetStatus(false);
                        pullToRefreshLayout.setEnabled(false);
                        setDisconnectStatusBar(true);
                    }
                })
                .setNotificationEnabled(false)
                .build();

        mCityName = findViewById(R.id.main_city_name);
        initSharedPref();
        initVolleyCallback();
        mVolleyRequest = new VolleyRequest(this, mIVolleyResponseCallback);

        mAddNewButton = findViewById(R.id.add_new_loc_btn);

        mAddNewButton.setVisibility(View.VISIBLE);
        mAddNewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchForNewLocation();
            }
        });
        if (!setCurrentCoordinates()) {
            fetchAllData(mLocationSharedPreferences.getAllLocations());
        }
        initRecyclerView();
        initPullToRefresh();
        if (!mIsInternetAvailable) {
            mCardModelArrayList.clear();
            mLocationRecyclerViewAdapter.clear();
            ArrayList<CardModel> cardModels = mOfflineDataSharedPreference.getOfflineData();
            if (cardModels.size() > 0) {
                mCardModelArrayList.addAll(cardModels);
            }
        }
    }

    private void setDisconnectStatusBar(boolean status) {
        final TextView networkStatus = findViewById(R.id.network_status);
        if (!status) {
            networkStatus.setVisibility(View.VISIBLE);
        } else {
            networkStatus.setVisibility(View.INVISIBLE);
        }
    }

    private void initPullToRefresh() {
        pullToRefreshLayout = findViewById(R.id.pullToRefresh);
        pullToRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!setCurrentCoordinates()) {
                    fetchAllData(mLocationSharedPreferences.getAllLocations());
                }
                pullToRefreshLayout.setRefreshing(true);
            }
        });
    }

    /**
     * Search engine from cursor.
     */
    private void searchForNewLocation() {
        CustomSearchDialog customSearchDialog = new CustomSearchDialog(this, this, new ArrayList<CitySearchItem>());
        customSearchDialog.show();
        customSearchDialog.setOnItemSelected(new OnSearchItemSelection() {
            @Override
            public void onClick(String cityId) {
                if (mLocationSharedPreferences.addNewLocation(cityId))
                    mVolleyRequest.getWeatherByCityId(cityId, WEATHER_BY_ID_HTTP_REQUEST);
                else
                    Toast.makeText(MainActivity.this, "Already Exist", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Method to initialize the sharedPreference.
     */
    private void initSharedPref() {
        mLocationSharedPreferences = new LocationSharedPreferences(this);
        mOfflineDataSharedPreference = new OfflineDataSharedPreference(this);
    }

    /**
     * Method to set the weather of other favourites location.
     *
     * @param jsonObject data
     */
    private void addFavouriteCityWeather(JsonObject jsonObject) {
        Gson gson = new Gson();
        MainResponse mainResponse = gson.fromJson(jsonObject.toString(), MainResponse.class);
        CardModel cardModel = new CardModel();
        String cityName = mainResponse.getName();
        if (cityName.length() > 16) {
            cityName = cityName.substring(0, 16) + "...";
        }
        cardModel.setName(cityName);
        cardModel.setCountryCode(mainResponse.getSys().getCountry());
        cardModel.setPosition(Integer.parseInt(mLocationSharedPreferences.getPositionByCityId(String.valueOf(mainResponse.getId()))));
        cardModel.setTemperature(String.valueOf(mainResponse.getMain().getTemp()));
        cardModel.setTime(mainResponse.getDt());
        cardModel.setSecondsShift(mainResponse.getTimezone());
        cardModel.setWeatherItem(mainResponse.getWeather().get(0));
        cardModel.setDescription(mainResponse.getWeather().get(0).getDescription().toUpperCase());
        cardModel.setCityId(String.valueOf(mainResponse.getId()));
        cardModel.setDayNight(ValuesConverter.getDayNight(mainResponse));
        for (Iterator<CardModel> iterator = mCardModelArrayList.iterator(); iterator.hasNext(); ) {
            if (iterator.next().getCityId().equals(cardModel.getCityId())) {
                iterator.remove();
            }
        }
        mCardModelArrayList.add(cardModel);
        Collections.sort(mCardModelArrayList, new Comparator<CardModel>() {
            @Override
            public int compare(CardModel o1, CardModel o2) {
                return o1.getPosition() - o2.getPosition();
            }
        });
        mLocationRecyclerViewAdapter.notifyDataSetChanged();
    }

    /**
     * Method to add current location plus city name to header.
     *
     * @param jsonObject data
     */
    private void addCurrentLocationData(JsonObject jsonObject) {
        Gson gson = new Gson();
        MainResponse mainResponse = gson.fromJson(jsonObject.toString(), MainResponse.class);
        CardModel cardModel = new CardModel();
        cardModel.setName("Current Location");
        cardModel.setCountryCode(mainResponse.getSys().getCountry());
        cardModel.setPosition(0);
        cardModel.setCityId(CURRENTLOCATIONDEFAULTCITYID);
        CURRENTLOCATIONCITYID = String.valueOf(mainResponse.getId());
        cardModel.setTemperature(String.valueOf(mainResponse.getMain().getTemp()));
        cardModel.setWeatherItem(mainResponse.getWeather().get(0));
        cardModel.setDayNight(ValuesConverter.getDayNight(mainResponse));
        cardModel.setDescription(mainResponse.getWeather().get(0).getDescription().toUpperCase());
        for (Iterator<CardModel> iterator = mCardModelArrayList.iterator(); iterator.hasNext(); ) {
            if (iterator.next().getCityId().equals(cardModel.getCityId())) {
                iterator.remove();
            }
        }
        mCardModelArrayList.add(cardModel);
        mLocationRecyclerViewAdapter.notifyDataSetChanged();
        mCityName.setText(String.format("%s, %s", mainResponse.getName(), mainResponse.getSys().getCountry()));
    }

    /**
     * Method to fetch all stored location.
     *
     * @param allLocations contain all saved location in sharedPreference.
     */
    private void fetchAllData(Map<String, ?> allLocations) {
        Log.d(TAG, "fetchAllData: " + allLocations);
        for (Map.Entry<String, ?> entry : allLocations.entrySet()) {
            mVolleyRequest.getWeatherByCityId(entry.getValue().toString(), WEATHER_BY_ID_HTTP_REQUEST);
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                pullToRefreshLayout.setRefreshing(false);
            }
        }, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCardModelArrayList.size() > 0) {
            mOfflineDataSharedPreference.storeData(mCardModelArrayList);
        }
        mIsInternetAvailable = mOfflineDataSharedPreference.getInternetStatus();
        Log.d(TAG, "onPause: " + mIsInternetAvailable);
        if (mIsInternetAvailable) {
            modifyFeatures(true);
        } else {
            modifyFeatures(false);
            mCardModelArrayList.clear();
            mLocationRecyclerViewAdapter.clear();
            ArrayList<CardModel> cardModels = mOfflineDataSharedPreference.getOfflineData();
            if (cardModels.size() > 0) {
                mCardModelArrayList.addAll(cardModels);
            }
        }
        Log.d(TAG, "onPause: " + Arrays.toString(mCardModelArrayList.toArray()));

        NetWatch.unregister(this);
    }

    private void modifyFeatures(boolean value) {
        mOfflineDataSharedPreference.setInternetStatus(value);
        pullToRefreshLayout.setEnabled(value);
        disableAddButton(value);
        setDisconnectStatusBar(value);
    }

    private void disableAddButton(boolean value) {
        if (value) {
            mAddNewButton.setVisibility(View.VISIBLE);
        } else {
            mAddNewButton.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        NetWatch.builder(this)
                .setCallBack(new NetworkChangeReceiver_navigator() {
                    @Override
                    public void onConnected(int source) {
                        Log.d(TAG, "onConnected: ");
                        modifyFeatures(true);
                        if (!setCurrentCoordinates()) {
                            fetchAllData(mLocationSharedPreferences.getAllLocations());
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        Log.d(TAG, "onDisconnected: ");
                        modifyFeatures(false);
                    }
                })
                .setNotificationEnabled(false)
                .build();
    }

    /**
     * Method to set date/time continuously.
     */
    private void setDateTime() {
        mDate = findViewById(R.id.main_date);
        mTime = findViewById(R.id.main_time);
        final Handler someHandler = new Handler(getMainLooper());
        someHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Calendar calendar = Calendar.getInstance();
                final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMM", Locale.ENGLISH);
                final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm aaa", Locale.ENGLISH);
                mDate.setText(dateFormat.format(calendar.getTime()));
                mTime.setText(timeFormat.format(calendar.getTime()));
                someHandler.postDelayed(this, 10000);
            }
        }, 10);
    }

    /**
     * Method to set current location coordinates.
     */
    private boolean setCurrentCoordinates() {
        final boolean[] retValue = {false};
        //TODO: check for image here
        Locator locationHelper = new Locator(this);
        locationHelper.getLocation(Locator.Method.NETWORK_THEN_GPS, new Locator.Listener() {
            @Override
            public void onLocationFound(Location location) {
                retValue[0] = true;
                mVolleyRequest.getWeatherByCoords(CURRENT_LOCATION_HTTP_REQUEST, location.getLongitude(), location.getLatitude());
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onLocationNotFound() {
                mCityName.setText("Location not found!");
                fetchAllData(mLocationSharedPreferences.getAllLocations());
                Log.e(TAG, "onLocationNotFound: ");
            }
        });
        return retValue[0];
    }

    /**
     * Method for initializing the volleycalls.
     */
    private void initVolleyCallback() {
        mIVolleyResponseCallback = new IVolleyResponse() {
            @Override
            public void onSuccessResponse(JsonObject jsonObject, String requestType) {
                switch (requestType) {
                    case CURRENT_LOCATION_HTTP_REQUEST:
                        addCurrentLocationData(jsonObject);
                        fetchAllData(mLocationSharedPreferences.getAllLocations());
                        break;
                    case WEATHER_BY_ID_HTTP_REQUEST:
                        addFavouriteCityWeather(jsonObject);
                        break;
                }
            }

            @Override
            public void onRequestFailure(VolleyError volleyError, String requestType) {
                switch (requestType) {
                    case WEATHER_BY_ID_HTTP_REQUEST:
                        break;
                }
            }

            @Override
            public void onSuccessJsonArrayResponse(JSONArray jsonObject, String requestType) {
                switch (requestType) {
                    case CITIES_DATA_FOR_SEARCH_LIST:
                        Log.d(TAG, "onSuccessJsonArrayResponse: " + jsonObject.toString());
                        break;
                }
            }
        };
    }

    /**
     * initialize the recycler view.
     */
    private void initRecyclerView() {
        mRecyclerViewLocations = findViewById(R.id.locations);
        recyclerViewListener = new IRecyclerViewListener() {
            @Override
            public void onSingleShortClickListener(String cityId, long time, String dayNight, String temperature, String description, String imageUrl, String cityName) {
                openDetailedView(cityId, time, dayNight, temperature, description, imageUrl, cityName);
            }
        };
        mLocationRecyclerViewAdapter = new LocationRecyclerViewAdapter(mCardModelArrayList, this, recyclerViewListener);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerViewLocations.setLayoutManager(layoutManager);
        mRecyclerViewLocations.setAdapter(mLocationRecyclerViewAdapter);

        RecyclerViewItemHelper<CardModel> touchHelper = new RecyclerViewItemHelper<>(mCardModelArrayList,
                (RecyclerView.Adapter) mLocationRecyclerViewAdapter);
        touchHelper.setRecyclerItemDragEnabled(true).setOnDragItemListener(new OnDragListener() {
            @Override
            public void onDragItemListener(int fromPosition, int toPosition) {
                Log.d(TAG, "onDragItemListener: from: " + fromPosition + ", to: " + toPosition);
                mLocationSharedPreferences.updatePosition(fromPosition, toPosition);
            }
        });
        touchHelper.setRecyclerItemSwipeEnabled(true).setOnSwipeItemListener(new OnSwipeListener() {
            @Override
            public void onSwipeItemListener(RecyclerView.ViewHolder oldPosition) {
                LocationRecyclerViewAdapter.MyViewHolder myViewHolder = (LocationRecyclerViewAdapter.MyViewHolder) oldPosition;
                if (myViewHolder != null) {
                    Log.d(TAG, "onSwipeItemListener: remove location: " + mLocationSharedPreferences.removeLocation(myViewHolder.cityId));
                }
            }
        });
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(touchHelper);
        itemTouchHelper.attachToRecyclerView(mRecyclerViewLocations);
    }

    /**
     * gets called when click on list item to show detailed view.
     *
     * @param cityId      city identifier
     * @param time
     * @param dayNight
     * @param temperature
     * @param description
     * @param imageUrl
     * @param cityName
     */
    private void openDetailedView(String cityId, long time, String dayNight, String temperature, String description, String imageUrl, String cityName) {
        Intent intent = new Intent(this, WeatherDetail.class);
        if (cityId.equals(CURRENTLOCATIONDEFAULTCITYID)) {
            cityId = CURRENTLOCATIONCITYID;
        }
        intent.putExtra("id", cityId);
        intent.putExtra("time", time);
        intent.putExtra("day", dayNight);
        intent.putExtra("temperature", temperature);
        intent.putExtra("desc", description);
        intent.putExtra("internetStatus", mIsInternetAvailable);
        intent.putExtra("image", imageUrl);
        intent.putExtra("cityName",cityName);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
