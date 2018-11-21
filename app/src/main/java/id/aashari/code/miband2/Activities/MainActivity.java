package id.aashari.code.miband2.Activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Set;

import id.aashari.code.miband2.Helpers.CustomBluetoothProfile;
import id.aashari.code.miband2.R;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import static android.support.constraint.Constraints.TAG;

public class MainActivity extends Activity {


    Boolean isListeningHeartRate = false;

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;

    Button btnStartConnecting, btnGetBatteryInfo, btnGetHeartRate, btnWalkingInfo, btnStartVibrate, btnStopVibrate;
    EditText txtPhysicalAddress;
    TextView txtState, txtByte, showmyIP;
    private String mDeviceName;
    private String mDeviceAddress;

    private static Socket s;
    private static InputStreamReader isr;
    private static BufferedReader br;
    private static String ip_server = "192.168.43.13";//Server IP Address
    private static PrintWriter printWriter;
    private static String my_IP;//local IP Address

    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView StrLatLong;
    double latShow = -6.8880713;
    double longShow = 107.6117433;
    String latShowStr;
    String longShowStr;

    private ConnectivityManager mComMgr;
    public NetworkReceiver mReceiver;

    Button get_unique_code;

    TextView print_unique_code;

    String server_url = "http://www.mocky.io/v2/5bc664273200004e000b0329";//insert the url or server address here

    private String heartRateMeasured;
    private String mark = "#";
    private String netID = "123";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeObjects();
        initilaizeComponents();
        initializeEvents();

        getBoundedDevice();

        //integration
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location)
            {

                latShow = location.getLatitude();
                longShow = location.getLongitude();
                Log.v(TAG, latShowStr);
                Log.v(TAG, longShowStr);
                latShowStr = Double.toString(latShow);
                longShowStr = Double.toString(longShow);

                StrLatLong.setText("\nLat = " + latShowStr + "\nLong = " + longShowStr);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 0, locationListener);

        mComMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mReceiver= new NetworkReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(mReceiver, filter);

        get_unique_code.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //send request by http
                final RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);

                StringRequest stringRequest = new StringRequest(Request.Method.POST, server_url,

                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {

                                print_unique_code.setText("Your Unique Code is " + response);
                                requestQueue.stop();
                            }
                        }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        print_unique_code.setText("Error to receive Unique Code");
                        error.printStackTrace();
                        requestQueue.stop();
                    }
                });
                requestQueue.add(stringRequest);
            }
        });

        //interrupt handling 1
        Thread t1 = new Thread(){
            @Override
            public void run(){
                while( !isInterrupted() ){
                    try {
                        Thread.sleep(60000);//tiap 120000millis = 120 sekon = 2 menit

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //startScanHeartRate();
                                sendDataToServer();//send data here every 120000 ms
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t1.start();//start doing Thread on new activity

        //interrupt handling 2
        Thread t2 = new Thread(){
            @Override
            public void run(){
                while( !isInterrupted() ){
                    try {
                        Thread.sleep(40000);//tiap 60000millis = 60 sekon = 1 menit

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startScanHeartRate();
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t2.start();//start doing Thread on new activity

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        my_IP = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
        //showmyIP.setText("My IP Address: " + my_IP);


    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        //unregister broadcast receiver during background activity
        if(mReceiver != null){
            unregisterReceiver(mReceiver);
        }
    }

    public void sendDataToServer()
    {
        sendTask st = new sendTask();
        st.execute();
        Toast.makeText(getApplicationContext(), "Data sent : " + heartRateMeasured + "| " + latShowStr + "| " + longShowStr, Toast.LENGTH_LONG).show();
    }

    public class sendTask extends AsyncTask<Void, Void, Void>//Section to send data via TCP
    {
        @Override
        protected Void doInBackground(Void... voids)
        {
            try
            {
                s = new Socket(ip_server,5000);//connect to socket port at port 5000
                latShowStr = Double.toString(latShow);
                longShowStr = Double.toString(longShow);

                printWriter = new PrintWriter(s.getOutputStream());
                printWriter.print(netID);
                printWriter.print(mark);
                printWriter.print(heartRateMeasured);
                printWriter.print(mark);
                printWriter.print(latShow);
                printWriter.print(mark);
                printWriter.print(longShow);
                printWriter.print(mark);
                printWriter.print(my_IP);
                //printWriter.write();
                printWriter.flush();
                printWriter.close();
                s.close();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            return null;
        }
    }

    //Network status button event handler
    public void onShowNetworkStatus(View v)
    {
        //make sure connection manager instance is not null variable
        if(mComMgr != null){
            //active network info struct
            NetworkInfo networkInfo = mComMgr.getActiveNetworkInfo();

            //check if active network interface has internet connection available
            if(networkInfo != null){

                //check if active network is Wifi
                boolean isWifi = mComMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();

                //check if active one is mobile data
                boolean isGSM = mComMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected();

                //check if bluetooth is connected
                //boolean isBTAvailable = mComMgr.getNetworkInfo(ConnectivityManager.TYPE_BLUETOOTH).isConnected();

                if( networkInfo.isConnected() ) {
                    if( isWifi ){
                        //display toast message that network is active and connected through WiFi connection
                        Toast.makeText(this, "Network is Available by WiFi Connection", Toast.LENGTH_SHORT).show();
                    } else if( isGSM ){
                        //display toast message that network is active and connected through GSM connection
                        Toast.makeText(this, "Network is Available by GSM/Mobile Data Connection", Toast.LENGTH_SHORT).show();
                    }
                }
            } else{
                //display toast message that network is not active
                Toast.makeText(this, "Network is Currently Not Available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class NetworkReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent){

            //get network info structure
            NetworkInfo networkInfo = mComMgr.getActiveNetworkInfo();

            if(networkInfo != null){
                //check if active network is Wifi
                boolean isWifiAvailable = mComMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();

                //check if active one is mobile data
                boolean isGSMAvailable = mComMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected();

                //check if bluetooth is connected
                //boolean isBTAvailable = mComMgr.getNetworkInfo(ConnectivityManager.TYPE_BLUETOOTH).isConnected();

                if(isWifiAvailable){
                    //Display wifi connected as toast
                    Toast.makeText(context, "WiFi is connected", Toast.LENGTH_SHORT).show();
                } else if(isGSMAvailable){
                    //Display GSM connected as toast
                    Toast.makeText(context, "GSM data is connected", Toast.LENGTH_SHORT).show();
                } else {
                    //Display network not available as toast
                    Toast.makeText(context, "Not connected/Not Available", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    void getBoundedDevice() {

        mDeviceName = getIntent().getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = getIntent().getStringExtra(EXTRAS_DEVICE_ADDRESS);
        txtPhysicalAddress.setText(mDeviceAddress);

        Set<BluetoothDevice> boundedDevice = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bd : boundedDevice) {
            if (bd.getName().contains("MI Band 2")) {
                txtPhysicalAddress.setText(bd.getAddress());
            }
        }
    }

    void initializeObjects() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    void initilaizeComponents() {
        btnStartConnecting = (Button) findViewById(R.id.btnStartConnecting);
        btnGetBatteryInfo = (Button) findViewById(R.id.btnGetBatteryInfo);
        btnWalkingInfo = (Button) findViewById(R.id.btnWalkingInfo);
        btnStartVibrate = (Button) findViewById(R.id.btnStartVibrate);
        btnStopVibrate = (Button) findViewById(R.id.btnStopVibrate);
        btnGetHeartRate = (Button) findViewById(R.id.btnGetHeartRate);
        txtPhysicalAddress = (EditText) findViewById(R.id.txtPhysicalAddress);
        txtState = (TextView) findViewById(R.id.txtState);
        txtByte = (TextView) findViewById(R.id.txtByte);
        StrLatLong = findViewById(R.id.textView2);
        get_unique_code = findViewById(R.id.get_unique_code_button);
        print_unique_code = findViewById(R.id.unique_code);
        showmyIP = (TextView) findViewById(R.id.myIPAddress);
    }

    void initializeEvents() {
        btnStartConnecting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConnecting();
            }
        });
        btnGetBatteryInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getBatteryStatus();
            }
        });
        btnStartVibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVibrate();
            }
        });
        btnStopVibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopVibrate();
            }
        });
        btnGetHeartRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScanHeartRate();
            }
        });
    }

    void startConnecting() {

        String address = txtPhysicalAddress.getText().toString();
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);

        Log.v("test", "Connecting to " + address);
        Log.v("test", "Device name " + bluetoothDevice.getName());

        bluetoothGatt = bluetoothDevice.connectGatt(this, true, bluetoothGattCallback);

    }

    void stateConnected() {
        bluetoothGatt.discoverServices();
        txtState.setText("Connected");
    }

    void stateDisconnected() {
        bluetoothGatt.disconnect();
        txtState.setText("Disconnected");
    }

    void startScanHeartRate() {
        txtByte.setText("...");
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.controlCharacteristic);
        bchar.setValue(new byte[]{21, 2, 1});
        bluetoothGatt.writeCharacteristic(bchar);
    }

    void listenHeartRate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.measurementCharacteristic);
        bluetoothGatt.setCharacteristicNotification(bchar, true);
        BluetoothGattDescriptor descriptor = bchar.getDescriptor(CustomBluetoothProfile.HeartRate.descriptor);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
        isListeningHeartRate = true;
    }

    void getBatteryStatus() {
        txtByte.setText("...");
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.Basic.service)
                .getCharacteristic(CustomBluetoothProfile.Basic.batteryCharacteristic);
        if (!bluetoothGatt.readCharacteristic(bchar)) {
            Toast.makeText(this, "Failed get battery info", Toast.LENGTH_SHORT).show();
        }

    }

    void startVibrate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.AlertNotification.service)
                .getCharacteristic(CustomBluetoothProfile.AlertNotification.alertCharacteristic);
        bchar.setValue(new byte[]{2});
        if (!bluetoothGatt.writeCharacteristic(bchar)) {
            Toast.makeText(this, "Failed start vibrate", Toast.LENGTH_SHORT).show();
        }
    }

    void stopVibrate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.AlertNotification.service)
                .getCharacteristic(CustomBluetoothProfile.AlertNotification.alertCharacteristic);
        bchar.setValue(new byte[]{0});
        if (!bluetoothGatt.writeCharacteristic(bchar)) {
            Toast.makeText(this, "Failed stop vibrate", Toast.LENGTH_SHORT).show();
        }
    }

    final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.v("test", "onConnectionStateChange");

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                stateConnected();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stateDisconnected();
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.v("test", "onServicesDiscovered");
            listenHeartRate();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.v("test", "onCharacteristicRead");
            byte[] data = characteristic.getValue();
            //txtByte.setText(Arrays.toString(data));
            heartRateMeasured = Arrays.toString(data);
            txtByte.setText(heartRateMeasured);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.v("test", "onCharacteristicWrite");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.v("test", "onCharacteristicChanged");
            byte[] data = characteristic.getValue();
            //txtByte.setText(Arrays.toString(data));
            heartRateMeasured = Arrays.toString(data);
            txtByte.setText(heartRateMeasured);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.v("test", "onDescriptorRead");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.v("test", "onDescriptorWrite");
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.v("test", "onReliableWriteCompleted");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.v("test", "onReadRemoteRssi");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.v("test", "onMtuChanged");
        }

    };

}
