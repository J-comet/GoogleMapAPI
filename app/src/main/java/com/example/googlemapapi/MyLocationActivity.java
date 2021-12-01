package com.example.googlemapapi;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MyLocationActivity extends AppCompatActivity implements OnMapReadyCallback {

    private String TAG = "MyLocationActivity";

    private GoogleMap map;
    private Button btnLocation;
    private String[] locationPermissionCheck = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    private FusedLocationProviderClient providerClient;  // 구글 플레이 서비스 위치 API
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private double locationLatitude;
    private double locationLongitude;
    private TextView tvLocation, tvAddress;
    private EditText etSearchAddress;
    private Button btnSearch;

    /**
     * 권한
     * <p>
     * 문제점 - 현재 연달아 권한을 거부하면 더이상 권한요청을 하지 않고 있음
     * [ 해결 ]
     * 안드로이드는 권한을 한번 거부한 사용자에게 따로 권한을 요청 하지 않는다.
     * 하지만 이때 권한의 상태값은 바뀌게 되는데  shouldShowRequestPermissionRationale 를 이용해서
     * 특정 권한에 대해 거부했던 이력이 있는지 확인할 수 있게 된다.
     * <p>
     * 한번 거부한 사용자는 인텐트를 이용해 앱 설정으로 이동시켜
     * 사용자가 직접 권한을 획득할 수 있도록 변경.
     */

    // 권한 획득 launcher
    ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts
                            .RequestMultiplePermissions(), result -> {

                        Boolean fineLocationGranted = null;
                        Boolean coarseLocationGranted = null;

                        Log.e("result", "result :" + result.toString());

                        // API 24 이상에서 동작
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            fineLocationGranted = result.getOrDefault(
                                    Manifest.permission.ACCESS_FINE_LOCATION, false);
                            coarseLocationGranted = result.getOrDefault(
                                    Manifest.permission.ACCESS_COARSE_LOCATION, false);

                        } else {
                            // API 24 이하에서 동작
                            fineLocationGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                            coarseLocationGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                        }

                        /**
                         * Android 12 이상 위치권한 동작방식 변경
                         * 1) 정밀한 위치일 때 ACCESS_FINE_LOCATION , ACCESS_COARSE_LOCATION 두가지 권한 다 필요.
                         * 2) 대략적인 위치일 때 ACCESS_COARSE_LOCATION 권한만 필요.
                         */
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                            Log.e("fineLocation", "fineLocationGranted :" + fineLocationGranted);
                            Log.e("coarseLocation", "coarseLocationGranted :" + coarseLocationGranted);

                            if (fineLocationGranted != null && fineLocationGranted && coarseLocationGranted != null && coarseLocationGranted) {
                                // Precise location access granted.
                                // 정밀한 위치 사용에 대해서 위치권한 허용
                                Log.e("hs", "정밀한위치");

                            } else if (coarseLocationGranted != null && coarseLocationGranted && fineLocationGranted == false) {
                                // Only approximate location access granted.
                                // 대략적인 위치 사용에 대해서만 위치권한 허용
                                Log.e("hs", "대략적인위치");

                            } else {
                                // No location access granted.
                                // 권한획득 거부
                                Toast.makeText(this, "현재위치를 가져오려면 위치 권한은 필수 입니다", Toast.LENGTH_SHORT).show();
                                Log.e("hs", "위치권한거부");
                            }
                        } else {
                            Toast.makeText(this, "현재위치를 가져오려면 위치 권한은 필수 입니다", Toast.LENGTH_SHORT).show();
                            Log.e("hs", "위치권한거부");
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_location);

        // 1. 위치 서비스 클라이언트 생성
        providerClient = LocationServices.getFusedLocationProviderClient(this);

        btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (etSearchAddress.getText().length() > 0) {
                    // 1. 해당주소를 위도와 경도로 바꾸기
                    // 2. 바꾼 경도로 지도 이동시키기

                    // 사용자들에게 주소값 어떻게 받아올지 고민하기
                    String searchAddress = "대한민국 " + etSearchAddress.getText().toString();

                    String testSearchAddress = "대한민국 서울특별시 봉천로31길 24-3";
                    Log.e("hs", "searchAddress" + searchAddress);

                    changeForLatLng(searchAddress);
                    Log.e("hs", "changeForLatLng" + changeForLatLng(searchAddress));
                    String[] arrLatLng = changeForLatLng(searchAddress).split("%");
                    String strLat = arrLatLng[0];
                    String strLng = arrLatLng[1];

                    Log.e("hs", "strLat :"+ strLat + "/ strLng :"+ strLng);

                    double lat = Double.parseDouble(strLat);
                    double lng = Double.parseDouble(strLng);

                    // map 초기화
                    // 이전의 검색된 마커 삭제
                    map.clear();

                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(lat,lng), 16));

                    MarkerOptions options = new MarkerOptions();
                    options.position(new LatLng(lat,lng));
                    options.title("검색주소");
                    options.snippet(searchAddress);

                    map.addMarker(options);

                } else {
                    Toast.makeText(MyLocationActivity.this, "주소를 입력해주세요", Toast.LENGTH_SHORT).show();
                }
            }
        });
        etSearchAddress = findViewById(R.id.et_search_address);
        tvAddress = findViewById(R.id.tv_address);
        tvLocation = findViewById(R.id.tv_location);
        btnLocation = findViewById(R.id.btn_location);
        btnLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 정확한 위치에 대한 권한
                if (ContextCompat.checkSelfPermission(MyLocationActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(MyLocationActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(MyLocationActivity.this, "위치 권한을 모두 허용했습니다", Toast.LENGTH_SHORT).show();

                }
                // 대략적인 위치에 대한 권한
                else if (ContextCompat.checkSelfPermission(MyLocationActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(MyLocationActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(MyLocationActivity.this, "대략적인 위치에 대한 권한만 허용했습니다", Toast.LENGTH_SHORT).show();

                } else {

                    // 사용자가 권한요청을 명시적으로 거부했던 적 있는 경우 true 를 반환
                    if (ActivityCompat.shouldShowRequestPermissionRationale(MyLocationActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                            || ActivityCompat.shouldShowRequestPermissionRationale(MyLocationActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)) {

                        AlertDialog.Builder builder = new AlertDialog.Builder(MyLocationActivity.this);
                        builder.setTitle("권한체크")
                                .setMessage("위치정보를 얻기 위해\n위치권한을 허용해주세요")
                                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + getPackageName()));
                                        startActivity(intent);
                                    }
                                }).setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                Toast.makeText(MyLocationActivity.this, "위치권한을 허용 거부", Toast.LENGTH_SHORT).show();
                            }
                        });

                        builder.show();


                        Toast.makeText(MyLocationActivity.this, "권한거부 했던 사용자", Toast.LENGTH_SHORT).show();


                    } else {
                        locationPermissionRequest.launch(locationPermissionCheck);
                        Toast.makeText(MyLocationActivity.this, "모두 거부", Toast.LENGTH_SHORT).show();
                    }

                }
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.google_map);
        mapFragment.getMapAsync(this::onMapReady);

//        Places.initialize(getApplicationContext(), getString(R.string.maps_api_key));
//        PlacesClient placesClient = Places.createClient(this);
//        providerClient = LocationServices.getFusedLocationProviderClient(this);
    }


    @Override
    protected void onResume() {
        super.onResume();
        getDeviceLatLng();
    }

    // 주소 -> 위도/경도
    private String changeForLatLng(String searchAddress){
        String resultLatLng = null;
        double lat = 0;
        double lng = 0;

        Geocoder geocoder = new Geocoder(this);
        List<Address> addressList;

        try {
            addressList = geocoder.getFromLocationName(
                    searchAddress, // 지역 이름
                    5); // 읽을 개수
            Log.e("hs", "searchAddress" + addressList.toString());

            if (addressList == null || addressList.size() < 0) {
                Toast.makeText(MyLocationActivity.this, "찾을 수 없는 지역입니다.",Toast.LENGTH_SHORT).show();

            } else {
                lat = addressList.get(0).getLatitude();
                lng = addressList.get(0).getLongitude();
                resultLatLng = lat + "%" + lng;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return resultLatLng;
    }

    // 위도/경도 -> 주소로 변환
    private String changeForAddress(double lat, double lng) {
        String address = null;
        Geocoder geocoder = new Geocoder(this);

        List<Address> addressList;

        try {
            geocoder.getFromLocation(lat, lng, 1);
            addressList = geocoder.getFromLocation(lat, lng, 1);

            if (addressList == null || addressList.size() < 0) {
                Toast.makeText(this, "주소를 발견 하지 못했습니다", Toast.LENGTH_SHORT).show();
                address = null;

            } else {
                address = addressList.get(0).getAddressLine(0);
            }

            Log.e("hs", "address.get(0) = " + address);

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }

        return address;
    }

    // Device 위도/경도 구함.
    private void getDeviceLatLng() {

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10 * 1000);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                Log.e("getLocations()", "getLocations()" + locationResult.getLocations());

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        locationLatitude = location.getLatitude();
                        locationLongitude = location.getLongitude();
                        tvLocation.setText(String.format(Locale.KOREA, "%s -- %s", locationLatitude, locationLongitude));
                        tvAddress.setText(changeForAddress(locationLatitude, locationLongitude));
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (map != null) {
                map.setMyLocationEnabled(true);  // 오른쪽 상단의 내위치로 이동시키기 버튼 활성화
                map.getUiSettings().setMyLocationButtonEnabled(true);
            }

            providerClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }


    // Device 현재 위치 구함.
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {

            Task<Location> locationResult = providerClient.getLastLocation();
            locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    if (task.isSuccessful()) {
                        // Set the map's camera position to the current location of the device.
                        if (task.getResult() != null) {
                            map.setMyLocationEnabled(true);

                            double resultLat = task.getResult().getLatitude();
                            double resultLng = task.getResult().getLongitude();

                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(resultLat, resultLng), 16));
                        }
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        Log.e(TAG, "Exception: %s", task.getException());
                        map.moveCamera(CameraUpdateFactory
                                .newLatLngZoom(new LatLng(30, 40), 10));
                        map.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                }
            });

        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }


    /**
     * 지도가 사용 준비가 되었을 때 호출되는 메서드
     * (Null 값이 아닌 구글맵 객체를 파라미터로 제공해 줄 수 있을 때 호출)
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;

        getDeviceLocation();

//        getMyLatLng();
//        Log.e("위도경도", locationLatitude +" : "+ locationLongitude);

//        LatLng curLatLng = new LatLng(locationLatitude, locationLongitude);
//
//        MarkerOptions options = new MarkerOptions();
//        options.position(curLatLng);
//        options.title("내 위치");
//        options.snippet("어딜까");
//
//        map.addMarker(options);
//
//        map.moveCamera(CameraUpdateFactory.newLatLngZoom(curLatLng, 10));
    }
}