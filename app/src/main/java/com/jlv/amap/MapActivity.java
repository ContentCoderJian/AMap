package com.jlv.amap;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
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
import com.jlv.amap.view.GPSView;

public class MapActivity extends CheckPermissionsActivity implements INaviInfoCallback,
        AMap.OnMyLocationChangeListener, AMap.OnPOIClickListener,
        AMap.OnMarkerClickListener, GeocodeSearch.OnGeocodeSearchListener,
        GPSView.OnGPSViewClickListener {
    private static final String TAG = "MapActivity";
    private int mCurrentGpsState = STATE_UNLOCKED;//当前定位状态
    private static final int STATE_UNLOCKED = 0;//未定位状态，默认状态
    private static final int STATE_LOCKED = 1;//定位状态
    private static final int STATE_ROTATE = 2;//根据地图方向旋转状态
    private int mZoomLevel = 16;//地图缩放级别，最大缩放级别为20
    private static long mAnimDuartion = 500L;//地图动效时长
    private int mMapType = MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER;//地图状态类型

    private AMap aMap;
    private TextureMapView textureMapView;
    private UiSettings mUiSettings;
    private MyLocationStyle myLocationStyle;

    private GPSView mGpsView;
    private View mToastView;
    private TextView mTvToast;

    private String currentPositionName;
    private LatLng currentPosition;
    private Poi clickPoi;

    private GeocodeSearch geocoderSearch;
    private LocationManager mLocMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLocMgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        /*
         * 设置离线地图存储目录，在下载离线地图或初始化地图设置; 使用过程中可自行设置, 若自行设置了离线地图存储的路径，
         * 则需要在离线地图下载和使用地图页面都进行路径设置
         */
        // Demo中为了其他界面可以使用下载的离线地图，使用默认位置存储，屏蔽了自定义设置
        // MapsInitializer.sdcardDir =OffLineMapUtils.getSdCacheDir(this);
        textureMapView = findViewById(R.id.map);
        textureMapView.onCreate(savedInstanceState);// 此方法必须重写
        mGpsView = findViewById(R.id.gps_view);
        mGpsView.setOnGPSViewClickListener(this);
        initToast();
        initAMap();
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
            mUiSettings.setZoomControlsEnabled(true);
            mUiSettings.setScaleControlsEnabled(true);
//            mUiSettings.setAllGesturesEnabled(true);
            // 默认显示zoom级别
            aMap.moveCamera(CameraUpdateFactory.zoomTo(Float.valueOf(mZoomLevel)));

            // 定位
//            mUiSettings.setMyLocationButtonEnabled(true); // 是否显示默认的定位按钮
            aMap.addOnMyLocationChangeListener(this);
            myLocationStyle = new MyLocationStyle();//初始化定位蓝点样式类myLocationStyle.myLocationType
            // (MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);
            // 连续定位、且将视角移动到地图中心点，定位点依照设备方向旋转，并且会跟随设备移动。（1秒1次定位）如果不设置myLocationType，默认也会执行此种模式。
            myLocationStyle.interval(10000); //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
            myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE);
            //定位一次，且将视角移动到地图中心点。
            aMap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
            aMap.setMyLocationEnabled(true); // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
            if (!isGpsOpen()) {
                showToast(getString(R.string.please_open_gps));
            } else {
                mCurrentGpsState = STATE_LOCKED;
                mGpsView.setGpsState(mCurrentGpsState);
            }

            // poi 点击
            aMap.setOnPOIClickListener(this);
            aMap.setOnMarkerClickListener(this);

            // 逆地理编码
            geocoderSearch = new GeocodeSearch(this);
            geocoderSearch.setOnGeocodeSearchListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        textureMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        textureMapView.onPause();
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        textureMapView.onSaveInstanceState(outState);
    }

    /**
     * 方法必须重写
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        textureMapView.onDestroy();
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
    public void onMyLocationChange(Location location) {
        // 从location对象中获取经纬度信息，地址描述信息，建议拿到位置之后调用逆地理编码接口获取
        Log.d(TAG, "location = " + location);
        if (aMap.getMyLocationStyle().getMyLocationType() != MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER) {
            aMap.setMyLocationStyle(myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER));
        }
        getAddress(new LatLonPoint(location.getLatitude(), location.getLongitude()));
        currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
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
        AmapNaviParams params = new AmapNaviParams(new Poi(currentPositionName, currentPosition,
                ""), null, clickPoi, AmapNaviType.DRIVER);
        params.setUseInnerVoice(true);
        AmapNaviPage.getInstance().showRouteActivity(getApplicationContext(), params,
                MapActivity.this);
        aMap.clear();
        return false;
    }

    @Override
    public void onPOIClick(Poi poi) {
        this.clickPoi = poi;
        aMap.clear();
        Log.i("Amap", poi.getPoiId() + poi.getName() + ", Coordinate = " + poi.getCoordinate());
        MarkerOptions markOptiopns = new MarkerOptions();
        markOptiopns.position(poi.getCoordinate());
        TextView textView = new TextView(getApplicationContext());
        textView.setText("到" + poi.getName() + "去");
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundResource(R.drawable.custom_info_bubble);
        markOptiopns.icon(BitmapDescriptorFactory.fromView(textView));
        aMap.addMarker(markOptiopns);
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
        //修改定位图标状态
        switch (mCurrentGpsState) {
            case STATE_LOCKED:
                mZoomLevel = 18;
                mAnimDuartion = 500;
                mCurrentGpsState = STATE_ROTATE;
//                setLocationStyle(MyLocationStyle.LOCATION_TYPE_MAP_ROTATE);
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
            }

            @Override
            public void onCancel() {

            }
        });
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
}
