package com.jlv.amap;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.AMapGestureListener;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Poi;
import com.amap.api.navi.AmapNaviPage;
import com.amap.api.navi.AmapNaviParams;
import com.amap.api.navi.AmapNaviType;
import com.amap.api.navi.INaviInfoCallback;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.jlv.amap.view.GPSView;

import java.text.DecimalFormat;

public class MapActivity extends CheckPermissionsActivity implements INaviInfoCallback,
        AMap.OnMyLocationChangeListener, AMap.OnPOIClickListener,
        AMap.OnMarkerClickListener, GeocodeSearch.OnGeocodeSearchListener,
        GPSView.OnGPSViewClickListener, LocationSource, AMapLocationListener, AMapGestureListener {
    private static final String TAG = "MapActivity";
    private int mCurrentGpsState = STATE_UNLOCKED;//当前定位状态
    private static final int STATE_UNLOCKED = 0;//未定位状态，默认状态
    private static final int STATE_LOCKED = 1;//定位状态
    private static final int STATE_ROTATE = 2;//根据地图方向旋转状态
    private int mZoomLevel = 16;//地图缩放级别，最大缩放级别为20
    private static long mAnimDuartion = 500L;//地图动效时长
    private int mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;//地图状态类型
    private static final int STROKE_COLOR = Color.argb(240, 3, 145, 255);
    private static final int FILL_COLOR = Color.argb(10, 0, 0, 180);

    private AMap aMap;
    private TextureMapView textureMapView;
    private UiSettings mUiSettings;
    private MyLocationStyle myLocationStyle;
    private OnLocationChangedListener mLocationListener;
    private AMapLocationClient mLocationClient;
    private AMapLocationClientOption mLocationOption;
    private AMapLocation mAMapLocation;
    private float mAccuracy;
    private String mCity;
    private static boolean mFirstLocation = true;//第一次定位
    private boolean mMoveToCenter = true;//是否可以移动地图到定位点
    private boolean isMinMap; //缩小显示地图
    private boolean onScrolling;//正在滑动地图
    private boolean isPoiClick;
    private Marker mLocMarker;//自定义小蓝点
    private Marker mPoiMarker;
    private Circle mCircle;

    private GPSView mGpsView;
    private View mToastView;
    private TextView mTvToast;
    private RelativeLayout rlClickPoiDetail;
    private TextView tvClickPoiTitle;
    private TextView tvClickDistance;
    private ImageView ivClickPoiClose;
    private TextView tvRoute;

    private String currentPositionName;
    private LatLng currentPosition;
    private Poi clickPoi;
    private LatLng mClickPoiLatLng;
    private String mClickPoiName;
    private int mScreenHeight;

    private GeocodeSearch geocoderSearch;
    private LocationManager mLocMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        Log.d(TAG, "onCreate");
        mLocMgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        /*
         * 设置离线地图存储目录，在下载离线地图或初始化地图设置; 使用过程中可自行设置, 若自行设置了离线地图存储的路径，
         * 则需要在离线地图下载和使用地图页面都进行路径设置
         */
        // Demo中为了其他界面可以使用下载的离线地图，使用默认位置存储，屏蔽了自定义设置
        // MapsInitializer.sdcardDir =OffLineMapUtils.getSdCacheDir(this);
        textureMapView = findViewById(R.id.map);
        textureMapView.onCreate(savedInstanceState);// 此方法必须重写
        initToast();
        initViews();
        initAMap();
    }

    private void initViews() {
        mGpsView = findViewById(R.id.gps_view);
        mGpsView.setOnGPSViewClickListener(this);
        rlClickPoiDetail = findViewById(R.id.rl_click_poi_detail);
        rlClickPoiDetail.setVisibility(View.GONE);
        tvClickPoiTitle = findViewById(R.id.tv_title);
        tvClickDistance = findViewById(R.id.tv_distance);
        ivClickPoiClose = findViewById(R.id.iv_close);
        ivClickPoiClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPoiMarker != null) {
                    mPoiMarker.destroy();
                }
                hidePoiDetail();
                onGPSClick();
            }
        });
        tvRoute = findViewById(R.id.tv_route);
        tvRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aMap.clear();
                hidePoiDetail();
                onGPSClick();
                AmapNaviParams params = new AmapNaviParams(new Poi(currentPositionName,
                        currentPosition,
                        ""), null, clickPoi, AmapNaviType.DRIVER);
                params.setUseInnerVoice(true);
                AmapNaviPage.getInstance().showRouteActivity(getApplicationContext(), params,
                        MapActivity.this);
            }
        });
    }

    /**
     * 初始化AMap对象
     */
    private void initAMap() {
        if (aMap == null) {
            // 初始化显示
            aMap = textureMapView.getMap();
            //显示实时交通
            aMap.setTrafficEnabled(true);
            mUiSettings = aMap.getUiSettings();
            mUiSettings.setZoomPosition(AMapOptions.ZOOM_POSITION_RIGHT_CENTER);
            mUiSettings.setZoomControlsEnabled(false);
            mUiSettings.setScaleControlsEnabled(true);
//            mUiSettings.setAllGesturesEnabled(true);
            // 默认显示zoom级别
            aMap.moveCamera(CameraUpdateFactory.zoomTo(Float.valueOf(mZoomLevel)));

            // 定位
//            mUiSettings.setMyLocationButtonEnabled(true); // 是否显示默认的定位按钮
//            aMap.addOnMyLocationChangeListener(this);
            aMap.setLocationSource(this);
            aMap.setMyLocationEnabled(true); // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
            setLocationStyle();
            if (!isGpsOpen()) {
                showToast(getString(R.string.please_open_gps));
            }

            // poi 点击
            aMap.setOnPOIClickListener(this);
//            aMap.setOnMarkerClickListener(this);

            //地图手势事件
            aMap.setAMapGestureListener(this);

            // 逆地理编码
            geocoderSearch = new GeocodeSearch(this);
            geocoderSearch.setOnGeocodeSearchListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        textureMapView.onResume();
        if (mLocationOption != null && mLocationClient != null) {
            mLocationOption.setInterval(2000);//定位时间间隔，默认2000ms
            mLocationClient.setLocationOption(mLocationOption);
            aMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        textureMapView.onPause();
        mLocationOption.setInterval(20000);//定位时间间隔，默认2000ms
        mLocationClient.setLocationOption(mLocationOption);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState");
        textureMapView.onSaveInstanceState(outState);
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        textureMapView.onDestroy();
        mFirstLocation = true;
        deactivate();
        if (null != mLocationClient) {
            mLocationClient.onDestroy();
        }
        if (mLocMarker != null) {
            mLocMarker.destroy();
        }

        if (mPoiMarker != null) {
            mPoiMarker.destroy();
        }
    }

    @Override
    public void onInitNaviFailure() {

    }

    @Override
    public void onGetNavigationText(String s) {

    }

    @Override
    public void onLocationChange(AMapNaviLocation aMapNaviLocation) {

    }

    @Override
    public void onArriveDestination(boolean b) {

    }

    @Override
    public void onStartNavi(int i) {

    }

    @Override
    public void onCalculateRouteSuccess(int[] ints) {

    }

    @Override
    public void onCalculateRouteFailure(int i) {

    }

    @Override
    public void onStopSpeaking() {

    }

    @Override
    public void onReCalculateRoute(int i) {

    }

    @Override
    public void onExitPage(int i) {

    }

    @Override
    public void onStrategyChanged(int i) {

    }

    @Override
    public View getCustomNaviBottomView() {
        return null;
    }

    @Override
    public View getCustomNaviView() {
        return null;
    }

    @Override
    public void onArrivedWayPoint(int i) {

    }

    @Override
    public void onMapTypeChanged(int i) {

    }

    @Override
    public View getCustomMiddleView() {
        return null;
    }

    @Override
    public void onNaviDirectionChanged(int i) {

    }

    @Override
    public void onDayAndNightModeChanged(int i) {

    }

    @Override
    public void onBroadcastModeChanged(int i) {

    }

    @Override
    public void onScaleAutoChanged(boolean b) {

    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        mLocationListener = onLocationChangedListener;
        Log.d(TAG, "activate: mLocationListener = " + mLocationListener + "");
        //设置定位回调监听
        if (mLocationClient == null) {
            mLocationClient = new AMapLocationClient(this);
            mLocationOption = new AMapLocationClientOption();
            //设置定位监听
            mLocationClient.setLocationListener(this);
            //设置为高精度定位模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //设置定位参数
            mLocationOption.setInterval(2000);//定位时间间隔，默认2000ms
            mLocationOption.setHttpTimeOut(30000);//可选，设置网络请求超时时间。默认为30秒。在仅设备模式下无效
            mLocationOption.setLocationCacheEnable(true);//开启定位缓存
            mLocationClient.setLocationOption(mLocationOption);
//            mLocationClient.startLocation();
            if (null != mLocationClient) {
                mLocationClient.setLocationOption(mLocationOption);
                mLocationClient.startLocation();
                //运行时权限
                /*if (PermissionUtil.checkPermissions(this)) {
                    mLocationClient.startLocation();
                } else {
                    //未授予权限，动态申请
                    PermissionUtil.initPermissions(this, REQ_CODE_INIT);
                }*/
            }
        }
    }

    @Override
    public void deactivate() {
        mLocationListener = null;
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
        }
        mLocMarker = null;
        mLocationClient = null;
    }

    @Override
    public void onLocationChanged(AMapLocation location) {
        if (null == mLocationListener || null == location || location.getErrorCode() != 0) {
            if (location != null) {
                Log.d(TAG,
                        "定位失败：errorCode=" + location.getErrorCode() + ",errorMsg=" + location.getErrorInfo());
            }
            return;
        }
        this.mAMapLocation = location;
        if (onScrolling) {
            Log.e(TAG, "MapView is Scrolling by user,can not operate...");
            return;
        }
        //获取经纬度
        double lng = location.getLongitude();
        double lat = location.getLatitude();
        // 当前poiname和上次不相等才更新显示
//        if (location.getPoiName() != null && !location.getPoiName().equals(mClickPoiName)) {
//            if (!isPoiClick) {
//                // 点击poi时,定位位置和点击位置不一定一样
//                mClickPoiName = location.getPoiName();
//                showPoiNameText(String.format("在%s附近", mClickPoiName));
//            }
//        }
        //LogUtil.d(TAG, "定位成功，onLocationChanged： lng" + lng + ",lat=" + lat + ",mLocMarker=" +
        // mLocMarker + ",poiName=" + mPoiName+",getDescription="+location.getDescription()+",
        // address="+location.getAddress()+",getLocationDetail"+location.getLocationDetail()+",
        // street="+location.getStreet());

        //参数依次是：视角调整区域的中心点坐标、希望调整到的缩放级别、俯仰角0°~45°（垂直与地图时为0）、偏航角 0~360° (正北方为0)
        currentPosition = new LatLng(lat, lng);
        getAddress(new LatLonPoint(location.getLatitude(), location.getLongitude()));
        if (!location.getCity().equals(mCity)) {
            mCity = location.getCity();
        }

        //首次定位,选择移动到地图中心点并修改级别到15级
        //首次定位成功才修改地图中心点，并移动
        mAccuracy = location.getAccuracy();
        Log.d(TAG, "accuracy=" + mAccuracy + ",mFirstLocation=" + mFirstLocation);
        if (mFirstLocation) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, mZoomLevel),
                    new AMap.CancelableCallback() {
                        @Override
                        public void onFinish() {
                            mCurrentGpsState = STATE_LOCKED;
                            mGpsView.setGpsState(mCurrentGpsState);
                            mMapType = MyLocationStyle.LOCATION_TYPE_LOCATE;
                            addCircle(currentPosition, mAccuracy);//添加定位精度圆
                            addMarker(currentPosition);//添加定位图标
                            mFirstLocation = false;
                        }

                        @Override
                        public void onCancel() {

                        }
                    });
        } else {
            mCircle.setCenter(currentPosition);
            mCircle.setRadius(mAccuracy);
            mLocMarker.setPosition(currentPosition);
            if (mMoveToCenter) {
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, mZoomLevel));
            }
        }
    }

    @Override
    public void onMyLocationChange(Location location) {
        // 从location对象中获取经纬度信息，地址描述信息，建议拿到位置之后调用逆地理编码接口获取
        Log.d(TAG, "onMyLocationChange, mMapType = " + mMapType + "location = " + location);
        currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
        getAddress(new LatLonPoint(location.getLatitude(), location.getLongitude()));
        if (mFirstLocation) {
            Log.d(TAG, "onMyLocationChange, isFirstLocation.");
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, mZoomLevel),
                    new AMap.CancelableCallback() {
                        @Override
                        public void onFinish() {
                            Log.d(TAG, "onMyLocationChange, onFinish.");
                            mCurrentGpsState = STATE_LOCKED;
                            mGpsView.setGpsState(mCurrentGpsState);
                            mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;
                            setLocationStyle();
                            mFirstLocation = false;
                        }

                        @Override
                        public void onCancel() {

                        }
                    });
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
//        // 构造导航参数
//        NaviPara naviPara = new NaviPara();
//        // 设置终点位置
//        naviPara.setTargetPoint(marker.getPosition());
//        // 设置导航策略，这里是避免拥堵
//        naviPara.setNaviStyle(AMapUtils.DRIVING_AVOID_CONGESTION);
//        try {
//            // 调起高德地图导航
//            AMapUtils.openAMapNavi(naviPara, getApplicationContext());
//        } catch (com.amap.api.maps.AMapException e) {
//            // 如果没安装会进入异常，调起下载页面
//            AMapUtils.getLatestAMapApp(getApplicationContext());
//        }
//        AmapNaviParams params = new AmapNaviParams(new Poi(currentPositionName, currentPosition,
//                ""), null, clickPoi, AmapNaviType.DRIVER);
//        params.setUseInnerVoice(true);
//        AmapNaviPage.getInstance().showRouteActivity(getApplicationContext(), params,
//                MapActivity.this);
//        aMap.clear();
        return false;
    }

    @Override
    public void onPOIClick(Poi poi) {
        this.clickPoi = poi;
//        aMap.clear();
//        Log.i(TAG, poi.getPoiId() + poi.getName() + ", Coordinate = " + poi.getCoordinate());
//        mMoveToCenter = false;
//        MarkerOptions markOptiopns = new MarkerOptions();
//        markOptiopns.position(poi.getCoordinate());
//        TextView textView = new TextView(getApplicationContext());
//        textView.setText("到" + poi.getName() + "去");
//        textView.setGravity(Gravity.CENTER);
//        textView.setTextColor(Color.BLACK);
//        textView.setTextSize(22);
//        textView.setBackgroundResource(R.drawable.custom_info_bubble);
//        markOptiopns.icon(BitmapDescriptorFactory.fromView(textView));
//        aMap.addMarker(markOptiopns);
        Log.d(TAG, "onPOIClick, poi = " + poi);
        if (poi == null || poi.getCoordinate() == null || TextUtils.isEmpty(poi.getName())) {
            return;
        }
        // 当前点击坐标
        mClickPoiLatLng = poi.getCoordinate();
        // 当前正在处理poi点击
        isPoiClick = true;
        addPOIMarderAndShowDetail(poi.getCoordinate(), poi.getName());
    }

    /**
     * 响应逆地理编码
     */
    private void getAddress(final LatLonPoint latLonPoint) {
        RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 200,
                GeocodeSearch.AMAP);// 第一个参数表示一个Latlng，第二参数表示范围多少米，第三个参数表示是火系坐标系还是GPS原生坐标系
        geocoderSearch.getFromLocationAsyn(query);// 设置异步逆地理编码请求
    }

    /**
     * 逆地理编码回调
     */
    @Override
    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
        if (rCode == AMapException.CODE_AMAP_SUCCESS) {
            if (regeocodeResult != null && regeocodeResult.getRegeocodeAddress() != null
                    && regeocodeResult.getRegeocodeAddress().getFormatAddress() != null) {
                currentPositionName = regeocodeResult.getRegeocodeAddress().getFormatAddress();
                Log.d(TAG, "currentPositionName = " + currentPositionName);
            } else {
                Log.e(TAG, "no result");
            }
        } else {
            Log.e(TAG, "rCode=" + rCode);
        }
    }

    /**
     * 地理编码查询回调
     */
    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int i) {

    }

    @Override
    public void onGPSClick() {
        if (!isGpsOpen()) {
            showToast(getString(R.string.please_open_gps));
            return;
        }
        CameraUpdate cameraUpdate = null;
        mMoveToCenter = true;
        isPoiClick = false;
        //修改定位图标状态
        switch (mCurrentGpsState) {
            case STATE_LOCKED:
                mZoomLevel = 18;
                mAnimDuartion = 500;
                mCurrentGpsState = STATE_ROTATE;
                mMapType = MyLocationStyle.LOCATION_TYPE_MAP_ROTATE;
                cameraUpdate =
                        CameraUpdateFactory.newCameraPosition(new CameraPosition(currentPosition,
                                mZoomLevel, 30, 0));
                break;
            case STATE_UNLOCKED:
            case STATE_ROTATE:
                mZoomLevel = 16;
                mAnimDuartion = 500;
                mCurrentGpsState = STATE_LOCKED;
                mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;
                cameraUpdate =
                        CameraUpdateFactory.newCameraPosition(new CameraPosition(currentPosition,
                                mZoomLevel, 0, 0));
                break;
        }

        aMap.setMyLocationEnabled(true);
        Log.d(TAG, "onGPSClick:mCurrentGpsState=" + mCurrentGpsState + ",mMapType=" + mMapType);
        //改变定位图标状态
        mGpsView.setGpsState(mCurrentGpsState);
        //执行地图动效
        aMap.animateCamera(cameraUpdate, mAnimDuartion, new AMap.CancelableCallback() {
            @Override
            public void onFinish() {
                if (mPoiMarker != null) {
                    mPoiMarker.destroy();
                }
                hidePoiDetail();
            }

            @Override
            public void onCancel() {

            }
        });
        setLocationStyle();
        resetLocationMarker();
    }

    /**
     * 设置地图类型
     */
    private void setLocationStyle() {
        // 自定义系统定位蓝点
        if (null == myLocationStyle) {
            myLocationStyle = new MyLocationStyle();
            myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));
            myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));//圆圈的颜色,设为透明
//            myLocationStyle.strokeColor(STROKE_COLOR);
//            myLocationStyle.radiusFillColor(FILL_COLOR);//圆圈的颜色,设为透明
//            myLocationStyle.strokeWidth(5);
        }
        //定位、且将视角移动到地图中心点，定位点依照设备方向旋转， 并且会跟随设备移动。
        aMap.setMyLocationStyle(myLocationStyle.myLocationType(mMapType));
    }

    /**
     * 是否打开GPS
     *
     * @return
     */
    private boolean isGpsOpen() {
        return mLocMgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void initToast() {
        mToastView = ((LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.toast_layout, null, false);
        mTvToast = mToastView.findViewById(R.id.tv_toast);
    }

    protected void showToast(String text) {
        if (mToastView == null) {
            initToast();
        }
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        mTvToast.setText(text);
        toast.setView(mToastView);
        toast.setGravity(Gravity.CENTER, 0, 200);
        toast.show();
    }

    private void addCircle(LatLng latlng, double radius) {
        CircleOptions options = new CircleOptions();
        options.strokeWidth(1f);
        options.fillColor(FILL_COLOR);
        options.strokeColor(STROKE_COLOR);
        options.center(latlng);
        options.radius(radius);
        mCircle = aMap.addCircle(options);
    }

    private void addMarker(LatLng latlng) {
        /*if (mLocMarker != null) {
            return;
        }*/
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(this.getResources(),
                R.drawable.navi_map_gps_locked)));
        markerOptions.anchor(0.5f, 0.5f);
        markerOptions.position(latlng);
        mLocMarker = aMap.addMarker(markerOptions);
    }

    private void addRotateMarker(LatLng latlng) {
       /* if (mLocMarker != null) {
            return;
        }*/
        MarkerOptions markerOptions = new MarkerOptions();
        //3D效果
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(this.getResources(),
                R.drawable.navi_map_gps_3d)));
        markerOptions.anchor(0.5f, 0.5f);
        markerOptions.position(latlng);
        mLocMarker = aMap.addMarker(markerOptions);
    }

    /**
     * 根据当前地图状态重置定位蓝点
     */
    private void resetLocationMarker() {
        aMap.clear();
        mLocMarker = null;
        if (mGpsView.getGpsState() == GPSView.STATE_ROTATE) {
            //ROTATE模式不需要方向传感器
            //mSensorHelper.unRegisterSensorListener();
            addRotateMarker(currentPosition);
        } else {
            //mSensorHelper.registerSensorListener();
            addMarker(currentPosition);
        }
        addCircle(currentPosition, mAccuracy);
    }

    /**
     * 添加POImarker
     */
    private void addPOIMarderAndShowDetail(LatLng latLng, String poiName) {
        animMap(latLng);
        mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;
        mCurrentGpsState = STATE_UNLOCKED;
        //当前没有正在定位才能修改状态
        if (!mFirstLocation) {
            mGpsView.setGpsState(mCurrentGpsState);
        }
        mMoveToCenter = false;
        // 添加marker标记
        addPOIMarker(latLng);
        showClickPoiDetail(latLng, poiName);
    }

    /**
     * 显示poi点击底部BottomSheet
     */
    private void showClickPoiDetail(LatLng latLng, String poiName) {
        mClickPoiName = poiName;
        tvClickPoiTitle.setText(poiName);
        String distanceStr = calculateDistanceStr(currentPosition, latLng);
        if (rlClickPoiDetail.getVisibility() == View.GONE) {
            showPoiDetail(poiName, String.format("距离您%s", distanceStr));
        } else {
            tvClickPoiTitle.setText(poiName);
            tvClickDistance.setText(String.format("距离您%s", distanceStr));
        }
    }

    /**
     * 显示底部POI详情
     *
     * @param locTitle 定位标题,比如当前所在位置名称
     * @param locInfo  定位信息,比如当前在什么附近/距离当前位置多少米
     */
    public void showPoiDetail(String locTitle, String locInfo) {
        tvClickPoiTitle.setText(locTitle);
        tvClickDistance.setText(locInfo);
        rlClickPoiDetail.setVisibility(View.VISIBLE);
    }

    public void hidePoiDetail() {
        tvClickPoiTitle.setText(null);
        tvClickDistance.setText(null);
        rlClickPoiDetail.setVisibility(View.GONE);
    }

    /**
     * 显示当前所在poi点信息
     */
    private void showPoiNameText(String locInfo) {
        tvClickDistance.setText(locInfo);
    }

    private void addPOIMarker(LatLng latLng) {
//        aMap.clear(true);
        if (mPoiMarker != null) {
            mPoiMarker.destroy();
        }
        MarkerOptions markOptiopns = new MarkerOptions();
        markOptiopns.position(latLng);
        markOptiopns.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_mark));
        mPoiMarker = aMap.addMarker(markOptiopns);
    }

    /**
     * 移动地图中心点到指定位置
     *
     * @param latLng
     */
    private void animMap(LatLng latLng) {
        if (latLng != null) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, mZoomLevel));
        }
    }

    @Override
    public void onDoubleTap(float v, float v1) {
        mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;
        mCurrentGpsState = STATE_UNLOCKED;
        mGpsView.setGpsState(mCurrentGpsState);
        setLocationStyle();
        resetLocationMarker();
        mMoveToCenter = false;
    }

    @Override
    public void onSingleTap(float v, float v1) {

    }

    @Override
    public void onFling(float v, float v1) {

    }

    @Override
    public void onScroll(float v, float v1) {
        //避免重复调用闪屏，当手指up才重置为false
        if (!onScrolling) {
            onScrolling = true;
            Log.d(TAG, "onScroll,x=" + v + ",y=" + v1);
            //旋转不移动到中心点
            mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;
            mCurrentGpsState = STATE_UNLOCKED;
            //当前没有正在定位才能修改状态
            if (!mFirstLocation) {
                mGpsView.setGpsState(mCurrentGpsState);
            }
            mMoveToCenter = false;
            setLocationStyle();
            resetLocationMarker();
        }
    }

    @Override
    public void onLongPress(float v, float v1) {

    }

    @Override
    public void onDown(float v, float v1) {
        Log.d(TAG, "onDown");
    }

    @Override
    public void onUp(float v, float v1) {
        Log.d(TAG, "onUp");
        onScrolling = false;
    }

    @Override
    public void onMapStable() {

    }

    /**
     * 计算两点间距离,返回字符串,比如100米,1.8千米
     *
     * @param latLng1
     * @param latLng2
     * @return
     */
    private String calculateDistanceStr(LatLng latLng1, LatLng latLng2) {
        float distance = AMapUtils.calculateLineDistance(latLng1, latLng2);
        StringBuilder builder = new StringBuilder();
        if (distance < 1000F) {
            // 1000米以内不保留小数点
            builder.append(((int) distance)).append("米");
        } else if (distance >= 1000F) {
            // 保留一位小数点
            DecimalFormat decimalFormat = new DecimalFormat(".0");
            builder.append(decimalFormat.format(distance / 1000F)).append("千米");
        }
        return builder.toString();
    }

    /**
     * 非全面屏下 虚拟键高度(无论是否隐藏)
     *
     * @param context
     * @return
     */
    private int getNavigationBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen",
                "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}
